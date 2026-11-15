# D1｜Mac1 现有 Rehab Core 受控替换：执行与验收（2026-11-15）

**审批范围**：用户在 D0–D1 受控准备后选择 `A`，明确授权 **D1 当前 Core 后端一次受控镜像替换/必要短暂重启**。本次授权 **不包括** Core SaaS 计划或权限包创建、Owner/Core 机器通道启用、`test` 开户、数据库 037 迁移、现有 HEP 接入或真实临床使用。

## 1. 实际执行了什么

- 在服务变更前于 2026-11-15 13:50（北京时间）生成新的 **20 项 Owner + Core AES-256-GCM 加密备份**，目录 `/Users/saber/my-project/rehab-runtime/backups/c1-owner-core-20261115T055040Z-b2913d39`。清单 HMAC 与全部 AEAD 均验真，并在**无网络、无业务卷**的一次性容器中成功恢复 Owner SQLite、Core MySQL 036、Core 附件。临时容器和恢复明文已清理。密钥独立保管，**未上传 GitHub**。
- 冻结、核验原始 Compose 文件 SHA-256 `14a0a333e0b52f76c6e6ab509005aa596dc0cb2965de352aa2c74aca522deddb`；没有修改已安装的 Launcher、原始 Compose 文件、运行资源签名/哈希清单、`.env` 或任何生产令牌。
- 原运行 Core 镜像：`sha256:065ce5fa65ee6467a68da56ecbfcb257bfc43ea0cfd4c32191f75b191427e809`，JAR SHA-256 `193061f8cef42870aa8f57a7dbeca500cb853e23fabc9e48e05c03fb336300e1`。
- D1 已核准 QA 候选：`rehab-c2-not-deployed:f3570d7b6db4`，镜像 ID `sha256:b0f26de5e12d6cddd8a5fd2a0b1e7e000af6aa6e6f207da05c5ab0b798fca660`，JAR SHA-256 `46503ed03d0e63dbbb37ed51c9b19d17be78037a365b0e07346ea45eabeea09c`，构建源提交 `f3570d7b6db4d7c4413bce0df20a4a5c266f0749`。C2 已在隔离双租户环境完成 58/58 HTTP 测试。
- 使用 Mac1 私有、精确到单服务的 Docker Compose 覆盖文件（仅一项 `services.server.image`），`--no-deps --no-build --pull never --force-recreate` **仅重建 Core `server` 容器**。MySQL、Redis、前端 Nginx 容器的原 ID、镜像及卷挂载均保持不变；Core 原数据卷与日志卷挂载保持不变。重建后仅对原有 Nginx 执行 `nginx -s reload`，以更新指向新 Core 容器的 DNS 解析，不重新创建前端容器。
- 私有受控执行器、覆盖配置和回执：`/Users/saber/my-project/rehab/validation-evidence/qa-20261115-d1-core-cutover/`。覆盖文件、执行器、JSON 收据均为仅本机私有文件；不会上传真实凭据或改写原始 Launcher 资源。

## 2. 实际验证通过

| 检查项 | 执行结果 |
|---|---|
| 新 Core 镜像 ID / 实际 JAR SHA-256 | **PASS**，运行容器与 C2 候选一致 |
| Core Spring Boot 健康检查 | **healthy** |
| 原 Admin、MySQL、Redis 容器 ID/卷挂载 | **完全一致**，没有重建 |
| Core 的原有数据、附件、日志卷挂载 | **完全一致**，未删卷、未恢复覆盖 |
| HTTPS 核验 | Owner 9444、Core 18443 静态登录页和代理 API 响应可用，TLS 校验 `0` |
| 不带机器 Token 的自动开户请求 | 返回业务错误码 `401` 且无 `data`，**没有成功开户**。注意当前 GlobalHandler 返回 HTTP `200`，不能将其误写成 HTTP `401` |
| 现有 `test` 许可证 | `APPROVED / ACTIVE / 20 席`，Core 仍 `NOT_CONFIGURED` |
| Core DB 账本 | **036:36**，没有迁移至 037 |
| 正式 `saas_plans` / `saas_provisioning_requests` | **0 / 0**，未创建计划/机构 |
| 正式 Owner 开通开关 | **false**，Core 机器开户启用环境项未设置 |
| 桌面脚本回归 | **131/131 PASS**，0 FAIL、0 SKIP |
| 迁移清单固定 SHA-256 | **PASS** |
| 备份恢复/回滚事前检查 | 20 对象加密备份验真和无网络隔离恢复 PASS；旧镜像保留、原 Compose 可用、回滚条件只读核验 PASS |

**未验证**：现有机构实际账号/真实管理员登录的完整交互、生产客户数据每一业务功能的人工验收、真实机器身份正确 Token + MFA 端到端开户、临床工作流/HEP/微信端、正式镜像签名发布。后续步骤不能因本轮服务健康通过就自动放开。

## 3. 本轮发现的长期稳定性限制（必须保留）

**已安装 Mac 启动器仍捆绑旧运行资源**：其 `LauncherService::start_inner` 会用基础 Compose（只包含旧服务镜像）执行 `docker compose up --detach --build`。因此，当前部署是**现运行容器已更新的 D1 QA 切换**，不是 Launcher 已可持续维护的完整发行升级。

- 当前 Launcher 的“**重启**”路径使用 Compose `restart`，不会重新构建镜像；但“**启动**”路径会 `up --build`，未来可能**重新构建并恢复旧 Core**。未来更新、重新安装运行资源或手工 Compose `up` 同样需要审慎对待。原安装文件的校验和绝不能通过手工改写 Compose/manifest 来绕过。
- 因此，在 D2/D3/D4/D5 的任何真实权限、计划或机器写入前，必须先实现/安装**受控的新运行资源与 Launcher 版本钉住切换机制**，或至少确认运行镜像 ID 与 D1 候选逐字相同。
- 试点只读预检 `desktop/scripts/owner-internal-pilot-readiness.mjs` 新增 `coreRuntimeCandidateImageVerified`（比对**实际运行容器**的 immutable image ID，不能只比镜像 tag）。若 Launcher Start 造成旧镜像回退，后续试点开通立即安全失败关闭。
- QA 镜像来自旧运行基础镜像 + 已验收 Java JAR 的受控替换，尚非带正式发行签名、完整依赖审计及经签署客户端资源更新的产品发行物，不可用于正式临床环境。

## 4. 回滚入口与风险

- **只读复核旧镜像和可恢复性**（已执行 PASS，**没有实际回滚**）：

```bash
python3 -B '/Users/saber/my-project/rehab/validation-evidence/qa-20261115-d1-core-cutover/d1-controlled-core-upgrade.py' --verify-rollback-ready
```

- **若将来在授权的维护/故障窗口需仅恢复原后端镜像**（**本轮未执行**）：

```bash
python3 -B '/Users/saber/my-project/rehab/validation-evidence/qa-20261115-d1-core-cutover/d1-controlled-core-upgrade.py' --rollback-d1
```

上述回滚仅通过官方原始 Compose 对 `server` 进行单服务重建、校验新旧 JAR/数据卷及健康，并热重载 Nginx。**不会**更改或回滚现有 MySQL 的数据行；D2–D5 一旦产生正式写入，不能只凭此命令完成双端事务回滚，必须另行授权并审查数据库兼容性。不能直接删除/覆盖真实租户表、病历或 Docker 卷，不得执行 `docker compose down -v`。

## 5. 下一阶段准入和交付

**D1 运行中的 Core 镜像替换与现场基础安全核验：通过。Launcher/Runtime 持久发行升级：尚未完成。** 正式 `test` 开户仍为 **NO-GO**，剩余业务门禁：

1. `effectiveCorePilotQuotaVerified`：正式 20/400/2000/5GiB 计划尚未创建/激活；
2. `corePermissionPackageHasExactPilotScope`：正式权限包不是严格的 `[9000,9700,9710]`；
3. `ownerCoreMachineChannelReady`：真实 Owner/Core 双向身份及开关尚未批准/启用。

除此之外必须避免当前 Launcher 旧资源的重新构建造成**版本回退**。推荐优先补上源代码/安装器一致版本的**安全可持续运行机制**，然后才考虑 D2 真实权限包、D3 真实 SaaS 计划，以及 D4/D5 机器通道及真实试点开户。每项均需要独立授权，不自动审批既有真实业务许可证和临床签发。
