# Rehab SaaS Client DEV

独立 SaaS 浏览器连接客户端；不是 Enterprise 离线安装器，也不包含数据库或服务端。
用户只填写受信任 HTTPS 平台地址，通过系统浏览器登录。机构、角色、订阅和正式签署都由服务器校验。
不要求许可证、Ed25519、TOTP 激活；不保存登录令牌或密码，不跳过 TLS 验证。

当前仅提供未签名/未公证开发版，禁止宣称正式临床发行。
原生构建使用 Python 3.11.14、uv.lock 和 PyInstaller；Windows 需要 Inno Setup 6。

```bash
uv sync --project desktop/saas-client --locked --all-groups
uv run --project desktop/saas-client --locked pytest -q desktop/saas-client/tests
uv run --project desktop/saas-client --locked python desktop/saas-client/build.py --output /new/output
uv run --project desktop/saas-client --locked playwright install chromium
uv run --project desktop/saas-client --locked python desktop/saas-client/validate_installer.py --package-dir /new/output
```

测试仅用临时目录及 clinic.invalid，不代表已完成 SaaS 服务端临床验收。--handoff 仅供显式无浏览器测试模式，生成一次性短时启动文件，不输出长期凭据。
