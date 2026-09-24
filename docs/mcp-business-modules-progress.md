# 机构业务模块开放：执行与验证台账

> 历史阶段记录：本文下文的「尚未完成」对应当时状态；最新隔离库、Chrome 路径与报表安全验证见 [2026-09-23 隔离验收记录](isolated-browser-acceptance-2026-09-23.md)。

日期：2026-09-23。状态：首批安全前置补丁与可选业务模块构建入口已写入独立本机检出；运行模块仍未开放，未完成浏览器跨板块验收，未推送。

## 目标、授权与工作树

用户指定：优先开放康复及机构运营相关板块；允许外部 AI 模型调用；允许使用独立数据库、独立 Compose 项目和合成数据进行测试，现有业务数据库和附件卷保持不变。外部调用的授权不等于已配置供应商、同意传输患者资料或完成费用/合规验收。

开发检出：`/Users/saber/Documents/Playground/rehab-module-enable-work`，`master`，起点 `bafc44104a7d732e5ea06e06cd4889103eb1e74b`。旧的 `rehab-management-integrate-all` 在 `git status` 的文件读取阶段停滞；已软中止，只在新检出修改。不使用 `ruoyi-vue-pro` 的旧部署页面冒充新检出验收。

## 模块边界（源码存在不等于可运行）

| 模块 | 当前基线构建/运行 | 目标状态 |
| --- | --- | --- |
| system / infra / rehab / member | 构建和服务依赖已包含 | 保持并补回归、业务权限与数据边界 |
| BPM / CRM | 父 POM 包含，服务端依赖关闭 | 先审依赖与 Bean、数据库和权限，再实施必要的咨询/客户流程 |
| ERP | 构建与服务依赖关闭 | 按库存、采购、领用最小闭环恢复；历史库存不可用演示数据代替 |
| report | 构建与服务依赖关闭 | 经营报表独立于康复报告；先确认授权数据源和统计口径 |
| ai | 构建与服务依赖关闭；康复模块有独立关闭的 AI 能力 | 需另行指定供应商、安全配置、合成数据调用和费用边界；不得默认传输真实患者资料 |
| mall / pay / mp / iot | 源码/页面存在但不在本次机构业务优先范围 | 暂不开放；如变更范围需重新审查依赖及验收 |

`yudao-dependencies` 与 `yudao-framework` 是工程基础，不是单独的可登录业务板块。前端页面存在并不证明服务端、表结构和菜单权限可用。

## 已实施：康复与 CRM/Member 桥接安全前置（未发布）

本地补丁修改 `RehabPatientServiceImpl`：

- CRM 与 Member 的原生 JDBC 查询增加当前 `tenant_id` 参数。原生 JDBC 不经过 MyBatis 租户拦截器；没有租户上下文时拒绝读取跨模块元数据。
- 患者绑定 CRM 前，要求目标客户在当前租户存在且未删除。失败时不写绑定，并返回不区分不存在/无权限的错误码。
- 新增相应单元回归：CRM/Member 两条查询的租户参数、不可访问客户拒绝、同租户客户可绑定、缺租户上下文拒绝。

变更仅在上述新检出中；未增加 Schema、未运行迁移、未默认接入 CRM/BPM/ERP 服务依赖、未调用 AI、未触碰运行数据库。开发中测试编译错误已修复；最终 JDK 17 定向 `RehabPatientServiceImplTest` **9 项通过，0 失败、0 错误、0 跳过**。JDK 17 `mvn -B -pl yudao-module-rehab -am test` 构建成功，91 个 Surefire 报告汇总 741 项：712 通过、29 跳过、0 失败、0 错误。首批补丁的 `git diff --check` 通过。这不是所有可选模块回归，更不是跨模块集成或浏览器验收。

## 本轮追加：构建级业务模块接入（不是运行启用）

- 根 `pom.xml` 与 `yudao-server/pom.xml` 增加显式 opt-in 的 `rehab-business-integration` Maven profile；仅指定 `-Prehab-business-integration` 时将 BPM、CRM、ERP、Report 带入服务端构建。默认构建配置、前端菜单、数据库和已有运行服务不变。
- JDK 17 `mvn -B -Prehab-business-integration -pl yudao-server -am -DskipTests package` 成功，构建的 JAR 中能看到上述四个模块。**这个产物缺少已确认的数据库迁移与权限验收，不得部署。**
- 同 profile 的 `mvn -B -Prehab-business-integration -pl yudao-server -am test` 成功：118 个 Surefire 报告汇总 830 项，794 通过、36 跳过、0 失败、0 错误。其中 BPM 45 项（6 跳过），Report 8 项；CRM 和 ERP 没有可计数的单元测试，不应记为已测通过。
- 前端冻结锁文件安装与 `pnpm build:internal` 成功。`pnpm ts:check` **失败：1,129 条诊断**，与此前同一基线的数量一致；本轮未改前端源文件，但不能据此宣称类型检查通过。
- `.github/workflows/ci.yml` 新增独立的可选模块测试/打包 job，主 backend job 的默认发布构建路径保持不变。本地尚未执行 GitHub Actions；新增 job 的云端结果仍待推送后验证。
- 尝试重新验证默认服务端构建时，Maven 长时间停在 `Scanning for projects...`，已软取消；**默认构建本轮未取得通过结果**。文件系统/IO 原因未确认，不强杀其它进程。隔离目录存在 opt-in 构建产物，不可取它当默认发布包。

### 静态表结构缺口（只读代码/SQL 扫描，不代表目标数据库现状）

按各模块 Java `@TableName` 统计，并与仓库 MySQL 建表语句比较：CRM 19 张表在历史 009 bootstrap 脚本中有定义，但 001—019 不允许作为升级脚本重放；BPM 8 张、ERP 33 张、Report 1 张未发现对应的 MySQL 建表语句；Member 11 张中有 4 张可见于历史桥接脚本，另 7 张没有发现。Flowable 引擎内部表不在这个 `@TableName` 清单内，缺口可能更大。仓库的 `sql/mysql/ruoyi-vue-pro.sql` 含 `DROP TABLE`，不得直接导入已有库或当成增量迁移。

对既有 `rehab-internal-mysql-1` 测试容器仅查询 `information_schema` 表名并按前缀计数：`ACT_` 39、`FLW_` 8、`crm_` 19、`member_` 4；未发现 `bpm_`、`erp_`、`report_` 业务表。**此结果仅描述既有测试数据库，不等于本批已建立独立隔离库；未读取患者/业务行，也未写数据库。**

进入隔离运行及真实浏览器验证前，须先确定各模块精确 DDL、初始化数据与权限来源，制作版本化 additive 迁移并测试，且保持现有业务卷不变。构建成功与页面文件存在都不代表模块能安全启动。

用户已确认可提供与当前源码匹配的 BPM/ERP/Report 原始 MySQL 脚本，**文件尚未收到**。收到后先核对版权使用范围、DDL 与 DO/Mapper 匹配、`DROP`/`TRUNCATE`/覆盖性 seed、菜单权限、历史迁移冲突；仅在隔离库验证经审查的新版本迁移，不直接导入现有数据库。

用户随后提供上游 Gitee 仓库地址 `https://gitee.com/zhijiantianya/ruoyi-vue-pro`，可用于定位源码来源，**不等于已提供匹配的业务模块 SQL**。只读查看 Gitee `master/sql/mysql/` 仅列出通用 SQL 与 Quartz SQL；官方 GitHub 镜像当前 `master/sql/mysql/ruoyi-vue-pro.sql` 有 48 个建表定义和同数 `DROP TABLE`，未见 `bpm_`、`erp_`、`report_`、`crm_`、`member_` 表。该上游最新主线与本项目固定旧基线并非同一版本，不得混用或直接导入。仍需上传具体可合法使用且版本匹配的模块 DDL/权限脚本；否则须改为基于当前 DO/Mapper 的逐表设计与隔离验收。

用户又提供官方 GitHub 仓库与 `https://doc.iocoder.cn/intro/`。已通过 GitHub API 只读枚举其 `master` 和 `master-jdk17` 完整 tree（两者均未截断，各 13,068 路径）：`sql/mysql/` 仍只有通用 SQL 与 Quartz；BPM/Report 的模块目录只有 `src/test/resources/sql/create_tables.sql`（测试表结构），ERP 模块目录没有 SQL 文件。检查公开的 69 个 GitHub release 没有附带可下载的模块 SQL 资产。官方文档的简介说明架构，但不提供所需业务 DDL。**当前仍没有可直接审查的 BPM/ERP/Report 生产 MySQL 建表包**；不以测试 H2 脚本或较新通用初始化库代替增量迁移。

## 剩余门禁与下批顺序

1. 先核对 CRM、BPM、ERP、Report 的依赖树、Bean、DDL 与迁移账本；设计 additive SQL 及隔离环境的前滚/失败处理，不重放历史初始化脚本。
2. 确认统一 Client 与 Patient/CRM/Member 映射、前台/销售/治疗师字段权限及跨租户绑定；随后逐模块接入后端、API、前端和菜单。
3. 对每批用隔离数据库和合成资料执行构建、单元/集成、安全及真实浏览器业务路径，覆盖板块联动、越权和异常路径。旧部署浏览器基线见 `docs/mcp-execution-batch-02.md`，不能继承其“通过”结果作为新检出的证明。
4. 外部 AI 调用须先有供应商/合同及合规范围、费用上限、脱敏策略、审计和人工确认；仅用合成资料做首次联调，禁止在日志和仓库放置密钥。
5. 全量范围达到约定验收并核对远端 master、排除 `.DS_Store` 和敏感文件后，再提交、非强制推送。当前没有提交或推送；不能称全部模块已经开放。


## 2026-09-23：Mac MCP 重连后 Report 020 第一阶段

- 原 `rehab-module-enable-work` Git pack 校验失败，未对其执行提交或强行修复；新建干净浅克隆 `rehab-module-enable-clean`，恢复既有 Maven/CI/Rehab 补丁和进度文档，应用 Report 020。旧树及业务卷未覆盖。
- `migrate.sh verify-files` PASS；迁移门禁 18 项，15 通过、3 项真实 MySQL 集成测试未启用；Report Service 9/9 通过。
- Mac JDK 17 opt-in reactor 测试：831 项、795 通过、36 跳过、0 失败；前端离线安装与 `build:internal` 成功。
- 专用 MySQL 8.4 无网络/无端口临时容器：020 表结构及两个合成租户、逻辑删除冒烟 PASS；测试库与容器已删除。未修改现有业务数据库。
- **未完成**：BPM/ERP/Member 缺失 DDL、Report/JMReport 安全审查、完整独立 Compose 运行、真实浏览器前后端验收与 Git 推送。不得把 020 的成功解释为 Report 已全面开放；提交时排除 `.DS_Store`。


## 2026-09-23：默认版和机构模块 opt-in 整体构建复核

- 默认后端 JDK 17 打包 PASS（155 MB）；机构模块 opt-in 后端打包 PASS（266 MB，包含 BPM/CRM/ERP/Report）。内部前端打包 PASS（729 个文件，约 11 MB）。命令、产物及 SHA-256 详见 `docs/build-verification-2026-09-23.md`。
- opt-in 后端 reactor 测试 831 项：795 通过、36 跳过、0 失败。迁移门禁 15 通过、3 项真实 MySQL 测试跳过。
- `pnpm ts:check` 仍失败，1129 条已有 TS 诊断；当前前端源文件相对 Git HEAD 无跟踪改动。其中 ERP/CRM/BPM/Member 分别约 327/143/30/27 条；不能宣称完整质量门禁通过。
- 仅构建和隔离 Report 020 冒烟，未补齐其余模块 DDL，也未做真实浏览器验收或推送 master；默认包和 opt-in 包不能互换，禁止把 opt-in 包直接部署到现有业务库。
