# 独立云端 Owner 授权中心：待部署安全运行手册

**状态：部署准备已实现，生产 NO-GO。** 这里没有云账号、真实签名私钥、真实证书、生产数据库或患者资料。本目录模板不得未经审核直接启用。

## 目标网络

- 工作室设备通过独立 HTTPS 域名 __ACTIVATION_FQDN__ 访问 /license/v1/；Ed25519 设备持有证明及 Owner 人工批准是独立的安全要求。
- 所有者后台使用**不同**的 __OWNER_FQDN__，只开放 /owner/，同时受管理员专用 VPN 来源 ACL、TLS 客户端证书、密码 + TOTP MFA 和敏感操作重新验证保护。
- Nginx 仅将白名单路由代理到 loopback 127.0.0.1:9444 上的 Node TLS 服务，且使用验证过的内部 CA 检查 DNS:owner-upstream.internal 证书 SAN。默认 Owner 来源 ACL 拒绝任何远程 IP。公网不得暴露 9444、SSH、MySQL、Redis 或患者业务端口。
- 授权云仅保留工作室组织、安装实例、授权、审计数据。严禁传送患者病历、视频或诊断报告。L6 临床员工远程访问使用另外的受控 tailnet，与此 Owner 云隔离。

## 文件

| 文件 | 作用 |
| --- | --- |
| nginx.conf.template | 独立公开激活与 mTLS Owner 域名、上游 TLS 验证、限流、默认 404 |
| rehab-owner-admin-acl.conf | 管理员入口只允许 loopback，必须人工批准窄 VPN CIDR 才能放通 |
| rehab-owner.service.template | 独立低权限用户、loopback 网络沙箱、加密 systemd credentials、持久化 SQLite StateDirectory |
| preflight.mjs | 只读安全模板审查，故意维持生产 NO-GO |
| desktop/license-control/credentials.mjs | 严格模式从 CREDENTIALS_DIRECTORY 的权限受限文件加载 Vault key 和 TLS key；拒绝符号链接、宽松权限、模糊来源及 TLS 私钥与证书不匹配 |

## 密钥保管

1. **区分密钥**：Ed25519 签发私钥在 Owner SQLite 中由 AES-256-GCM Vault 密钥加密；32 字节 Vault 主密钥独立存储；工作室安装密钥仅留在工作室设备；外部 TLS 私钥不得复用任何授权密钥。
2. 运行单元要求 REHAB_OWNER_REQUIRE_CREDENTIALS=1。Vault 主密钥使用 systemd LoadCredentialEncrypted 传入；TLS 上游私钥亦使用加密 credential，公钥证书使用 LoadCredential。操作员在受控会话中按审核流程用 systemd-creds encrypt 创建密钥密文，不能把值传到 Shell 参数、普通 CI 变量或聊天。
3. **局限**：systemd 加密凭据和软件层 AES 不等于 KMS/HSM。当前 Owner 使用单实例 Node 22 实验性 node:sqlite；正式生产必须评估稳定数据库驱动、密钥不可导出/托管签名能力、双人审批与灾备恢复。云厂商选择后才能落实对应 KMS/HSM 适配和 IAM 权限。
4. 启用签发者之前先批准真实管理域名、Owner MFA、签名者公钥受信任分发以及私钥异地恢复策略。公钥经有签名的软件更新预置至正式客户端，绝不可只从公开 /license/v1/issuers 动态学习根信任。
5. 备份 SQLite 应使用一致性在线备份 API 而非粗暴复制在线 WAL 文件；备份加密送跨故障域不可变存储，并在完全隔离环境定期恢复演练。Vault 恢复材料与 DB 备份分权保存；RHA1 审计链锚点写入独立 WORM，不得只保存在同一台主机。
6. 签名密钥轮换采取**先向所有客户端可信分发新的验证公钥、后启用新签名密钥**的顺序。旧验证密钥保留至旧租约过期后才能退役；离线客户撤销不是实时的，单次租约最多 72 小时。

## 云厂商确认后的安全实施顺序

1. 选择独立云账号/项目、区域和数据保留边界；确定这两个真实 FQDN、DNS/TLS 证书自动续期、网络分段、运维 VPN、精确 CIDR、审计与合规要求。
2. 审核 firewall：仅让公网激活 API 通过 443；Owner 入口仍要求 VPN 限制 + mTLS。用系统安全日志与 provider telemetry 验证真实策略，不以 Nginx 模板代替云防火墙。
3. 锁定发行源码和供应链构建凭据，把只读代码安装在 /opt/rehab-owner；创建不可交互登录的 rehab-owner 用户，最小 IAM，仅写 /var/lib/rehab-owner，配置时间同步、补丁与不可篡改告警。
4. 在受控终端按多人保管流程建立 Vault 主密钥与内部 CA/证书，安装加密 systemd 凭据；验证文件权限、证书匹配、内部 DNS SAN、Nginx 配置与服务沙箱。**不得由研发脚本自行生成正式签发密钥。**
5. Owner 首次 bootstrap 必须通过受控交互 TTY，并由使用加密凭据的服务用户执行；把首次显示的 TOTP 种子安全登记到真实 MFA 器，不能进入日志。密码管理、TOTP 灾难恢复和账户锁定同样需要验收。
6. 受控管理员审批后建立真实 Ed25519 签发密钥，经可信分发渠道写入新正式客户端公钥配置。工作室激活前先合并 024–025 分支与许可证迁移编号并在零患者合成数据库测试。
7. 合成工作室云端 E2E：两机构独立申请与 Owner MFA 审批，有限/无限席位，在线 RHL1/RHR1，Rust/Java 双重验签，离线最长 72h，撤销代际、过期、伪造、换机、断网、时钟回拨、数据库一致性备份与恢复。
8. 经窗口审批后在真实 Windows/macOS 发行包、真实 TLS 客户端证书、浏览器与业务后端上重复测试，完成所有 P0 临床数据、安全与安装包门禁后方可正式运营。

## 本地只读验收与明确阻断

在仓库根运行：

    node --test desktop/license-control/credentials.test.mjs deploy/owner-cloud/preflight.test.mjs
    node deploy/owner-cloud/preflight.mjs

第一行仅使用合成凭据；第二行模板检查应显示 templatesValid=true、productionReady=false 并**故意退出码 1**，直到云账号、DNS/TLS、真实密钥保管、MFA、备份和 E2E 验收齐备。不要将此退出码改成 0 来冒充生产部署。

**仍待确认**：云厂商/账号区域、DNS 域名、KMS/HSM 方案、Owner 管理员接入 VPN/证书方案、合规/备份周期；本次没有访问任何云账号、启用公网、迁移生产库或签发真实工作室授权。
