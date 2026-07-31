# Windows 签名与 macOS 公证

仓库和工作流不保存证书、私钥、密码或 Apple 凭据。缺少 Secrets 时 CI 仍构建带
`unsigned` 文件名的内部测试包。

## Windows

在 GitHub Actions Secrets 配置：

- `WINDOWS_CERTIFICATE`：代码签名 PFX 的 Base64；
- `WINDOWS_CERTIFICATE_PASSWORD`：PFX 密码；
- `WINDOWS_TIMESTAMP_URL`：可信 RFC 3161 时间戳服务。

工作流把证书临时导入 runner 当前用户证书库，读取 thumbprint 生成临时 Tauri 配置，签名
完成后 runner 销毁。证书不得提交。正式发布前在干净 Windows 机器验证 Authenticode
签名、时间戳、安装、更新和卸载。

## macOS

配置：

- `APPLE_CERTIFICATE`：Developer ID Application `.p12` 的 Base64；
- `APPLE_CERTIFICATE_PASSWORD`；
- `APPLE_SIGNING_IDENTITY`；
- `APPLE_API_ISSUER`：App Store Connect API issuer；
- `APPLE_API_KEY`：API key id；
- `APPLE_API_KEY_P8`：API 私钥正文。

工作流创建临时 keychain，并把 P8 写入 runner 临时目录后通过 `APPLE_API_KEY_PATH` 交给
Tauri。正式发布前必须验证：

```bash
codesign --verify --deep --strict --verbose=2 "/Applications/康复管理系统.app"
spctl --assess --type execute --verbose=2 "/Applications/康复管理系统.app"
xcrun stapler validate "/Applications/康复管理系统.app"
```

缺少任一公证凭据时不能把构建标记为正式已公证版本。
