# 2026-10-09 工程接替与运行门禁复核

> 状态：**NO-GO**。这是针对当前本地未冻结工作树的执行记录，不是正式发行验收，也不表示已合并 PR 或完成临床审核。

## 本机工作树（接替时）

| 仓库／工作树 | 分支与 HEAD | 本地差异 |
| --- | --- | --- |
| 主系统 | master / `3d778538a3` | 既有报告/发布改动；保持未提交 |
| 主系统动作评估 | feature/motion-assessment / `2f7f515973` | 3 个 Java 文件修改；保持未提交 |
| Engine 研究 | research/phase5-7-workbench / `ab6e3ac` | 既有 5 项本地改动；本次另加免认证模式防误用检查和 3 项测试 |
| Engine 临床 | feature/motion-assessment / `a31deb2` | 干净 |
| Reporter | main / `c1a0563` | 干净 |

上述 HEAD 仅记录接替时状态；任一工作树变更后，须再次生成对应测试证据并重新冻结 SHA。未覆盖、提交、推送或合并已有改动。

## 本轮实际运行

| 范围 | 命令／结果 | 原始输出位置及 SHA-256 |
| --- | --- | --- |
| 主系统 Java（JDK 17.0.15） | `mvn -B -pl yudao-module-rehab -am test`；110 项通过，0 失败 | `/tmp/rehab-master-full-20261009.log`；`3abbfb498532cb188e1dfbe636da9fe705506daae9ecb79171dbe49560cbde5a` |
| 动作评估 Java（JDK 17.0.15） | 同一 Maven 命令在动作评估 worktree 执行；168 项通过，0 失败；MotionTrialCondition 6/6 | `/tmp/rehab-motion-full-20261009.log`；`305daf3dbb38e8841fe10b0d7e1a95ade131fdaadaa85a26238708e23c9776dc` |
| 研究 Engine（完成本轮本地安全加固后） | `.venv/bin/ruff check .` 通过；`.venv/bin/pytest -q` 302 项通过、1 条第三方弃用警告 | `/tmp/rehab-engine-final-20261009.log`；`f454430c382311664f7c5449a962dc4a23a469d060e181f3f5f46e692b1f9b70` |
| 桌面资源脚本 | `node --test desktop/scripts/*.test.mjs`；14/14 通过 | `/tmp/rehab-desktop-tests-20261009.log`；`40f5171e94be208a5db8a44260e4877532dba137f92cf218a67dd616b8aec71e` |
| 部署契约与迁移离线门禁 | `python3 -m unittest discover -s deploy/internal -p 'test_*.py' -v`；29 项运行、26 通过、3 跳过、0 失败；没有真实 DB 操作 | `/tmp/rehab-deploy-contracts-20261009.log`；`5d78a3b548535b529cc850369039ff4cc74fc1c7261cb0816d4235914a62b603` |
| Python Reporter | `python3 -m pytest -q`；42 项通过、21 条弃用/兼容警告 | `/tmp/rehab-reporter-tests-20261009.log`；`ea2ed094e69fe060bf1ada876e1eeae4696d8b40e5293514a8f2cc00338c41e0` |
| 主系统 Web 安全（JDK 17） | `ApiAccessLogInterceptorTest,GlobalExceptionHandlerTest`；2 项通过，0 失败 | `/tmp/rehab-web-security-20261009.log`；`27d9a98e945770544a6549e54de50d97ebfca316dc3e695e0caad30b46e47415` |
| Vue TypeScript | `pnpm ts:check`；退出 0 | `/tmp/rehab-vue-tscheck-20261009.log`；`3713a0519dd6396d51b21a22dd73da3230d4092228ebd738857df6463ecea1e7` |
| Vue 内部构建 | `pnpm build:internal`；退出 0；输出 828 个文件，新建同 HEAD 的 frontend receipt；发现 Browserslist 数据过旧与 Vite CJS 弃用提示 | `/tmp/rehab-vue-build-20261009.log`；`a69a31b8a2bd4ed1bac6d70d0a966ec16f6f7cb7a305d751c5e40409a7798b06` |
| 隔离 Spring Boot JAR 构建（JDK 17） | `bash deploy/internal/build-server-isolated.sh`；成功；新制品 SHA-256 `60522884855b1c56f6dcfc09a1fd242f251fcfcb18acfd414811876f30e89a58` | `/tmp/rehab-isolated-jar-20261009.log`；`725589b15ea21c8b794454ec4068c9e9dee2b4fa424433d31c11b9b72e83f0f9` |
| 报告页面 ESLint | `pnpm exec eslint src/views/rehab/report/index.vue`；退出 0 | `/tmp/rehab-report-eslint-20261009.log`（空日志表示无问题） |
| 安装包前门禁 | `node desktop/scripts/prepackage-readiness.mjs`；`ready=false`、14 个阻断项、退出码 1（预期 NO-GO） | `/tmp/rehab-readiness-20261009.log`；`f9376160773c1b42d4c283856cd7a68eb124648941334be47c20ef324ed8dca9` |

上表临时日志保存在 **Mac 本机 /tmp**，并非已归档的永久证据；清理临时文件前须在本地受控路径归档，并按原日志重新校验哈希。不得上传患者信息或凭据。Vue、完整跨仓 E2E 与发行冻结提交仍需另行验收。

## 中断恢复：桌面 bootstrap SQL

- `desktop/build/desktop-bootstrap.sql` 不存在，未用空 SQL 或绕过校验方式修复。
- 新前端 receipt：`commitSha=3d778538a38f8444571deb2c356308586dba0359`，`outputFileCount=828`，`outputSha256=0eff11c3c704842b001b1dac5f483d7bae5c332f000256e10354627715a28a39`；仅是前端构建产物回执，不能替代源码清洁与发行冻结。
- 再次实际执行 `node desktop/scripts/build-runtime.mjs --output /tmp/rehab-prepackage-runtime-20261009`，在输入门禁处明确因缺少 `desktop/build/desktop-bootstrap.sql` 退出 1，**没有创建** runtime 目录。日志 `/tmp/rehab-runtime-attempt-20261009.log`，SHA-256 `b4cb4e3f12b10afa8090d606d1fd77e7558e7c9c0ba741739bd66127c4cee170`。
- `build-sanitized-bootstrap.mjs` 中列举的 23 个初始化 SQL 文件全部存在。该脚本启动随机命名、`--rm` 的固定 digest MySQL 容器，使用随机凭据，依次导入仓库脚本、清洗、dump，并在同一临时容器中**删除重建临时数据库**验证恢复后 `system_users=1`、`rehab_patient=0`、迁移账本 19 条。仅适用于专用临时容器，**不得把清洗 SQL 用于业务库**。
- 本机 Docker Unix Socket 存在，但 `/_ping` 请求 2 秒超时，Docker 服务暂不可验证；不能安全运行需要 Docker 的真实 snapshot 构建及独立 Compose E2E。
- 前端 receipt 和后端 JAR 均已由本轮重新构建，但缺少经隔离 MySQL 生成的脱敏 bootstrap SQL；两者齐全仍绝不能当作完整 runtime 或正式 RC 验收。
- Docker 服务恢复后先核验守护进程和专用构建容器作用域，执行仓库 `AGENTS.md` 中的标准 bootstrap、runtime、check-runtime 命令；不用已有业务容器与卷。

## 安全修复、未验收与下一批

- 研究 Engine 的 `--local-no-auth` CLI 新增拒绝预设 `ENGINE_DB_PATH`、拒绝同时使用 `--init-demo`，防止免认证 CLI 意外沿用已有数据库。新增 3 个独立负例并通过 302 项全量 pytest。原有请求侧 Host/Origin/远端 peer 安全逻辑及既有测试保留。
- 动作评估三个未提交 Java 文件已审阅差异：临床字段更新现在保留 Trial 的 conditionCode；YBT 在无选中协议/试次时不因单独填写肢长被请求。168 项 Java 通过，但尚需完整跨仓 OpenCap 会话、签署/并发和租户 E2E。
- PR #5 在检查时仍开放，当前未合并；GitHub 报告 mergeable，但不能将此当作已完成代码审查、跨仓接口兼容或 020–025 隔离数据库验收。
- B 版临床合同与母版已经找到并读取工程章节映射。当前 Java 仍是 raw-data-only，不属于正式治疗师或家长版可签发报告；证据冻结、冲突解决、双文档生成和逐页 QA 继续阻断。
- 其他阻断：正式患者身份与家长授权、租户/文件范围及并发负例、隔离迁移和备份恢复、许可证、Windows/macOS 安装签名和升级回退，均不能用离线单测替代。
- `deploy/internal/docker-compose.yml` 的两个内部自建镜像仍使用 `:latest` 标签；发行冻结之前需核查镜像构建与 digest。没有在未经核查前擅自替换镜像引用。

**执行原则：** 本记录不变更 `PRE_PACKAGE_GATES_20261008.md` 的 NO-GO 结论；所有正式发布门禁必须绑定冻结 SHA、真实日志、独立验收证据与人工审批。

## 2026-10-09 后续续执行：新增代码与验收

本次重新连接 ShunCode 后，复查五个工作树 HEAD/dirty diff 与上表一致。Docker Desktop 进程存在、Unix Socket 可访问，但 `/_ping` 与 Docker CLI 健康检查仍不响应；未启动业务容器或操作已有 Docker volumes。

### 实际代码修改（全部未提交）

1. `desktop/scripts/build-sanitized-bootstrap.mjs`：在创建专用临时 MySQL 容器前进行 12 秒超时的 Docker daemon 预检；引擎未就绪时显式拒绝构建，容器尚未启动时不调用清理命令，容器清理增加 10 秒超时。原有 23 项 SQL 来源、脱敏、恢复测试和 001–019 账本门禁保持不变。新增 `desktop/scripts/build-sanitized-bootstrap.test.mjs`，用退出码非零的模拟 Docker 验证失败前只调用 `version`，不会发起 run/rm/SQL 输入，不会留下输出。
2. `AdolescentEvidencePackagePolicy.java` 和 `AdolescentEvidencePackagePolicyTest.java`：新增与旧报告分离的 B 版草案结构/证据审核纯函数，校验 Source ID、引用、QEI 有效性、无效 YBT 零成绩、SFMA FN/FP/DN/DP 疼痛矛盾、时间戳、冲突解决和来源锁定版本。绝不自动授权签名，`clinicalReady=false`、`signatureAuthorized=false`。
3. `docs/report/schemas/adolescent-evidence-b-v1.schema.json`：补充 Draft 2020-12 证据交换 Schema；`docs/report/adolescent-b-v1-contract.md` 已注明“草案、未接数据库和报告生成器”，防止被误报为 B 版生产完成。

### 本轮续执行真实回归

| 本轮检查 | 结果 | SHA-256（Mac 私有日志） |
| --- | --- | --- |
| 主系统 JDK 17 全量 rehab | **119 通过、0 失败**；包含 9 项新增 B 证据测试 | `7c43be25bc27fc2f03697a02036799159af4dccf1037d4eda87b39b20c496df0` |
| B 版定向 Maven | 9 通过、0 失败 | `a815a4aa757ba327551b12c2c52ce8d8dec13d24cc0d0132bc328fc3025d2d95` |
| 桌面 Node.js 测试 | **15 通过、0 失败** | `0deda48f7a5a39ca577e5420562f477136440c6ef75fccf521914e0986e51743` |
| Engine 临床分支 Python | **363 通过、1 跳过**（真实样本目录未授权提供）；使用研究仓库 venv，但 PYTHONPATH 明确指向临床工作树自身 src | `7545125cb1dc748bf9bf12a3d81934684bb6089f704711563e6917b7922ffabf` |
| Docker Bootstrap 实际预检 | `DOCKER_DAEMON_UNAVAILABLE`，退出 1，**没有生成 SQL**（预期安全失败） | `20c5d93dac360f46763caf9a8987ef63f781dd9ba5089ef370273665b8f39714` |
| JDK 17 Spring Boot 隔离构建 | 成功，JAR SHA-256 `6632afced4e372a34999fe1ce08bd4f2add2f93a47197bd5cdb1a89c0d495e3b` | `8fe2c931b1607097a1f2259bdf3f18bd3da66cc0bd852ab4c7c06aa95564d4ed` |
| 当前发布前门禁 | **NO-GO / 14 项阻断**，退出 1，未填造通过证据 | `f9376160773c1b42d4c283856cd7a68eb124648941334be47c20ef324ed8dca9` |

上表七份原始日志已保存在 Mac 本机、**五个 Git 仓库之外**的 `/Users/saber/my-project/rehab/validation-evidence/2026-10-09/`，目录权限 700、文件权限 600；不提交或上传外部服务。曾用研究 Engine 已有的 `jsonschema` 依赖校验新增 Draft 2020-12 Schema：合成合法样本通过，非法日期、失败试次携带有效成绩被拒绝，退出码 0。该 ad-hoc 验证尚未成为固定 CI 测试，不得计为生产契约验收。

### 继续阻断与下批次

- **P0 Docker：** 服务恢复之前，`desktop/build/desktop-bootstrap.sql` 仍缺失，后续 runtime/check-runtime、隔离 MySQL 020–025 升级及恢复和 Compose E2E 都未通过；必须先安全核验 Docker Desktop，不能伪造 SQL 或复用真实业务数据库。
- **P0 动作评估：** PR #5 仍开放且无 GitHub 正式 Review；虽然 Java 168 项、Engine 临床离线 363 项通过，但没有固定跨仓版本的端到端上传/OpenCap/签署/权限矩阵和真实容器迁移验收。
- **B 版报告：** 已落地**草案 Schema 和纯函数来源审查**，尚未接入 B 版版本化数据存储、审核事务、治疗师和家长双版本 DOCX/PDF、逐页 QA 及临床专家确认。不得将纯结构测试称为临床审核通过。
- **发行门禁：** `source_integration`、`database_migration`、`biomechanics_contract`、`clinical_report_b`、`patient_identity`、`code_signing` 等仍为 pending；虽然 Java/Vue/Engine 有本轮局部成功日志，正式门禁尚需冻结提交和全部完整证据。

本次未 `git commit`、`push`、`merge`，没有操作真实患者数据库、数据卷、受保护归档或启动正式安装包发布。

## 2026-10-09 午间续执行：临床写入权限及 B 版证据互证

**状态继续 NO-GO**。ShunCode 曾短暂不可用，恢复后确认五个工作树 HEAD 不变、既有改动仍在；Docker Unix Socket 2 秒健康检查再次超时，未执行 Compose、SQL restore 或任何 Docker 清理操作。

### 新增已验证源码

- 主系统 `AdolescentEvidencePackagePolicy` 草案：增加 QEI 索引查重、来源引用与人工复核状态；四域（medical_safety、symptom、movement_control、growth_and_measurement）风险来源校验；红旗及症状未解决时拒绝将 `CONDITIONAL_TRAINING` 视为有效决策；KPI 基线/单位/复测方法/Source ID 与评估后 4–6 周复评日期校验。新增 4 项 Java 合成负例，原有原始数据报告接口和 `clinicalReady=false`、`signatureAuthorized=false` 保持不变。
- 对应 `docs/report/schemas/adolescent-evidence-b-v1.schema.json` 新增 qei、safety、risks、kpis 结构约束并使用合成数据验证 Draft 2020-12；五种异常输入（缺 QEI、风险证据为空、无效日期、未知红旗状态、疼痛超范围）均被拒绝。原始个案未导出。
- 动作评估工作树发现并修复高优先级授权缺口：`RehabMotionAccess.requireClinician` 原本只拒绝文员，使非治疗师的普通角色可能通过服务层临床写入检查。现在对空用户及非 `REHAB_THERAPIST` / `SUPER_ADMIN` 显式拒绝；文员保留旧错误码，新普通角色错误码为 `CLINICAL_ROLE_REQUIRED`。增加普通角色/空 ID 拒绝以及混合角色允许测试；详细边界见该工作树的 `docs/motion-assessment/security.md`。这是服务层白名单，不替代 Controller RBAC、患者/租户授权和真实端到端测试。

### 本次重跑的实际结果及私有证据

Mac 本地日志追加到 `/Users/saber/my-project/rehab/validation-evidence/2026-10-09/`，目录权限 700、文件 600。先前日志不覆盖；以下为**本次修改后**的记录：

| 检查 | 结果 | 日志 SHA-256 |
| --- | --- | --- |
| 主系统 B 版定向单测 | 13 项通过，0 失败 | `rehab-qei-safety-unit-20261009.log` / `b71a8ee77f2d12107eb0b683558d86b1e3dc15824b7bb016ebc6e26a212c2332` |
| 主系统 JDK17 rehab 模块全量 | **123 项通过**，0 失败 | `rehab-full-qei-20261009.log` / `2caabab60337a4dc3b60d8b50c5f40eb1bc482d52262f1032afe060e3902cc77` |
| 动作评估角色与 TrialCondition 定向 | **16 项通过**，0 失败 | `rehab-motion-role-regression-20261009.log` / `fdc5c0b235d29ee01c645e4f9333e27d3524675b2a52d23a049ab783e05cf2a3` |
| 动作评估工作树 JDK17 rehab 模块全量 | **170 项通过**，0 失败 | `rehab-motion-full-roles-20261009.log` / `0f0fa7d135edb1b62026d0223c24feb58c2e145240994e27a783a60d5000bc6b` |
| Spring Boot 隔离 JAR | **构建成功**，JAR SHA-256 `2d97427fd90512506a37cb52ea566573c18e668acf62af16261a7eeb8057e84a` | `rehab-jar-qei-20261009.log` / `b16c19ca7e5b88d9f4996fdea8909563fb2cfe50953e7d0d48ca8ff6cc9248da` |
| 桌面发布门禁 | **NO-GO，14 个阻断**，退出码 1 为预期结果 | `rehab-no-go-current-20261009.json` / `f9376160773c1b42d4c283856cd7a68eb124648941334be47c20ef324ed8dca9` |

上述 JAR 仍基于**未冻结、带未提交修改**的工作树，不属于发行候选产品。前端自上轮通过后未作修改，未重复旧测试；Engine/Reporter 同理未修改，沿用既有阶段记录但不能代替合并后的联调。

### 下一批依赖与阻断

1. Docker daemon 恢复后严格使用独立随机临时容器，生成并恢复校验 desktop bootstrap SQL、Runtime 与 020–025 迁移；绝不使用业务卷。Docker 不可用时不标记完成。
2. 冻结动作评估分支前需审核新白名单的全部调用路径、Controller 角色权限、租户跨请求、成员登录身份及签署并发。PR #5 在本轮远端只读检查时仍开放、无正式 Review；未合并。
3. B 版还缺少正式持久化/冻结事务、版本审计、签名权限、证据文件鉴权、治疗师和家长双版本生成及 Word/PDF 逐页 QA。新增 Policy/JSON Schema 仅结构验证，不能签发临床报告。
4. 所有 Go 门禁保持 pending，直到完整受控合成数据 E2E、隐私安全、数据库回滚、备份恢复、安装升级、许可证与签名证据齐备。

**未执行**远端 push、主线 merge、正式发布或删除已有数据。

## 2026-10-09 后续接替：成员/管理员身份 ID 碰撞防护和证据包输入限额

### 实际代码增量与安全边界

- **动作评估分支：** `RehabMotionAccess.requireClinician` 在角色白名单之前额外检查 Web 登录用户类型。若为 `UserTypeEnum.MEMBER`，即使其数字 userId 碰巧对应一个具有治疗师角色的管理员账号，也以 `CLINICAL_ROLE_REQUIRED` 拒绝临床写入。新增 `memberIdCollisionWithAdminTherapistMustNotAuthorizeClinicalWrite` 的合成 Mock HTTP 请求测试（MEMBER 拒绝，ADMIN 允许）；对确无请求上下文的内部后台调用仍按已存在的服务层角色白名单判断。此项不代替真实患者端 JWT、RBAC、租户隔离和签署并发 E2E。同步补充动作评估分支 `docs/motion-assessment/security.md`。
- **主系统报告证据草案：** `AdolescentEvidencePackagePolicy` 增加最大全局顶层字段 32、对象字段 64、每类登记列表 512、每条 Source ID 引用 64 的资源限额；超限时给出固定阻断码而不遍历超限集合。相同的顶层/数组限额已加入 Draft 2020-12 JSON Schema。新增 2 个 Java 合成测试和 JSON Schema 合成负例。仍为未接数据库或正式签署的草案，`clinicalReady=false`、`signatureAuthorized=false` 保持不变。

### 已确认的本轮回归

| 检查 | 本轮结果 |
| --- | --- |
| 动作评估访问控制 JUnit | 11/11 通过；包含 MEMBER/ADMIN 同值用户 ID 测试 |
| 动作评估工作树完整 rehab 模块 | **171/171 通过**，0 失败 |
| 主系统 B 证据包定向 JUnit | **15/15 通过**，0 失败 |
| 桌面 Node.js 脚本 | **15/15 通过**，0 失败 |
| 迁移离线契约 | **29 项：26 通过、3 跳过**（跳过项需隔离 MySQL），0 失败 |
| JSON Schema Draft 2020-12 | 合成有效包通过，513 条来源、65 个引用、33 个额外顶层字段均因限额被拒绝 |

本轮日志路径统一为 Mac 本机 `/tmp/rehab-motion-member-namespace-20261009.log`、`/tmp/rehab-motion-member-full-20261009.log`、`/tmp/rehab-b-evidence-size-bounds-20261009.log`、`/tmp/rehab-desktop-tests-resume-20261009.log`、`/tmp/rehab-migrations-resume-20261009.log` 和 `/tmp/rehab-schema-limits-20261009.log`。这些是**当前未冻结工作树**的局部执行记录；不能将单测或 Java 编译误报为发布候选验收。

### 继续阻断

Docker Desktop 进程/Unix Socket 存在，但 3 秒 `/_ping` 超时。由于未获得安全隔离的 MySQL daemon，仍未执行脱敏 Bootstrap SQL 构建、Runtime 验证、020–025 实库迁移/回滚或 Compose 端到端测试。不得删除业务卷、伪造 SQL、跳过门禁、自动合并 PR #5、推送未冻结代码或发布正式安装包。NO-GO 结论维持。

## 2026-10-09 最终本地续执行：身份命名空间及 Schema 互证回归

本轮 ShunCode 曾暂时断连，随后重新连接本机。五个原仓库/工作树继续保留原 HEAD 与未提交内容，未覆盖历史改动。

**代码与契约最终增量：**

1. 主系统证据校验器和 JSON Schema 增加输入资源上限：顶层 32 字段、对象最多 64 字段、每类登记最多 512 条、每项引用最多 64 个。超过上限只返回固定阻断码，不逐条扫描过大的集合。Java 新增 2 个合成单元测试，并用本机 Engine venv 的 JSON Schema 2020-12 验证正常合成包及超限源、引用、顶层字段。
2. 反证引用可选字段协议对齐：缺席的 `contradictory_evidence_ids` / `counter_evidence_ids` 表示未引用；**显式 null 非法**；必需来源 `evidence_ids` 缺失仍阻断。Java 新增合成测试防止把“字段未提供”混同为“字段格式非法”或把显式 null 当作合格来源。
3. 动作评估 `RehabMotionAccess.requireClinician` 在服务层治疗师/超级管理员角色白名单之外，拒绝显式 `MEMBER` Web 身份，阻断与管理端 userId 数值碰撞时的角色混淆；`RehabMotionAccessAndTaskTest` 使用 Mock HTTP 请求验证 MEMBER 拒绝、ADMIN 允许。未移除原有 controller RBAC、患者权限或租户检查。

**全部为真实本地执行结果，不是正式发行门禁验收。** 私有日志存在 Mac 本地 `/Users/saber/my-project/rehab/validation-evidence/2026-10-09/`，目录 700、文件 600；所有新测试使用合成数据，未写入真实患者资料：

| 最终验证 | 结果 | 私有日志或制品 SHA-256 |
| --- | --- | --- |
| 动作评估定向 JUnit | 11 项通过，0 失败 | `rehab-motion-member-namespace-20261009.log`：`5af1ae7cd362152d945a78101d143069e5171ed391ddc47e2950eb9e57c5980f` |
| 动作评估 rehab 模块完整 JUnit | **171 项通过，0 失败** | `rehab-motion-member-full-20261009.log`：`6fab0b9074c5d381b0c32eee7385c609d6b43bb9a7e28ba2a5a49cd22bbd9ad9` |
| 主系统证据校验定向 JUnit | 15 项通过，0 失败（增补可选字段前） | `rehab-b-evidence-size-bounds-20261009.log`：`a9189dab687568cb6b5332445977e0ff6a4901edc9e38bbe30f93d17ce930a51` |
| **主系统最终 rehab 模块完整 JUnit** | **126 项通过，0 失败**（包含可选反证和 null 区别测试） | `rehab-master-final-optional-reference-20261009.log`：`75f7a0d983d5bd05feb92b1842bedb80272be00c69e3c9b2a2a2824a97e3b6fe` |
| 桌面 Node.js 测试 | 15 项通过 | `rehab-desktop-tests-resume-20261009.log`：`bbe198a9f1fb61f63dd80a2ab25e37fd82ab8f29883c6476d977cdf2b1771753` |
| 迁移离线契约 | 29 项运行、26 通过、3 跳过、0 失败 | `rehab-migrations-resume-20261009.log`：`fd608d708b3142e9b8e596ba6e0de922e611f1accd473b1ee5c08d321e0cef71` |
| JSON Schema 限额与可选引用结构 | 草案 Schema 合法，正常合成包和超限负例按预期 | `rehab-schema-limits-20261009.log`：`c37e57495b92317c4306ce0ff3f776a90fa8334624be17a180e73875e51c6cc9`；`rehab-schema-limits-final-20261009.log`：`a38506f8d112429c9feb0edd88e2b47bcfe20fff612a49a2b736e64fa084e6c9` |
| **最终 Spring Boot 隔离 JAR** | 本轮最终修改后重新构建成功 | `yudao-server/target/yudao-server.jar` SHA-256 `4149cc55e1c26c5bb0d03bc60ca7068e4d7bc34afb2d5a2f2241067d6631f67c`；日志 `rehab-jar-final-contract-20261009.log` SHA-256 `6d651db8d42036ec149b62fe40e83049b0a0ed61664e61926974eb86540916f8` |
| 正式安装前检查 | **NO-GO、14 阻断**，退出码 1（预期安全拒绝） | `rehab-prepackage-final-current-20261009.json`：`f9376160773c1b42d4c283856cd7a68eb124648941334be47c20ef324ed8dca9` |

**Docker 根因仍未确认：** `desktop-linux` 上下文指向 `/Users/saber/.docker/run/docker.sock`，Socket 文件和 Docker Desktop 后台进程存在，但 `/_ping` 仍超时。未重启或结束 Docker 进程、未清理卷；需要在不影响既有业务容器的前提下人工恢复 Docker daemon 后才能继续桌面 SQL / Runtime / 实库迁移 / Compose E2E。当前 `desktop/build/desktop-bootstrap.sql` 仍缺失。

**未完成 / 保持阻断：** PR #5 审查与合并、患者身份强门禁与真实 JWT/租户测试、跨仓 Engine/OpenCap API 端到端、020–025 实库迁移与回滚、双版本 B 版 DOCX/PDF 及逐页临床验收、桌面运行资源哈希、备份恢复、Windows/macOS 安装与签名。新 JAR 仍绑定**未冻结未提交**代码，不能用作正式发布依据。

本次没有 `commit`、`push`、`merge`、安装包发布或数据库数据变更。下一阶段依赖 Docker 可验证地恢复和临床审核授权。

## 2026-10-09 后续持续执行：共享权限与 Java–Engine 静态契约

**发布判定：NO-GO，14 项阻断。** 当前仍是未冻结、含未提交修改的本机工作树；以下成功项不能自动填入正式发布门禁。五个本地 HEAD 未变化：主系统 `3d778538a3`，Java 动作评估 `2f7f515973`，Engine 临床 `a31deb2`，Engine 研究 `ab6e3ac`，Reporter `c1a0563`。

### 本轮实际修改

1. **动作评估患者可见范围：** `RehabMotionAccess.visiblePatientIds` 对空 userId 和显式 `MEMBER` 登录返回空集合；避免 MEMBER 数字 ID 与 ADMIN 相同时取得“`null` = 全部患者”的后台分页范围。审计 `role` 优先区分 MEMBER，不冒用同值管理端治疗师/管理员角色。测试覆盖 MEMBER 被拒、ADMIN 正常可见及角色日志差异；对应 Mapper 的空集合路径直接返回空页。
2. **共享数据权限：** 主系统和 Java 动作评估工作树的 `RehabDataPermissionService` 同步拒绝 MEMBER 登录从管理员角色表继承可见患者集；空 patientId / userId 拒绝读取。通过 Mock HTTP 请求、相同数字 ID 的两种用户类型、空 ID 测试验证。该改动应在 PR #5 整合时**协调同名文件在两个工作树中的 diff**，不能自动合并覆盖未提交内容。
3. **可复验跨仓协议测试：** 新增 `deploy/internal/verify_motion_engine_contract.py`，要求显式提供 Java 和 Engine 两个工作树路径；只读对比 Java 评估测试常量与 Engine `tests_registry.catalog()` 的代码、所属体系、允许侧别，输出带双方分支、HEAD 和 dirty 标记的 JSON。新增 `deploy/internal/test_motion_engine_contract.py`：合成的匹配、代码遗漏及侧别不一致负例。当前 Java 与 Engine 临床分支 **19 对 19**，代码/体系/侧别**零差异**。

手工重复跨仓检查（使用已授权、隔离的本地 Python Engine venv）：

```bash
cd /Users/saber/my-project/rehab/rehab-management-system
/Users/saber/my-project/rehab/services/rehab-biomechanics-engine/.venv/bin/python \
  deploy/internal/verify_motion_engine_contract.py \
  --java-tree ../worktrees/motion-assessment \
  --engine-tree ../worktrees/engine-motion-assessment
```

此检查只核对**静态代码、体系与侧别**；尚未实际联通 Java REST 调用与 Python Engine ZIP 分析，不能替代接口响应兼容性、租户、OpenCap 或签署 E2E。

### 本轮真实回归与日志

日志均保存在 Mac 本机 `/Users/saber/my-project/rehab/validation-evidence/2026-10-09/`，700 目录、600 文件权限，之前的日志不覆盖。SHA-256 仅对应下列此次执行的原始日志或制品：

| 项目 | 结果 | 日志文件与 SHA-256 |
| --- | --- | --- |
| 共享数据权限主系统定向测试 | 3 通过，0 失败 | `rehab-permission-shared-main-20261009.log`（同目录，哈希可重新核验） |
| **主系统完整 rehab 模块** | **128 通过，0 失败** | `rehab-main-patient-access-full-20261009.log`，`9eb81174780c89015d630995bb1ba90ec4f28c94cd7fa2c5f0104baad0301eb0` |
| 动作评估权限和共享权限定向测试 | 15 通过，0 失败 | `rehab-motion-patient-access-targeted-20261009.log`（同目录） |
| **动作评估完整 rehab 模块** | **174 通过，0 失败** | `rehab-motion-shared-permission-full-20261009.log`，`9836da82bfc46e4a704f2dfee43fee22da12e429b679ab236b8874143a5b445f` |
| 跨仓 Java–Engine Catalog 校验 | **19 对 19，0 不一致**，Java dirty、Engine 临床干净 | `rehab-crossrepo-catalog-contract-20261009.json`，`b38840635bedbc1d770698836f64a71b12f219c95883e8f57922d7daeb50f29f` |
| Engine 临床分支内部 HTTP 单测 | **15 通过**，1 条 Starlette 第三方弃用警告 | `rehab-engine-http-contract-20261009.log`，`6cb49bc3a2572724ebc6b5c47476a521d03c41cf935c05721801935d3ece8d3e` |
| 部署离线契约（包含新增 3 项 Python 合成合同单测） | **32 项运行、29 通过、3 跳过**（隔离 MySQL 未启动） | `rehab-deploy-and-motion-contract-full-20261009.log`，`3fc072814a79cf9ea3b0ddfedaffa5073bc3b11e7c88bed26dfc9e8c4c432857` |
| 桌面 Node.js 脚本 | **15 通过，0 失败** | `rehab-desktop-contract-next-20261009.log`，`bce9051abd041a48141187ed6e0fea5056be554e3ae4b8875e0471925686aa92` |
| 隔离 Spring Boot JAR（最新主系统安全修复后） | **构建成功**；制品 SHA-256 `72ac0c6b51145646a1ca0cabb2f046280856c81687b21a99dc5342f4625f62d3` | `rehab-main-permission-jar-20261009.log`，`983cec1153b7c780e8cf9fc9e88da99d5947145b79c18c68f3b6516b2d377154` |
| 正式安装前门禁 | **NO-GO / 14 阻断**，退出 1（预期）；`desktop-bootstrap.sql` 仍缺失 | `rehab-readiness-contract-next-20261009.json`，`f9376160773c1b42d4c283856cd7a68eb124648941334be47c20ef324ed8dca9` |
| Docker bootstrap 实际预检 | **DOCKER_DAEMON_UNAVAILABLE**，退出 1，未创建 SQL | `rehab-bootstrap-next-20261009.log`，`20c5d93dac360f46763caf9a8987ef63f781dd9ba5089ef370273665b8f39714` |

### 未验收及下一批任务

- **最高环境阻断**：Docker Desktop daemon 对本机 `/_ping` 超时，不重启/终止现有 Docker 进程、不改业务数据卷。待安全恢复后在随机独立容器生成脱敏 SQL、恢复验证、构建 Runtime、校验 SHA 与 020–025 隔离迁移／回滚／备份恢复。
- **联调缺口**：静态 Catalog 匹配并非真实 Java→Engine ZIP API E2E；还需模型/协议版本锁定、OpenCap 双路径、错误码与多租户隔离、并发审核和签署。PR #5 尚未合并。
- **临床缺口**：B 版来源与质控草案不等于治疗师正式版；双版本 DOCX/PDF、证据冻结事务、临床人工复核、逐页检查及客户授权依旧缺失。
- **发行缺口**：患者身份门禁、许可证、镜像 digest、Windows/macOS 安装升级回滚及签名未验收，不能声称正式版完成。

本轮未执行 Git 提交、远端 push、PR merge、真实数据库变更、容器/卷删除或正式发布。所有回归是**当前未冻结源码**的阶段性工程证据。

## 2026-10-09 16:26–16:39：桌面 Runtime 无持久卷 E2E 与原生启动器回归

**现场状态：** Docker Engine 29.8.2，Compose v5.5.1；五个 Git 工作树 HEAD 与本审计前文一致，保留所有未提交修改。现有业务桌面端口 18080/18443 被占用，没有停止这些服务、连接真实患者数据库或删除任何 Docker volume。

**本轮新增源码：** desktop/scripts/test-runtime-e2e-ephemeral.sh（新建，权限 755）。不使用历史脚本 test-runtime-e2e.sh 的 down --volumes，也不重用其固定端口。新脚本使用唯一随机 Compose 项目和本机随机端口；通过 Compose !override 删除全部固有 named volume 映射，MySQL/Redis/Java 数据目录仅使用 tmpfs，TLS/SQL 客户端配置来自临时目录中的合成文件；运行前核对合并后的 Compose 只包含四项服务、挂载来源和 localhost 端口。执行完毕仅 down 自身项目，未使用 --volumes。该脚本分为无容器的 --preflight-only 和完整 E2E 两个模式。

### 本轮已完成、可复核的真实测试

本轮增量日志均按 700/600 权限保存在 Mac 本地私有目录 /Users/saber/my-project/rehab/validation-evidence/2026-10-09/。同时补齐了上轮已验证但当时滞留 /tmp 的 bootstrap、runtime、迁移日志。SHA-256 如下：

| 检查 | 本次真实结果 | 日志与 SHA-256 |
| --- | --- | --- |
| 旧 Bootstrap 构建/恢复证据归档 | 成功归档，历史内容没有冒充本轮重新生成 | rehab-bootstrap-verified-20261009.log；65380b212940b6f91fae1e780c1624c75c8a280d3c8d1b8e0bfd334fc662ecdc |
| Runtime 840 项哈希完整性检查 | **本轮重新检查通过** | rehab-runtime-integrity-20261009.log；c3352a045c3292fe673108568e180fb3e04d10bb23f6ab49a2a7e18ded294c47（本条日志为前轮快照；本轮重检直接终端回显成功） |
| 隔离桌面 E2E Compose 预检 | 通过，没有启动容器，没有增加卷 | rehab-ephemeral-preflight-20261009.log；4cb1da7e15de61f96585214a1b0ca7179f8784c905115cce13f61866f7b6e6c2 |
| **隔离四服务桌面 Runtime E2E** | **通过**：MySQL/Redis/Java/Nginx 全部 healthy；TLS/前端/反向代理/租户正常；患者 login、bind 明确 HTTP 403；患者 0、基线账本 19；临时容器无残留，卷总数仍为 24 | rehab-desktop-ephemeral-e2e-20261009.log；9b17daf6ec2508bb56e6509cb001159766bb98e7f997451a0998843e7b23a54c |
| **实际 001–023 隔离迁移复测** | **通过**：脱敏空库基线 0/19、020–023 全部应用、账本 23 条、再次 apply 不重复、升级后备份恢复 0/23、无容器残留 | rehab-migration-e2e-retest-20261009.log；bb3e6361bd4d5b5942b2b363da47cb4dc3dd1b31b070c010c9890820ff41aad9 |
| 桌面 Node.js 全量脚本 | **15/15 通过** | rehab-desktop-e2e-node-regression-20261009.log；1096efa7efd1ded7dc44d890f6ca338354f758835cd3bf0a55f843f1a3a955aa |
| 部署 Python 离线单测 | **32 项运行，29 通过、3 跳过**；需 MySQL 的实际集成已由上面独立 tmpfs E2E 覆盖 | rehab-deploy-python-regression-20261009.log；f809bf63c85689e3075abc3c2fa3b7ecf047c5662bf4da05035ce8891f546959 |
| 桌面启动器 Vitest | **5/5 通过** | rehab-launcher-vitest-20261009.log；9bbf0b39c32f7a2921fe168cd9c78ed7566e5153b4fdba44b4bae985725fced4 |
| 桌面启动器 TypeScript + Vite 生产构建 | **通过**，只构建启动器前端，未生成安装包 | rehab-launcher-ui-build-20261009.log；6fa3d42b8a6490b14c33e0dea398ac214a7810b028633bccfdbaa22f001355f5 |
| Tauri/Rust 原生测试，Cargo locked/offline | **25/25 通过**，无失败、忽略 | rehab-launcher-rust-tests-20261009.log；6af39172c5c2dd701731a4ead2f288cb8922e45deb190e29bdd6bff45462dd0c |
| 正式发布门禁 | **NO-GO，14 阻断，退出 1**，不得用阶段性测试伪造冻结提交签署 | rehab-release-after-ephemeral-e2e-20261009.json；f9376160773c1b42d4c283856cd7a68eb124648941334be47c20ef324ed8dca9 |

**关联制品：** 脱敏 Bootstrap SQL SHA-256 999d35adad167c934632fe43d1573a31af34ef81cead6df7e65451e136ef34ad；本地 Runtime 临时目录 /tmp/rehab-prepackage-runtime-20261009/1.0.0，840 行哈希清单 SHA-256 ff13c2560b52fa6dba0102ee5bbcc01bcab742ada26976284190e24711405b52；隔离 JAR SHA-256 72ac0c6b51145646a1ca0cabb2f046280856c81687b21a99dc5342f4625f62d3。Runtime VERSION.json 仍标记 1.0.0/HEAD 3d778538a3，但源码**尚未冻结**。

**仍未通过：** 当前清单只到 023，024/025 尚未登记；PR #5 尚未获正式审查合并；完整 Java → Python Engine ZIP API/OpenCap 集成、双版本 B 版报告生成及专业临床审核、真实租户/JWT/并发签署、安全许可、Windows/macOS 安装升级回滚与代码签名、公证都未完成。D03 的基础健康 E2E 通过不能等价于其规定的完整业务 E2E。Go/No-Go 仍为 **NO-GO**。

本阶段未执行 Git commit/push/merge，未修改任何真实业务数据库、患者文件、运行卷，也未生成正式安装包。后续需先完成 B 版和动作评估的业务 E2E、源码审查与冻结提交，才能将真实验收证据绑定到同一发行候选。

### 后续完整 Java 与 Web 安全回归（同一未冻结工作树）

在新增桌面 E2E 安全脚本及上述合成验证后，使用本机 JDK 17 按 AGENTS.md 再次执行：

- 主系统 Maven rehab 模块（-pl yudao-module-rehab -am test）：**128/128 通过，0 失败/错误/跳过**。日志 rehab-main-after-desktop-e2e-20261009.log，SHA-256：3cbfa39e2d870db72ff5f45fea24ad80bc7c414b9f565f5d7faebfbabcf2eb2a。
- Web 安全定向测试（ApiAccessLogInterceptorTest、GlobalExceptionHandlerTest）：**2/2 通过**。日志 rehab-web-security-after-e2e-20261009.log，SHA-256：8b3ceeaa77fe7e20439bba34b00eeda07f7a859a269457bbe4e5a053b0ab364f。
- 无持久卷 E2E 新增脚本源码 SHA-256：49e90277718f3ec73bf2dd27c0229b46d95788dcdf2c614105495c3e3f582000，已设置可执行权限 755；Git diff --check 通过。
- 发布检查最终仍返回 **ready=false、14 阻断、退出码 1**，日志 rehab-readiness-final-20261009.json 的 SHA-256 为 f9376160773c1b42d4c283856cd7a68eb124648941334be47c20ef324ed8dca9。源码处于 master 3d778538a3 的未提交状态，不能形成正式 Go 结论。Docker volume 数复查为 24，合成临时容器无残留。

以上新增日志均在 /Users/saber/my-project/rehab/validation-evidence/2026-10-09/，目录 700、文件 600。它们是当前工作树的开发阶段验证，不代表已通过 Windows/macOS 安装与回滚验收、临床 B 版审核签署或源码冻结。

## 2026-10-09 后续 P0/P1：静态资源权限修复、前端生产依赖安全整改、最新 Runtime 验收

**状态：阶段性工程构建成功；发布结论仍为 NO-GO / 14 项阻断。**

### P0：已修复的真实运行资源权限缺陷

- 新 Vue 内部构建在严格 `umask 077` 下产生 `dist-internal/index.html` 模式 0600、部分 `assets/` 目录模式 0700。旧版 `desktop/scripts/runtime-tools.mjs` 直接把文件模式复制入 Nginx 镜像，即使四个容器全部 healthy，HTTPS 首页仍返回 HTTP 403。先后两次失败均已保留日志，未伪称成功。
- `runtime-tools.mjs` 现在仅把**运行资源暂存副本**中的公开 `admin/web` 静态内容规范化为文件 0644、目录 0755，拒绝符号链接/特殊文件；`validateRuntime` 在 POSIX 系统核验读取权限。不会改变原始 `dist-internal` 文件、应用鉴权、数据库或其它秘密目录。
- `runtime-tools.test.mjs` 增加合成 `0600/0700` 负例、仅更改副本、篡改模式被完整性门禁拦截的验证。`test-runtime-e2e-ephemeral.sh` 仅对环回探针使用 `curl --noproxy '*'`，避免本机 HTTPS 请求误经外部代理。**桌面 Node 测试 15/15 通过**。
- 权限修复后，合成 Compose 实测 MySQL/Redis/Java/Nginx 全部健康、TLS、首页、租户 API 可用、患者 login/bind 均为明确 HTTP 403、患者数 0、迁移基线 19 条；退出后无测试容器残留，系统原有 Docker 卷总数仍为 24。

### P0：生产依赖审计整改（有真实复测）

当前 frontend `yudao-ui/yudao-ui-admin-vue3-app/package.json` 及其 `pnpm-lock.yaml` 在**同一未提交工作树**中完成了定向升级，且 `pnpm install --frozen-lockfile` 通过。起初 `pnpm audit:prod` 报告 18 项已知漏洞（9 high / 6 moderate / 3 low）；第一轮 Axios/Vue/DOMPurify/KaTeX/source-map-js 修复后剩 1 项 `sprintf-js` 中危，升级四个配套 Markmap 包后剩 `yaml` 中危，再锁定 `yaml@2.8.3` 后最终审核结果 **No known vulnerabilities found，退出 0**。这仅是对应审计时点和锁文件的生产依赖漏洞核对，不代表应用整体安全验收。

| 组件/控制 | 本阶段状态 |
| --- | --- |
| Axios | 1.18.1 → **1.20.0** |
| Vue / server renderer | 3.5.12 → **3.5.42** |
| DOMPurify / KaTeX / source-map-js | overrides **3.4.16 / 0.18.2 / 1.2.2** |
| Markmap (common/lib/toolbar/view) | **0.18.9 / 0.18.12 / 0.18.12 / 0.18.12** |
| YAML | overrides **2.8.3** |
| 前端锁文件与构建 | `--frozen-lockfile` 通过；Vue 全量 `ts:check` 通过；最新 `build:internal` 成功 **828 个文件** |
| 思维导图升级冒烟 | 使用合成文本验证 ESM `Transformer.transform`、`Markmap.create` 和 `Toolbar.create` 均可使用（浏览器模块入口）。Node 默认 CJS 路径的 Toolbar 导出与浏览器 ESM 不同；未为了绕过此差异删去功能 |
| 尚需兼容性核查 | `echarts-wordcloud 2.1.0` 的 peer 限定 ECharts ^5，项目当前 ECharts 6.1.0；安装告警未被篡改消除。CryptoJS、vue-i18n、Browserslist、Vite CJS 构建弃用提示同样需要后续维护 |

### 最新可重现的工程制品

- **JDK17 Spring Boot 隔离 JAR**：`yudao-server/target/yudao-server.jar`，SHA-256 `7bfbefa372e9f158583dcb8517f282b600793909cc454ecc7ac8d3b9b181f4bf`。本阶段 Java Rehab **129/129 通过**，Web 安全 **2/2**；Java Motion 分支 **174/174**，Biomechanics Engine 临床测试 **363 通过 / 1 跳过**，Reporter 合成/禁外部 AI 测试 **42/42**。
- **最终 Vue 回执**：`desktop/build/frontend-build-receipt.json`，源码哈希 `3fc33a48f5953cb2eb0f1d5d1dd7613b5134b164e4e892906cf07a6ced4d2d82`、输出哈希 `2716897abfc11877c5ebaa6f4b5cdf3d4220ec4178c0ff086cab5bd9da9efa73`、828 个文件；源码仍在 master `3d778538a3` 未冻结。
- **新 Runtime（非正式安装包）**：`/tmp/rehab-prepackage-runtime-20261009-security-fixed/1.0.0`；完整性清单 SHA-256 `9fa37724adf2fae2804c88e9cc976ef33689180663b9e8158792e05a16d6d6e3`。独立重新调用 `check-runtime.mjs` 通过，HTML 0644、assets 目录 0755。
- **最终新 Runtime 合成 E2E**：上述四服务和权限检查全部通过；独立随机 Compose 项目、随机 localhost 端口、MySQL/Redis/Java 临时 tmpfs，无持久 Docker volumes、无业务数据库调用、无测试容器残留。
- **Java/Engine 静态协议**：19 个动作测试代码/所属体系/允许侧别一致；新增 3 个 FMS 分级项目的条件顺序及 3→2 评分顺序校验；`deploy/internal` 35 项 Python 测试 **32 通过、3 跳过**（没有隔离 MySQL 的那次运行），实际迁移及合成恢复测试已在上文独立记录。静态比较**不代表** Java→Engine ZIP API E2E。

### 原始测试日志及哈希（仅 Mac 私有目录）

所有新日志原封不动保存到 `/Users/saber/my-project/rehab/validation-evidence/2026-10-09/`（目录 0700、文件 0600），没有上传 GitHub；早先失败及中间版本审计日志也被保留。

| 日志 | SHA-256 |
| --- | --- |
| `rehab-p0p1-frontend-dependency-audit-20261009.log`（初次 18 项） | `19a8b2c20013ba87eddfc52e7fa0d13b392e0174da5fa3de7f6702e60fa96c8d` |
| `rehab-p0p1-frontend-audit-final-20261009.log`（最终 0 项） | `4ee3a9fbcae65fb2016d10a2e67d3cbd95b73720c98e5f374b01a652d2cbcde3` |
| `rehab-p0p1-frontend-ts-finaldeps-20261009.log` | `3713a0519dd6396d51b21a22dd73da3230d4092228ebd738857df6463ecea1e7` |
| `rehab-p0p1-frontend-build-finaldeps-20261009.log` | `a69a31b8a2bd4ed1bac6d70d0a966ec16f6f7cb7a305d751c5e40409a7798b06` |
| `rehab-p0p1-markmap-esm-final-20261009.log` | `b3fd5d81d744012081268d38355e8cb21cf44537cf0855f7c4ce0c63c582ddf7` |
| `rehab-p0p1-security-runtime-build-20261009.log` | `62f8f07362cc68551b879a6bfe206bea5d0d41b6530c764bb829a832ca4fdebd` |
| `rehab-p0p1-security-patched-runtime-e2e-20261009.log` | `89a183aa98bc8f950c671008a2e5639abb9ca305d7ce8d750600443821989953` |
| `rehab-p0p1-security-final-desktop-node-20261009.log` | `266c52be7ebe00d741316c80dba821f9d8d13488e3a2c1a1a00ffa066bc21ccb` |
| `rehab-p0p1-security-final-readiness-20261009.json` | `f9376160773c1b42d4c283856cd7a68eb124648941334be47c20ef324ed8dca9` |

**仍需继续：** 024–025 增量迁移尚未登记；PR #5 本地未提交权限修复与远端 139 文件合并仍待审计与授权；患者/监护人身份门禁、真正 Java→Engine/OpenCap 受控接口 E2E、青少年治疗师/家长 B 版完整 DOCX/PDF 生成、来源冻结并发签署、临床人工验证、安装升级回滚、跨平台证书及正式冻结源码全部不具备独立验收证据。因此发布门禁工具本次仍严格返回 `ready=false`、**14 项阻断**、退出 1。**没有 commit、push、merge、正式安装包或对真实患者数据/数据卷做破坏性操作。**

### 2026-10-09 18:45 后续 P0：旧数据库迁移 adopt 结构核验加固

**阶段性结果：动作评估工作树代码已修改、回归通过，但 PR #5 尚未合并，正式门禁继续 NO-GO。**

* 已在 `rehab/worktrees/motion-assessment/deploy/internal/` 新增 `adopt_schema_guard.py`，并修改 `migrate.sh` 与 `test_migration_guard.py`。`adopt` 仅可核验纯 CREATE TABLE 的 020–023；新校验不信任列名相同的旧表，而是只读取 `information_schema` 核对列定义、索引、唯一性、InnoDB 引擎及表排序规则，任一不一致均拒绝写入账本；024（含菜单授权 DML）和 025（ALTER）不得 adopt。所有已存在未提交修改均保留。
* 新增负例覆盖字段类型、默认值、可空性、索引缺失/唯一性、引擎和排序规则。`AdoptLegacyInitdbTests` **8/8 通过**。隔离、无网络、无持久卷的 MySQL 8.4.10 中 `MigrationGuardMySQLTests` **4/4 通过**，其中 020 被故意改为错误 VARCHAR(90) 时拒绝登记；恢复正确类型后才实际登记。其他 3 项分别验证旧数据保留与缺失账本保护。
* 首次 MySQL 测试尝试将 mysqladmin ping 错当作密码认证就绪，初始化尚未完成即触发 1045；该失败日志保留。第二次改为随机 root 密码执行 `SELECT 1` 成功后才启动测试，并全部通过。测试容器结束后另经检查停止遗留的**本次专用临时容器**。当前没有 `rehab-adopt-synthetic-*` 容器、原有 Docker volume 数仍为 **24**；从未挂载或删除任何真实业务卷。
* 对当前最终代码重复执行部署目录离线回归：**61 项运行，57 通过、4 跳过，0 失败**；跳过的是 MySQL 显式启用项，已在上段独立测试。Shell 语法、Python 编译、Git diff --check 均通过。尚未对 024–025 执行完整隔离升级、合成备份恢复或安装升级验收，因此不能解锁迁移发行门禁。

**私有证据目录**：`/Users/saber/my-project/rehab/validation-evidence/2026-10-09/`（目录 0700 / 文件 0600），未上传患者数据或测试日志至 Git。

| 新证据 | SHA-256 |
| --- | --- |
| `rehab-adopt-strict-negative-20261009.log` | `edbd423e39a22c5e66d2de3d8f1ae502a0a4f753904ba52d184e89f71112ed9d` |
| `rehab-adopt-strict-real-mysql-retest-20261009.log` | `6a3f12be0b805d2149a0a86824ef4953f10ae9d4a78779a2f0ddd875ba78de65` |
| `rehab-adopt-final-suite-20261009.log` | `71dec4fb6deabd64e1d893c07ee068bb7376c2be9c1bb516f1cd852b012c5292` |
| `rehab-adopt-after-readiness-20261009.json` | `f9376160773c1b42d4c283856cd7a68eb124648941334be47c20ef324ed8dca9` |

最新代码 SHA-256：`adopt_schema_guard.py` = `761430e320fbd1aac9437f964042f5ecfa77db8543d38f6660d98b3209159214`；`migrate.sh` = `501cc4459230170eed497eef9c2008acb64558b6c2f8b0418eb6c45162caff61`；`test_migration_guard.py` = `2623ddd2016d43fc8528980ec5a2b02b0ce847d2653b3adb8d0d45ff7a812628`。

2026-10-09 再运行 `node desktop/scripts/prepackage-readiness.mjs`，仍返回 `ready=false`、**14 项阻断**、退出码 1。正式安装包、Git 提交、远端推送、PR 合并、真实患者数据库更改都未执行。

### 2026-10-09 19:14–19:38 后续 P0：024–025 完整隔离升级、PR #5 工程构建及 Runtime E2E

**已完成并验证的工程工作，仍非正式发布验收。所有检查均针对当前未提交工作树，并保留原始日志。**

1. **迁移 024–025 独立升级通过：** 使用新的随机 Compose 项目和 MySQL 8.4.10，禁用网络，无业务卷，以 tmpfs 保存全部数据库数据。脱敏新装快照从 001–019 账本和 0 患者开始；先正常应用 020–023，再故意制造冲突的 `rehab_motion_assessment` 表，确认 024 失败而账本仍为 23；只从该次合成数据库删除自己生成的冲突表后，正常 `apply` 024 与 025。最终核对 **11 张动作评估表、22 个可空审计字段、9 个动作菜单、25 条账本、0 患者**，重复 `apply` 不执行 DDL，合成备份恢复保持 0/25/11。**没有把 024/025 伪登记为 adopt**，没有改动真实业务库。专用测试结束后无容器残留，原有 Docker volumes 仍为 24。
2. **升级测试可重现：** 在主仓库现有未跟踪的 `deploy/internal/test_migration_upgrade_synthetic.sh`（仍未提交）增加 `REHAB_MIGRATION_SOURCE_ROOT` 入口，并按固定清单动态选择 023 或 025。默认指向主线时，020–023 的升级与恢复重新通过；指向 `rehab/worktrees/motion-assessment` 时，020–025、024 合成碰撞阻断、恢复、幂等和账本校验全部通过。该测试脚本仅修改专用临时目录，不读取已有业务卷或运行数据。
3. **PR #5 安全整合准备：** 主线与动作评估分支的 `RehabDataPermissionService.java` 与对应单元测试的脏文件内容及补丁哈希完全一致，不应重复开发。PR 已提交的桌面 Runtime 增量迁移打包代码已与主线修复逐项核对，并把主线经验证的 Nginx 静态资源权限控制（暂存副本文件 0644、目录 0755，拒绝前端符号链接及模式篡改）移植到动作评估工作树 `runtime-tools.mjs` 和对应单元测试；**14/14 Node 测试通过**。主线桌面 **15/15** 也重新通过。未执行任何 merge/rebase/commit/push。
4. **独立回归：** 动作评估 Java 模块 **174/174 通过**；JDK17 隔离 JAR 构建成功；Engine 临床 **363 通过、1 跳过**（只跳过本地真实样本，未上传任何样本）；Java→Engine 静态注册协议 **19 项匹配、3 项 FMS 有序条件匹配**（不等于 ZIP API E2E）；Tauri Rust **41/41 通过**；动作评估前端专项 **10/10 通过**（按测试文件说明使用 Node 的 --experimental-strip-types）。
5. **动作评估前端依赖整改及正式资源生成：** feature 分支独立更新锁定的 Axios、Vue、Markmap、DOMPurify、KaTeX、source-map-js、yaml 等依赖，`pnpm install --frozen-lockfile` 和 `pnpm audit:prod` 通过，审计 **0 项当前已知生产依赖漏洞**；完整 Vue TypeScript 通过、内部 Vite 构建成功 **875 个输出文件**。从主线复制的仅是带 001–019 相同固定迁移账本的**脱敏 bootstrap**生成件，SHA-256 完全一致且保存在 feature 被 Git 忽略的桌面构建目录。
6. **最新动作评估 Runtime（非正式安装包）：** `/tmp/rehab-p0-motion-runtime-20261009/1.0.0`，含 020.sql–025.sql、已通过回归的 Java JAR 和新前端；构建与 `check-runtime.mjs` 通过。清单 SHA-256：`96dc15f0e77b64caae471115dcee839017048156da446d51ecf4f78b0943396a`。Java JAR SHA-256：`a99b7e62e48841cc4f1efc6f70e526f5b1aed61e6e6b032014430f7548baca48`。前端构建回执：源码 SHA-256 `646ab3a3b10fd39d7fc9c2173b22518ba6c80a7318e24b8b5fedb0efbe51308f`、输出 SHA-256 `c5b4865ca5317563266aa6e91ef1932f13295e15216ea116917c21f43afbd002`，875 个文件。
7. **最新完整四服务 E2E 成功：** 使用上述刚构建的 Runtime 和随机无持久业务卷 Compose，MySQL/Redis/Java/Nginx 全部健康，HTTPS/首页/代理/租户路由通过，患者 login/bind 均明确 HTTP403；脱敏新装仍是 **0 患者、19 条基线账本**（新装初态，不能与增量升级测试的 25 条混淆）。测试退出码 0，Compose 容器已全部清理，Docker volume 总数仍为 24。

**本阶段原始日志**仅保存在 Mac 本地 `/Users/saber/my-project/rehab/validation-evidence/2026-10-09/`，目录 0700 / 文件 0600，没有上传 GitHub。早期因遗漏 Node TypeScript 开关或 cargo PATH 的失败日志也保留，最终按仓库规定修正了执行环境并通过，没有更改测试代码以掩盖结果。

| 关键验收日志 | SHA-256 |
| --- | --- |
| `rehab-p0-motion-024025-reproducible-20261009.log` | `0dc6db2a8f9af3449b9a6d10800975e65064f902131e36d8877fc554145511a4` |
| `rehab-p0-main-020023-reproducible-20261009.log` | `0aac83a814d07bac542203e87c4d2c8257ee14e82cc827f7d1e39870efcde750` |
| `rehab-p0-motion-java-current-20261009.log` | `9af826b499627f27a64f25640db46337f3d2c9bd6e8b7179053a17400162822e` |
| `rehab-p0-engine-clinical-full-20261009.log` | `0a4a80b15d0aab76ec65e07e7b8f6900f962afd52d1efad41a439fca0a4f3b61` |
| `rehab-p0-feature-deps-audit-20261009.log` | `bf0d82652c097d0c00aa70e6a1156e1f53431ab3ea5ad7f375d52b5e938806b2` |
| `rehab-p0-motion-vue-build-security-20261009.log` | `a1cc0a993e782a38d46d9cf4bc2a445d34af683d89149f5ecb804ffd8e810a2c` |
| `rehab-p0-motion-runtime-e2e-20261009.log` | `b85a3d6577166343e4207ccac9bf8aac96e27a619f066889397f7100fb20cb4f` |

**仍未解决的发行门禁：** 真实主线与 PR #5 的整合/源码冻结（尚有未经许可的主线合并和两棵工作树未提交改动）、真实租户/患者权限集成及患者身份门禁、Java→Python Engine/OpenCap 实际安全 API E2E、完整临床 B 版治疗师/家长报告及签署、跨平台真实安装升级回滚与代码签名、商业许可证/安全审计等。基础四服务 E2E 和合成迁移结果不能替代这些门禁。所有实际测试通过也不得绕过 `NO-GO`。

### 2026-10-09 20:33 续接 P0：Runtime 打包 SQL 升级与匿名访问门禁

**验收结果：新增隔离 E2E 最终通过，正式发布仍为 NO-GO（14 项阻断）。**

- 修改主线的 desktop/scripts/test-runtime-e2e-ephemeral.sh：新增 --upgrade-smoke（仍不触及任何已有 Docker 项目/volume），使用当前动作评估 Runtime **实际打包的 020–025 SQL**，逐项按迁移清单校验 SHA-256、在独立 tmpfs MySQL 中执行并登记；六项成功后账本为 25 条、患者 0、动作评估表 11 张、审计字段 22 个。它与先前的源码迁移测试不同，也**不等于 Tauri 启动器升级/加密备份/回滚端到端验收**。
- 新增合成第二租户的治疗师/患者角色后，验证两个租户的治疗师各有 9 项正确租户范围内的动作菜单权限，两个患者角色均无动作菜单授权。升级后 HTTPS、租户路由以及患者 login/bind HTTP403 仍有效；跨租户菜单验证不替代患者行级数据库权限测试。
- 对实际 Java 动作评估接口 page、get、sign 增加匿名访问负例。第一次失败发现它们的 HTTP 响应为 **200**，因为现有 Yudao AuthenticationEntryPointImpl 使用 ServletUtils.writeJSON 输出业务错误码 401，未设置 HTTP 状态。严格保留 HTTP404 禁止条件，最终同时断言 HTTP200/401/403 之一、**JSON code 为 401/403 且 data 为 null**；三个接口最终均拒绝并无数据返回。HTTP200 业务拒绝响应需后续 API 规范审查，**不能宣称 HTTP 状态语义已达到标准**。
- 升级夹具初版还因 SQL 命令消费清单 stdin，仅执行 020；已将只读 SQL 查询 stdin 设为 /dev/null 并重跑，最终六项全部通过。所有初次失败及复测日志原封不动保存，不隐瞒或覆盖。
- 最终 --upgrade-smoke 退出 0；MySQL/Redis/Java/Nginx 均健康；专用 Compose 容器全部清理、原有 24 个 Docker volumes 不变。桌面 Node 测试 15/15，Shell 语法、Git diff --check、Runtime 完整性复核均通过。主线发布门禁仍返回 ready=false、blockerCount=14、退出 1。

**私有验证日志：** /Users/saber/my-project/rehab/validation-evidence/2026-10-09/（目录 0700、文件 0600、未上传 Git）。

| 关键文件 | SHA-256 |
| --- | --- |
| rehab-p0-runtime-upgrade-cross-tenant-20261009.log（首次匿名 HTTP 状态断言失败） | 0ff6181990174fc2e6db75669d9c56a5ecab4e72f8cdbe6416102ab881b64e52 |
| rehab-p0-runtime-upgrade-auth-retest-20261009.log（最终通过） | 8e2cbf35525093eb3acb07be073146c3b61d2037dec6ae89b8740a7c511e0f7a |
| rehab-p0-resumed-desktop-tests-20261009.log（15/15） | 0641f971cc7898715e5463da837821eff9190fd3bf6f97a71f104da4a28dda84 |
| rehab-p0-resumed-runtime-check-20261009.log | ccfc81e4945f78b26f570dc4909fe0a3e2a2598aa978b3e9608ee865ffa12113 |
| rehab-p0-resumed-readiness-20261009.json（NO-GO） | f9376160773c1b42d4c283856cd7a68eb124648941334be47c20ef324ed8dca9 |

**新增 E2E 脚本的 SHA-256：** 60417768b903b6c48d2cae280a1ec5d0f49faef5b3a87663e27c5e93fc45dbe5。尚未完成：PR #5 正式整合与源码冻结、真实跨租户/患者行级 JWT 权限、Java→Engine/OpenCap 实际 API、临床 B 版双版本签署、Tauri 加密备份与安装升级回滚。

### PR #5 本地无副作用合并风险核对（下一批 P0）

在 `master=3d778538a3` 与 `feature/motion-assessment=2f7f515973` 之间，`git merge-base` 为 `3d778538a3`。只读 `git merge-tree <base> master feature/motion-assessment` 输出**未检测到冲突标记**；这只能说明已提交的两棵树在该模拟下没有文本冲突，**不包含未提交工作，不能认定真实集成安全或已合并**。

- Java 主线未提交改动与动作评估工作树未提交改动重叠 **2 个权限文件**：`yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/RehabDataPermissionService.java`、对应 `RehabDataPermissionServiceTest.java`。
- PR #5 的已提交改动与主线未提交改动另重叠 **3 个桌面文件**：`desktop/scripts/build-sanitized-bootstrap.mjs`、`desktop/scripts/runtime-tools.mjs`、`desktop/scripts/runtime-tools.test.mjs`。尤其后两者包含此次 Nginx 静态资源可读性门禁，整合时不得被旧补丁覆盖。
- 已提交 PR 改动范围 139 个文件；审核合并前须按差异逐项整合、进行权限负例、数据库迁移、Java/Engine 合成接口、Node/Vue 和桌面隔离 E2E 回归。未经明确授权不得操作远端 PR 或破坏任何现有未提交修改。

### 2026-10-09 21:28 P0/P1 续建：前端工作台、安全净化与最新 Runtime

**阶段结论：两棵工作树的前端均构建成功，最新动作评估 Runtime 的隔离 OAuth2 E2E 通过；正式发布仍 NO-GO、14 项阻断。本段不是临床 B 版审批或安装包签名验收。**

1. **动作评估 Vue UI（未合并的 feature/motion-assessment）：** 在 `src/views/rehab/motion/index.vue` 优化标题与操作入口、筛选、当前页待上传/审核/已完成/需关注概览、空状态与错误重试、签署后编辑/删除按钮禁用及窄屏布局；在 `detail/index.vue` 补充失败/取消/旧版本/未签署的状态提示、审核快捷入口与响应式步骤导航、错误和无效评估编号反馈。统计明确限定**已加载当前页**，不会把局部分页数据当作全租户或全院总体；不改临床评分或签署规则。
2. 新增纯函数 `motionMetrics.ts` 与 3 项合成指标测试；动作评估前端专项测试 **13/13**、Vue 完整 `ts:check`、内部 Vite 生产构建均通过。最新前端构建回执：source SHA-256 `4d305a9414135641fe4332b54daaad25c083a09960ea0affb51a92ce69e901b5`，output SHA-256 `c91243a46bb55ee08380eeff45657b397f9a800e718cba0599c2d0170e2a6d0f`，**876 文件**。
3. **主线报告中心：** 在原本已存在的报告状态、报告审批和下载逻辑外增加工作台标题、当前页患者/评估/报告指标、空状态和响应式报告对话框；针对原 `v-html` 直接显示后端报告 HTML 的风险，明确添加 DOMPurify **3.4.16** 直接生产依赖与锁文件，预览前进行 HTML 净化并禁用危险标签及内联样式属性。离线锁文件更新、冻结依赖安装、完整 Vue TypeScript、内部 Vite 生产构建均通过。报告中心构建回执 source SHA-256 `2bb99ec679b58e521d59b0a3d27358a4f17879013df8218dd2a2a5723e3af571`，output SHA-256 `a036c76b365511b24cb741e998a6704fa3769fa89dced241b61c7557ebfdbfe9`，**828 文件**。官方 npm 生产依赖审计：**0 项当前已知漏洞**；这不代表许可证与全部安全审计门禁通过。UI 仍应安排真实浏览器和辅助技术人工验收。
4. **新动作评估 Runtime：** `/tmp/rehab-p1-ui-runtime-20261009/1.0.0` 生成并通过 `check-runtime.mjs`，保留 020–025 六项增量 SQL。完整性清单 SHA-256 `0e6f2873073a6ad5e5f82f57a2b59256db5b6c1fe792a24658a643221ce92f5f`。针对这批**最新 UI 制品**再次运行真实 MySQL/Redis/Java/Nginx 的独立合成四服务 E2E：TLS、页面与反向代理正常，020–025 全数通过 checksum 与执行，合成数据库先从 0 患者/19 条初始化账本升级至 25 条，再仅在该一次性测试库造三个虚构患者及四个身份，验证两个租户真实 OAuth2 登录后的管理员、治疗师患者列表与详情隔离、受限角色和租户冒用拒绝。没有任何真实患者数据或业务库读写；测试退出码 **0**，临时容器全部清理，原 Docker volumes 仍为 **24**。
5. **非正式工程候选已持久化：** `/Users/saber/my-project/rehab/validation-evidence/2026-10-09/rehab-p1-motion-runtime-engineering-candidate-20261009.tar.gz`（约 145 MB，600 权限），压缩包 SHA-256 `08721cd30222b305634b7989b30d0dbfea969dc18c1426e56cb480f53f53f968`。此前独立编译但未签名、未打包的 arm64 启动器也只在该私有目录存有工程副本，SHA-256 `720f8dc65335f21dfbe9c28341b6aef2cc45b73938b59fbe0fb3b2584ca35d74`；二者都不能称为正式交付安装包。

**本次私有证据目录** `/Users/saber/my-project/rehab/validation-evidence/2026-10-09/`（目录 0700，日志和归档 0600，未上传）：

| 关键记录 | SHA-256 |
| --- | --- |
| `rehab-ui-motion-unit-20261009.log`（13/13） | `e8b52b181d106070ea5f74236340b154ef29fa524cbce9e906f73cb9934b09a0` |
| `rehab-p1-motion-ui-build-20261009.log` | `ba1bd65465c07710709f9b7e8dfb2441a09e8a213b72c4c70036b78c167bf6e7` |
| `rehab-p1-report-ui-build-20261009.log` | `a69a31b8a2bd4ed1bac6d70d0a966ec16f6f7cb7a305d751c5e40409a7798b06` |
| `rehab-p1-report-ui-audit-20261009.log` | `15950a68a7ed99c59779717acefdceb3f69cfd31bde67d2c954f2a3cea4d7955` |
| `rehab-p1-ui-oauth2-runtime-e2e-20261009.log`（最新 Runtime 全链隔离通过） | `8df6c2a5dc33e30489779199e85c4e50fa7ad0d488a32ab025a6596622405de1` |
| `rehab-p1-ui-release-readiness-20261009.json`（NO-GO、14 项阻断） | `f9376160773c1b42d4c283856cd7a68eb124648941334be47c20ef324ed8dca9` |

**边界仍在：** 主线与 PR #5 均有原始未提交改动，未 merge/commit/push 或冻结源码；真实 Java→Python Engine/OpenCap 业务链、临床 B 版家长/治疗师双版本人工审批及签署、完整患者身份接入、Tauri 启动器实际加密备份→升级→失败回滚、跨系统安装测试、商业许可证与正式代码签名均未形成发布门禁可采信的完整证据。`prepackage-readiness.mjs` 重跑结果保持 `ready=false`、`blockerCount=14`，**不得将本轮合成工程通过误记为正式 GO**。

### 2026-10-09 22:15 实机 Chrome 内测准备与 14 门禁核对

**执行结果：合成工程质量进一步验证通过；正式内测安装包发布依旧 NO-GO，14 项发布门禁并未得到授权关闭。**

- 在主线 `desktop/scripts/browser-visual-smoke.py` 增加真实 Mac Google Chrome 154 的 CDP 浏览器验收（仅随机回环 HTTPS、一次性隔离用户配置/合成 OAuth2 账户，无真实患者资料）；`test-runtime-e2e-ephemeral.sh` 增加 `--upgrade-browser-smoke`，将 020–025 的 SHA 验证迁移、两租户权限和浏览器截图放入同一次隔离四服务 E2E。
- 截图首次检出 768px 导航面包屑文字竖排、390px 动作详情六阶段流程不知可横向滚动、首次登录截图不显示表单。已在 feature/motion-assessment 修改 `src/components/ConfigGlobal/src/ConfigGlobal.vue`（小于 960px 折叠菜单）和 `src/views/rehab/motion/detail/index.vue`（滑动/键盘提示），将浏览器脚本改为等待实际可见表单与动画后再拍摄，并增加移动端“查看审核”真实点击。第二轮测试夹具因误把正常 50px 导航栏高度当换行而失败，此误报的日志保留；改为检测狭窄面包屑文字后第三轮成功。
- 第三轮真实 Chrome E2E 退出码 **0**：匿名登录桌面/手机、动作列表与详情的 1440×900、768×1024、390×844 六视口、移动端审核标签实际点击全部通过；各页根视口横向溢出 0px。主线报告中心最新版、计划/复评和 Safari/Windows 不在本轮 Chrome 已验收范围，**不等于完整真人工视觉签收**。同一批最新 Runtime 通过六项 020–025 增量迁移、真实 OAuth2 跨租户/患者可见性、HTTPS、患者 login/bind HTTP403 门禁；原 24 个 Docker volumes 未改、无临时测试容器残留。
- 本次修复后的动作前端 `pnpm build:internal` 通过（876 前端文件），完整 Vue TypeScript **退出 0**、动作专项测试 **13/13**、主线桌面 Node **15/15**、bash/python 语法与 Git diff --check 均通过。
- 两套前端生产 npm 许可证清单均已保存（各 10 个 license 分组、未发现名称直接包含 GPL/AGPL/UNKNOWN 的分组）；这仅是初步分类，不代替 Java/Rust/Python/模型和评估方法的合法许可审批。
- Mac 私有证据：`/Users/saber/my-project/rehab/validation-evidence/2026-10-09/browser-visual-20261009-v3/`（9 张 PNG + JSON，人审签名明确 false）；最终成功原始日志 `rehab-real-browser-e2e-v3-20261009.log` SHA-256 `880a0c55c34105bb18526021c46e055a4f821243b48a37eb2e3fdce246d86145`，包含初次成功与第二轮失败的全部原始记录；截图及 JSON 原始哈希清单保留于私有目录。经过真实 Chrome 验证的独立、**不可安装/未签名** Runtime 候选压缩包 `rehab-beta-browser-validated-runtime-20261009.tar.gz` SHA-256 `535cb7d3d6fd0c5be70d7788da7efe2bcb14df8c62765d95bde3ad3e525da6bc`、权限 0600。
- 14 项门禁详细逐项清单、人工视觉/临床签收空白表和内测范围放行条件：`docs/releases/INTERNAL_BETA_READINESS_20261009.md`。未执行 merge、commit、push、签名、公证、现有业务数据库查询或 Docker 数据卷清理。

### 2026-10-09 22:56 报告中心 Chrome 实测、响应式缺陷修复与 Rust 内测预检

**本批完成的工程验收：**

- 主线 `desktop/scripts/test-runtime-e2e-ephemeral.sh` 新增 `--report-browser-smoke`：仅在随机回环端口和全部临时 tmpfs 的四服务合成环境中，挂载一份主线报告中心已构建 UI 的临时副本到 Nginx，保持动作分支既有 Java Runtime 不变。仅合成创建一条患者所关联的疗程/评估/未签署草稿报告；验证 020–025 校验和与账本、真实 OAuth2 双租户治疗师及管理员权限、患者 login/bind HTTP 403，合成 Java 报告列表及预览 API。没有动真实患者/业务库、现有工作树资源或 Docker 数据卷。
- Mac Google Chrome 154 真实浏览器打开报告中心 `1440×900`、`768×1024`、`390×844` 三种视口，登录表单可见、报告列表与版本选择可见，三者根视口均未横向溢出。真实手机截图第一次发现报告弹窗固定宽表格导致内容挤压，筛选的日期范围被截断；已修改主线 `src/views/rehab/report/index.vue`，手机改为患者报告卡片，全宽筛选与日期控件，保留原审批/锁版/解锁/预览/导出/版本/审计的权限控制，单页结果不渲染多余的报告分页条。主线 Vue 完整 TypeScript 检查 **退出 0**、内部生产构建 **退出 0**，828 个文件；第二轮 Chrome E2E **退出 0**，新增移动筛选控件/卡片可见、旧宽表隐藏及内部水平溢出 ≤3px 检查均通过。
- 在合成报告预览 HTML 中专门置入无害测试样本 `script`、`iframe`、`onerror`、`javascript:`，浏览器操作“选择评估报告”→“预览”后证实实际 DOM 中危险标签/事件/链接被 DOMPurify 过滤、测试 JavaScript 未执行。截图只含合成数据，`humanVisualSignoff=false`；这不是治疗师报告审批或真人工签收。第二轮成功日志 `rehab-beta-report-browser-v2-20261009.log` SHA-256 `0a3674adc90a0e67dcb09bddf5b312e8d34f6385ebea44a8f04c303ee263ba96`，截图目录 `/Users/saber/my-project/rehab/validation-evidence/2026-10-09/report-browser-20261009-v2/`。主线独立静态 UI 工程预览归档 `rehab-beta-report-ui-preview-20261009.tar.gz`（非安装包，SHA-256 `72d7d4d7931a747c6811f0e907606bb802287d244439a6e7d7023aa65f923335`）。
- 动作分支 Tauri 新增单测 `unconfirmed_full_import_does_not_touch_docker_or_existing_backups`：覆盖导入确认文字错误时调用数为 0，已有加密备份哨兵不改变；Rust 格式检查通过，完整 Rust **43/43**，日志 `rehab-beta-rust-import-guard-tests-final-20261009.log` SHA-256 `5407b640360973f8a99676426b32eb03ed0c444feb076234f5255fbbdd796012`。但数据库 DDL 迁移失败时并无完整自动回滚；仅停止后续迁移并保留加密备份，需要真实启动器加密备份→恢复链路验证，故桌面恢复门禁仍阻断。
- Rust `Cargo.lock` 使用已有本地 RustSec 公告数据库运行 `cargo audit --no-fetch`：0 项列出漏洞、17 项未维护警告、1 项 `glib 0.18.5` unsound（`RUSTSEC-2024-0429`）；`--deny unsound` **退出 1**，不能称为通过供应链门禁。macOS arm64 目标依赖图查询 `glib` “nothing to print”，只对所查目标和现有离线依赖图有效；Windows 图因本地缺少 `objc2-quartz-core` 元数据失败，另一次完整 metadata 因缺 `android_system_properties` 缓存失败，公告新鲜度和跨平台许可证均需核实。私有 RustSec JSON SHA-256 `72f63a58e879ad50a40c2ff3952b875ca5298b69580a82edd5742a647e72f6eb`。
- 回归：主线桌面 Node **15/15**，Python/脚本语法，双仓 `git diff --check` 均通过。所有临时合成项目已清理，已有 Docker volumes **24** 个维持原状。日志、截图、哈希、RustSec 报告和源码构建回执仅归档到权限 0700 的 Mac 私有目录；未 commit/push/merge/运行正式安装器。正式 `prepackage-readiness.mjs` 重跑结果仍是 **NO-GO、14 项阻断**。具体缺项和人审空白清单参见 `docs/releases/INTERNAL_BETA_READINESS_20261009.md`，机器门禁内容参见 `docs/releases/PRE_PACKAGE_GATES_20261008.md`。

### 2026-10-10 接任增量：Chrome v4、备份校验与公开静态资源权限

- ShunCode Bridge 连接真实 Mac，五个仓库的分支、HEAD 与 dirty 状态重新核对；主线 3d778538a3、动作分支 2f7f515973，主线 30 个、动作 22 个 dirty/untracked 路径，重叠 6 项，不能在此状态整合或冻结。Watchdog 历史记录已读取，自动恢复能力未被当成测试通过。
- 主线 `pnpm ts:check` **exit 0**，`pnpm build:internal` **exit 0**，828 文件、源 SHA `af07c38cb9add4996ce33e7f7096a1db52d831083e0850bc6959e073e11024e9`、输出 SHA `b86c864e0c4cf43185c56366091298133511e697e35f32e846c27d569ff9c6f7`；新日志 `rehab-report-typecheck-20261010.log`、`rehab-report-build-20261010.log`。历史 v3 日志及截图均在原路径保留。
- 第四轮真实 Chrome E2E 用一次性四服务项目和完全合成草稿报告，**exit 0**。三视口报告页横向溢出 0px；通过手机卡片与预览可见性、日期控件、DOMPurify 不安全 HTML 注入负例；OAuth2 两租户权限、匿名和患者 API、020–025 迁移继续通过。截图与结果 JSON 新建在私有目录 `report-browser-20261010-v4/`，人审标志 false；日志 `rehab-beta-report-browser-v4-20261010.log` SHA `1840a7a3dce28d44f82d5bfbf5d20b433c8281720aadbad78d16c60d13809631`。测试容器无残留、原 Docker volume 数 24。
- 真实发现：旧 Runtime 归档在 `umask 077` 的常规 `tar -xzf` 后静态资源被收紧为 0600/0700，直接 `check-runtime` 失败。仅对临时解压副本执行权限修正后完成 Chrome v4；原归档和原始源码未改。新工程 tar `rehab-beta-runtime-public-web-permissions-20261010.tar.gz` SHA `d27ac98ef9f0e924c936b13a263853bda3de626a08ec16c741ba32d5d12e1ee7`，以 `tar -xzpf` 保留存储权限解压后校验与 Compose 安全预检 exit 0；**不带 -p 的严格 umask 场景仍失败，不能声称 tar 可在任意解包器直接使用**。
- 动作分支 `service.rs` 新增备份清单的 SQL/附件密文存在性、regular-file、防路径逃逸、SHA-256 校验并在开始非事务 DDL 前拒绝不符；文件哈希改 64KiB 缓冲块读取。`runtime.rs` 在验证安装源及副本后只对安装副本的 `admin/web` 规范化公开读权限；新增合成权限回归，测试秘密文件与原素材权限保持不变。首轮 Rust 44/44 通过但格式检查不通过，保留日志并修正；最终 Rust **45/45、fmt exit 0、test exit 0**，日志 `rehab-tauri-runtime-perms-tests-v2-20261010.log` SHA `08082b3649b6cd4552164f58d625fc4a5a6a0dc1d1de7e768ad783827c83b9b8`。
- **工程未完成项**：本次并未由 Tauri 启动器真实调度 Docker 中的自动加密备份→成功及中途失败迁移→受控数据库/附件恢复。新增密文校验是必要条件而非恢复证明；未做签名、跨平台安装、临床 B 版签署或人工视觉确认。重跑只读 `prepackage-readiness.mjs` **exit 1 / ready=false / 14 阻断**；日志 `rehab-readiness-20261010.json` SHA `f9376160773c1b42d4c283856cd7a68eb124648941334be47c20ef324ed8dca9`。未执行 commit/push/merge，也未访问或清理真实业务数据库与 Docker 卷。

### 2026-10-10 后续工程：合成环境真实 Rust 备份→升级故障→人工恢复→成功升级

- Mac ShunCode Bridge 继续在同一脏工作树执行，新增默认跳过的 `desktop/launcher/src-tauri/src/service_live_synthetic.rs` 和 `service.rs` 测试入口，主线 `desktop/scripts/test-runtime-e2e-ephemeral.sh` 新增 `--launcher-fault-restore`。安全预检验证四服务、仅回环端口、MySQL/Redis tmpfs、独立随机 Compose 项目和仅临时目录 bind 附件；测试执行器隔离 Docker project，不修改正式项目名或真实数据卷。
- 真实 Rust `upgrade_database` 先创建两份 age 密文备份，020 成功登记、在私有副本中人为制造的 021 SQL 语法错误导致停止；检查失败版本未登记、020 DDL 留存且后续未跑。两份密文通过 SHA-256 校验与实际解密，随后人为改写**完全合成** SQL/附件哨兵、明确通过测试分支调用 `restore_transfer_sources`，确认 19 条账本、020 建表撤销、两个哨兵恢复和四服务健康。再从已恢复的基线调用相同 Rust `upgrade_database` 加载原始 020–025 SQL，全部成功，账本 25 条、动作评估 11 表，重新启动健康。测试在独立 project 内结束，临时容器清理、原 volume 仍 24。
- 第一轮新测试断言把 020 `report_go_view_project` 表误认成动作评估表，失败 exit 101，日志 `rehab-launcher-real-fault-restore-e2e-v1-20261010.log`（SHA `4747ad300b95d57a72eab416808064ab0ecfd95b478f695af74172ed105d31c0`）已原样保存。第二轮修正断言后故障/恢复 exit 0，日志 `rehab-launcher-real-fault-restore-e2e-v2-20261010.log` SHA `ae28b436aa85556801379760d5ac6e2d61d8074ae2e9611e8157bf05355993e3`。第三轮加入恢复后成功升级，**exit 0 / Rust 1 passed**，日志 `rehab-launcher-real-fault-restore-and-success-v3-20261010.log` SHA `0e6f16cd54c12edecdf6776416562b7b4a53b7cde273277ceb2580e9ba768ea8`。常规 Rust **45 passed，0 failed，1 ignored**，fmt 通过，日志 `rehab-launcher-restore-unit-v3-20261010.log` SHA `20ab1785bdfa17c16fe6442bdaa3aaaaee70200996ba18ea324af8c8e5874ac6`；Shell 语法与主线/动作工作树 Git diff --check 通过。
- 证据均在 `/Users/saber/my-project/rehab/validation-evidence/2026-10-09/`（0700/0600），且无残留临时容器，Docker volumes 总数 24 未变。只读 `prepackage-readiness.mjs` **exit 1 / ready=false / 14 blockers**，日志 `rehab-readiness-after-live-launcher-20261010.json` SHA `f9376160773c1b42d4c283856cd7a68eb124648941334be47c20ef324ed8dca9`。
- **不构成正式放行**：测试借助 `#[ignore]` 及测试专用 project 重写器调用的是真实 Tauri Rust **业务方法**，未通过 GUI 触发，未运行签名/安装包、跨平台安装或生产自动回滚；并发数据库与附件变化时的全量一致性、恢复失败后的操作审计、完整隐私/临床/签署仍未验证。不得将本测试宣称为完整内测版安装器、恢复门禁或发布 GO。

- 同批补跑主线 `node --test desktop/scripts/*.test.mjs` **15/15 通过 / exit 0**，`rehab-launcher-post-e2e-node-tests-20261010.log` SHA `247b7f6a9dc19f100e07e1575d361e1cbe6cd5eef5714bd7e4ba20d9742f43f2`。主线/动作工作树 `git diff --check` 与 E2E Shell `bash -n` 退出 0。六个共享 dirty 路径的只读语义审计另见 `docs/releases/SOURCE_INTEGRATION_AUDIT_20261010.md`：两份 Java 权限文件内容相同，桌面 Runtime 工具需要整合权限修复和迁移清单，Vue package/lock 必须保留主线 DOMPurify。未合并、未提交。
