# 所有者独立授权控制面（L1 工程交付）

**状态：L1 本地可执行实现与合成安全测试通过；尚未部署运营授权服务器，不是正式生产许可服务。**

这是与 `rehab-management-system` 的患者数据面、Java 登录、Vue 管理后台及 Tauri 启动器**完全分离**的 Node.js 授权控制面。它只管理机构申请、租户 UUID、许可/安装公钥、签名密钥和操作审计；**不会读取或迁移病历、治疗视频、诊断报告和患者身份信息**。

## L4 所有者操作台（本地开发已实现，正式上线仍 NO-GO）

**L5 合成安全联调**：`node --test desktop/license-control/l5-e2e.test.mjs` 用两家虚构工作室走完整 Owner MFA、申请审批、签名签发、撤销、攻击负例与临时 SQLite 恢复；`bash desktop/scripts/run-l5-cross-runtime.sh` 用实际 Node Owner 合成签名直接测试 Java 和 Rust 的同协议验签，绝不创建生产密钥或连接患者系统。完整日志、SHA-256、SQL 隔离、跨平台代码签名阻断、临时 SBOM 与 NO-GO 条件参见 `docs/releases/TENANT_LICENSE_L5_E2E_RELEASE_GATES_20261010.md`。

在受信任 TLS 的所有者服务器启动后，用**独立的所有者浏览器**访问 `https://127.0.0.1:9444/owner/console`（如经过受控代理，须用正式域名并设置完全相同的 `REHAB_OWNER_BROWSER_ORIGIN`）。控制台静态资源由 `http.mjs` 的固定白名单提供，拒绝跨域 Origin，启用严格 CSP、禁止页面缓存与嵌入；浏览器仅将 15 分钟 MFA Bearer 保存在当前页面内存，不使用浏览器持久化存储。

工作流包括机构公开申请、补充资料、待审机构/新设备检查、人工审批/拒绝原因、合同到期、明确签名的无限或有限员工席位、授权模块/部署实例数、租户续期/暂停/恢复/不可逆撤销、短期离线许可证下载、设备换机所有者审批、脱敏审计与签名链头导出。公开申请只接收机构基础资料和联系人，不接收患者、视频或病历。高风险操作仍必须由服务端核实新的 TOTP 时窗；工作室 Vue 管理员**无法**在此授权软件。Owner 页面**禁用直接签发密钥轮换**，因为发行包可信公钥没有受控分发前启用新 key 会锁死已有租户；API 级密钥治理只属于另行审批的运维变更。

工作室自助员工/席位 UI 在 Java `StudioLicenseController` 下的 `GET /admin-api/system/studio-license/overview`，仅允许当前登录租户 + `system:user:query`，从已验签的 RHL1/RHR1 获取席位，不能由前端调整签名配额。完整 L4 交接见 `docs/releases/TENANT_LICENSE_L4_OWNER_STUDIO_UI_20261010.md`。本机并**未**真实启动授权服务或签发实际工作室许可证。

## 已实现

1. **所有者身份**：本机交互式单次初始化唯一 owner，scrypt 密码哈希、AES-256-GCM 加密 TOTP 种子、30 秒 RFC6238 TOTP、单步防重放，5 次登录失败锁 15 分钟；15 分钟不透明 Bearer 会话，敏感授权操作只允许近 2 分钟 MFA 验证，过期可再次输入新 TOTP 进行 step-up。
2. **独立持久化**：单独 SQLite（Node22 `node:sqlite`，当前为 *Experimental* API），事务化状态和约束、单例 owner、工作室申请/批准/拒绝、期限/员工席位/功能范围及部署实例数量，机构管理员不能自批许可。
3. **在线/离线签发**：Ed25519 长度/格式固定的 RHL1，安装实例证明、一次性 5 分钟签名挑战、最长 72 小时租约，人工审批后才生效。未注册/未批准/过期/暂停/撤销拒绝签发。
4. **密钥管理**：签名私钥和 MFA 种子由独立运维保管的 32 字节 vault key 用 AES-256-GCM 加密保存；可人工轮换签名 `kid`，旧公钥在有效票据到期前保留验证，新签发使用新私钥；禁止提前常规退役旧公钥。紧急泄露可通过额外 MFA 验证立即停止使用对应签发者，但**离线设备仍只能等下一次可信密钥状态同步或租约到期时撤销**。所有许可使用者的可信公钥初始分发必须走**可信的软件发行/带签名的更新渠道**，不能直接把开放 API 返回的未知公钥视为可信。
5. **审计和设备**：工作室申请与审批、人工额度调整、会话/密钥变更、设备换绑、续签和撤销生成链式 SHA-256 审计；启动校验已记录的链；支持所有者签名的审计链头快照 `RHA1`，须另行存入不可变更的外部审计保留系统；更新许可/换机/停用时递增撤销代际并取消未使用的挑战。
6. **TLS 与请求安全**：独立 HTTPS 服务仅绑定 `127.0.0.1`，必须配置运维 TLS 证书与密钥；默认拒绝无 TLS；JSON 请求大小限制、Owner Bearer 身份、明确路由和请求字段、Origin 限制与基础内存限流。环回 HTTP 只允许由显式测试模式启动，不能用于部署。

## 安全的本地运维启动（**当前未执行**）

**禁止在源码目录中创建正式 SQLite、证书或私钥。** 运维者自行在受控服务器或 Mac 独立目录创建持久化状态，并从安全凭证存储提供三个环境变量：

- `REHAB_OWNER_DB_PATH`：操作员可写的**独立专用** SQLite 文件路径，不允许使用既有患者 DB、项目源码目录、Docker volumes。
- `REHAB_OWNER_VAULT_KEY_B64`：用安全随机数产生的 32 字节 key 的 base64url（43 字符）编码，**必须在独立保密存储做备份**；不能硬编码、提交到 Git、复制进 runtime 或记录到普通 shell 历史。
- `REHAB_OWNER_USERNAME`：首次初始化时的唯一所有者账号 ID，格式是 4–40 位字母起始的安全字符。

在 operator 本机终端，使用 `node desktop/license-control/cli.mjs bootstrap`。密码仅从交互式终端隐藏输入，不通过参数传递；首次唯一 TOTP 种子只显示一次，需**立即加入受控 MFA 管理器和安全恢复保管**。重复 bootstrap 会被阻断。此步骤**不自动创建签名密钥**；首次登录 owner MFA 后通过授权 API 初始化/轮换活动签名 key。

之后还需在运维安全存储提供：

- `REHAB_OWNER_TLS_CERT`：面向 `127.0.0.1` 内部代理或正式授权域名的 TLS 证书路径。
- `REHAB_OWNER_TLS_KEY`：与该证书匹配的私钥路径。
- 可选 `REHAB_OWNER_PORT`（默认 9444）、`REHAB_OWNER_BROWSER_ORIGIN`（仅接收该 HTTPS Origin）。外部正式站点需要**独立的受控 HTTPS/零信任入口及防火墙/速率限制**，不得通过 `0.0.0.0` 将本服务直接开放公网。

运行 `node desktop/license-control/cli.mjs serve` 后，操作员应先在有证书验证的独立 HTTPS 客户端通过 `POST /owner/v1/login`（`username/password/totp`）取得短期 Bearer，再 `POST /owner/v1/signing-keys/rotate` 初始化签发者。不得将 Bearer/TOTP/签名明文记录在日志、接口测试证据或聊天中。真实域名、TLS、证书、HSM/KMS、账户恢复/灾备和网络访问策略上线前必须由软件所有者实际提供和核准。

本地申请入口是 `https://127.0.0.1:9444/owner/register`（测试服务器可用随机环回 HTTP）。登录页提供 `/owner/recovery` 本机一次性 MFA 恢复入口；它要求所有者密码和明确确认短语，生成新的 TOTP 配置、注销旧会话并使旧密钥失效，绝不回显旧密钥。申请进入 `PENDING`，Owner 可改为 `NEEDS_INFO`、`APPROVED` 或 `REJECTED`；审批时在独立控制台选择 `rehab,motion,report` 等模块。主系统不再承载拥有者租户管理台；旧 `/system/saas/platform/*` 只有显式设置 `yudao.legacy.saas-platform.enabled=true` 才会启用，默认关闭。

### Owner 审批到主系统开通

主系统的租户开通接口默认关闭。要在本地联调时，在主系统注入 `YUDAO_PROVISIONING_ENABLED=true`、至少 24 位的 `YUDAO_PROVISIONING_TOKEN`，并在 Owner 进程注入 `REHAB_MAIN_PROVISIONING_URL`、同一 token、`REHAB_MAIN_PROVISIONING_PLAN_ID` 和 `REHAB_MAIN_PROVISIONING_PACKAGE_ID`。审批页面的首位管理员账号/临时密码只在当前请求中转发，不写入 Owner SQLite；主系统用 `saas_provisioning_requests.idempotency_key` 和申请 UUID 防重复创建。

流程为：机构注册 → Owner MFA 审核 → 签发软件授权 → 异步调用主系统私有接口 → 创建 `system_tenant` 并写入同一 `tenant_uuid` → 建立订阅与用量 → Owner 页面显示“主系统已开通”。失败会保留 `FAILED` 和脱敏错误，可使用“重试开通”继续同一个幂等请求。数据库先执行 `sql/mysql/rehab-saas-provisioning-v3.sql`。

## API （均为 JSON；受保护接口需要 `Authorization: Bearer <session>`）

L3 桌面离线许可证对接：只有软件所有者 MFA 登录并审批安装实例后，调用 `POST /owner/v1/leases/offline`，成功 JSON 同时包含 `token`、`statusProof` 和可直接保存为 `.rehab-license` 文件的 `licenseFile` 子对象。后者由 `schemaVersion/tenantId/installationId/token/statusProof` 构成，必须通过独立安全通道交付目标工作室。不得导出签名私钥、MFA 种子、短期 Bearer 或患者数据；工作室不得自签许可证。

| HTTP | 路由 | 作用 |
| --- | --- | --- |
| POST | `/owner/v1/login` | 密码 + TOTP 获得 15 分钟会话 |
| POST | `/owner/v1/step-up` | 新 TOTP，刷新 2 分钟高风险操作窗口 |
| GET | `/owner/v1/mfa/recovery/challenge` | 环回一次性 MFA 恢复挑战，5 分钟有效 |
| POST | `/owner/v1/mfa/recover` | 环回恢复并轮换 TOTP；成功响应只显示新配置一次 |
| POST | `/owner/v1/logout` | 删除服务端会话 |
| GET | `/owner/v1/applications` | Owner 审核申请列表 |
| POST | `/public/v1/applications` | 机构提交基础资料和联系人，创建 PENDING 申请 |
| GET | `/owner/register` | 机构公开注册页面 |
| POST | `/owner/v1/applications/{id}/approve` | 审批、确定员工席位/实例数/期限/模块 |
| POST | `/owner/v1/applications/{id}/retry-provision` | MFA 后重试主系统租户开通 |
| POST | `/owner/v1/applications/{id}/reject` | 驳回附原因 |
| POST | `/owner/v1/applications/{id}/request-info` | 要求申请人补充资料 |
| POST | `/owner/v1/signing-keys/rotate` | 初始化/轮换所有者签名密钥 |
| POST | `/owner/v1/signing-keys/{kid}/retire` | 旧租约全部过期后退役验签 key |
| POST | `/owner/v1/signing-keys/{kid}/emergency-revoke` | 高风险临时冻结被疑似泄露签名 key；后续需轮换并受控同步客户端信任目录 |
| GET | `/owner/v1/tenants/{uuid}` | 查看租户签名许可状态 |
| POST | `/owner/v1/tenants/{uuid}/policy` | 更新期限/席位/模块/实例数并推进撤销代际 |
| POST | `/owner/v1/tenants/{uuid}/status` | ACTIVE/SUSPENDED/REVOKED；撤销是不可逆终态 |
| POST | `/owner/v1/installations/replace` | Owner 验证新设备公钥证明并批准换机 |
| POST | `/owner/v1/leases/offline` | Owner 人工签发最长 72h 离线许可 |
| GET | `/owner/v1/audit` | Owner 访问脱敏、链式审计 |
| POST | `/owner/v1/audit/checkpoint` | Owner 生成 Ed25519 签名审计链头证明，必须独立留存 |
| POST | `/license/v1/applications` | 工作室提交机构与带签名实例注册 |
| POST | `/license/v1/challenges` | 已批准设备获取一次性 5 分钟续租挑战 |
| POST | `/license/v1/leases/online` | 设备签名证明并获取 ≤72h 在线租约 |
| GET | `/license/v1/issuers` | 只读发布公钥目录；**不替代安装包中预置的可信锚** |

其中许可审批请求的 `policy` 具有 `expiresAtMs`（Unix epoch 毫秒）、`seatLimit`（正整数或明确 `null`=无限席位）、`maxInstallations`（正整数）、`modules`（有限且不重复的模块数组）。`tenantId/installationId` 为小写 UUID，`possessionSignature` 为申请/挑战消息的 Ed25519 base64url 签名，注册与在线续租消息的格式由 `desktop/scripts/tenant-license-owner-core.mjs` 中的 `enrollmentMessage` / `renewalMessage` 定义。

## 本机合成回归

```bash
node --test desktop/license-control/*.test.mjs desktop/license-control/console/*.test.mjs
node --test desktop/scripts/*.test.mjs
```

其中 Chrome 真实窗口的临时 HTTPS 冒烟测试**默认跳过**，仅在经批准的 L5 浏览器测试运行器设置 `REHAB_L4_CHROME_SMOKE=1` 后执行；ShunCode Mac 受管终端已观察到 Chrome task-policy 启动限制，不能将默认跳过误报为真实视觉/可访问性 E2E 已通过。

测试使用随机临时 SQLite、只在内存中的签名密钥和测试用 TOTP 账号，HTTP 只绑定随机 `127.0.0.1` 端口；TLS 测试生成并销毁一天有效的自签名合成证书。**测试不会创建生产 owner、生产 signing key 或连接真实工作室数据库/患者数据。**

## 尚未满足的生产上线依赖

- **密钥保管**：本地应用加密密钥由操作者通过环境变量提供；尚未接入外部 KMS/HSM 实际密钥不可导出签名接口或完成双人操作。该实现不是 HSM，不能以 AES 加密本地私钥等同真正的密钥隔离。上线前必须完成 KMS 选择、密钥恢复与轮换/应急撤销方案及实测。
- **运维控制**：当前 SQLite 单实例，不具备跨主机高可用、WORM 审计外部锚定、受监管的备份恢复、灾备目标和正式生产恢复材料托管；本地恢复页只适用于 loopback 开发环境。哈希链能检测**普通数据库篡改**，但不能阻止拥有整个数据库写权限的攻击者重新计算整个链。
- **公网安全**：仅完成独立 loopback TLS API 与测试；没有 DNS、证书正式签发、互联网网关/WAF、机密管理、集中告警或正式网络渗透测试。远程员工访问业务系统归属于 **L6**，绝不能复用本控制面转发患者临床数据。
- **强制拦截与端到端发布**：L2 已写入 Java API/OAuth2/员工席位和租户作业的后端签名校验，L3 已写入桌面激活与 Java 启动交接，L4 已写入独立 Owner 审批台与工作室员工界面；**仍未在真实生产库应用已编号迁移或开启强制授权**，实际 WebSocket、全部第三方入口、危险操作重新认证、合成跨租户全路径和受信任正式制品仍需 L5。不能把本地单测通过视为所有访问路径已在发布构建中安全强制。
- **稳定性**：Node `node:sqlite` 在当前本机 Node v22.15.0 仍为实验性 API，冻结发布前应迁移/评估稳定数据库驱动及版本兼容性、恢复性。

**当前发布结论持续为 NO-GO；不自动部署、公网开放、改写现有登录或生产租户记录。**