# Windows 安装与卸载

支持 Windows x64。下载 `rehab-management-system_1.0.0_windows-x64-setup.exe`；未签名测试构建文件名
带 `_unsigned`。先用同一 artifact 中 `SHA256SUMS-windows.txt` 校验，再运行安装器。

安装器为当前用户创建开始菜单入口和标准卸载入口；NSIS 界面可选择桌面快捷方式。安装前
启动 Docker Desktop，并确保 WSL2 或 Hyper-V 后端按 Docker 官方要求正常。系统运行不需要
Git Bash、WSL 命令行、Homebrew、JDK、Maven、Node.js 或 pnpm。

未签名构建会触发 SmartScreen。它只供内部测试，不应绕过组织安全策略进行正式分发。正式
版本必须配置 Windows 代码签名证书。

从“设置 → 应用 → 已安装的应用”卸载程序。卸载默认保留 `%APPDATA%` 下的应用数据和四个
Docker 命名卷；重新安装可以继续使用。彻底删除必须在卸载前从启动器危险区明确执行。
