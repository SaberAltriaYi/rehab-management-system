# 启动器首次使用授权｜真实操作流程与发行包问题

## 真实根因

2026-11-14 用户截图显示「尚未授权」「工作室未登记」「实例未生成」「申请未提交」，六个授权操作均灰色。

核查前端、Rust 授权客户端及 QA 包资源，发现内部 QA 包的 trusted-issuers.json 配置为 ownerBaseUrl=null、issuers=[]：**没有可信 Owner 公钥，也没有在线 HTTPS 授权端点**。首次登记因此被安全门禁禁用；其他按钮需要注册的本机身份及申请编号。直接去掉禁用会造成后台拒绝或生成无法提交的孤儿安装密钥，不是修复。

## 发行者 / Owner 必须先完成

1. 在受控发行过程中，将官方 Owner 发行密钥对应的真实公钥、kid 和可从用户电脑访问的可信 HTTPS Owner 根地址一起装入发行包，由合法的软件所有者审核和签名。**不可通过用户粘贴或在线未认证的 issuer 接口引入新信任根。**
2. 如果工作室使用不同电脑，127.0.0.1:9444 指向的是使用者电脑，不是 Owner 的电脑。正式发行端点必须是具有可验证证书、真正可达的 HTTPS 域名；发布检查拒绝 localhost / 测试域名。
3. 正式发行必须启用强制许可证校验，完成源码/数据库/权限/撤销的门禁以及 Apple Developer ID 签名与公证。用户此前选择 Apple B（没有正式签名身份），仍只能 QA-only，不能以 ad-hoc 签名包冒充正式临床发行。
4. 20 用户、400 患者、2,000 次运动评估、5 GiB 已经确认，但有效 SaaS 计划、权限套餐 ID、Owner MFA、Core 和 HEP 身份链仍须单独审批及验收；配额确认不是授权成功。

## 工作室首次用户的正常流程

1. 获取软件所有者提供的**正式签名发行包**；安装后点击「首次登记安装身份」，输入工作室名称。本机生成 Ed25519 安装私钥、租户 UUID 和安装实例 UUID。私钥不应离开本机。
2. 点击「在线提交授权申请」，获得申请编号；申请尚待审批，不能因为得到编号就认为已经授权。
3. 软件所有者登录独立 Owner 控制台，通过真实 MFA 审核该申请，核对机构、权限套餐和许可资源后依法批准。
4. 工作室点击「在线获取或续签」，验证签名许可证及撤销状态证明。租约最长72小时，必须验证签名、租户、实例、时间和撤销状态。
5. 启动器和 Core 服务端都必须校验同一合法授权，随后才可使用生产业务。
6. 换机由 Owner 独立审批；离线许可只能由 Owner 正式签发，且必须匹配原安装身份。不得复制旧设备私钥或导入未知发行者公钥。

## 本轮已修复的 UI 行为

- 在缺少可信配置时明确说明原因，增加任何状态下都可以点击的「查看首次授权步骤」，点击后显示五步说明；5 秒自动刷新不会收起。
- 区分已有可信公钥但缺少在线 Owner 端点；前端收到后端的 ownerEndpointConfigured 状态后才能启用首次登记及在线提交。
- 不允许没有真实本机安装身份、可信发行者或申请编号时无提示地执行续签、撤销检查或离线证书导入。
- 「身份已创建但尚未提交申请」显示准确状态，不再直接提示「等待审批」。
- 通过 TypeScript/Vitest、独立 Chrome 合成 Tauri API 点击测试验证；不会真实提交机构、生成私钥、审批或签发。

## 方案 A 后续修复：自动引导的工程验证（2026-11-14）

- Mac1 的 `/Applications/康复管理系统内部版.app` 是旧版本，未包含上述帮助按钮；其打包信任资源也没有正式 Owner 公钥和 HTTPS 授权地址。源码或下载目录中的 QA 包不会自动更新已安装应用。
- 新候选将未激活用户的五步授权引导**默认展开**，始终显示当前步骤、责任人和无法继续的原因；用户手动收起/展开后，五秒刷新保留其选择。
- 各步骤只依据可信配置、本机安装身份、申请编号、本机验证的签名许可推断进度，**不把本机许可有效误当作 Core 服务端完成验收**；撤销、暂停、无效或过期状态显示专用处理说明。
- 正式安装包尚未配置前，首次登记仍被禁用；此变化仅为用户体验和安全状态说明，不改变 Owner MFA、许可证校验及签名门禁。
- 工程候选已通过 `pnpm build`（TypeScript 严格检查和 Vite 构建）、`pnpm test`（Vitest 9/9、维护与真实 Chrome 合成测试 10/10）及 `git diff --check`。测试不使用真实 Owner 凭据/患者数据/设备私钥。证据存于 `../../../validation-evidence/qa-20261114-first-install-guided-onboarding-v2/`（相对于本文档）。
- 只读正式 MVP 门禁返回 `ready=false`、`pass=0`、`notAccepted=182`，这是预期的禁止发行结果，**不能记为业务验收通过**。
- 未替换 `/Applications` 中现有 App；此轮没有重新构建 DMG，既有 `a00d4e4` QA 包也不包含本轮自动展开的界面变更。

## 当前停点

现有 QA 安装包仍没有可信公钥和 Owner 端点，无法用于正式首次授权。**修改源码并不代表已经更新了用户正在打开的 App**。真正可用的发行包仍须满足 Owner 信任配置、真实 MFA、生产 Core/HEP、安全验收与正式代码签名门禁。

本轮证据：Mac1 /Users/saber/my-project/rehab/validation-evidence/qa-20261114-first-install-license-onboarding/。低于8GiB可用磁盘时不应执行新的重型 Rust/Tauri 打包，也不得擅自删除业务、历史证据或正式安装。

## 方案 A：直接在现有 Mac1 执行只读发行预检（2026-11-14）

- 在现有服务运行状态下，执行 `owner-core-provisioning-preflight.mjs --live` 和 `hep-release-preflight.mjs --live`：两者均返回 `ready=false`（预期阻断）；Owner→Core 机器开通未启用、机器身份未核验、真实套餐/权限套餐未验证；Core 正式环境仍为 036，HEP 正式身份和 037 链路未就绪。
- 原 `approved-pilot-rollout-preflight.mjs --live-readonly` 只检查 `/Applications` 中 App 目录与 Info.plist 是否存在，会把尚无可信发行配置的旧 App 标为 `installedLauncherBundlePresent=true`，容易让验收者误解。
- 已新增独立门禁 `installedLauncherTrustConfigured`：只读检查已安装 App 内**打包的**信任配置文件，要求有效的发行商公钥编码与编号、无重复编号、合法 HTTPS Owner 站点根地址；缺失、格式错误、指向本机或不可信测试域名时拒绝。既不导入公钥，也不联系 Owner 或接触签发私钥。`installedLauncherBundlePresent` 继续如实表示文件存在，不再被当作具备授权能力的证明。
- 这只是**静态配置格式门禁**，不会证明密钥归属、Owner 站点真实可达、TLS 证书、公证签名、许可证签发或 Core 业务授权。原有 `formalInstallerGateReady`、Owner MFA、Core/HEP 身份及临床签核等仍是独立的强制门禁。当前 App 预期 `installedLauncherTrustConfigured=false`，`canScheduleFormalCutoverAfterAllGates=false`。
- 新增安装包缺失/空信任配置/HTTP 端点/保留测试域名/无效公钥/合成合法配置测试，并同步强化 `check-l3-release.mjs` 对 `.test`、`.invalid` 等保留域名的拒绝；旧有「所有门禁通过也不能由预检工具直接升级生产」断言仍保留。`node --test desktop/scripts/*.test.mjs` 全量通过 **120/120**；语法及 `git diff --check` 通过。现有源码的 L3 发行门禁仍正确拒绝未配置公钥与地址的正式发行。全程不进行真实审批、机构创建、生产数据库升级、患者数据读取或软件安装。
- 后续仍需软件所有者本人核验受控发行公钥与真实 HTTPS 域名，在符合发行及正式签名条件后打包；本人 Owner MFA 审批合法申请，并完成正式套餐、037/HEP 身份配置、182 项验收与 5 项临床真人签核。**不得通过直接编辑已安装 App 的 JSON、删除按钮禁用逻辑或绕过安全校验促成申请。**
