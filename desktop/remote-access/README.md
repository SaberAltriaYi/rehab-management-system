# L6 私有零信任远程访问交接

当前仅提供 L6 本地安全策略校验与合成测试，正式远程网关尚未部署。默认拒绝外部访问。

## 目标架构

选用经过组织批准的 Tailscale 私有 tailnet 与 Serve；不得使用 Funnel 或将 Docker、MySQL、Redis、Java 内部端口、软件所有者控制台直接开放公网。浏览器通过正式 tailnet HTTPS FQDN 访问工作室本地 Nginx，原 Docker Compose 保持在 127.0.0.1:8443 绑定。

入网、账户和设备必须在 IdP/Tailscale 管理控制台实际配置：组织 SSO MFA、设备逐台审批和姿态、精确工作人员→指定工作室主机的 ACL、短会话有效期、审计和丢失设备撤销。业务端 Java 必须继续验证用户 OAuth2/RBAC、租户签名许可与患者数据权限。软件所有者许可证控制面不得接收任何患者文件或数据。

本目录中的 policy.mjs 检查配置意图和只读 tailnet/Serve 快照，它**不部署 VPN、不启动服务、不证明云端 MFA/ACL 已实际开启**。l6-policy.example.json 是示例而不是任何真实线上网络的证明；不得凭配置文件就发布。

部署前由具备组织管理权限的操作员依当前官方 Tailscale Serve 版本文档核实正确 CLI 命令，完成网络和身份审计后才启用 tailnet 私有 HTTPS 反向代理。切勿在本阶段自动执行 Serve/Funnel 命令。必须实测 Windows/macOS/iOS/Android、浏览器登录、设备吊销、远端 Token 注销、跨租户越权、网络失联、TLS 证书和日志保护。

本地命令：node --test desktop/remote-access/*.test.mjs

正式发布仍需 L0–L5 的原有所有门禁、合规恢复、KMS/HSM、真实所有者公钥、签名、公证和漏洞扫描；现阶段不能称作完整 L6 生产上线。
