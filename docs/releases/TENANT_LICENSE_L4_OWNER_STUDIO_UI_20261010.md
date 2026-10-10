# Rehab Management System · L4 软件所有者与工作室授权界面交接

**范围：独立所有者 MFA 控制台 + 工作室员工/席位只读管理界面。**

**交付性质：本地开发与合成验证；正式发布仍为 NO-GO。** 此文档接续 `TENANT_LICENSE_L0_ARCHITECTURE_20261010.md`、`TENANT_LICENSE_L2_SERVER_ENFORCEMENT_20261010.md`、`TENANT_LICENSE_L3_DESKTOP_ACTIVATION_20261010.md`。不更改既有患者数据库、Docker named volumes、正式租户许可、签名私钥，亦不合并动作评估工作树。

## 一、软件所有者管理台（与康复临床数据面完全分离）

入口为 **独立 Owner HTTPS 服务**的 `GET /owner/console`；Node 控制面 `desktop/license-control/http.mjs` 仅允许白名单的静态前端资源，设置 `CSP: default-src 'none'; script-src 'self'; connect-src 'self'`、`no-store`、`nosniff`、`X-Frame-Options: DENY` 和拒绝跨域 Origin。前端位于 `desktop/license-control/console/`：

- **所有者独立认证**：账号、强密码、TOTP 登录，15 分钟内存态 Bearer，不使用 localStorage/sessionStorage/cookies；风险操作由服务端 `requireStrongOwner()` 强制核验最近 2 分钟 TOTP，失效时请求新验证码。工作室管理员凭证不能通过此控制面签发软件许可证。
- **工作室申请处理**：只读列出待审批申请及安装标识，人工选择后填写审批理由、有效期、明确签名的员工上限（`seatLimit:null` 表示不限，不是省略字段）、部署实例数量、授权模块；批准/拒绝都需 Owner MFA，再由服务端事务和链式审计记录。
- **现有租户合同与停启**：通过原 owner-only API 查看、变更授权策略、暂停、恢复、永久撤销；永久撤销需输入确切租户 UUID，且后端继续执行不可逆限制。没有直接修改本机 license 行或改写业务 tenant RBAC 的按钮。
- **离线签发与换机**：经 owner MFA 才能为**已经批准的具体安装**签发 ≤72 小时的 `.rehab-license`；换机要求新设备 `RHL1-ENROLL` 签名持有证明及旧设备身份，通过 owner API 审核后递增撤销代际；不得直接复制旧实例私钥或旧签名租约代替审核。
- **授权审计**：仅展示白名单事件摘要、时间、操作主体、目标、审核理由与链哈希；不输出患者数据、Bearer/TOTP 或签名私钥；RHA1 签名审计锚点必须另行落入 WORM，页面仅支持受控复制，不自称已外部锚定。
- **发行商密钥治理**：由于旧版直接 rotate 会马上启用未分发的 signing key，UI 有意将轮换按钮**禁用**；待 L5 建立先分发可信验证公钥、再切换签发 key 的受控变更流程后才可启用。底层 owner MFA API 仍属于单独运维权限，不将 UI 禁用当成 API 级硬隔离。

### Owner 控制面的运维约束

正式操作员必须持有真实 owner MFA 凭证、独立 HTTPS TLS 证书和可信域名；所有者服务只能绑定 `127.0.0.1`，经经审核的 TLS/零信任入口访问。浏览器 Origin 须与 `REHAB_OWNER_BROWSER_ORIGIN` 完全一致（本地默认 `https://127.0.0.1:9444`；反向代理应明确配置其自己的 HTTPS Origin）。不能把授权服务通过 `0.0.0.0` 或患者业务网络暴露公网。本次开发没有创建真实 owner，也没有启动正式 9444 监听。

当前 Owner SQLite/Node22 `node:sqlite` 仍带实验性风险；真实 KMS/HSM、密钥恢复、灾备、高可用、独立 WORM、DNS/TLS/审计备案及人工审批组织归属证据仍属于生产 NO-GO 条件。

## 二、工作室账号与员工席位界面（不具备软件所有者权限）

Java `StudioLicenseOverviewService` 和 `StudioLicenseController` 新增 **只读** `GET /admin-api/system/studio-license/overview`（Spring 控制器路径为 `/system/studio-license/overview`）。API 必须通过原 `system:user:query` 权限，并从已经认证的 `TenantContextHolder` 读取租户 ID，**不接受用户提交任意 `tenantId` 参数**。

当服务端正式开启 `yudao.license.enforce=true` 后，`TenantLicenseGuard.requireTenant()` 严格验证所有者固定公钥签发的 RHL1、RHR1、安装绑定、时钟/撤销代际及授权截止，然后仅在本租户约束下统计 `system_users` 启用员工数量。原始登录、Token、用户创建/启用/导入与席位并发事务限制仍由 L2 服务端执法，不受页面传回数字的影响。

`seatLimit:null` 仅表示**所有者签名中明确的无限员工席位**；有限配额返回 `activeStaff`、`availableSeats`。当开发实例默认禁用强制许可时，返回 `NOT_CONFIGURED`，**不伪装成正式有效许可，也不臆造无限额度**。撤销、过期、DB/验签失败拒绝敏感查询；不会从未签名的旧 `system_tenant.account_count` 获取有效额度。

Vue 端模块：

- `src/api/system/studioLicense/index.ts`：只读 API 与明确类型。
- `src/views/system/studioLicense/index.vue`：响应式许可概况、到期和模块、有限/无限席位区分、员工列表分页/状态查询、复用既有员工创建/编辑、角色、启停、密码重置操作；在额度耗尽时禁用新增及重新启用，Java 后端仍强制执行事务与 RBAC。
- `src/router/modules/remaining.internal.ts`：提供隐藏内部路由 `/studio/license`，从 `src/views/system/user/index.vue` 的原有员工管理页跳转；不替换整个既有用户系统或患者权限。
- Vue 操作仍使用原 `system:user:create/update/query`、`system:permission:assign-user-role` 等 RBAC 能力，不允许前端直接调整签名许可或提供任何 Owner 密钥入口。

## 三、合成验证与风险边界

测试使用本地随机 owner/TOTP、合成组织 UUID、临时 SQLite、HTTP loopback 和临时 TLS 自签证书。Java JDBC 依赖以 Mockito 代替真实数据库，测试验签、租户隔离、禁用时 `NOT_CONFIGURED`、`null` 签名无限配额、有限配额、撤销拒绝及控制器权限元信息。静态资源验证同源 CSP、只读审计投影、不使用浏览器持久存储或把签名轮换误开放。

**以下无法以单元测试代替真实上线证据**：Windows/macOS 签名安装包和真实浏览器无障碍实机复核、跨租户数据库端到端联调、生产 TLS 代理 Origin 测试、Owner 密钥上线/KMS、人工审批依据、邮箱/SMS 送达、运行中的 Java 续租和临床数据迁出/恢复合规审核。这些纳入 L5/L6。尤其不能把所有者管理台视为患者数据的授权访问入口。

## 四、最终本机回归证据

证据目录：`/Users/saber/my-project/rehab/validation-evidence/2026-10-09/`，所有回归日志均独立保存；不得覆盖历史失败日志，只以最后实际通过的冻结记录作成功断言。

- Java `mvn -o -B -pl yudao-module-system -am test`：包括 `StudioLicenseOverviewServiceTest` 与 `StudioLicenseControllerBoundaryTest`，以及原完整系统模块回归；最终日志与 SHA-256 见本节追加冻结记录。
- Node `node --test desktop/license-control/*.test.mjs desktop/license-control/console/*.test.mjs desktop/scripts/*.test.mjs`：Owner HTTPS、MFA/RBAC、强签名租约、控制台安全响应与页面入参测试；最终日志与 SHA-256 见冻结记录。
- Vue `vue-tsc --noEmit` 全项目类型检查，且对 L4 相关改动执行 `eslint --no-fix --max-warnings 0`；不以 0 个 ESLint error 代替类型/运行时验证。
- 原发布门禁 `prepackage-readiness.mjs` 及新增 `check-l3-release.mjs` 继续**预期拒绝**；不是单测失败，更不应修改脚本使其伪造 GO。

### 2026-10-10 Bridge 恢复后冻结回归

下列文件均位于 Mac 的 `/Users/saber/my-project/rehab/validation-evidence/2026-10-09/`，日志使用 0600 权限，不含正式 Owner Bearer、实际组织信息或患者数据：

| 项目 | 最终独立记录与 SHA-256 |
| --- | --- |
| Java Reactor 完整回归 | **522 项，0 失败、0 错误、9 跳过，Maven BUILD SUCCESS**。`rehab-license-l4-java-final-resumed-v2-20261010.log`；`9e6405d6c9313017ff84b12c1b70909697d9c4f3d851a239a9229e34cefa33d6` |
| Node owner + console + L0/L1/L3 工具回归 | **91 项：90 通过，0 失败，1 跳过**。跳过项是明确标注的 L5 Chrome headless 实机测试。`rehab-license-l4-node-freeze-with-optional-browser-v4-20261010.log`；`e3af1aa5dc23e09a4b8ea54b15c992c9efca35d3259703fadb4e4568e692b87a` |
| Vue 全项目 TypeScript 类型检查 | `vue-tsc --noEmit` **EXIT 0、零类型错误**。`rehab-license-l4-vue-tscheck-resumed-v3-20261010.log`；空日志 SHA-256 `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| Vue 涉及文件 ESLint | `eslint --no-fix --max-warnings 0` **EXIT 0，0 错误，0 警告**。`rehab-license-l4-eslint-final-resumed-v3-20261010.log`；空日志 SHA-256 `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| 既有正式 P0 发布门禁 | `ready=false`，**14 个原 blocker**，预期退出码 1。`rehab-license-l4-readiness-original-resumed-20261010.json`；`f9376160773c1b42d4c283856cd7a68eb124648941334be47c20ef324ed8dca9` |
| L3 商用授权发行门禁 | `ready=false`，**3 个发行依赖 blocker**，预期退出码 1。`rehab-license-l4-readiness-license-resumed-20261010.json`；`647cb1a6a498208bc4cce6f7876f7bff3c0f0f6f405f217e80c0b91cd262b7e4` |

**真实浏览器边界（不可误报通过）**：尝试在 ShunCode macOS 受管终端启动本机 Chrome 154、单日合成 TLS 及临时 profile，对所有者网页作 1280 宽桌面和 390 宽手机视图冒烟测试；Chrome 在 20 秒后超时，报本机 `task_policy_set ... invalid argument`，该**尝试失败**。`rehab-license-l4-chrome-browser-smoke-v1-20261010.log`，SHA-256 `56adbebfe318e2641691a07b1519c60528365228465b6907c18f39a2babd2101`。没有发现遗留测试浏览器进程或正式 9444 监听。浏览器测试已保留为 `desktop/license-control/console/browser-smoke.test.mjs`，需要 L5 的专用实机运行器显式设置 `REHAB_L4_CHROME_SMOKE=1` 才执行；默认跳过**不代表视觉/无障碍 E2E PASS**。

工作树 `master@3d778538a3`，既有 Docker named volumes **24**，没有留下 L4 临时服务器或自动提交。没有运行真实数据库迁移、正式 owner bootstrap、电子证书签发、NSIS/DMG 发行包构建、代码签名、公网部署、患者数据迁入迁出。

## 五、发布状态

**L4 本地功能实现、类型检查、静态安全检查、Node 与 Java 合成回归已完成；正式浏览器/跨租户 E2E 仍归属 L5，不宣称已通过。正式商用/内测仍 NO-GO。** 主线迁移清单截至 023、动作评估分支 024–025 尚未合并、L2 许可表未正式编号和升级演练、真实发行公钥及所有者 HTTPS 入口未配置、签名安装包与临床恢复未验收；既有 **14 个 P0 发布阻断不变**。不启用生产许可、开放互联网端口，不接触患者数据，不提交/合并/推送或发布。
