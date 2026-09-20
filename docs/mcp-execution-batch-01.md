# 全部非 AI 升级计划：执行进度与验证记录（批次 01）

日期：2026-09-20。

**当前为部分实施，不是全部功能交付或全系统验收完成。**

## 1. 已确认的执行授权与环境

用户确认：实施全部非 AI 阶段；使用已有专用测试环境；允许在其上执行合成数据测试。

经只读定位并由用户确认，该环境为：

- 入口：`https://172.16.8.41:8443`。
- Compose project：`rehab-internal`。
- 实际部署目录：`ruoyi-vue-pro/deploy/internal/`。
- 服务：Nginx 管理端、Spring Boot 后端、MySQL 8.4.10、Redis 7.4.10。
- 现有 MySQL、附件、日志、Redis 数据卷保留，不删除、不清空。

实施代码位于另一个工作树 `rehab-management-integrate-all/`，基线 `995247148d9e8d90237ed648ab50c443f708fac8`，已创建 `fix/migration-bootstrap-guard` 分支。

**运行服务与开发工作树不同。本批代码没有部署到运行服务，不会把旧服务页面正常打开当作新代码测试通过。**

## 2. 已实施变更

### 2.1 P0 迁移安全护栏

修改 `deploy/internal/migrate.sh`：

- status 不再 CREATE 迁移账本，只进行 SELECT。
- 缺少账本时直接停止，禁止猜测历史或自动建账。
- 历史 001—019 迁移全部禁止在 apply 中重放，阻断包含 demo seeds、无条件删除及广泛回填的初始化路径。
- 执行前一次性核对全部已有版本/校验和，发现未知版本或漂移直接停止。
- 检查清单版本格式、顺序、校验和、路径和描述。
- baseline 要求显式人工确认，且仍不能自动建立缺失账本。
- 不更改历史 SQL、历史 checksum、业务表结构或现有应用数据库内容。

新增 `deploy/internal/test_migration_guard.py`，并将无数据库模式的护栏测试加入 `.github/workflows/ci.yml`。CI 同时新增 `mvn -B -pl yudao-module-rehab -am test`，不再只依赖密码引导测试及跳过测试的构建。

注意：本批解决已发现的历史重放危险路径；不宣称未来任意增量 SQL 自动安全。未来迁移仍需独立审查、备份和演练，并发迁移锁等增强尚未实施。

### 2.2 评估保存与归属保护

修改：

- `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/assessment/RehabAssessmentServiceImpl.java`
- `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/enums/ErrorCodeConstants.java`
- `yudao-module-rehab/src/test/java/cn/iocoder/yudao/module/rehab/service/assessment/RehabAssessmentServiceImplTest.java`
- `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/assessment/create/index.vue`

内容：

1. 普通编辑禁止更换已有评估的 patientId，防止其与既有附件、报告及计划的患者归属分裂。
2. 自动初始化的空占位模块使用 not_started，不再使用 completed。
3. 显式保存 draft 时，派生状态刷新不将其提升为 completed。
4. 前端保存草稿时提交 partial 模块状态，而非 completed。
5. 新评估业务编号改用完整主键后缀，不再使用 id % 10000；历史编号不改写。
6. 新增 5 个回归测试，涵盖上述保护。

边界：这些是针对已发现问题的小补丁，不等于完整状态机、服务端逐项完整性校验、自动保存或并发 revision 已交付。现有量表验证仍保留，未为了让草稿保存通过而关闭安全/协议校验。

## 3. 已执行测试及结果

| 检查 | 实际结果 | 说明 |
|---|---|---|
| `sh -n deploy/internal/migrate.sh` | 通过 | Shell 语法 |
| 护栏默认单元测试 | 14 通过、3 跳过 | 默认不触碰数据库；3 个真实 MySQL 用例明确 opt-in |
| 护栏＋真实 MySQL 集成测试 | **17 全部通过，0 跳过** | 见下方隔离方式 |
| 修改文件的 `git diff --check` | 通过 | 不代表编译/业务流程通过 |
| VS Code rehab 诊断 | 当前快照 0 条 | 工具明确说明结果不保证完整，不能当作测试通过 |
| 默认 JDK 的 Maven 回归 | 失败 | 实际为 JDK 23，Mockito/Byte Buddy mock JDK/注解类型失败 |
| JDK 17 全 reactor 回归 | 未完成，已取消 | 长时间停在 infra 测试的文件读取，不计通过 |
| 前端 `pnpm ts:check` | 未完成，已取消 | 长时间停在依赖文件读取，不计通过 |
| JDK 17 定向评估回归（隔离构建） | **18 全部通过，0 跳过** | 含本批新增 5 项 |
| JDK 17 康复及依赖 reactor 全回归（隔离构建） | **735 项：706 通过、29 跳过、0 失败、0 错误** | 91 个测试套件；康复模块 93 项全通过；不是所有可选模块测试 |
| 前端 `pnpm build:internal`（隔离构建） | **通过** | 不等于 TypeScript 检查通过 |
| 构建生成声明后 `pnpm ts:check` | **失败：1,129 条错误** | 原始基线同样 1,129 条，诊断逐条一致，本批新增 0 条 |
| 浏览器真实业务 E2E | 未执行 | 新代码尚未部署 |
| iPhone/Android/iPad 真机 | 未执行 | 不用模拟视口冒充真机 |

### 真实 MySQL 测试隔离方式

使用用户确认的 `rehab-internal-mysql-1` 容器，但每个集成用例创建独立、随机命名的 `rehab_guard_test_<UUID>` 数据库，仅包含合成哨兵记录和迁移账本。

测试覆盖：

- 账本缺失：status/apply 均拒绝，账本不被偷偷创建，哨兵数据不变。
- 缺少 015：apply 在任何危险 SQL 前拒绝，已有记录不变。
- 账本完整：status/apply 不写数据库，哨兵数据不变。

测试后仅删除该次用例创建的随机测试库，没有删除应用库；已只读复核残留 `rehab_guard_test_%` 数据库数量为 **0**。测试所需认证在容器内部使用现有环境变量，不输出密码。

复现命令：

```bash
python3 -m unittest discover -s deploy/internal -p test_migration_guard.py -v

# 仅在已确认的专用测试 MySQL 容器使用：
REHAB_TEST_MYSQL_CONTAINER=rehab-internal-mysql-1 \
  python3 -m unittest discover -s deploy/internal -p test_migration_guard.py -v
```

## 4. 测试环境阻塞的证据

- 默认 Maven 实际使用 Homebrew JDK 23，项目 CI 使用 JDK 17。
- 本机已有 Microsoft JDK 17：`/Users/saber/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home`，后续命令显式指定 JAVA_HOME/PATH，未修改全局 Java 设置。
- JDK17 全回归停在 `ApiAccessLogServiceImplTest` 的 Spring/MyBatis 类扫描，线程栈显示 main 在 `FileDispatcherImpl.read0`，打开文件为 `yudao-framework/yudao-common/target/classes/.../package-info.class`。
- 前端检查打开的文件停在 `node_modules/.pnpm/@types+lodash-es@4.17.12/.../ceil.d.ts`，进程 CPU 接近 0。
- 这是本次观测到的文件读取阻塞；根因尚未证实，不能凭此认定一定是 iCloud、同步软件或代码缺陷。
- 已对未完成全回归和类型检查正常发出软取消，没有杀死用户服务或强制停止 Docker。

### 已验证的绕过方案及最终结果

已在 `/tmp/rehab-guard-build.HbqDTl/` 建立独立临时构建区，下载固定 GitHub SHA 的源码包，仅叠加本批修改的后端源文件/测试和前端录入文件，不复制原工作树 target 或 node_modules。它验证的是“固定基线＋本批补丁”，不包含其他 worktree 的未提交修改。

- 后端定向测试用时约 26 秒；全 reactor 回归约 50 秒，BUILD SUCCESS。
- 前端冻结锁文件安装、独立依赖缓存及 build:internal 成功，未修改原工作树依赖或全局 Java。
- 首次在未生成自动导入声明前检查产生大量诊断，不能直接当最终基线。构建生成声明后重新检查，最终仍有 1,129 条类型错误。
- 再将临时构建区的前端录入文件恢复为固定 SHA 原版、重新检查：原版 1,129 条，补丁版 1,129 条，诊断逐条完全一致；测试后已恢复临时构建区的补丁文件。
- 主要错误分布：ERP 327、CRM 143、支付 123、商城视图 206、BPM 视图 30、BPM 设计器 76 等。现有类型债尚未修复，不能通过排除板块或放松 TS 配置伪造全量通过。
- 29 个后端跳过项如外部文件客户端等并未真实执行，需按实际启用能力另补环境和集成验收。

原工作树的文件读取原因仍待查，但已存在可重复的隔离构建验证路径。日志留在临时构建目录，不放入 docs；汇总结果保存于本记录。

## 5. 全系统实施/验收矩阵（尚未完成）

| 板块 | 本批状态 | 后续真实路径验收 |
|---|---|---|
| 登录/租户/角色/患者范围 | 未改整体机制，待全链回归 | 多角色登录、跨租户/患者拒绝、过期会话 |
| 患者/Episode | 保留 | 建档、编辑、导入、分配、归档与关联 |
| Assessment | 小范围安全补丁 | 新建/草稿/恢复/完成/归档、左右/单位/并发 |
| SFMA/FMS/YBT/其他量表 | 保留，待扩展现场模式 | 治疗师逐项录入和评分黄金样例 |
| Visbody | 原件专用流程待开发 | 上传、预览、历史、下载、hash 和关联 |
| 动作视频 | 待开发 | 相机/相册、0-N视频、后台上传、重试、权限 |
| OpenCap | Raw Session/File 待开发 | 官方格式样本保存、原路径/hash、历史、不解析 pickle |
| Validation/Context Export | 待开发 | 复制/MD/TXT/DOCX/ZIP、未测、manifest、全字段不遗漏 |
| 报告/审核/版本 | 保留，待回归 | 两种报告来源一致、审批下载和历史快照 |
| 训练/执行/复评/随访 | 保留，统一纵向指标待开发 | 评估问题→目标→训练→执行→结果→复评 |
| Client Master/CRM | 待开发/兼容恢复 | 个人身份统一、咨询到成交、健康字段隔离 |
| 预约/课程包/时间轴 | 待开发/整合 | 咨询→预约→到店→课程→续费/结案 |
| ERP/库存/资产 | 待恢复/适配 | 采购→入库→领用→盘点、资产维修 |
| 员工/BPM/经营报表 | 待按已批准路线实施 | 最小权限、流程/统计及数据口径 |
| 桌面/LAN/备份恢复 | 本批不部署 | 全新安装、旧库升级、TLS、备份恢复演练 |
| AI | 保持关闭 | 真实路径网络调用为零的断言仍需补全 |

“所有系统和板块测试完成”的判定必须包含每个板块的用例、角色、数据前置条件、实际运行版本及证据。不存在或尚未开发的模块不能仅因导航隐藏就标为测试通过。

## 6. 数据与发布纪律

- 本批没有业务 Schema 修改，不执行患者数据回填或合并。
- 原始文件、数据库卷、生产/测试应用数据未清理。
- 未调用 OpenAI/第三方 LLM，不新增 API Key。
- 尚未 commit/push、未发布、未恢复 CRM/ERP/BPM 等运行模块。
- 原有其他 worktree 未提交修改未合入。
- 后续涉及新表/列和主数据映射时，先提供确切迁移与回滚/前滚方案，不以本次总授权替代破坏性数据操作确认。

## 7. 下一批工作的前置条件

1. 复用已验证的隔离构建方式，治理 1,129 条既有前端类型错误，补足跳过的必要集成测试；本批后端回归已通过。
2. 形成安全草稿 revision 与媒体关联等具体 additive Schema 方案。
3. 实施现场最小评估＋确定性导出闭环，再逐步扩充全部量表和媒体。
4. 逐模块推进 Client/CRM/预约/训练复评及机构运营，持续维护真实路径验收矩阵。

**全计划仍在进行中，不应将本批记录视为全部修改完成。**
