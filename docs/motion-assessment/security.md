# 安全、隐私与 AI 护栏

## 访问控制与隔离

* 11 张 `rehab_motion_*` 表全部继承 `TenantBaseDO`（`tenant_id` + 索引），`everyMotionTableIsTenantScoped`
  用反射检查；跨租户行不可见、非授权患者返回禁止（`otherTenantRowIsInvisibleAndOtherPatientIsForbidden`）。
* 患者可见性复用 `RehabDataPermissionService`（机构/治疗师数据权限），不新增旁路。
* 菜单 9700“动作评估”，按钮权限 `rehab:motion:{query,create,upload,process,review,sign,export,audit}`
  （9710–9717）；前台/文员角色不能执行审核、签署等临床动作（`clerkCannotPerformClinicalActions`）。
* 临床写入的业务服务二次校验现为**显式白名单**：只有 `REHAB_THERAPIST` 或 `SUPER_ADMIN` 角色可通过 `requireClinician`；
  既不是治疗师也不是管理员的普通角色、空用户 ID 都拒绝，而不是仅仅排除文员。文员继续使用既有
  `CLERK_WRITE_FORBIDDEN` 错误码，其他未授权角色返回 `CLINICAL_ROLE_REQUIRED`。这是临床写入权限的
  服务层补强，不能取代控制器 `@PreAuthorize`、患者可见性和租户隔离。回归包含
  `arbitraryRoleAndMissingUserCannotWriteClinicalRecords`、`assignedClinicalRolesMayWriteEvenIfAlsoClerk`。
* 登录身份命名空间隔离：显式 `MEMBER` 会话不能借助与 `ADMIN` 管理员/治疗师同值的数字 userId
  通过 `requireClinician`，即使服务层的角色查询恰好命中同值管理端角色；
  `memberIdCollisionWithAdminTherapistMustNotAuthorizeClinicalWrite` 分别验证同值 MEMBER 被拒和 ADMIN 被允许。
  对无 HTTP 请求上下文的后台任务仍以显式角色白名单校验，不因此自动放行患者端身份。
* 分页可见范围与审计身份进一步收紧：`RehabMotionAccess.visiblePatientIds` 对显式 `MEMBER` 和空
  userId 返回空集合，禁止调用 ADMIN 角色查询（其 `null` 返回值代表“全部患者”）；
  `RehabMotionAssessmentMapper.selectPage` 对空集合直接返回空页。审计 `role` 对
  MEMBER 明确标识为 `member`，不以同值管理端 ID 冒用 `admin/therapist` 角色。
  `memberIdCollisionCannotListAllPatientsOrMasqueradeInAuditRole` 使用相同数字 ID 的
  MEMBER/ADMIN 请求分别验证拒绝和管理端正常访问。该防护不表示患者身份门禁已通过；
  正式患者端 API 仍不得开放。
* 共享数据权限服务 `RehabDataPermissionService.getVisiblePatientIds` 已同步增加 MEMBER 与空 userId
  的拒绝规则，避免报告、评估、训练和仪表盘等其他消费者绕过动作评估专用服务；
  `canReadPatient` 对空 patientId / userId 直接拒绝，而已绑定的患者端用户仍仅走授权绑定校验。
  `RehabDataPermissionServiceTest` 使用合成 MEMBER/ADMIN 同值 ID 验证服务层不查询错位的管理员角色。
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

## 旧数据库迁移账本修复安全（尚未达到发布验收）

* 仅对旧 Compose 已创建 020–023 增量表、却没有登记迁移账本的特殊情况允许 `deploy/internal/migrate.sh adopt <version>`。必须事先人工核验备份并显式设置 `CONFIRM_ADOPT=ADOPT-REHAB-INITDB`。
* 已移除原先“只核对列名/顺序”的信任逻辑。`adopt_schema_guard.py` 首先以严格白名单解析纯 CREATE TABLE DDL，再只读比较 `information_schema.columns/statistics/tables` 中的**列类型、顺序、可空性、默认值、自增、自动更新时间、索引名称/顺序/唯一性、InnoDB 引擎和排序规则**。任何差异都拒绝账本写入；不读取患者业务行。
* 024 的菜单/授权写入与 025 的 ALTER 不属于纯建表迁移，**严禁 adopt**。必须通过独立合成数据库进行正常 apply、账本和备份恢复验证，不能通过登记伪造迁移完成。
* 迁移保护测试包含故意修改列类型、默认值、可空性、索引、引擎/排序规则等负例。真实 MySQL 的测试只允许在新建、无网络、tmpfs、无业务卷的随机容器中进行；不得使用既有患者库或已有 Docker volumes。
* 迁移脚本通过单表 DDL 核验**并不等于**完整权限、租户、恢复、并发与生产升级验收，正式发布依旧 NO-GO。

## 签署阻断条件

未处于待审核、分析已过期（输入版本变化）、仍有进行中的任务、存在未审核分数（含 needs_recheck）、
存在未处理的 AI 草稿 —— 任一成立都不能签署（`signBlockersCoverStaleActiveUnreviewedAndUnhandledDraft`）。
