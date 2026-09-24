# Report 模块第一阶段：Mac 隔离验证记录（020）

> 历史阶段记录：本文下文的「尚未完成」对应当时状态；最新隔离库、Chrome 路径与报表安全验证见 [2026-09-23 隔离验收记录](isolated-browser-acceptance-2026-09-23.md)。

状态：**Mac 上的候选改动已完成局部隔离验证；尚不能部署、开放菜单、提交/推送或修改现有业务库**。原独立工作树的 Git 对象包损坏，已在新的 `rehab-module-enable-clean` 浅克隆恢复先前源码改动并应用 Report 020；旧树及业务卷未覆盖。

## 设计范围

- 020 仅新增 `report_go_view_project`；与 DO 字段对应，`status` 为数值，`content` 为 LONGTEXT，包含 `tenant_id`、BaseDO 审计及逻辑删除字段。重复/不兼容表故意失败，不静默跳过；不含清表、种子数据、历史迁移重放。
- DO 继承 `TenantBaseDO`，按 MyBatis 多租户机制隔离；项目 get/update/delete 额外检查创建者，跨用户按不存在处理；原生 JDBC 的 `get-by-sql` 一律拒绝（原实现绕开租户拦截器且可读取患者数据）。HTTP 示例接口不再给出随机伪造运营指标，改为未配置错误。此前 Report 的 H2 测试 `status varchar` 与 DO 不一致，候选测试 DDL 改为 integer 并加 tenant_id。
- 020 登记在 `migrations.manifest`，新数据卷通过 109 脚本先建表再由 110 初始化账本记录；已有库只能在独立测试环境完成前置核验后按迁移器流程处理，不能重放 001—019。

## 已完成的静态与隔离检查

- `sh deploy/internal/migrate.sh verify-files` PASS；`git diff --check` PASS。
- `python3 -m unittest discover -s deploy/internal -p 'test_migration_guard.py' -v`：18 项，15 通过、3 项真实 MySQL 测试因未显式 opt-in 而跳过；020 缺失时的只读状态与 001—019 禁止重放均通过。
- Mac JDK 17 中 Report Service 测试 9/9 通过；opt-in 全量 reactor 测试共 831 项：795 通过、36 跳过、0 失败。专用无网络、无端口、临时数据卷 MySQL 8.4 容器中，020 新表/数值 status/两个合成租户/逻辑删除冒烟测试通过；容器已删除。前端 `pnpm install --offline --frozen-lockfile` 和 `pnpm build:internal` 通过。**以上不等于应用级跨租户测试、完整新库初始化或真实浏览器验收。**

## 在 Mac 执行的后续门禁（当前未完成）

1. 已在独立且 Git 完好的 `rehab-module-enable-clean` 恢复先前改动并应用 020；损坏的旧克隆未覆盖。`.DS_Store` 在新检出中发生本地变化，后续提交必须排除。
2. 已在**专用一次性 MySQL 8.4 容器**按说明运行 `deploy/internal/test_report_schema_isolated.py`（需显式设置 `REHAB_TEST_MYSQL_CONTAINER` 与 `REHAB_TEST_MYSQL_ACK=DEDICATED_SYNTHETIC_CONTAINER`）。脚本只建/删随机命名的独立测试库并写入合成行；PASS 仅证明表结构，不证明应用租户拦截器生效。仍需在独立 Compose 项目通过完整新库初始化/升级迁移器与 `migrate.sh status` 验证 020 账本；测试现有 001—019 数据的保留。
3. JDK 17 Report Service 单测 9/9 已通过；仍需补充无权限、实际跨租户访问的真实 MySQL DAO/HTTP 权限回归以及 JMReport/GoView 依赖表审计。此阶段不得上传患者数据到任何外部 AI。
4. 全部通过后才审阅菜单/权限与前端真实路径，开启相应功能，进行本地浏览器联动；然后审阅 Git 状态、提交并推送。当前**不开放 Report**：原应用配置还将 `/jmreport/*` 列为租户忽略 URL，第三方 JMReport/JimuBI 表及其安全边界未审；020 绝不意味着完整报表已可用。BPM／ERP／Member 的缺口另行逐表处理。

## 安全理由与残余风险

旧 `/report/go-view/data/get-by-sql` 接受任意 SQL，经 JdbcTemplate 直连应用库，绕开 MyBatis 租户拦截器；即使有权限字符串也不可允许读取跨租户/患者数据。本候选补丁直接 fail closed，而不是用不可靠的 SQL 正则解析。项目元数据隔离仍需在实际 MySQL + HTTP 权限环境验证；JMReport 第三方端点的租户策略待单独审计。不要把构建成功或只有表的隔离测试认定为前后端开放。
