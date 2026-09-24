# 康复与机构运营项目：本地整体构建核验（2026-09-23）

> 历史阶段记录：本文下文的「尚未完成」对应当时状态；最新隔离库、Chrome 路径与报表安全验证见 [2026-09-23 隔离验收记录](isolated-browser-acceptance-2026-09-23.md)。

## 结论

**后端默认包、机构模块 opt-in 包和内部前端生产构建均成功；TypeScript 全项目静态检查仍失败。** 这仅是构建核验，不是 BPM／ERP／Member 数据库可运行、跨租户安全或本地浏览器联动验收。未部署、未触及现有业务数据库/卷、未提交或推送 master。

工作目录：Mac `/Users/saber/Documents/Playground/rehab-module-enable-clean`（从同一 master `bafc441` 新建的完整浅克隆；旧独立工作树 Git pack 已损坏，未覆盖）。JDK 17；前端用锁文件离线安装 `pnpm install --offline --frozen-lockfile` 成功。

| 检查 | 结果 | 产物/说明 |
|---|---|---|
| `mvn -B -q -pl yudao-server -am -DskipTests package` | PASS | `yudao-server/target/verified-default/yudao-server.jar`（155 MB，默认版，仅 System/Infra/Rehab/Member）；SHA-256 `e694f48905e0dd25b004210486dbde7b4eac77e233201eb43e86fabee60e7fdc` |
| `mvn -B -q -Prehab-business-integration -pl yudao-server -am -DskipTests package` | PASS | `yudao-server/target/yudao-server.jar`（266 MB，另含 BPM/CRM/ERP/Report）；SHA-256 `7514ac6d203851a42a409a57f56ea503de8b7af513fa391d2a3b18226f7d890a`。**仅构建用，不得作为现有库的默认发布包** |
| JDK 17 `mvn -q -Prehab-business-integration -pl yudao-server -am test` | PASS | Surefire XML 共 831 项：795 通过、36 跳过、0 失败；包括 Report 9 项 |
| `pnpm build:internal` | PASS | `yudao-ui/yudao-ui-admin-vue3-app/dist-internal`，729 个文件、约 11 MB |
| `pnpm ts:check` | **FAIL** | 1129 条 TS 诊断；该前端目录对 Git HEAD 无跟踪文件改动，故这些错误并非本轮补丁引入。与当前范围相关的分类约 ERP 327、CRM 143、BPM 30、Member 27 条；另有 Mall/Pay/AI/IoT 等非本轮模块及通用组件错误。日志：Mac `/tmp/rehab-clean-frontend-ts-check.log` |
| `sh deploy/internal/migrate.sh verify-files` 与 `git diff --check` | PASS | 迁移 020 校验和及补丁空白检查 |
| 迁移门禁测试 | PASS（部分跳过） | 18 项、15 通过，3 项真实 MySQL 集成测试未显式 opt-in |
| Report 020 隔离 MySQL 冒烟 | PASS | 独立无网络、无映射端口的临时 MySQL 8.4 容器，合成数据验证建表/整数状态/两个租户过滤/逻辑删除；容器和测试库已删除。**不是应用级租户隔离验收** |

## 阻断“完整交付”的问题

1. BPM、ERP、Member 仍缺源代码对应的生产 MySQL 模块增量 DDL；Report 020 只覆盖 GoView 一张表。现有业务卷不可用于试探。第三方 JMReport 路径的租户边界待审。
2. TypeScript 全量检查失败，其中机构相关模块也有大量既存诊断；前端打包通过不等于类型安全。
3. 尚未在独立 Compose 项目完成全量初始化/迁移、真实数据库 DAO 越权测试、本地浏览器菜单/API 联动验收。
4. Git 工作树仍含未提交补丁以及 `.DS_Store` 的本机变化；未进行提交/推送。生成的 JAR、前端构建产物、密钥和患者资料不得纳入 Git。

后续顺序：逐模块设计和独立库验证增量表结构 → 处理机构相关 TS 诊断与权限门禁 → 运行隔离服务及浏览器验收 → 审阅变更和敏感信息 → 验收合格后才推送 master。无需针对每一步再次索取审批，但不得将未验收的 opt-in 包部署到现有业务库。
