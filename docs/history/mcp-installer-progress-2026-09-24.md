# 康复管理系统安装包进度（2026-09-24）

## 项目识别

`/Users/saber/Documents/playground` 是包含多份项目检出的工作目录，并非单一应用仓库。顶层 `README.md` 描述的是 Python/FastAPI 的“综合运动康复评估系统 v1.0.0”；桌面安装器和新 UniApp 客户端对应 `rehab-module-enable-clean` 下的 RuoYi Vue Pro 二次开发项目“运动康复评估与业务管理系统 V1.0”。该检出最新提交为 `49b3e40`（2026-09-24）。桌面启动器使用 Tauri v2；安装包携带 Spring Boot、Vue 管理端和 Docker Compose 运行资源，用户仍需安装 Docker Desktop。

## Windows 与 macOS 桌面包

**状态：已有内部测试预发布包；正式发行门禁仍未完成。**

- GitHub 当前最新桌面预发布标签为 [`desktop-v1.0.0-preview.3`](https://github.com/SaberAltriaYi/rehab-management-system/releases/tag/desktop-v1.0.0-preview.3)，发布于 2026-08-03，标记为 prerelease。
- 已上传 Windows x64 NSIS 安装器 `rehab-management-system_1.0.0_windows-x64_unsigned-setup.exe` 与 macOS Apple Silicon/Intel universal DMG `rehab-management-system_1.0.0_macos-universal_unsigned.dmg`，并提供 `BUILD-INFO` 与 `SHA256SUMS.txt`。
- 两包均为 unsigned：Windows 尚无 Authenticode 签名，macOS 尚无 Developer ID 签名、公证与 stapling。当前适合可信环境内部测试，不应视为可公开生产发行的安装包。
- 当前检出在 2026-09-24 仍有后续代码提交，但未发现晚于 2026-08-03 的桌面发布；需确认 preview.3 与当前源码的差异，并基于目标提交重新构建/验证。
- `docs/desktop-release-checklist.md` 的检查项仍全部未勾选，包含自动化检查、干净设备安装/更新/卸载回归、签名与公证、发布证据等。另一个旧检出 `rehab-management-desktop-packaging/README.md` 仍指向 preview.1；GitHub 已将 preview.1 标记为被后续版本取代，当前说明应以 preview.3 为准。

## UniApp 移动端

**状态：MVP 源码与平台编译目录已存在；尚无可分发的原生安装包/已发布小程序。**

- 客户端位于 `rehab-module-enable-clean/yudao-ui/yudao-ui-rehab-uniapp`，最新相关提交 `98a2fc4`（2026-09-24）。同一 Vue 3/UniApp 工程覆盖治疗师/管理员与患者侧。
- 工程提供 `build:h5`、`build:mp-weixin`、`build:app`；本机已有 `dist/build/h5`、`dist/build/mp-weixin`、`dist/build/app` 编译目录。App-Plus 编译目录不是 APK/IPA。
- 在客户端子目录未发现 `.apk`、`.aab`、`.ipa` 或 `.wgt`。README 明确指出交付的是源码，不是签名 APK/IPA；没有 App Store、Google Play 或微信小程序发布资产。
- 发布前置仍缺：DCloud AppID 与微信 AppID（manifest 中均为空）、面向目标设备的 HTTPS API 域名/合法微信 request 域名、原生包标识和 Android/iOS 签名材料。完成配置后仍需走 HBuilderX/云打包及平台验证/审核。
- **安全阻断项：**患者登录默认关闭；现有患者认证仅以手机号和患者编号匹配，需先改造独立身份验证、限频/防枚举和绑定流程并完成安全审查。移动端令牌尚未接入 iOS Keychain/Android Keystore。
- README 另记录 DCloud 插件将 Vite peer 固定在 5.2.8，依赖审计报告了 2 个高危 Vite 开发服务器 advisories（说明为不随发布产物打包）；不要把开发服务器暴露到不可信网络，兼容升级后需重跑 H5、微信和 App 构建及审计。

## 本次接续验证（2026-09-24）

- 康复模块后端在清理旧 `target` 后重新执行 `mvn -B -ntp -pl yudao-module-rehab -am clean test`：99 项测试通过，0 failures / errors。先前增量运行遇到 Surefire 找不到旧测试类；本次干净构建通过。
- JDK 17 隔离后端构建成功，`yudao-server/target/yudao-server.jar` SHA-256：`37c805e09b72bf250c7923df9696ed3f6386e331a59cf130d5d521f2192e43a3`。
- 脱敏数据库快照已重新生成，脚本完成敏感值扫描及空数据库恢复验证；`desktop/build/desktop-bootstrap.sql` SHA-256：`fde66e1e8658f358a838ec220c09e91af774b17d590e54576de7d22386392689`。
- 管理端生产依赖审计通过（未发现已知漏洞）。标准 `pnpm run build:internal` 在加载 ESLint 插件规则约 15 分钟、无构建产物后被软中断停止；这不是该构建的失败结论。
- 为诊断 bundle，曾在本机临时移除 Vite 的 build-time `EslintPlugin` 后重跑 internal build；shell trap 已恢复原文件，且 `git diff` 确认配置无残留改动。该诊断构建失败于 `src/layout/Layout.vue?vue&type=script&lang.tsx`：`TypeError: [vite:vue-jsx] unknown file: t.stringLiteral is not a function`（`@vitejs/plugin-vue-jsx@3.1.0`、`@vue/babel-plugin-jsx@1.2.5`、`@babel/core@7.26.0`）。独立 JSX 转换测试通过，但完整生产依赖图中的根因尚未定位；未改锁文件或留下构建绕过。
- 现存 `dist-internal` 仍为 2026-09-23 的旧产物，未用于生成 runtime；`desktop/runtime/1.0.0` 尚未生成或验证。
- UniApp 类型检查及 H5、微信小程序、App-Plus 编译此前均通过；生产依赖审计复核仍有 21 项（4 low、15 moderate、2 high），两项高危均经 DCloud 固定依赖链到 Vite。未做未经兼容验证的覆盖升级。
- 桌面启动器前端测试 5/5、构建通过；runtime-tools 单测复核 3/3 通过。`desktop/runtime/1.0.0` 尚待管理端官方内部构建成功后生成及完整性检查。
- 本轮未修改源码；Git 中只保留原有根目录 `.DS_Store` 改动。Rust/Cargo 仍未安装，因此 Tauri 原生测试/打包未运行；签名、公证、AppID/API 域名及患者认证安全决策仍是正式发布阻塞项。

## 下一步建议

1. 先定位完整生产图中的 `@vue/babel-plugin-jsx` `t.stringLiteral` 错误，并让未绕过 ESLint 的标准 `pnpm run build:internal` 成功完成。
2. 仅在官方内部构建通过后更新 `dist-internal`，运行 `REHAB_BUILD_COMMIT="$(git rev-parse HEAD)" node desktop/scripts/build-runtime.mjs` 与 `node desktop/scripts/check-runtime.mjs desktop/runtime/1.0.0`，再检查产物、源码差异及敏感信息。
3. 确认本次验收目标提交；基于该提交重建 Windows/macOS 预发布包，并在干净设备完成安装、启动、更新和卸载回归。
4. 取得并配置 Windows 代码签名证书、Apple Developer ID 与公证凭据；完成签名、公证、stapling、校验和及发布清单后再做正式发布。
5. 为 UniApp 确定 API 域名/AppID/包标识和签名所有者；先保持患者登录关闭。补完患者认证与令牌安全审查后，分别构建 Android/iOS 原生包，并完成微信小程序配置、预览、上传及审核。

## 依据

- `rehab-module-enable-clean/README.md`
- `rehab-module-enable-clean/docs/desktop-release-checklist.md`
- `rehab-module-enable-clean/yudao-ui/yudao-ui-rehab-uniapp/README.md`
- GitHub 公开 Release：`desktop-v1.0.0-preview.3`
