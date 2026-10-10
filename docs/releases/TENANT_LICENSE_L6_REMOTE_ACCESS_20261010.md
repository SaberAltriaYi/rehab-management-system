# 康复管理系统 L6 安全远程访问与发行候选交接

日期：2026-10-10

## 阶段结论

**L6 本地策略实施及合成测试通过；远程身份、网络与客户端运营部署未完成，正式安装包不可发行（NO-GO）。**

远程网络仅考虑私有 tailnet，通过组织 SSO MFA、受管设备姿态/批准、精确员工 ACL、设备注销和会话审计实现独立的访问防线。Nginx 仍应只暴露 127.0.0.1:8443；禁止 Tailscale Funnel、HTTP 公网反向代理、把 MySQL/Redis/Java API 内部端口或许可证所有者服务暴露互联网。企业 VPN 与实际业务认证同时存在，不能让 VPN 身份绕过 Java RBAC、租户签名许可证或患者访问权限。

## 本地交付

- `desktop/remote-access/policy.mjs`：默认拒绝的远程授权策略、tailnet 健康状态、私有 HTTPS FQDN、Serve 状态的输入验证和风险检查。
- `desktop/remote-access/preflight.mjs`：仅执行只读的本机 Tailscale 状态查询，不接入未知远程主机、不修改网络配置、不启动服务；未安装 Tailscale 和未配置 Serve 时自动 NO-GO。
- `desktop/remote-access/l6-policy.example.json`：可审核的安全约束样例。**此文件本身不能证明 MFA/设备 ACL 在 IdP 已真实设置**。
- `desktop/remote-access/*.test.mjs`：6/6 合成安全测试通过，覆盖公网/未审核设备/无 MFA/无签名许可/数据泄漏/不安全端口、健康和状态输入的反例。
- `desktop/remote-access/README.md`：独立运营角色必须完成的部署、证据、撤销和跨客户端验收步骤。

### 对实时部署的准确声明

本次没有 Tailscale 管理帐号、私有 tailnet 与 ACL 审核证明，没有实际在外部 Windows/macOS/iOS/Android 设备连接访问工作室。L6 preflight 结果 `locallyConfigured:false,productionGo:false`，原本要求的真实 SSO MFA、丢失设备撤销、远端用户退出、TLS 与跨租户 UI E2E 均尚未完成。因此**不应将本地策略验证宣称为 L6 全部上线完成**。

## 历史版本隔离

保留原有 `desktop-v1.0.0-preview.1`–`.4`、`lan-v1.1.0-rc.1` 和其他 archive 标签，不删除旧制品。L6 源码使用独立研发候选分支，尚未构成新的正式软件发行版本。旧 1.0.0 安装包不带 L0–L6 新签名许可/远程接入能力，不得从 README 历史下载误认为新版。

## 生产 NO-GO 事实

原 P0 发行门禁仍为 14 项阻断。L3/商业签名发行另有 3 项阻断：正式 Ed25519 发行公钥未配置、正式 Owner HTTPS 服务未上线、许可迁移 SQL 没有正式编号并整合。Mac 当前没有任何有效的 Developer ID 代码签名身份，也没有可验证的 Apple 公证凭证；Windows 证书与 CI 发布资产的签名结果同样未收到。内网预检通过也不等于公网远程安全条件得到批准。

本机合成研发允许从 JDK17 隔离构建生成后端、内部 Vue、Desktop Runtime、Rust 测试及未签名**内部**实验制品，但禁止把该未签名、无法正式激活的构建发成正式测试版安装包。恢复合法患者数据、浏览器无障碍测试、真实设备及制品级 SBOM 和漏洞审计同属发布前置条件。

测试证据：`/Users/saber/my-project/rehab/validation-evidence/2026-10-09/` 下的 `rehab-l6-combined-policy-tests-v1-20261010.log`、`rehab-l6-remote-preflight-no-go-v1-20261010.json`、`rehab-l6-isolated-backend-build-v1-20261010.log`、`rehab-l6-runtime-build-v1-20261010.log`。其它 L0–L5 证据见 `TENANT_LICENSE_L5_E2E_RELEASE_GATES_20261010.md`。
