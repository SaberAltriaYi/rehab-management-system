# 康复管理系统 Tauri v2 启动器

## 本工作室内部受控升级（2026-10-28）

用户已选择仅供工作室内部使用，不作商业发行。独立只读检查为
`node desktop/scripts/internal-controlled-readiness.mjs /absolute/path/internal-evidence.json`：
可以不要求商业代码签名，但仍须真实验证身份/权限、业务、数据迁移和恢复，
不能把内部证据交给正式发布流程冒充通过。原正式L3/签名门槛不变。

特别注意：本机现用Core、运动评估、HEP为原生多服务；本启动器管理的是Docker四服务。
两者并非天然同一数据库，不能仅替换.app/启动容器就宣称完成升级。
目标、验收和精确旧安装包清理范围见
[`internal-controlled-installation.md`](../../docs/execution/internal-controlled-installation.md)。
尚未验收时不删除现用程序、数据、备份或必要回滚版本。

## L3 工作室授权/首次激活（P0、本地开发，生产 NO-GO）

新增独立 Rust `src-tauri/src/license.rs` 与 Tauri 的工作室授权面板；为每台新安装生成私有 Ed25519 密钥和永久安装 UUID，支持在线申请/在线签名挑战续租、离线所有者签发 `.rehab-license` 导入、已签名 RHR1 撤销状态查询、最长 72 小时许可、到期告警，以及新机器请求所有者审核换机。**发行商可信公钥只能预置于已签名安装包，不会信任客户导入的临时公钥或公开 `/issuers` 目录**。当前随仓库的 `src-tauri/resources/license/trusted-issuers.json` 为空，以确保没有真实发行商运营密钥时不能激活。

正式发行版必须使用 `--features license-required`；这会在启动器和 Java 服务两端验证许可。Rust 生成不含安装私钥的只读 Java 激活清单；`TenantLicenseBootImporter` 启动时再验 RHL1/RHR1，旧 Token、无权租户不能借助 UI 控件绕过后端许可。当前兼容/开发模式默认关闭强制校验，以免未迁移的真实工作室被错误锁死。

**受控发行校验**：仓库根执行 `node desktop/scripts/check-l3-release.mjs`。它要求真实 owner HTTPS 域名/签名公钥、已编号哈希许可迁移，以及所有 Windows/macOS 打包都启用强制授权；当前预期失败。原有 `prepackage-readiness.mjs` 发布门禁额外保留。更多细节和剩余临床恢复操作见 `docs/releases/TENANT_LICENSE_L3_DESKTOP_ACTIVATION_20261010.md`。

强制授权构建在具备真实管理员重认证和独立审计之前，**从后端拒绝**旧版全店覆盖导入、整店迁出、修改管理员、全量删除。仍保留原有加密备份机制。请勿在 L5 的可合法迁出/恢复流程和安全批准完成前发版。

该目录只实现本机 Docker Compose 服务管理，不重复实现 Vue 康复业务页面。Rust 后端负责
Docker 检测、运行资源校验、配置、端口、TLS、健康状态、备份、整店迁移、管理员账号设置、
日志脱敏和单实例；WebView
提供中文控制界面。

管理员首次登录密码使用独立的 16 位随机生成器；MySQL、Redis 和备份口令使用彼此不同的
48 位基础设施密钥。登录接口仅为早期预览包生成的 48 位临时密码保留兼容，用户登录后必须
立即修改密码。

整店迁移使用单个 age-scrypt 加密 `.rehab-transfer` 包，包含完整业务数据库（含系统用户和
权限）与康复附件。目标设备导入前自动创建本机备份，再执行覆盖恢复；本机基础设施密码、
TLS、端口、日志、缓存和既有备份不进入迁移包。内置超级管理员设置仅允许修改租户 1、用户
ID 1，账号限制为 4–30 位安全字符，密码限制为 12–16 位，明文不写文件或日志。

## 开发检查

```bash
pnpm install --frozen-lockfile
pnpm test
pnpm build
pnpm cargo:test
```

`cargo:test` 使用可注入 command runner，不要求测试环境安装或运行 Docker。`tauri build`
前必须将完整的 `desktop/runtime/1.0.0/` 复制到
`src-tauri/resources/runtime/1.0.0/`；占位 README 只能满足单元测试编译，不能生成可交付
安装包。

平台构建：

```bash
pnpm tauri build --bundles nsis --target x86_64-pc-windows-msvc
pnpm tauri build --bundles dmg --target universal-apple-darwin
```

不要把运行时 `.env`、`secrets/`、TLS 私钥、备份、患者数据或签名文件复制进此目录。
生产安装包由 `.github/workflows/desktop-release.yml` 生成。
