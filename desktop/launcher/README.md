# 康复管理系统 Tauri v2 启动器

该目录只实现本机 Docker Compose 服务管理，不重复实现 Vue 康复业务页面。Rust 后端负责
Docker 检测、运行资源校验、配置、端口、TLS、健康状态、备份、日志脱敏和单实例；WebView
提供中文控制界面。

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
