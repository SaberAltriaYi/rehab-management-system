# 康复管理系统 Tauri v2 启动器

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
