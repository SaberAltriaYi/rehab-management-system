# Rehab Management System — P0 L3 桌面激活与许可证签名交接

**本地代码状态：已实现并开始独立安全验收。生产/内测发行：NO-GO。**

承接 `TENANT_LICENSE_L0_ARCHITECTURE_20261010.md`、`TENANT_LICENSE_L2_SERVER_ENFORCEMENT_20261010.md`。保留原有患者、租户、临床安全、打包及数据迁移门禁。工作树不合并、不提交，不操作既有患者数据库、Docker named volumes 和实际许可证私钥。

## 1. 已实施流程

| 功能 | 代码与信任边界 |
| --- | --- |
| 首装身份/公钥 | `desktop/launcher/src-tauri/src/license.rs` 使用 Rust Ring Ed25519 生成独立 PKCS8 实例密钥及两个 UUID，安装私钥仅存启动器私有 `secrets/license` 目录（Unix 0600）；已有实例不能未经所有者授权直接重置 |
| 申请审核 | Rust 通过正式发行包中固定的 HTTPS owner origin 提交 `RHL1-ENROLL` 实例证明，保存 `PENDING` 申请编号；不收集患者、影像或康复报告 |
| 在线挑战续租 | L1 的 `/license/v1/challenges` + `/license/v1/leases/online`；设备私钥签名 `RHL1-RENEW`，解析 RHL1+RHR1 两个证明，最长72h，不能自签或增席位 |
| 离线文件导入 | 所有者在 MFA 控制台人工签发的 `licenseFile` JSON 可保存为 `.rehab-license`；Rust 严格校验签名、租户/实例、时间、身份、撤销代际、模块/席位和状态，不能把任意 JSON 当许可 |
| 可信发布根 | `src-tauri/resources/license/trusted-issuers.json` 仅用于**已签名的发行包**；支持发行商多个公钥轮换；当前文件 `issuers:[]`、`ownerBaseUrl:null`，**有意不配置生产根，激活失败关闭**。绝不能从未认证的 `/issuers` 在线学习公钥 |
| 离线与异常 | 客户端返回 UNLICENSED/PENDING/ACTIVE/GRACE/EXPIRED/SUSPENDED/REVOKED/REPLACED/INVALID；本地维护撤销代际及 UTC 高水位，不允许旧签名数据回退，自动提醒即将到期；离线证明本身严格 ≤72h，断网不是实时撤销 |
| 换机 | 新设备生成另一密钥对，提供原租户 UUID、原安装 UUID 和新公钥持有证明，展示可复制的换机申请材料；**必须由独立所有者 MFA API 人工批准**，不能绕过安装数或直接导入旧机器许可 |
| Tauri 交互 | `desktop/launcher/src/main.ts`、`model.ts`、`styles.css`：工作室登记、提交、续期/查询、文件选择、换机申请、状态/到期日/席位提示；界面只显示脱敏状态，无私钥或完整票据 |
| Java 双层交接 | Rust 生成**不含私钥**的只读 `config/license-runtime/activation.json`，Compose 挂载到 Java `/app/license:ro`；`TenantLicenseBootImporter` 仅在 `yudao.license.enforce=true` 时加载，并通过 L2 `TenantLicenseImportService` 再次验证发行商签名、租户1身份和现存绑定；缺失/无效不得启动正常业务 |
| 兼容/正式构建隔离 | Rust Cargo feature `license-required` **仅供真实签名发行构建**，要求本机签名有效才允许启动/重启业务服务；现有开发版 default feature 不强制以免现有用户在迁移前被锁死。正式发布工作流三个 NSIS/DMG 打包分支均需显式 `--features license-required` |
| 正式发行门禁 | `desktop/scripts/check-l3-release.mjs` 独立于原门禁：要求实际发行商公钥、有效 HTTPS owner 地址、编号 + SHA-256 已整合的许可 SQL 迁移、且所有 Windows/macOS 打包命令带强制 feature。测试均使用合成临时目录；真实工程预期 **NO-GO** |

## 2. 真实安全边界与当前未完成的生产条件

1. **正式信任根**：软件所有者尚未提供运营域名、签名发行公钥、TLS/代码签名凭证及批准的密钥分发流程；没有这些不能/不会激活或发行正式可用安装包。L1 中的私钥也尚未部署到运营 KMS/HSM。禁止将测试 key 或动态 `/issuers` 返回值当成真实根。
2. **持久迁移**：主线迁移目前只到 023，动作评估工作树还有未合并的 024–025；`sql/mysql/rehab-tenant-license-v1.sql` 是**未落库的草案**。必须合并后明确新编号/完整哈希，在合成数据库验证升级/回滚后才能迁移真实租户。安装包在该前置条件未完成时应被构建门禁阻止。
3. **危险操作与临床数据受控迁出**：强制授权构建中已从 Rust 后端**硬性拒绝**缺乏独立身份验证的整店迁入/迁出、修改管理员和全量删除，并在 Tauri UI 中禁用对应按钮，避免用前端确认弹窗冒充管理员身份。常规加密备份功能保留以避免丢失病历。**具备身份重新验证、审计和合法数据迁出的可用恢复通道尚待 L5 开发/审查**；因此当前功能不满足生产临床数据可迁出要求，发布继续 **NO-GO**。不得把禁止高危操作当成最终恢复方案。
4. **激活运行交接**：启动时 Java 从已挂载的 RHL1/RHR1 证明做服务端验证，在线/离线签发后可写新的本地交接文件；运行中后端载入新 RHL1 凭证仍需受控重启（或 L5 新增同等级强认证/签名热更新 API）。Tauri 的 ACTIVE 只是本机已验签，不代表已完成服务端许可/账号/RBAC/患者权限，正式业务必须同时通过 Java 许可过滤器。
5. **跨平台/外部网络**：本轮本地 Mac Rust 与 Vite 构建及 Rust `license-required` 双模式测试，不等于 Windows x64 和 macOS universal 代码签名/公证或外部证书运营/代理联调。外部安全访问属于 L6，不能据此开放本机 Docker/MySQL/Redis 端口。所有者控制面不接收患者数据。
6. **时间/主机控制**：短期 RHR1/RHL1、SQLite/文件高水位只能在普通误操作和远程攻击模型下提供保护；本地主机管理员 root 和恶意二进制可以复制/恢复状态，不能承诺无法破解或网络隔离下即时撤销。

## 3. 运行和测试（均为非生产、安全合成）

```bash
# 从仓库根运行，cargo/rustc 与 Node/pnpm 需已安装
node --test desktop/scripts/*.test.mjs desktop/license-control/*.test.mjs
cd desktop/launcher && pnpm test && pnpm build && cd ../..
cargo test --manifest-path desktop/launcher/src-tauri/Cargo.toml --offline
cargo test --manifest-path desktop/launcher/src-tauri/Cargo.toml --offline --features license-required
mvn -o -B -pl yudao-module-system -am test

# 只读；当前官方发行凭证和 SQL 迁移未就绪，必须返回非 0
node desktop/scripts/check-l3-release.mjs
node desktop/scripts/prepackage-readiness.mjs
```

**切勿**使用 `pnpm tauri build`、真实 `bootstrap` 或 `docker compose up` 绕过既有发布证据与门禁。签名正式包之前不得创建 owner 生产私钥或把授权数据复制进运行时患者数据目录。当前仅对随机生成的合成密钥、合成租户和临时路径自动化测试。

## 4. 交接给 L4/L5/L6

- L4：提供真实所有者 MFA 受控审批 UI、当前工作室管理员只读许可/席位管理界面，绝不以租户 admin 提升为软件所有者。
- L5：最终迁移整合、授权中心真实 HTTPS/TLS、Java 双层启动/在线续签热更新、危险操作重新认证/备份迁出、跨租户与历史 Session 攻击、Windows/macOS 完整签名及发布门禁复验。
- L6：经合规审核的零信任跨互联网多终端远程业务数据网关，数据原则上留在工作室；绝不将数据库或 Tauri 服务直接暴露公网。

**NO-GO：保留原有 14 项正式发布阻断，绝不把本地单测通过等同生产就绪。**

## 5. 2026-10-10 L3 最终本机合成回归与证据

以下验证仅针对开发工作树和合成临时身份；无真实工作室、所有者私钥、真实临床数据库或发行安装包被操作。所有日志均独立保存在：
`/Users/saber/my-project/rehab/validation-evidence/2026-10-09/`。

| 项目 | 结果 / 证据文件（SHA-256） |
| --- | --- |
| Java L1/L2/L3 系统模块完整回归 | **514 tests，0 failures，0 errors，9 skipped，BUILD SUCCESS**；`rehab-license-l3-java-full-v1-20261010.log`，`56218380955b020c8a6d400a9f46afec9013db1990de7c62fb7482a612cf6f5a` |
| Rust desktop 默认兼容模式 | **32/32 PASS**；与强制授权 mode 一并记录在 `rehab-license-l3-rust-all-modes-v1-20261010.log`，`9bf26b76a7f7f3c4232754103a1b3cb4b5a7cd720fe10a97838448fbfc9b5b6f` |
| Rust `license-required` 发行模式 | **33/33 PASS**（含无真实发行根时启动/重启/删除/整店迁出/管理员变更失败关闭）；最终增量编译日志 `rehab-license-l3-rust-final-v3-20261010.log`，`6462507e42756675193ed6cfbac7a9eb9985e80222f347e98f0ccef496ee5b97` |
| Tauri 视图模型 / TypeScript / Vite | **7/7 PASS，TypeScript 无错误，Vite build SUCCESS**；`rehab-license-l3-final-ui-build-v2-20261010.log`，`5a39e91078711461784c8051f0f3f1af240a7f0345aee18d61ba4c908ab15aae` |
| Node 授权中心、桌面旧脚本、L3 门禁 | **86/86 PASS**；`rehab-license-l3-final-node-v2-20261010.log`，`cc3334b26de69991bffc66dc2db20a494c12de36a2c996e5bdde3df3a39a718f` |
| 发行要求静态负例 | **5/5 PASS**；`rehab-license-l3-release-gate-tests-v1-20261010.log`，`ce83687309d3abf8617ff5558ca1d4689b7aa335e01dd2c943d42aaca4a9f6b6` |
| 真实 L3 发行前置条件 | **预期拒绝：3 项 blocker**（真实 owner 根、HTTPS 域名、正式编号许可 SQL）`rehab-license-l3-release-gate-no-go-v1-20261010.json`，`647cb1a6a498208bc4cce6f7876f405f217e80c0b91cd262b7e4` |
| Compose 模板静态配置 | `docker compose config --quiet`，**exit 0**；使用合成变量，没有启动容器/触碰卷；`rehab-license-l3-compose-readonly-v1-20261010.log`（空 stdout） |

最后阶段未执行 Windows/macOS installer 打包、签名、公证或真实 owner 在线审批，因为必要运营材料尚不存在。**当前发行仍然明确 NO-GO**。

### 2026-10-10 源码冻结前最终复核（优先于上表较早日志）

在补充“离线签名许可显示为 GRACE、而不是误报实时 ACTIVE”、正式版所有高风险操作后端拒绝，以及发行商配置不完整时禁止建立孤儿安装身份后，**再次独立运行完整 Rust 双模式测试**：兼容构建 **32/32 PASS**、正式强制许可构建 **33/33 PASS**，0 失败。最终 Rust 证据：

`rehab-license-l3-rust-freeze-both-v4-20261010.log`，SHA-256 `ca8e286da6683915ce928e1334a5e86e46720627a533c54923f4739f222e7ad1`。

同一次只读检查确认：`prepackage-readiness.mjs` **ready=false，14 个既有发布 blocker**（`rehab-license-l3-freeze-original-gates-v1-20261010.json`，SHA256 `f9376160773c1b42d4c283856cd7a68eb124648941334be47c20ef324ed8dca9`）；`check-l3-release.mjs` **ready=false，3 个 L3 发行依赖 blocker**（`rehab-license-l3-freeze-publisher-gates-v1-20261010.json`，SHA256 `647cb1a6a498208bc4cce6f7876f7bff3c0f0f6f405f217e80c0b91cd262b7e4`）。`git diff --check` 退出 0、`master@3d778538a3` 未提交、Docker named volumes **24**、临时许可测试容器 **0**。以上是**本地工程完成/发行阻断保留**的双重事实，不可把测试 PASS 理解成已经生产发布。
