# 康复管理系统 · L5 全链路安全 E2E / 发行门禁交接

**2026-10-10 · Mac 本地工程。L5 可在本机独立完成的开发、合成 E2E 与证据冻结已执行；生产/真实发行 GO 尚未完成，仍为 NO-GO。**

衔接 `TENANT_LICENSE_L0_ARCHITECTURE_20261010.md`、`TENANT_LICENSE_L2_SERVER_ENFORCEMENT_20261010.md`、`TENANT_LICENSE_L3_DESKTOP_ACTIVATION_20261010.md`、`TENANT_LICENSE_L4_OWNER_STUDIO_UI_20261010.md`。仅使用合成租户、独立 Ed25519/Owner MFA、随机临时 SQLite、Docker `--network none` / tmpfs，未触碰患者库、原有 24 个 Docker named volumes、Owner 生产密钥或实际工作室授权。

## 1. 本地 L5 实施和边界

| 工作流 | 验证 / 变更 |
| --- | --- |
| 独立控制面两机构端到端 | `desktop/license-control/l5-e2e.test.mjs` 真实走本机测试 HTTP 白名单：两租户独立申请、签名持有证明、所有者 MFA、审批、定额2席位与明确 null 无限席位、在线/离线签发、Owner-only 接口、非法 Origin、挑战一次性消费、跨租户换票/伪造、离线最长72小时、签名篡改、公钥替换、时钟回拨、暂停/恢复/不可逆撤销、换机代际、注销、链式审计和 SQLite 关闭后恢复。**13/13 PASS**。明文 HTTP 仅用于显式 loopback 合成单测，绝不能用作真实 Owner 部署。 |
| 三语言相同发行票据 | `desktop/scripts/l5-node-issuer-fixture.mjs` 在随机临时私有数据库中调用真正的 `OwnerLicensingService`，只输出测试公钥、两机构 RHL1/RHR1 与撤销状态，不导出私钥、MFA 或密码；`run-l5-cross-runtime.sh` 将**同一批**签发材料交给 Java `L5NodeIssuedLeaseInteropTest` 和 Tauri Rust `l5_node_owner_signed_tokens_interoperate_with_rust_real_issuer`。Java **6/6 PASS**，Rust **1/1 PASS**。Rust 普通无 fixture 单测的 return 分支**不作为跨语言成功证据**；必须看联调脚本的两个明确 PASS 标志。 |
| MySQL 席位竞态 | `deploy/internal/test_tenant_license_isolated.sh` 使用本机已有 MySQL 8.4.10 镜像、无网络、tmpfs 和临时合成表测试租户许可证行 `SELECT FOR UPDATE`；事务等待2秒，第二事务正确看到已占1席，**PASS**；Docker volumes 24→24。只证明 SQL 隔离，不宣称生产环境席位接口 E2E。 |
| 迁移、回放和恢复 | `deploy/internal/test_migration_upgrade_synthetic.sh` 通过独立 Compose + tmpfs 将无患者的 `001–019` 净化引导 SQL 升级到 `023`，校验固定 SHA-256、幂等重复迁移、23条 history 和第二合成数据库的备份恢复，**PASS**。L2 许可证草案 SQL 尚**未分配正式迁移编号**，故不包含于此测试，不得加入真实库。 |
| Java 全模块 | `mvn -o -B -pl yudao-module-system -am test`：**522项、0失败、0错误、9跳过**；单元与模拟库覆盖 Java 账号/RBAC/许可过滤器以及租户席位。 |
| Rust 桌面 | `cargo test --offline` 默认模式 **33/33**、`--features license-required` **34/34**；正式功能模式强制拒绝无签名启动/危险操作，不改变已有研发/兼容客户行为。 |
| Node 总回归 | `node --test desktop/license-control/*.test.mjs desktop/license-control/console/*.test.mjs desktop/scripts/*.test.mjs`：**105项中104通过、0失败、1跳过**；跳过项目为仅在批准 L5 图形 runner 显式设置环境变量后启动的 Chrome 审计测试。之前 ShunCode Mac Chrome 启动曾因 task-policy 超时，不视为通过。 |
| Vue 员工管理 | 全仓库 `vue-tsc --noEmit` **exit 0**、变更页面 ESLint **0错误/0警告**；`build:internal` **成功、833个文件**，浏览器兼容数据库 `caniuse-lite` 已有23个月未更新警告，需 L5 真实浏览器测试复核。 |
| 依赖安全 | `pnpm audit --prod --audit-level high --registry=https://registry.npmjs.org` 返回 **No known vulnerabilities found**（仅适用于此次查询的前端生产依赖）。`l5_sbom_inventory.py` 基于 Cargo.lock、两个 pnpm lock、离线 Maven 模块依赖树，生成 CycloneDX 1.5 **1905 个组件的临时锁文件来源清单**，4/4 解析器测试通过。**不是完整发行制品 SBOM**；Maven 有11条本地模块 POM 缺失警告，未完成 Java/Rust/镜像正式 CVE 扫描、外部漏洞数据留存及制品级材料链。 |
| 发行签名链 | `.github/workflows/desktop-release.yml` 移除 unsigned fallback。无 Windows PFX 即失败，构建后使用 `Get-AuthenticodeSignature` 核查证书与签名；无 Apple Developer ID + 公证 API 即失败，构建后严格 `codesign`、`notarytool submit --wait`、检查 Accepted、`stapler staple/validate`、`spctl` 检查。 `check-l3-release.mjs` 拒绝遗漏上述流程及任何未强制 license feature 的构建分支。**仅完成 CI YAML 静态检查，尚无真实签名包及 Apple 公证成功回执**。 |

## 2. 测试日志冻结与 SHA-256

位置：`/Users/saber/my-project/rehab/validation-evidence/2026-10-09/`。日志与合成 SBOM 均通过严格私有权限写入；早期失败日志不覆盖且列为非通过记录。

| 证据文件 | 结果摘要 | SHA-256 |
| --- | --- | --- |
| `rehab-license-l5-owner-e2e-first-20261010.log` | 13/13 PASS | `613a2342253ed02b5da94fe1f410f70c5f40f22764d0bb522bb147272a6cb355` |
| `rehab-license-l5-node-java-rust-cross-v2-20261010.log` | Java 6/6 + Rust 1/1 PASS | `cdc8749f150ee4eba12270b5cf0edccdf484829ce49cd3b297690d60b610f5bb` |
| `rehab-license-l5-mysql-isolated-v1-20261010.log` | 独立 MySQL 锁竞争 PASS，24→24 卷 | `bec0d701f1be1c325531b2e18ceb347c4a7e8b523b0c39191fed9c1bc4b0fd3e` |
| `rehab-license-l5-upgrade-restore-synthetic-v1-20261010.log` | 001–023 SQL 迁移/幂等/恢复 PASS | `31046e0e847f87f75c5578bfc0d27844aae5b97d00a416401e6c60bb1fc6d685` |
| `rehab-license-l5-java-system-full-v1-20261010.log` | Java 522/0失败/9跳过，BUILD SUCCESS | `9d63567a9bd08b574768302866efa81c8a348263f1cf5a35b31a45f220ce772f` |
| `rehab-license-l5-rust-both-v1-20261010.log` | Rust 33/33 + 34/34 PASS | `4a3a0283d91a3c71da0e8101164cba29e1e33654d97ea1d48832544c14e69739` |
| `rehab-license-l5-node-freeze-v2-20261010.log` | Node 105项，104 PASS、1 SKIP、0失败 | `781d3ca6f1cbcd15f52cd46185fb3a1cd8141cc6f8bdeea855e7b25b2e03416c` |
| `rehab-license-l5-vue-types-eslint-v1-20261010.log` | Vue TS+ESLint exit 0 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| `rehab-license-l5-vue-internal-build-v1-20261010.log` | 前端构建成功，833文件 | `127a59c26227e11c14e5b47dfae8e8bf22927d446451af03768255d8288ce820` |
| `rehab-license-l5-frontend-dependency-audit-v2-20261010.log` | 生产依赖审计无已知漏洞 | `15950a68a7ed99c59779717acefdceb3f69cfd31bde67d2c954f2a3cea4d7955` |
| `rehab-license-l5-provisional-sbom-v2-20261010.cdx.json` | 临时 CycloneDX 1.5，1905组件 | `0ffc5ea4f2e227e1ec548d7346d3c93af554f1252bec1e3fe4b049f77bca37e1` |
| `rehab-license-l5-provisional-sbom-v2-20261010.log` | 4/4 inventory tester PASS | `07c9b431d3b4e937dba11841345a1d370ec722a3828b575a675cd4f1e371bd8e` |
| `rehab-license-l5-signed-release-gate-v1-20261010.log` | 6/6 发行门禁合成安全测试 PASS | `9bbb753db99b8bf9430c78f6ca525683f8b1011b41eca095910d518f64db5177` |
| `rehab-license-l5-ci-workflow-static-v1-20261010.log` | CI YAML/签名/公证路径静态检查 PASS | `8780b74501ff2174093a57dbee35dc41fbf72b5fb776af4b4c65d35a0c0863e1` |
| `rehab-license-l5-p0-readiness-final-v1-20261010.json` | **NO-GO：原14个阻断** | `f9376160773c1b42d4c283856cd7a68eb124648941334be47c20ef324ed8dca9` |
| `rehab-license-l5-release-guard-no-go-v1-20261010.json` | **NO-GO：L3发行3个阻断** | `647cb1a6a498208bc4cce6f7876f7bff3c0f0f6f405f217e80c0b91cd262b7e4` |

保留失败日志（不得将失败记录当成通过）：初版 `l5_sbom_inventory` unittest 调用错误已在 v2 改正；首次 `pnpm audit --ignore-scripts` 命令参数不受 pnpm audit 支持，v2 使用只读 audit 命令完成。此前 L4 Chrome 真实浏览器测试曾超时，仍为 L5 开放项。

## 3. 真实 GO 所需的不可绕过项

**下列均未完成，禁止用随机测试密钥/自签证书/开发截图替代正式验收：**

1. 由软件所有者安全建立受控 KMS/HSM 管理的 Ed25519 正式密钥、经双人确认分发信任根、配置真实 HTTPS/TLS/DNS 与 MFA 运营账号、独立 WORM 审计和灾备恢复演练。
2. 许可证 SQL 草案与当前 023 以及未合并的动作评估 024–025 迁移版本在主线正式协调，生成不可变更编号+哈希；在不含患者信息的可抛弃合成环境验证从历史版本升级/回滚后，须有真实备份与变更审批才可进入生产。
3. **真实 Java 服务进程+实际 RHL1/RHR1+MySQL+Redis+Vue/Tauri GUI** 的全部认证、OAuth2、老 Token、业务模块、WebSocket/任务和并发员工限制完整端到端测试；本次 Node→Java/Rust 交叉验证与模拟库 SQL **不等于完整业务流联调**。
4. L3 正式 build 的整店迁入/迁出、账号变更和全量删除当前安全拒绝，但尚缺经管理员重新认证、法定数据归属审核、审计、双人批准的合法恢复/迁出可用路径。**不能把禁用危险操作视为满足患者数据可恢复性要求**。
5. Windows x64 和 macOS Apple Silicon/Intel 通用架构真实原生 UI、Owner Console Chrome/无障碍测试，以及正式 Windows 有效 Authenticode 签名、Apple Developer ID + 公证 Accepted 与 stapler 成功回执。ShunCode 的 Chrome task-policy 失败未解除。
6. 对最终安装包、Docker 基础镜像及 Java/Rust/JS **所有交付组件**生成可复验的 CycloneDX/SPDX 制品级 SBOM、完整高危漏洞/许可证合规审计，并固定真实发布制品校验和、证书/公证信任链与构建 provenance。本地依赖清单和前端 pnpm 审计不足以宣称供应链 GO。
7. 保留并补齐原 `prepackage-readiness.mjs` 的**14个 P0阻断**；CI `check-l3-release.mjs` 的**3个商业发行前置阻断**为真实发行根、HTTPS 域名、正式许可迁移。相关真实凭证及密钥当前空白，发行流程**设计为拒绝**。

## 4. 后续实施顺序

L5 本地安全自动化工作已形成完整证据。下一步必须先取得真实基础设施、合规迁移和签名材料的运营批准，然后在授权隔离测试环境完成未闭环的跨平台实机、真实业务 E2E、合法恢复/迁出和制品审计。**这些是 L5 生产门禁待办，而不是 L5 已通过项。** 跨公网零信任多端网关仍为 L6，数据库/Docker/Tauri/Redis 服务不得直接暴露互联网。

**最终：本地 L5 自动化开发验收完成；生产/商业安装包及跨端交付 NO-GO。**
