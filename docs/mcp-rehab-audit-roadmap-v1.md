# 康复管理与机构运营系统技术审计及升级路线图 V1.0

- 审计日期：2026-09-20
- 目标仓库：SaberAltriaYi/rehab-management-system
- 基线：GitHub `master`，`995247148d9e8d90237ed648ab50c443f708fac8`
- 对应本地工作树：`rehab-management-integrate-all/`
- 性质：第一轮代码、配置、SQL、部署及产品适配静态审计；不是生产安全认证，不是已经通过运行验收的结论。
- 本轮边界：不修改业务代码、Schema、配置；不运行迁移、清理、安装、发布、AI API；仅新增审计文档。
- 配套报告：`docs/mcp-ruoyi-module-compatibility-v1.md`。

## 执行摘要

**结论：保留现有 RuoYi 模块化单体架构，在现有康复模块中渐进升级。不要重新做一套患者、评估或训练系统。**

系统已有患者主档、Episode、结构化评估模块、评估附件、报告与版本、训练计划/任务/执行、进度、复评触发、随访、审计及部署工具。当前最明显的差距不是“没有康复系统”，而是：

1. 数据库升级通道存在潜在整库康复业务数据删除路径，必须先封堵。
2. 评估状态与真实填写完整度不一致；草稿、自动保存、并发更新和现场操作尚不足以替代纸质表。
3. 视频、Visbody、OpenCap 尚未成为可溯源、按具体动作/Session 关联的完整原始资料体系。
4. 缺少独立、确定性、不调用 AI 的 Assessment Context Package 导出流程。
5. CRM 有桥接但不是统一个人主数据，恢复 CRM 会同时牵涉 BPM 和安全边界。
6. 现有权限不是空白，但文员全患者可见、直接 JDBC 跨模块查询等必须细化。

**建议次序：升级安全护栏 → 评估状态和安全草稿 → 手机/iPad 最小现场闭环 → 原始媒体/文件 → Validation 与 ChatGPT 导出 → CRM/训练/复评整合 → ERP/运营。AI 始终保持关闭。**

### 阅读导航

- 第 0 节：审计覆盖、证据与限制。
- 第 1—10 节：技术架构与核心数据模型。
- 第 11—18 节：现有能力、缺口、风险与上游关系。
- 第 19—29 节：模块恢复及目标数据/文件/导出设计。
- 第 30—40 节：迁移、代码处置、分级、路线图、分支与测试。
- 最后：第一个真正开始编码的功能及验收标准。

## 0. 审计方法、覆盖和限制

### 0.1 本次实际完成

- 经 MCP 查看工作区、仓库和 worktree，读取项目约束文件及关键代码。
- 通过 GitHub API 确认远端 master SHA 与本地主分支 HEAD 相同。
- 获取上游基线和项目基线的完整 Git tree（均未截断），对 6,761 个已跟踪文件进行路径/blob 比较；**这不等于逐行阅读全部 6,761 个文件**。
- 深入抽查患者、评估、附件、权限、AI、报告、复评、前端录入、数据库迁移、CI、备份恢复及 CRM/ERP 依赖。
- 核对 OpenCap 官方源码中的文件输出/下载路径，而非凭经验假设格式。
- 记录代码已证实的问题、条件性风险、需求缺口和待验证事项。

### 0.2 审计对象纠正

工作区根目录的 `rehab_reporter/` 是另一个 Python 报告工具，不是本项目主架构。此前基于根 README 的介绍不适用于本次指定的完整 RuoYi 系统。

`ruoyi-vue-pro/` 当前位于 `agent/lan-one-click-deployment`、HEAD `89aef780b9`，存在已修改及未跟踪内容；它不是 GitHub master 的同一工作树。本轮不把其中未提交的变化自动纳入主线结论。`rehab-management-integrate-all/` 对应 master，另有桌面与软著 worktree。后续严禁在错误目录开发或覆盖其他会话修改。

### 0.3 尚未验证

- 未连接运行数据库，未读取真实患者、凭据或备份；表结构依据迁移 SQL/DO，不代表部署现场已应用全部迁移。
- 未完成主工作树的完整未提交变更清单：全量 git 状态/差异操作耗时异常，已取消；关键文件以 MCP 读取和固定 SHA 源码交叉检查。
- 未启动系统、运行测试、构建或新一轮漏洞扫描；不能宣称全部流程正常、依赖安全或漏洞已被利用。
- 未核实现场数据库账号授权、对象存储 ACL、TLS 安装、备份可恢复性和公网暴露状况。
- 上游关系以已识别共同祖先为准，未宣称已与 2026-09-20 最新 Gitee 所有分支完成兼容比较。
- 未全量扫描 Git 历史中的秘密和真实资料；仓库已有扫描机制不代表本轮扫描通过。

这些是上线前必须补齐的门禁，不应以静态审计报告替代。

### 0.4 证据索引

以下路径均相对目标仓库根目录；行号对应审计基线或本轮读取版本。

| 编号 | 证据 |
|---|---|
| E01 | `pom.xml:10-48`；`yudao-server/pom.xml:22-120` |
| E02 | `yudao-ui/yudao-ui-admin-vue3-app/package.json:7-28,30-145` |
| E03 | `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/dataobject/` 下 patient、episode、assessment、plan、task、checkin、progress、report、binding |
| E04 | `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/assessment/RehabAssessmentServiceImpl.java:54-59,94-182,270-381,446-599` |
| E05 | `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/assessment/create/index.vue:248-255,391-415,434-491,531-566` |
| E06 | `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/RehabDataPermissionService.java:33-75` |
| E07 | `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/patient/RehabPatientServiceImpl.java:350-403,632-699` |
| E08 | `deploy/internal/migrations.manifest:2-20`；`deploy/internal/migrate.sh:21-26,57-70,73-103`；`deploy/internal/clean-demo-rehab-data.sql:1-38` |
| E09 | `sql/mysql/rehab-step3-v1.sql:10-126,223-249`；`sql/mysql/rehab-step9-tenant-v1.sql:13-53`；`sql/mysql/rehab-step10-integrity-v1.sql:21-76` |
| E10 | `yudao-server/src/main/resources/application-internal.yaml:127-150`；`yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/ai/RehabAiServiceImpl.java:74-91,597-616` |
| E11 | `deploy/internal/nginx.conf`；`deploy/internal/docker-compose.yml`；`deploy/internal/backup.sh`；`deploy/internal/restore.sh` |
| E12 | `.github/workflows/ci.yml:13-47,53-73`；`deploy/internal/build-server-isolated.sh`；`AGENTS.md` |
| E13 | `yudao-module-crm/pom.xml`；`yudao-module-erp/pom.xml`；各自 `dal/dataobject/` |
| E14 | `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/report/RehabReportServiceImpl.java:86,182-255,549-618,754-784` |
| E15 | `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/trigger/RehabReassessmentTriggerServiceImpl.java:125-146` |
| E16 | `deploy/internal/SECURITY_RISK_REGISTER.md:12-38`（历史登记，不是本轮复扫） |

## 1. 当前技术栈

| 层 | 已识别技术 | 解释 |
|---|---|---|
| 后端 | Java 编译目标 1.8，Spring Boot 2.7.18，Maven 多模块 | CI 使用 JDK 17 构建；不能因此称为 Spring Boot 3/Jakarta 项目 |
| 持久化 | MyBatis/MyBatis-Plus、MySQL、动态数据源/Druid | internal Compose 固定 MySQL 8.4.10；运行现场版本未验证 |
| 缓存/调度 | Redis、Quartz JDBC | Compose Redis 7.4.10-alpine；内部配置定时器启动 |
| 安全 | Spring Security、Token、RBAC、多租户与业务患者范围 | 不只是前端菜单控制 |
| 前端 | Vue 3.5.12、TypeScript 5.3.3、Vite 5.1.4、Element Plus 2.11.1、Pinia、Vue Router、Axios | 版本来自 package.json，不代表全部解析依赖无漏洞 |
| 报告 | Java DOCX 模板/POI 相关能力、PDFBox | 既有 V4.1 报告模板，不应替换成另一套 Python 报告系统 |
| 部署 | Docker Compose、Nginx TLS、Shell/PowerShell、GitHub Actions | 内部局域网交付边界 |
| 桌面 | Tauri v2 启动器 | 启动/管理容器，不是用桌面进程替代后台服务 |

## 2. 系统架构

```text
PC / 移动浏览器
  → Nginx HTTPS
  → Vue 管理端 / admin-api
  → yudao-server
      ├─ system：账号、权限、组织、租户
      ├─ infra：基础设施、通用文件等
      ├─ member：会员端身份支撑
      └─ rehab：患者 → Episode → 评估 → 报告 → 训练 → 进度/复评
  → MySQL + Redis + 康复私有文件卷

Tauri launcher → 管理本地部署服务
```

本项目仍是上游模块化单体。推荐继续沿用 Controller → Service → Mapper/DO 分层，不引入微服务、事件总线集群或另一套认证。未来 AI Gateway 应是隔离适配边界，不是第二套患者库。

## 3. 前端结构

主交付前端：`yudao-ui/yudao-ui-admin-vue3-app/`。

- `src/views/rehab/`：患者、评估、计划、打卡、工作台、预警、通知、复评、报告及保留的 AI 页面。
- 评估已有 `assessmentFormRegistry.ts`、结构化配置、各类型表单、SFMA 多个动作/分解表单，具备复用基础。
- 创建/编辑共用录入页；详情页汇总模块及附件；现状偏桌面后台，部分 YBT 布局已有响应式断点。
- 不新建第二个独立手机业务系统。优先在现有路由下增加现场模式组件，共用 API、字段契约和权限。
- 上游及其他 uniapp 目录的存在不代表现场评估功能已完成；不默认把它们都纳入第一阶段交付。

## 4. 后端结构与 API

`yudao-server` 装配业务模块；`yudao-module-rehab` 包含 admin/app controller、VO、service、mapper、dataobject、规则汇总和测试。核心 API 已有方法级权限注解及服务级患者范围检查。

保留 REST 风格和现有错误码。新草稿、验证、文件、导出接口必须复用统一认证、租户和患者范围，不能新增匿名下载或通过前端隐藏代替授权。

需要逐项复核更新目标：评估更新服务当前校验旧患者可见性，以及新 Episode/Patient 配对，但未在该方法中显式校验新患者是否可见（E04:152-169）。这属于条件性权限缺口，应以跨患者用例验证，并优先禁止无业务依据的评估迁移。

## 5. 数据库结构

现有逻辑关系：

```text
rehab_patient
 ├─ rehab_episode
 │   ├─ rehab_assessment_record
 │   │   ├─ rehab_assessment_module_data
 │   │   ├─ rehab_assessment_attachment
 │   │   └─ rehab_assessment_operation_log
 │   ├─ rehab_report → rehab_report_version
 │   ├─ rehab_care_plan → rehab_exercise_task / rehab_task_schedule
 │   ├─ rehab_daily_checkin → rehab_task_execution
 │   └─ rehab_progress_record / rehab_reassessment_trigger
 ├─ therapist_assignment / tags / followup / notifications / audit
 └─ patient_crm_binding / patient_user_binding
```

- 模块 JSON 存于 `longtext data_json`，同时有模块类型、状态、来源、版本；不是整份评估仅一个自由文本字段。
- `assessment_id + module_type` 唯一约束限制同一评估每种模块一条现态记录；多次试验需要明确数组/子记录，而非再插同模块行。
- 迁移 011 为康复表补 `tenant_id`，默认 1；历史多租户数据不能未经归属核实直接回填为 1。
- 迁移 016 补关系外键，使用 RESTRICT；不是级联物理删除。
- 核心 DO 继承 BaseDO 不足以断言租户失效：上游 TenantDatabaseInterceptor 对已知表也会加租户条件。直接 JDBC 查询则另需审计。
- 索引、外键、账本是否真正存在，需在用户批准的只读数据库会话中核对 information_schema；本轮未验证。

## 6. 当前 Client / Patient 模型

`RehabPatientDO` 已含 ID、患者编号、姓名、生日、年龄、性别、电话、联系人/紧急联系人、体重身高、优势侧、主诉、疼痛、病史/伤史/训练史、来源、状态、主责治疗师。

优点：业务主档已建立，不应再新建孤立 Client 表替代全部外键。

不足：生日和年龄可能漂移；联系人不等于正式监护授权；CRM customer、member account 和患者身份仍为分立实体。手机号可共享、可变更，不能作全局人唯一标识。CRM 桥接只提供关联/冲突状态，不等于主数据治理。

## 7. 当前 Assessment 模型

- 主表已有唯一内部 ID、assessmentNo、patientId、episodeId、评估类型/日期/评估人、状态、疼痛、质量和摘要。
- 子表已有 `moduleType/moduleStatus/dataJson/sourceType/version`。
- 已有静态、身体成分、NASM-CES、SFMA、FMS、YBT、OpenCap、观察、综合评估入口。
- 已有规则 summary builders 和 SFMA protocol 校验，不应删除成熟规则。

关键不足：

1. Assessment 类型不应同时承担“本次初评/复评”与“具体模块类型”两个语义。
2. 缺少明确 baseline/previous Assessment 关系及标准化测量指标字典。
3. `version` 是字符串，没有证明它锁定完整模板定义；不是并发锁。
4. 自动规则摘要直接 enrich 到模块 JSON，Raw 与 Generated 需要显式来源分层。
5. 部分默认模块可为空却标记 completed；完整性不可信，见第 13 节。
6. ASM/CS 的业务定义、表单来源和评分协议需要与治疗师确认，不能擅自等同 NASM-CES 或 FMS。

## 8. 当前 Training 模型

已有 CarePlan：患者、Episode、来源评估、治疗师、周期、短中长期目标 JSON、禁忌、注意事项、家庭/门店训练开关、复查周期。已有 ExerciseTask、Schedule、DailyCheckin、TaskExecution、ProgressRecord 及复评触发。

建议保留关系和历史记录，逐步加上：Problem → Goal → Intervention → Task → Outcome 的来源映射；训练调整版本与原因；课程签到与家庭训练执行的语义区分。不要把库存 ERP 或支付流水当成训练执行记录。已有目标 JSON 不需要立刻全量拆表，先对新增结构定义稳定字段。

## 9. 当前文件系统

康复附件当前落地 `storagePath/assessments/{assessmentId}/attachments`，数据库保存绝对路径、文件名/类型/大小、上传人、评估与模块关联、解析状态。报告落地独立 reports 目录。通用 infra 文件能力同时存在。

已有保护：16 MB 上限、扩展名白名单、随机服务端文件名、下载患者授权、canonical path 根目录检查、部分异常清理（E04）。

不足：无动作 item、左右侧、角度、OpenCap Session、原始文件版本/checksum 等专用字段；绝对路径迁移不便；下载一次读入 byte[] 不适合大视频；MIME 来自客户端，未在已读上传路径发现内容签名校验。当前不接收 mp4/mov/trc/mot/osim/zip，不能宣称支持完整动作视频或 OpenCap 原始文件。

## 10. 当前 AI 模块状态

**代码层已证实：internal 配置关闭 AI analysis/mock/platform bridge；上游 AI 模块不进入 server 的正常依赖列表。**

康复模块内部仍有 AI Job、Output、PromptTemplate、ReviewLog、Fallback 和客户端代码。`RehabAiServiceImpl` 同时检查配置开关与数据库开关，关闭时返回 fallback，不进入 `client.generateStructured`（E10）。因此“AI 关闭”不等于“仓库没有 AI 代码”。

保持原状，不配置 Key、不恢复 AI 菜单、不触发真实调用。后续测试应断言关闭状态网络调用次数为零、导出不依赖 AI、Mock 不伪装成专业结论。运行环境开关可能被外部覆盖，生产实际生效值仍待核验。

## 11. 当前已实现功能（实现依据，不等于本轮运行通过）

| 领域 | 已有代码/结构 |
|---|---|
| 客户/患者 | 建档、导入导出、治疗师分配、归属、状态、详情聚合 |
| 康复周期 | Episode、阶段/状态、患者关系 |
| 评估 | 类型化表单、结构化模块、规则汇总、SFMA 协议、附件 |
| 报告 | V4.1 DOCX 模板、PDF、报告版本、审核/锁定相关字段与服务 |
| 训练 | 计划、任务、排期、签到/打卡、执行 |
| 管理 | 进度、预警、随访、通知、复评触发、工作台 |
| 平台 | 登录、角色/菜单、租户、审计、系统/基础设施 |
| 交付 | 内部 TLS/Compose、迁移账本、备份恢复、桌面启动器、CI |

## 12. 半完成功能与新增需求缺口

- **现场快速评估：** 已有表单、手动保存和类型切换提醒；未形成耐中断、弱网、并发安全的现场流程。
- **Visbody：** 通用附件能保留 PDF，但缺少 Visbody 原件专用角色、确认/提取分层和原件历史语义。
- **视频：** 当前附件白名单明确不支持主要视频格式，且没有动作级关联。
- **OpenCap：** 有录入表单不等于 Session/Raw Files 管理，需新增关联层。
- **导出给 ChatGPT：** 当前报告生成不能替代完整 Context Package；未发现此专用闭环实现证据。
- **复评：** 已有触发和创建入口，不等于所有模块同指标可比的纵向数据体系。
- **CRM：** 已有桥接，但内部交付未启用完整 CRM，也没有统一 Client。
- **预约/课程包：** 根 README 明确无独立预约模块；不能用“已恢复上游”代替实际交付。
- **ERP/WMS/HRM：** ERP 源码存在；独立 WMS/HRM 模块未在根模块清单找到。不要将需求缺口误叫现有功能故障。

## 13. 当前 Bug / 静态可证实缺陷

### B01：初始化清理脚本可被升级通道执行——P0

证据：E08。清单 015 指向 `clean-demo-rehab-data.sql`；脚本无业务 WHERE 地删除患者、评估、报告、训练、AI 和审计等表，并关闭外键检查。`migrate.sh apply` 对未登记版本直接执行，无“必须空库/仅 bootstrap”检查。

触发条件：有业务数据但迁移账本缺少 015 的环境执行 apply，且通过前序检查。不能据此断言生产已被清空，但此路径足以阻断未核验数据库的升级。脚本中的“仅首次初始化”注释不是程序保护。

### B02：空模块/草稿可能被自动标记完成——P1

E04:458-470 初始化空 map 却使用 completed；E04:568-599 仅按模块状态计数判定整体完成。E05:434-469 即使用户选择 draft，模块状态也构造为 completed；创建/更新后重新刷新会覆盖非 reviewed 状态。用户以为保存草稿，系统却可能显示 completed。应将填写事实、Validation 与治疗师完成动作分离。

### B03：模块并发更新无显式冲突检测——P1 条件性数据丢失风险

E04:490-511 读取后直接 updateById；DO 无 @Version 并发字段，字符串 version 用作模块版本。双设备或重试可能静默后写覆盖，尚未动态复现。自动保存上线前必须解决。

### B04：评估编号取模——P2

E04:473-475 使用当天日期 + `id % 10000`；SQL assessment_no 唯一。若同一天的 ID 出现相差 10000 的记录，会发生编号碰撞/创建失败；单机构近期概率低，不应夸大为当前所有创建不可用。内部主键仍独立，不应更换历史 ID。

### B05：患者更新目标授权缺口——P1，确认跨患者可写时提升 P0

E04:152-169 先授权旧 patient，再接受新 patient/episode，仅核对二者配对。需要限制评估所有权不可普通编辑修改，或分别校验旧/新对象权限、依赖关系并记录正式转移审计。尚未运行越权 PoC。

## 14. 技术债与重复代码

- 核心服务承担较多职责，尤其报告、AI、评估；优先抽离 Context Builder、Validation、Storage 与状态策略，避免只因文件大而机械拆分。
- 数据和生成摘要共处模块 JSON，模板及指标语义需版本化。
- 现有版本字符串不等于并发锁、历史快照或模板注册表。
- 业务附件与 infra 文件存在两条能力路径，应复用底层存储接口，保留康复权限域，不强行迁移全部历史文件。
- SFMA 在前端协议/流程配置、后端协议/summary builder 中都有规则表达，存在漂移风险；未证明全部为可删除重复。先建立同一黄金样例，再决定共享生成源。
- 本轮按 blob 相同扫描较大 Java/TS/Vue 文件，没有发现需要据此立即删除的康复完全相同副本；语义重复检查未全量完成。
- Java 8 编译目标/Spring Boot 2.7 基线是升级债。不能夹带在第一轮现场录入功能里一次性迁移。
- CI 的构建与实际临床流程验证之间有缺口，见第 40 节。

## 15. 安全风险

| 风险 | 判断 | 处置 |
|---|---|---|
| 迁移清理业务数据 | 已证实危险执行路径 | P0 阻断，先改升级护栏 |
| CRM/member 直接 JDBC 查询无 tenant 条件 | E07:680-699；绕开 MyBatis 租户插件 | P1；恢复 CRM/多租户前必须补跨租户验证，确认实际泄露升 P0 |
| 文员可见全部患者 | E06:49-51；与用户目标的健康资料最小授权不一致 | 按“基本联系信息/健康详情/导出/媒体”拆权限，不仅拆菜单 |
| 媒体/导出扩大健康信息外泄面 | 当前通用上传不等于安全大文件处理 | 文件签名、隔离、授权、审计、清理、限额及下载控制 |
| 日志含评估内容 | operation log 保存 before/after JSON | 业务审计也属于健康数据，限制查看、导出、保留期；不能因通用访问日志关闭而声称日志无健康资料 |
| 旧依赖风险 | 历史风险登记有剩余 High/Critical，带局域网条件接受 | 本轮未复扫，不背书历史适用性；恢复模块/公网化后重新扫描 |
| 环境误用 | 通用 application 默认 local；internal 有专用加固 | 发布必须锁定 internal，不用开发配置公开运行 |
| 数据库与备份权限 | 配置存在但实际 grant/密钥保管未核验 | 上线前独立只读核验与恢复演练 |

内部 YAML 关闭 API 文档、调试认证和通用请求体日志，是应保留的加固。当前不判定项目“整体不安全”，也不能因为这些加固而忽略数据库迁移与业务权限风险。

## 16. 移动端现状

已有部分响应式表单，但创建/详情页仍有固定 12、16/8 分栏与宽表格。创建页有类型切换未保存提醒，没有在该页发现自动保存、离页/刷新拦截和持久恢复机制；其他全局保护未完整验证，因此不泛称整个应用完全没有保护。

Nginx `Permissions-Policy` 当前禁用 camera/microphone；若采用 getUserMedia/语音输入，必须按同源、用途和同意机制调整，不能全局放开。原生 `<input capture>` 的行为要按 Safari/Chrome 真机验证，不能简单推断同样被禁止。

目标：44—48 CSS px 触控目标；左右侧明确标签；主要动作一屏；保存中/已保存/待重试/冲突可见；中断恢复；弱网不丢已确认记录；视频上传队列不阻塞继续录入。先做到在线可靠、短暂断网恢复，不默认做长期离线 PWA 或无保护 localStorage 健康资料缓存。

## 17. 与上游 RuoYi 的差异

Git merge-base：`d3400b70d61fca76e14be8255f9fc188dfff6e81`，与本地 origin/master 相同；GitHub compare 为 ahead 33 / behind 0，相对这个固定基线而言。

完整 tree blob 对比：**2,291 个文件路径变化：新增 2,243、修改 46、删除 2。** 按顶级目录计：前端 1,712、rehab 320、desktop 98、deploy 41、docs 30、sql 23、AI 14、server 8、framework 7 等。数字包括所有跟踪文件，不是有效代码行数；前端大量新增也与上游仓库原有前端组织方式有关，不能据此说重写了 1,712 个上游文件。

主要定制：新增康复域、引入/定制管理端、报告模板、部署/桌面交付、安全及迁移措施；保留上游分层和平台基础设施。框架/系统/infra/依赖已被修改，不能把新上游覆盖进去。

建议生成长期上游改动登记表：原始 SHA → 本地补丁 → 影响模块 → 测试 → 回滚方式。官方升级补丁逐项审查，不直接 merge 最新主分支。

## 18. 对应的 RuoYi 版本判断

上游固定基线 POM 为 **`2026.01-jdk8-SNAPSHOT`**，Java 1.8、Spring Boot 2.7.18；本项目将产品 revision 改为 `1.0.0`。

可确信：这是该 JDK8/Boot2 代码线的二次开发，不是凭项目版本号猜测的 RuoYi 通用 1.0，也不能称为某个未核实的正式稳定 tag。CI JDK17 仅是构建工具链选择。

## 19. CRM 恢复可行性

**有条件可行，P1，不能直接启用。** 源码、前端资产和桥接具备基础。关键阻碍：CRM POM 依赖 BPM；server 目前不打入 CRM/BPM；恢复 CRM 会增加流程引擎、API、表和依赖风险。

以个人客户为中心裁剪：咨询来源、意向、跟进、预约/到店、体验/评估、成交与续费。复用跟进、负责人、权限和统计，暂不默认开放 B2B 商机/合同审批全部功能。先确定 Client 主数据契约和租户边界，再选择“完整依赖恢复后最小开放”或“隔离 CRM 对 BPM 的使用点”；两者需编译/运行验证，不能承诺简单排除依赖就能启动。

## 20. ERP 恢复可行性

**有条件可行，P2。** 现有产品、单位、供应商、采购、库存、盘点、出入库与财务 DO 可复用。先恢复采购→入库→领用→盘点闭环，屏蔽不需要的复杂财务/销售入口。

恢复前核对模块 SQL 是否在实际库、菜单权限是否冲突、Tenant 注入、库存并发/精度、审核反审核逻辑和负库存策略。产品销售客户不能再成为另一份脱离 Client 的个人档案。

## 21. WMS 恢复可行性

本基线未发现独立 `yudao-module-wms`，不能承诺“一键恢复 WMS”。先复用 ERP stock/warehouse/stock-in/out/move/check；库位、批次、有效期按真实耗材需求渐进补充。

固定资产单独维护设备编号、位置、状态、维修与报废，数量型消耗库存不能替代唯一设备履历。机构未出现实际库位复杂度前，不建立大型 WMS。

## 22. HRM / BPM / Report / Member 等模块价值

- **HRM：** 未找到独立 HRM 模块。system 的用户/部门/岗位可复用；排班、资质和员工业务档案按需求补，暂不做薪税全套。
- **BPM：** 源码/父构建声明在，server 未交付；未来适合采购、退款、计划审核，第一阶段评估确认用简单状态机足够。CRM 若需 BPM 是技术依赖，不等于产品立即全面开放工作流。
- **Report：** 上游报表模块与康复报告不是同一东西。优先复用现有 rehab DOCX/PDF；上游经营大屏在有真实指标之后再恢复。
- **Member：** 已是 server 依赖，不能误判完全关闭。患者/家长自助需绑定关系、未成年人监护授权和访问范围，不能直接开放所有会员接口。
- **FMS：** 康复中的 Functional Movement Screen 与财务系统缩写不是同一模块；本基线没有证实独立财务 FMS 产品，先澄清术语。
- AI、支付、商城、IoT 等继续不交付；不因源码存在就默认启用。

## 23. Client Master Data 整合方案

先将现有 `rehab_patient.id` 作为康复身份稳定锚点，增加“个人主数据适配层”，不改写所有历史外键。

1. 梳理患者/CRM/member 重复、未绑定、错误绑定和字段来源，用脱敏统计；不按姓名/手机号自动合并。
2. 明确姓名、生日、联系方式、监护关系的唯一写入责任；健康信息归康复域，不让 CRM 默认读取。
3. 第一阶段复用绑定表加强唯一约束和 tenant 检查；身份候选由人工确认。
4. 当 CRM 真实启用时，决定 Patient 提升为 Client 或增设轻量 Person Master；保持旧 patientId 可寻址，并建立可审计 identity mapping。
5. 合并采用“指向主身份＋保留原 ID/来源/历史”，提供误合并恢复；共享家庭电话不得强制去重。
6. CRM 生命周期、课程、付款、随访、Timeline 共用同一身份，不用患者端账号 ID 代替自然人 ID。

## 24. Assessment 渐进重构建议

保留主表、模块表、已有表单及 ID；不是按需求清单机械创建十几套 assessment 表。

- 评估头补充 encounter kind（初评/复评）、基线/前次引用、schema/templateVersion、明确状态流与 revision。
- 对模块 JSON 建立服务端 schema 校验、稳定 item code、side、value/unit、原始单位、测试状态与观察备注。
- 状态区分 `not_tested / not_applicable / partial / completed`，禁止 missing 默认 normal；Optional 模块缺失不阻止完成。
- 模块 schema 定义必填规则只针对本次选择的项目；痛感/安全中止有明确原因。
- 已有 module version 保留含义；新增并发 revision，不复用旧字符串作锁。
- Raw、Parsed、Generated、Confirmed 以显式来源/派生关系和不可变快照区分。先在现有模块 schema 内划分命名空间，逐步增加确认快照，不马上搬迁全表。
- 已 reviewed/confirmed 的更新必须进入新修订；历史报告仍指向旧确认快照。
- SFMA/FMS/YBT 规则和单位先建立黄金样例，尊重量表版本与授权，不把软件规则当医学确诊。

## 25. 视频架构建议

每条媒体记录最少：fileId、patientId、assessmentId、moduleCode、testItemCode、side、viewAngle、capturedAt、uploadedAt、uploader、size、mimeDetected、checksum、status、原始/派生关系。

动作与媒体为 1:N；0 段视频合法。原始视频不可被转码/缩略图覆盖，派生媒体另存并注明来源。上传实现独立队列、进度、失败重试、幂等、断点续传或分片、受控并发；不阻塞结果保存。

存储仍可用现有私有磁盘卷＋接口抽象，后续再换私有对象存储。下载/Range/缩略图每条都经过患者权限；不可暴露永久公共 URL。增加保留期、配额、删除审计及孤儿文件回收。不能只把 16 MB 改成无限制并继续 byte[] 下载。

## 26. Visbody 文件架构建议

复用评估附件存储，增加文件角色 `visbody_original`、设备报告日期/标识、hash、版本、上传来源；链接 Patient→Assessment→原件。

首期只交付上传、查看、下载、历史、受控删除和导出带原件。PDF 预览授权后进行，文件内容不执行；没有自动提取也可以完成评估。

后期 `extraction` 引用特定原件 hash，保留解析器版本、单位、质量和人工确认。治疗师结论另存。严禁用提取数据或 AI 解释覆盖原 PDF。

## 27. OpenCap 文件架构建议（已查官方来源）

本轮读取官方 `opencap-org/opencap-core` 的 `utils.py`，可见 `sessionMetadata.yaml`、MarkerData 中 `.trc`、OpenSimData/Model 中 `*_scaled.osim`、Kinematics 中 `.mot`、相机映射/标定 `.pickle`，以及 `.mov` 相关逻辑。这些是官方实际代码路径，不是凭空指定格式；不同处理流程仍可能有额外文件，不宣称该清单穷尽。

官方证据： https://github.com/opencap-org/opencap-core/blob/main/utils.py （读取日期 2026-09-20；main 会变化，实施时锁定具体 SHA/软件版本并保存测试样本）。

建议：

- 增加 OpenCapSession 关联 Assessment、外部 session ID、采集日期、操作者、设备/协议版本；Trial 标识可先存元数据。
- RawFile 保存原相对路径、原文件名、大小、hash、格式、导入批次、历史版本、Session/Trial。
- 第一阶段采用受控多文件或原始 ZIP 上传；完整保存，不自动调用 OpenCap 云端、不解析生物力学。
- ZIP 首期可仅保存不解压；如生成目录索引，防路径穿越/符号链接/压缩炸弹并限总解压量。
- pickle/pkl 只能按不透明原始字节保存下载，**严禁在服务端反序列化不可信 pickle**。
- OpenCap 处理产物在本系统属于“外部原件”，不等于传感器原始真值；保留上游产生来源。
- 3D 数据不承诺浏览器直接可视化；查看首期指 Session 元数据、文件清单与可安全预览的内容。

## 28. Export for ChatGPT / Context Package 设计

### 28.1 确定性导出，不接 AI

`AssessmentContextBuilder` 仅从当前用户可访问且明确选择的本次评估、历史评估和关联文件生成一个稳定 DTO；各 renderer 共用它，输出复制文本、Markdown、TXT、DOCX、ZIP。不要调用现有 AI client 来“整理文本”。

所有导出格式使用相同 Validation 和字段映射，未识别的新 JSON 字段必须进入“原始结构附录”，防模板漏字段。原始值与统一单位显示值并存，转换规则/精度可追溯；左右侧列不可按展示顺序推断。

### 28.2 推荐包结构

```text
ClientCode_Date_AssessmentId/
  00_Assessment_Context.md
  00_Assessment_Context.txt
  00_Assessment_Context.docx
  01_Patient_Information/data.json
  02_Symptoms_Pain/data.json
  03_Visbody/data.json
  04_ASM_CS/data.json
  05_SFMA/data.json
  06_FMS/data.json
  07_YBT/data.json
  08_Movement_Assessment/data.json
  09_Therapist_Observation/data.json
  10_History_Reassessment/data.json
  Visbody/Original_Report_<fileId>.pdf
  MovementVideos/<item>_<side>_<view>_<fileId>.<ext>
  OpenCap/<sessionId>/Raw_Files/...
  manifest.json
```

目录示例不是要求生成不存在的数据。未实施的测试在 Context 明确标记，包内可省略无文件目录。默认以客户代号命名，完整姓名版仅在明确授权后导出；不把所有历史自动无边界打包。

### 28.3 Context 内容与完整性

身份/年龄时点 → 本次主诉与病史 → 选择的评估模块 → 原始测试结果/左右侧/单位 → 治疗师观察 → 原件/视频/Session 索引 → 明确选择的历史对照 → 缺失和异常摘要 → 资料来源与版本 → 请治疗师最终确认的说明。

Validation 分成：

- 阻断：越权、身份/关联错配、损坏原文件、无法确定左右侧的数据映射错误。
- 可确认后导出：部分项目未测、Optional 媒体未提供、异常数值、可比性不足。
- 提示：历史数据单位不同、方法版本不同、非同条件测量。

不得因“没有视频/OpenCap/Visbody”一概拒绝完成，也不得为了通过验证补零或推断正常。

### 28.4 manifest 和版本

至少：packageSchemaVersion、assessmentTemplateVersion、exportTemplateVersion、assessmentId、revision/snapshotId、导出时间/操作者、隐私模式、模块完成度、warning 列表、原始字段映射摘要、每个文件相对路径/大小/SHA-256/item/side/view/session/sourceRole。

导出要锁定一致快照，不能打包期间读到一半旧数据一半新数据。失败/缺文件不得静默漏掉；提供可重试、原子发布、临时文件清理和空间上限。

完整归档包不保证 ChatGPT 能直接读 ZIP、视频或 OpenCap 专业格式。界面区分“用于上传的文本/文档”和“完整原始档案包”；最终可上传文件类型、大小、数量取决于用户当时产品能力。不能承诺自动分析所有视频或 3D 数据。

## 29. 初评/复评纵向数据设计

- 保留 patientId、episodeId、assessmentId；新增 initial/reassessment 与 baselineAssessmentId、previousAssessmentId。
- 指标键建议：moduleCode + testItemCode + side + metricCode + protocolVersion + unit；每次测量含时间、测试条件、原始值和缺失原因。
- 初评/复评的模块可不同，不把未测当改善或 0；禁止跨版本无解释直接比较。
- 6 周、12 周、6 个月是可配置随访计划，不写死为仅三个数据库列。
- 改善方向由指标定义：疼痛下降与活动度变化不是同一种判读；无临床阈值依据则仅显示变化量，不自动宣称显著改善。
- 视频并排按动作/左右/视角配对；OpenCap 按 Session/Trial/协议版本配对。原始记录永不覆盖。
- 复评触发目前给创建路由参数（E15），需让新记录保留触发来源与明确基线，而不是只返回一个新建页面链接。

## 30. 数据 Migration 风险

首要危险是 B01，此外历史 SQL 有固定 ID 演示种子与 ON DUPLICATE KEY UPDATE（E09），对旧库重放可能覆盖数据。默认 tenant 1、添加唯一约束/外键也可能失败或改变历史语义。

每个变更提交迁移说明：目的、新增/删除、旧数据映射、冲突数量、锁表窗口、备份、回滚/前滚和验收 SQL。首期一律 additive，不删老表/列、不重排患者/评估 ID、不在迁移中自动合并客户。

业务数据库与文件卷应同一恢复点备份；报告/附件 hash 抽查。MySQL DDL 不等于可事务整体回滚，不承诺“失败自动恢复”。恢复脚本本身有明确破坏步骤，必须人工确认、先备份、隔离演练；不能为了验证而在生产执行。

## 31. 应保留的代码

保留 system/infra/member 平台、Token/RBAC/租户基础；rehab Patient/Episode/Assessment/Training/Report/Progress 核心；SFMA/FMS/YBT 现有表单和验证/汇总；报告版本/审核；已有审计与备份加密/TLS/发布机制；现有主键和历史数据。

保留 AI 代码为受控关闭的未来适配，不在本轮“清洁重构”中删除。

## 32. 应逐步重构的部分

- 评估状态推导与 Validation：纠正空值完成、草稿失真。
- 保存服务：明确 revision/幂等与编辑权限，提供模块级保存。
- 附件服务：存储抽象＋业务关联＋大文件流式传输，而非业务 JSON 内塞视频。
- 报告数据组装：抽取可复用 Context Builder，保持旧 report renderer 输出兼容。
- CRM/member JDBC 访问：租户与权限明确的适配接口。
- 移动端：在已有字段契约上抽现场模式，不复制整套逻辑。

## 33. 应删除/合并的部分

**本轮没有批准删除任何现有代码、表或数据。**

未来候选：生产升级链中的 demo 清理入口应移除或强隔离；已应用的历史 SQL 不随意改 checksum。重复的字段映射/单位字典在测试证明等价后合并；无使用且未交付的导航可继续隐藏，不等于删源码。旧附件接口先兼容再弃用。worktree 和历史文件不得因名字相似而批量清理。

## 34. P0 / P1 / P2 / P3 问题总表

这里“问题严重度”与用户“产品优先级”分开：视频和导出是产品 P0，但尚未开发不是生产安全 P0。

| ID | 级别 | 问题/条件 | 状态 |
|---|---|---|---|
| R01 | P0 | 通用迁移可能执行全业务清理 | 静态执行路径已证实；未执行 |
| R02 | P1 | 草稿/空模块被判完成 | 静态逻辑已证实；需回归 |
| R03 | P1 | 并发保存无冲突保护 | 静态缺口；待双客户端复现 |
| R04 | P1→P0 | 更新可变 patientId 的目标权限不足 | 待越权用例；确认跨患者写入升 P0 |
| R05 | P1→P0 | CRM/member JDBC 未含 tenant 约束 | 恢复/多租户前阻断；实际泄露则 P0 |
| R06 | P1 | 文员患者范围过宽，健康字段不分层 | 与新权限需求冲突；需实际权限配置验证 |
| R07 | P1 | CI 未覆盖康复关键回归、Typecheck/移动流程 | CI 配置已证实；不等于所有测试不存在 |
| R08 | P1 | 新文件场景将突破历史安全接受边界 | 设计门禁；需内容验证/资源限制 |
| R09 | P2 | 取模业务编号可能碰撞 | 条件明确，未运行压力复现 |
| R10 | P2 | 模板版本和 Raw/Generated 分层不足 | 数据语义债 |
| R11 | P2 | 绝对路径、本地 byte[] 下载限制迁移/视频 | 架构债 |
| R12 | P2 | CRM→BPM 依赖，恢复影响面大 | 依赖已证实 |
| R13 | P2 | 旧 Boot2 基线与依赖风险需持续治理 | 历史登记，需本轮后补复扫；公网化提前 |
| R14 | P3 | 分栏、术语、交互密度、重复配置治理 | 需治疗师真机观察 |

## 35. 第一阶段开发任务：替代纸质与资料整理（产品 P0）

| 顺序 | 交付切片 | 验收门禁 |
|---|---|---|
| 0 | 迁移安全护栏、基线/备份核验 | 旧数据不可被初始化脚本触达 |
| 1 | 草稿状态、revision、模块保存与服务端 Validation 基础 | 空值不算完成，重复请求/并发不丢数据 |
| 2 | 以 SFMA 一个完整路径验证手机/iPad 现场模式 | 可点选、自动保存、中断恢复、保留现有 PC 流程 |
| 3 | 扩展 FMS/YBT/其他确认后的量表 | 单位/左右/评分黄金样例通过 |
| 4 | Visbody 原 PDF、动作级 Optional Video、OpenCap Session/Raw Files | 原件 hash 不变，缺媒体仍可完成 |
| 5 | Context Builder + 复制/MD/TXT/DOCX + 完整包 | 所有已填字段可追溯，无虚构，权限与包一致性通过 |
| 6 | 初评/复评关联与历史对照首版 | 同指标可比，未测不当改善 |
| 7 | 试点与发布 | 测试、备份恢复、真机、临床试用签收 |

导出并非必须等所有媒体功能完成才开始：第 2 切片即可实现文本导出垂直闭环，随后扩充媒体与完整包。任一阶段都不接 AI API。

建议先用合成样例，再由治疗师选定标准流程试点；记录填写耗时、二次录入次数、资料整理时间、缺漏率、恢复失败率。目标值先测基线再确认，不伪造当前指标。

## 36. 第二阶段开发任务：Client / CRM / 训练复评贯通（产品 P1）

Client 身份治理 → CRM 最小个人客户流程 → 预约/到店 → 时间轴 → 课程包/成交事实 → 评估问题/目标连接训练 → 复评与随访 → 续费/结案。

验收：一个真实人不因渠道不同成为两套独立档案；前台不能默认查看健康细节；Timeline 可授权追溯原件；训练说明能回答“针对什么问题、是否改善”。收费先记录必要业务，不优先复杂计费引擎。

## 37. 第三阶段开发任务：机构运营与可选 AI（产品 P2/P3）

先按实际业务启用 ERP 最小库存、固定资产、员工排班、经营统计；再决定 BPM 和上游 Report。租户/机构已有基础先用好，不现在实现复杂 SaaS Billing。

AI 是独立的未来批准事项：经权限裁剪的 Context → Gateway → 结构化输出校验 → 治疗师审核 → 确认版本。成本预算、出境/云上传同意、供应商条款与审计先确认。不把进入“第三阶段”视为自动授权开 API。

## 38. 推荐 Branch 策略

- 以已核实 master SHA 建审计后冻结点；先核对并保护本地未提交修改，再经批准建 feature branch。
- 建议 `fix/migration-bootstrap-guard`、`feat/assessment-safe-draft`、`feat/assessment-context-export`、`feat/assessment-media`、`feat/client-crm-bridge`。
- 每个切片独立 Commit/PR，附测试证据、Migration 说明和回滚步骤；业务安全修复不要夹带 UI 重做。
- 上游维护分支只做对比/补丁评估，记录准确 upstream SHA；不要整体 merge 最新主线。
- 本轮不切换分支、不 commit/push；不 force push、不重写历史、不删除 worktree。

## 39. 推荐数据库 Migration 策略

1. 保护已有 checksum 历史；分离“全新 bootstrap”“增量 schema”“demo seeds”“破坏性维护”。
2. status 真正只读；现脚本 status 会先 CREATE 账本（E08:57-70），不能用它冒充只读探测。
3. 应用前先检查是否非空、账本是否缺失/漂移、约束是否已存在。没有可靠基线则 fail closed，不猜测自动 baseline。
4. 正式增量采用 expand → 回填/校验 → 切读写 → 稳定观察 → 经批准收缩；不马上弃用现有账本换工具。
5. 新增字段先可空/兼容读取；Client/媒体/模板历史回填都有来源标记，无法确定的标 unknown。
6. 每次迁移在旧版本脱敏副本演练，核对患者、评估、模块、报告、附件数及 hash；测试并发/锁表时间。
7. 回滚业务代码不自动删除新数据。DDL 失败优先修复前滚；破坏性恢复必须用同一时点 DB＋文件＋密钥备份并人工确认。

## 40. 推荐测试策略

现状：Git tree 有 20 个康复 Java 测试源文件（不是 20 个测试用例）；已涵盖权限、患者、评估、SFMA、计划、执行、进度、报告、预警等。主 CI 后端显式只执行 PasswordHashCliTest，构建脚本使用 skipTests；前端执行 build:internal 和依赖审计，没有显式 ts:check/lint/临床 E2E。主前端 tree 未发现 `.test.*`/`.spec.*` 文件，不代表仓库完全没有其他测试机制。

建议门禁：

- 静态：只读 lint（不要直接调用带 --fix 的现有 npm 脚本）、TypeScript、Java 编译、迁移 checksum。
- 单元：草稿/完成状态、左右/单位、FMS/YBT 计算、未测/缺项、模板版本、Context 所有字段映射。
- 集成：隔离 MySQL/Redis，租户、患者归属、事务、乐观锁、幂等、历史迁移和媒体关联；不能用真实库测试。
- 安全：未登录/其他治疗师/文员/其他租户下载和导出被拒；伪造扩展名、大文件、路径穿越、ZIP 炸弹、恶意文件名、pickle 不执行。
- E2E：建档→Episode→现场评估→中断恢复→上传可选资料→Validation→导出→复评；无任何 Optional 文件也可完成。
- 真机：iPhone Safari、Android Chrome、iPad Safari 和 PC；横竖屏、键盘、相机/相册、权限拒绝、断网/切后台/锁屏、并发编辑。
- 报告/包：历史黄金样例回归，DOCX 可打开、原始数值不改、manifest hash 一致、缺文件不能静默漏包、权限裁剪正确。
- 运维：备份恢复演练、密钥离线保管、空间不足、升级失败、数据库不可用、原件丢失告警。
- AI：关闭状态 network call = 0；Mock/fallback 不冒充原始观测或最终结论。

**本轮未运行上述测试，不宣称测试通过。**

---

# 建议第一个真正开始编码的功能

## 首个工程功能：数据库升级安全护栏（P0）

### 为什么先做

任何后续 Assessment/媒体/Client 开发都将接触迁移。当前已证实存在旧库缺账本时执行无条件数据清理的路径。先降低数据损失风险，比先做导出按钮更符合“保护现有功能与数据”的第一原则。

### 修改范围（需要你确认后才实施）

- `deploy/internal/migrate.sh`：只读 status、空库/账本预检、禁止 upgrade 执行 bootstrap-only 清理、失败关闭。
- 迁移清单/分类元数据、bootstrap 调用入口及相关 CI/文档：仅在保持历史 checksum 和既有版本可识别的前提下修改。
- `deploy/internal/clean-demo-rehab-data.sql` 的可执行入口隔离，不盲目重写已登记脚本内容。
- 加入合成数据的迁移护栏测试；核对 internal 与桌面 runtime 两条初始化路径。

### 是否涉及数据库 / 影响现有数据

涉及数据库升级流程；**第一个补丁原则上不修改业务 Schema、不迁移患者数据、不执行数据库写入**。测试在隔离新库与合成旧库进行。已有生产库仅在你批准后做只读检查；若需要补账本/维护操作，将作为独立计划再次确认。

### 风险

错误阻断合法首次安装、旧部署账本差异导致升级暂停、internal 与桌面包初始化行为不同。通过明确模式、双路径测试和可诊断错误处理控制；不以允许危险脚本继续执行来“兼容”。

### 验收标准

1. 有患者/评估/报告/训练的合成旧库，即使没有迁移账本或缺少 015，升级也必须在执行危险 SQL 前拒绝；业务行与文件 hash 不变。
2. `status` 不创建/修改任何数据库对象。
3. 全新 bootstrap 仍能安装，演示数据清理只能在可验证的新建隔离库路径发生。
4. checksum 漂移、未知迁移或不明历史状态均停止并说明原因，不自动 baseline。
5. 已正确登记的旧版本可按允许的增量路径升级；不能修改既有数据含义。
6. CI 覆盖上述路径；日志不泄露凭据或患者数据；没有 AI 网络调用。
7. 审核 diff、测试证据及回滚说明后，由你批准合并/发布。

## 随后的第一个业务功能

**“SFMA 现场安全草稿＋自动保存＋一键复制/Markdown 导出”的小范围垂直闭环。**

复用现有 Patient/Episode/Assessment/SFMA；补 revision/状态语义，做单屏现场操作与恢复，再由确定性 Context Builder 导出。它同时验证“消灭二次录入”和“减少 ChatGPT 整理时间”，不等待 CRM/ERP 或完整多媒体平台。

数据库只做向后兼容的 revision/模板关联等必要增量，具体 SQL、回填及回滚单独提交；历史记录不自动重算。验收应包括：同一病例一次录入、中断恢复、双端冲突提示、缺项不编造、左右不混淆、没有 AI API、原 PC 报告/训练流程不退化。

---

## 本轮结束状态与待确认事项

已形成静态审计及路线图，未开始编码。继续之前请确认：

1. 以 `rehab-management-integrate-all` 对应 GitHub master 为主线，不把其他 worktree 未提交修改自动合入。
2. 是否同意先做 P0 迁移护栏，再做现场安全草稿与导出闭环。
3. 后续只读数据库核验的目标环境与允许范围；不需要在聊天中提供任何密码或患者资料。
4. ASM/CS 的具体定义和首批现场表单范围。

文档对所有要求给出了第一轮结论，但尚未核验的运行数据库、安全扫描、真机与测试必须继续保持“待验证”，不能视为已完成上线审计。
