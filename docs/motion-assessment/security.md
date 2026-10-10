# 安全、隐私与 AI 护栏

## 访问控制与隔离

* 11 张 `rehab_motion_*` 表全部继承 `TenantBaseDO`（`tenant_id` + 索引），`everyMotionTableIsTenantScoped`
  用反射检查；跨租户行不可见、非授权患者返回禁止（`otherTenantRowIsInvisibleAndOtherPatientIsForbidden`）。
* 患者可见性复用 `RehabDataPermissionService`（机构/治疗师数据权限），不新增旁路。
* 菜单 9700“动作评估”，按钮权限 `rehab:motion:{query,create,upload,process,review,sign,export,audit}`
  （9710–9717）；前台/文员角色不能执行审核、签署等临床动作（`clerkCannotPerformClinicalActions`）。
* 签署后只读；更正（amend）必须在已完成状态并填写原因，旧报告被标记为 superseded，全程留痕。
* 后台异步任务在 `TenantUtils.execute(tenantId, …)` 内执行；任务领取在 `executeIgnore` 中以租约锁（`lock-seconds`）进行，同组任务不并发。

## 上传安全

* 服务端白名单分类（`MotionFileRules`）：`.mot/.trc/.osim/sessionMetadata.yaml/视频`；拒绝目录穿越、绝对路径、
  反斜杠、未知类型；内容嗅探阻止改扩展名的文件；拒绝可执行序列化格式（`.pkl` 等）与 VTP/图片/日志。
* 单文件上限：mot/trc/视频 16 MB、osim 8 MB、metadata 256 KB；路径 ≤ 255；Spring multipart 16 MB / 32 MB、
  Nginx 32 MB 不放宽。前端从 `GET /rehab/motion/upload-policy` 取同一份规则过滤，服务端逐个复核；前后端规则
  快照 `src/test/resources/motion/upload-policy.json` 由测试强制一致。
* 视频需要评估上的“视频授权”才接受；未授权时前端直接跳过全部视频，服务端也拒绝。
* 原始数据存储在 `REHAB_STORAGE_PATH/motion`，不进 SQL；SQL 中只保存路径、SHA-256、大小与元数据。
* 引擎侧 zip 解包拒绝不安全路径、符号链接、异常文件名与压缩炸弹；请求体大小受限。

## 引擎通信

* 引擎仅内部网络可达（Compose 不映射宿主端口），除 `/internal/v1/health` 外均需 Bearer 令牌（≥16 位，
  部署 preflight 要求 ≥32 位随机串且不得复用其他密码）。
* 容器只读根文件系统、`/tmp` tmpfs、`cap_drop: ALL`、`no-new-privileges`。
* 4xx 视为致命不重试，5xx/429/超时指数退避重试（最多 5 次）；异常消息不含令牌与 URL 凭据
  （`timeoutIsRetryableAndMessageHasNoSecrets`）。只接受安全格式的错误码。
* OpenCap 令牌（可选）只存在于引擎容器环境变量。

## 日志与隐私

* 日志只记录评估/任务编号、状态、错误码、耗时等元数据，不记录患者姓名、证件、联系方式、视频内容。
* OpenCap `sessionMetadata` 只保留技术字段（模型、相机数、版本等），丢弃人员信息
  （`test_metadata_parser_keeps_technical_fields_only`）。
* 审计：建档/修改/删除、文件上传/下载/删除、Trial 映射、人工录入修改（含原因）、发起/取消/重试任务、评分审核、AI 草稿接受/拒绝/重生成、签署、更正、报告查看与 PDF 导出均写审计日志，
  `rehab:motion:audit` 权限可在“审计”页查看。

## AI 护栏

* 默认关闭：`yudao.rehab.motion.ai-enabled=false` 且全局 `OPENAI_ENABLE_AI_ANALYSIS=false`（Compose 强制）。
  两者同时开启才调用；密钥只在后端环境变量。
* 输入仅为去标识化指标与分数摘要（无姓名、无视频、无自由文本病史），只携带被引用的指标
  （`payloadIsDeidentifiedAndOnlyCarriesReferencedMetrics`）。
* 输出 JSON Schema 严格校验，出现分数等额外字段即拒绝（`schemaRejectsExtraFieldsSuchAsScores`）；
  每条结论必须引用已知证据 ID，涉及改分、诊断的语句被剔除（`claimsMustCiteKnownEvidenceAndCannotChangeScores`）。
* AI 草稿的接受/拒绝永远不改动分数（`aiDraftReviewNeverTouchesScores`）；分析重算后旧草稿标记 stale 不能接受。
* AI 关闭、超时或失败时生成带证据引用的模板草稿，评分/审核/签署/PDF 全部可用
  （`fallbackDraftWorksWithoutAiAndCitesEvidence`）。

## 签署阻断条件

未处于待审核、分析已过期（输入版本变化）、仍有进行中的任务、存在未审核分数（含 needs_recheck）、
存在未处理的 AI 草稿 —— 任一成立都不能签署（`signBlockersCoverStaleActiveUnreviewedAndUnhandledDraft`）。
