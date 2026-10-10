# Rehab Management System · P0 L2 服务端许可证与员工席位工程交接

**状态：本地 Java/Node 安全实现，生产准入依然 NO-GO。**

本文件接续 `TENANT_LICENSE_L0_ARCHITECTURE_20261010.md` 与 `TENANT_LICENSING_AND_ACCOUNT_AUTH_PLAN_20261010.md`。所有工作始终在已有脏工作树中保留原始未提交改动，不合并运动评估 worktree，不访问/导入/改写真实患者数据和现有 Docker 业务卷。

## 1. 实际已实现的 L2 分层防护

| 边界 | 后端实现 |
| --- | --- |
| RHL1 合同 | `LicenseLeaseVerifier`：Java 8 兼容 BC Ed25519，严格 JSON/UTF-8/base64url、签名密钥匹配、租户与安装绑定、最长 72h、合同时间、撤销代际、席位值和模块清单 |
| RHR1 当前状态 | `LicenseStatusVerifier`：独立所有者签名的 ACTIVE/SUSPENDED/REVOKED/EXPIRED/REPLACED、UTC 时间、72h 截止、撤销代际；网络不可达时仅保留上次已验证证明，不自动放行 |
| 租户-实例绑定 | `TenantLicenseGuard`：可信公钥仅由部署安全配置预置，不信任 DB/客户端下载的未知公钥；DB 内租约 + 状态证明逐次验签；DB 缺失/异常、时间回滚、吊销、假冒租户均失败关闭 |
| 登录及受保护 API | `TenantLicenseEnforcementFilter`：Spring Security 和租户校验之后，对 admin/app API 包括 `@PermitAll`、普通登录、下载、报表等检查；只允许 OPTIONS 和精确登出清理 |
| 高风险功能范围 | `rehab` / `report` / `motion` 路由除了 Spring Security RBAC，还需要 owner 签名的授权单模块；模块不存在时拒绝访问 |
| OAuth2 | `OAuth2TokenServiceImpl`：访问令牌检查、刷新令牌重新签发、创建令牌的许可校验；当强制授权开启时每次校验停用员工身份，不信任旧 Redis Token |
| 员工席位 | `AdminUserServiceImpl`：创建、注册、重新启用、批量导入新增/重新启用员工全部预留席位；禁用释放席位、无限席位为**明确签名的 null**；MySQL InnoDB 同租户许可行 `FOR UPDATE` 串行化占位 |
| 后台任务 | `TenantJobAspect` 经 `TenantJobExecutionGuard` 在 `@TenantJob` 逐租户执行前查验签名许可；不再只依赖缓存的 tenant status |
| 签名许可证导入 | `TenantLicenseImportService`：**不提供公共 Web API**；在 L3 受保护的激活/管理器调用之前先验 RHL1 和 RHR1；owner 签名产生撤销代际及可信时间，禁止任意输入伪造高水位；已有绑定不得自动换租户/设备 |
| 在线撤销状态同步 | `TenantLicenseStatusSynchronizer`：可选受信任 HTTPS URL 每 5 分钟轮询、最多每批 50 租户，轮询状态逐租户签名/代际校验；断网后最长有效期届满即拒绝 |
| 轮换 | `yudao.license.trusted-public-keys` 支持运营者**预先固定多个** `kid=Ed25519PublicKeyBase64URL`，旧 key 保留到旧租约失效；不能将公开的 `/issuers` 返回数据直接当作信任根 |

L1 的 `OwnerLicensingService.issueStatusProof()` 和 `POST /license/v1/status` 为 L2 提供 RHR1 状态证明；在线/离线签发响应现附带 `statusProof`，L3 可将配对的 `token/statusProof` 提交给**内部**签名导入服务。没有 owner 私钥、患者资料或真实组织信息被提交到源码。

## 2. 默认关闭现有研发实例的安全约束

**现有部署不自动强制开启授权**：`yudao.license.enforce=false`（默认），避免尚未完成 L3 的激活以及数据备份/恢复路径就锁死原工作室。**这个默认值不是生产许可放行验收**；正式制品的 `tenant_license_authorization` P0 门禁必须检查 `enforce=true`、可信发布者公钥集、许可证绑定表和 API/后台任务 E2E 合成测试。

环境中的例示安全配置（仅参考语义，不是正式密钥/域名）：

```yaml
yudao:
  license:
    enforce: true
    trusted-public-keys: "owner-2026a=<owner-pinned-base64url>,owner-2026b=<next-pinned-base64url>"
    owner-status-url: "https://<trusted-owner-domain>/license/v1/status"
    status-poll-initial-delay-ms: 60000
    status-poll-delay-ms: 300000
```

若仅运营一个已预置发布者 key，可使用旧版 `issuer-kid` 和 `issuer-public-key-base64url` 配对字段。**发布私钥严禁进入 Java/Tauri/Docker 镜像或此配置；配置来源必须由独立受控运维提供**。离线服务器允许为空的 `owner-status-url`，但必须导入 owner 签名 RHR1，并在最多 72h 到期时失败关闭。

## 3. 数据库版本与上线门禁

`sql/mysql/rehab-tenant-license-v1.sql` 新增独立表 `rehab_tenant_license_binding`，包含现有 `tenant_id`、全球不可变 `tenant_uuid`、`installation_uuid`、签名许可证 `signed_lease`、签名状态证明 `status_proof`、`revocation_epoch`、`trusted_time_ms` 和更新时间，无患者字段。独立隔离 MySQL DDL/行锁测试使用网络隔离、临时存储容器验证；原始业务卷和患者数据库**从未执行这个 SQL**。

主线 `deploy/internal/migrations.manifest` 截至 023，独立动作评估工作树仍有 024–025 尚待整合。因此**不能猜测下一编号，也不能把此 SQL 直接追加/运行到现有主线迁移**。必须由 `source_integration` 负责人合并 024–025 后分配正式版本号、SHA256、升级/回滚/幂等用例，并在**合成空库及合成升级库**上进行发布迁移演练。

## 4. 保持 NO-GO 的风险和后续明确工作

1. **真实上线身份/密钥**：L1 依旧缺真正外部 KMS/HSM、真实 owner MFA 恢复、独立域名、正式 TLS/零信任边界及 WORM 审计锚定。L2 不能自行创建/签发正式软件所有者身份。
2. **Windows/macOS 激活 UI**：L3 需要强认证后接入 `TenantLicenseImportService`，并实现租户唯一绑定、换机受审、失效后备份/合规导出（不开放病历读写）、安全时间锚和错误恢复；目前不能把内部 bean 当成完整的终端激活功能。
3. **全路径与生产流量验收**：本阶段完成 HTTP 管理/应用入口、Token/刷新、`@TenantJob` 和已知模块 URL 的强制策略；非 `@TenantJob` 的定时任务、实际 WebSocket、第三方回调、下载流与异步任务仍需 L5 逐路由审查。未取得完整合成 HTTPS E2E 路由矩阵通过前，`account_license_enforcement` 不能标为发布 PASS。
4. **租户数据安全**：没有任何授权逻辑可以授权所有者查看患者资料；禁止为许可验证向 owner 服务器上传患者数据/音视频。原项目 `tenant_permission`、`patient_identity` 等门禁仍然有效，数据备份/导出路径未批准前不能直接对真实租户启用 fail-closed。
5. **威胁模型现实边界**：拥有运行容器和数据库 root 的客户可以修改应用/时钟/存储，软件层加密无法阻止完全控制主机的恶意修改；RHR1/RHL1 与 HTTPS 能降低误用和远程伪造风险，但无法证明 100% 本地主机防破解或离线实时撤销。

**结论：L2 工程代码与隔离回归是构建完成状态，L2 发布验收仍需正式迁移编号、受信任激活、完整跨设备 API/WebSocket/后台任务矩阵和部署安全配置。原 14 个阻断保持不变。**

## 5. L2 最终接续：Bridge 恢复后补齐跨语言 RHR1 验签

- Java 中 `LicenseStatusVerifierTest.node22OwnerSignedRhr1IsVerifiedByJava8BouncyCastle` 引入随机生成、只保留公钥和签名报文的**合成 Node.js Ed25519 fixture**，严格验证 Java 与实际 Node 签发协议的相互兼容；没有正式签名私钥、患者标识或业务账号。
- 单独定向核验 `LicenseStatusVerifierTest` **10/10 PASS**，Maven `BUILD SUCCESS`，日志：`/Users/saber/my-project/rehab/validation-evidence/2026-10-09/rehab-license-l2-crossruntime-rhr1-resumed-20261010.log`，SHA-256：`46dfde110985c856bccfc706c572ae761baaa05621bc3dab1903c006650100e8`。
- 先前完整模块回归 `rehab-license-l2-resume-java-full-final-v1-20261010.log` 为 **508 项、0 失败/错误、9 跳过**；L1/Node `rehab-license-l2-resume-node-full-final-v1-20261010.log` **81/81 PASS**；隔离 MySQL `rehab-license-l2-resume-mysql-final-v3-20261010.log` PASS，原有 Docker volumes 24→24、临时容器 0；只读发布校验 `ready=false`、`blockerCount=14`。这些是最终跨语言样例添加**之前**的历史回归，需要以本节之后的最终完整回归最新记录为准。
- 该阶段只完成本地 L2 安全构建及合成测试。**尚未宣称**真实发行许可强制生效或可生产部署；不得清除原发布阻断、擅自替换签名信任根、执行真实数据库迁移、开放公网端口、关闭患者隔离或触碰现有数据。

**2026-10-10 最终完整复验（覆盖此前所有临时测试记录）**：ShunCode Bridge 恢复后补充的 Node→Java RHR1 验签测试已纳入整个 Maven Reactor。实际完整结果为：

| 最终验证项目 | 结果与不可变证据 |
| --- | --- |
| Java `mvn -o -B -pl yudao-module-system -am test` | **509 tests、0 failures、0 errors、9 skipped，BUILD SUCCESS**；`rehab-license-l2-final-after-bridge-java-20261010.log`，SHA-256 `3dbb3a8fd52663fc54785ef9360da8025e9e8bdede5678c0880b39ffbc893c05` |
| Node `node --test desktop/license-control/*.test.mjs desktop/scripts/*.test.mjs` | **81/81 PASS**；`rehab-license-l2-final-after-bridge-node-20261010.log`，SHA-256 `2c6b15e256e222269c08d2cfefbe0b0f6d2adbcf4e16405ec371fcef3e6b1847` |
| 最新只读 release readiness | `ready=false`、`blockerCount=14`、预期退出码 1；`rehab-license-l2-final-after-bridge-readiness-20261010.json`，SHA-256 `f9376160773c1b42d4c283856cd7a68eb124648941334be47c20ef324ed8dca9` |
| 已验证的隔离 MySQL 8.4.10 行锁 | 第二事务观察到 1 个被占用席位，等待 2 秒，原卷 **24→24**，容器残留 **0**；`rehab-license-l2-resume-mysql-final-v3-20261010.log`，SHA-256 `bec0d701f1be1c325531b2e18ceb347c4a7e8b523b0c39191fed9c1bc4b0fd3e` |

上述全部证据均位于 Mac 的独立 `/Users/saber/my-project/rehab/validation-evidence/2026-10-09/` 目录。核验时 `master@3d778538a3` 未发生提交或推送，`git diff --check` 通过，Docker 卷仍 24，无临时 MySQL 或正式 9444 授权服务监听。**不得由本地 PASS 推断正式生产部署、密钥运营、跨互联网入口或全路由授权准入已通过。**
