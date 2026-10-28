# UniApp 移动客户端

统一 Vue 3 + UniApp 工程，为治疗师/管理员与患者提供同一套源码，目标平台为 H5、iOS/Android App-Plus 和微信小程序。项目对接仓库已有的 `/admin-api/app-admin` 与 `/app-api/app-patient` 接口。

## 当前 MVP

- 治疗师/管理员：滑块验证码登录、工作台摘要、我的患者列表与康复摘要、通知查看/已读。
- 患者：计划/注意事项摘要、今日任务完成标记与打卡、评估报告摘要、通知查看/已读。
- 访问范围和租户隔离由后端 API 控制；客户端不缓存患者业务记录，也不记录请求/响应内容。
- 这是一份可构建的客户端源码，不代表已生成签名 APK/IPA，也不代表微信小程序已审核或发布。

## 环境与开发

要求 Node.js `>=22.15.0 <23` 和 pnpm `10.15.1`，以 `package.json` 和锁文件为准。

```bash
cd yudao-ui/yudao-ui-rehab-uniapp
pnpm install --frozen-lockfile
cp .env.example .env.local
# 编辑 .env.local，配置可从目标设备访问的 API 地址
pnpm type-check
pnpm test:security
pnpm dev:h5
```

`.env.local` 示例：

```env
VITE_REHAB_API_ORIGIN=https://rehab.example.com
VITE_REHAB_CAPTCHA_ENABLED=true
VITE_REHAB_PATIENT_LOGIN_ENABLED=false
```

- H5 可留空 API origin 并使用同源反向代理；代理必须把 `/admin-api/` 和 `/app-api/` 转发到后端。
- H5 本地开发如需跨域代理，可在 `.env.local` 设置 `REHAB_DEV_PROXY_TARGET=https://<API 域名>` 并留空 `VITE_REHAB_API_ORIGIN`；Vite 会代理 `/admin-api` 与 `/app-api`。代理使用正常 TLS 校验，不要关闭证书校验。
- 原生 App 和微信小程序必须使用设备可访问的 HTTPS API origin，不能配置 `127.0.0.1` 或桌面设备的 localhost。
- 微信小程序还需在微信公众平台配置合法 request 域名及有效 TLS 证书；`src/manifest.json` 中的小程序 AppID 尚为空。
- `.env.local` 已加入忽略规则，不要提交真实域名之外的密钥、签名证书或访问令牌。

## 构建

```bash
pnpm type-check
pnpm build:h5
pnpm build:mp-weixin
pnpm build:app
```

- H5 输出到 `dist/build/h5/`。
- 微信小程序输出到 `dist/build/mp-weixin/`，之后用微信开发者工具预览/上传。
- App-Plus 客户端构建内容输出到 `dist/build/app/`；这不是可安装的 Android APK 或 iOS IPA。原生包仍需填写 DCloud AppID、原生包标识与平台签名资料，并通过 HBuilderX/云打包等流程完成。当前没有 App Store、Google Play 或微信小程序发布资产。
- `src/manifest.json` 有意保持 AppID 为空；Android 只声明网络所需权限，没有照搬模板中的无关权限。

## 认证与健康数据安全（发布阻断项）

**患者 UI 默认关闭，尚未获准面向真实患者开放。** 旧版“手机号 + `patient_no`”直接登录仍被服务层拒绝。隔离验收可设置 `VITE_REHAB_PATIENT_LOGIN_ENABLED=true`，使用机构邀请＋既有会员短信登录；首次绑定需邀请码，已绑定者再次短信登录不需要重用邀请。后端会核对登录会员/档案/绑定的手机号与启用状态，会员会话不等于患者授权。真实短信、限频/防枚举、历史令牌与监护人授权等仍须完成；前端开关不代替发布批准。详见根目录 `docs/patient-auth-release-gates.md`。

患者访问令牌仅保存在当前页面/进程内存，不写通用本地存储，不保存刷新令牌；刷新页面或重启后重新登录。旧版已持久化患者会话会清除。退出时先尝试服务端注销，无论网络是否正常都会清除本机凭据；网络故障会显示服务端注销未确认。H5 同源安全会话及原生 Keychain/Keystore 仍需正式评审。这些是 Core UniApp 的实现，不代表独立 HEP 患者壳已完成安全交接。

治疗师端登录使用用户名/密码与现有滑块验证码；滑块仅是自动化滥用防护，不等同多因素认证。当前会话令牌保存在 UniApp 私有存储中，但尚未接入 iOS Keychain / Android Keystore；公开发行前还需完成令牌安全存储、撤销/过期策略、隐私告知与完整威胁建模。`uniStatistics` 已关闭。请在授权的隔离测试环境使用虚构数据完成验证。
当前 DCloud 插件仍将 Vite peer 固定为 `5.2.8`。截至 2026-09-24，`pnpm audit --prod` 对 Vite 开发服务器报告 2 个高危 advisories；它们不随生成的 H5/App 发布产物打包。其余高危传递依赖已由 `package.json` 的 `pnpm.overrides` 固定到修补版本，勿移除覆盖或在未审计时解锁版本。在 DCloud 提供兼容的 Vite 修复并完成复测前，不得把开发服务器暴露给不可信网络；升级后须重跑 H5、微信小程序与 App 构建及依赖审计。

## 接口

- `POST /admin-api/app-admin/auth/login`，`GET /admin-api/app-admin/dashboard/summary`、`/patients/my-page`、`/patients/summary`、`/notifications/page`、`POST /notifications/read`。
- 会员短信：`POST /app-api/member/auth/send-sms-code`、`/sms-login`、`/logout`。
- 患者：`POST /app-api/app-patient/auth/bind`，`GET /app-api/app-patient/profile`、`/home/summary`、`/plan/current`、`/tasks/today`、`/reports/page`、`/notifications/page`，`POST /checkin/create` 与 `/notifications/read`（后述相对路径均加 `/app-api/app-patient`）。旧 `/auth/login` 仅为拒绝弱身份的兼容入口，不用于新客户端。
- 员工端验证码采用当前后端 `blockPuzzle` 契约；打卡提交当前计划 ID 及任务执行列表。
