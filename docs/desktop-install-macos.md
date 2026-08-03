# macOS 安装与卸载

universal DMG 同时支持 Apple Silicon 和 Intel。下载
`rehab-management-system_1.0.0_macos-universal.dmg`；无签名测试构建文件名带 `_unsigned`。核对
`SHA256SUMS-macos.txt` 后打开 DMG，将应用拖入 Applications。

先安装并启动 Docker Desktop。启动器会检查应用包内 GUI 环境常见的 Docker Desktop 路径，
不依赖终端的 `.zshrc`、Homebrew 或开发工具。

未签名或未公证构建会被 Gatekeeper 警告，只供内部测试。正式版本必须使用 Developer ID
Application 签名并由 Apple notarization。不要通过关闭 Gatekeeper 作为正式部署方案。

卸载时退出启动器并从 Applications 删除应用。默认保留
`~/Library/Application Support/com.saberaltriayi.rehab/` 以及 Docker 命名卷。彻底删除
必须在应用仍可运行且 Docker 正常时，从危险区明确执行。
