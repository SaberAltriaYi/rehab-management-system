# `test` 内部试点：Core 套餐与 Owner 机器开户受控准备

日期：2026-11-15（北京时间）。目标：复用 Mac1 现有 Owner/Core 服务，使**仅限合成数据**的已批准 `test` 试点在满足独立生产变更审批后能够创建 Rehab Core 账号。本文是操作准入检查及待执行方案，**不是上线授权、变更批准或已开户证明**。

## 一、已确认事实

| 检查对象 | 现场检查结果 | 判定 |
| --- | --- | --- |
| 试点 Owner 许可证 | `APPROVED / ACTIVE`，`seat_limit=20`、含 `rehab`，未绑定 Core；撤销代际 4 | 许可席位条件满足 |
| 已确认试点额度 | 20 用户 / 400 患者 / 2,000 次动作评估 / 5 GiB（5,368,709,120 B） | 不再重议、不降至旧 1 GiB |
| Core SaaS 计划 | `saas_plans` 0 条；同名试点计划 0 条 | 无可供开户的启用计划 |
| 租户权限套餐 | 1 条已启用且未删除，Owner 当前配置对应的套餐确实存在 | 保留现有权限包，不重新设计 |
| Core 机构开通 | `saas_provisioning_requests` 0 条；Owner `NOT_CONFIGURED` | 主系统机构账号未创建 |
| Owner→Core 通道 | HTTPS 端点匹配且可达；Owner 开关关闭、Core 开关关闭、机器令牌未通过双端一致性核验 | 禁止真实开户 |
| Core DB 迁移 | 账本 36 条、最新 `036`；Owner 机构 UUID / 许可修订结构 V3/V4 已存在 | 并不证明当前运行 JAR 包含最新安全修复 |
| 实际后端镜像 | `rehab-studio-internal-server:1.0.0`，容器 11 月 10 日启动，运行的 `/app/app.jar` 文件时间 10 月 29 日；未找到本轮匹配的可验证构建收据 | 与当前源码 `03f96e26c1` 的等价性**未证明**，不得盲目打开开关 |
| 历史 SaaS 平台入口 | `SaasPlatformController` 由 `yudao.legacy.saas-platform.enabled=true` 才启用；正式 DB `platform_admins` 启用记录 0 条 | 不能依赖旧平台管理入口创建本次计划，也不得临时放宽认证 |
| 传统部署迁移检查 | `migrate.sh verify-files` PASS；`migrate.sh status` 在当前代码目录因缺少 `deploy/internal/.env` 停止 | 未擅自复制或新造正式环境文件；已通过在线只读 SQL 分别核对账本 |

备注：`test` 当前许可证到期 **2028-01-01 17:56**、最大部署实例 **1**，与最初合同不同，是此前误操作留下的独立问题。本轮不静默恢复；如要改回 **2027-11-14 17:56 / 2 台**，须另行单独授权。

## 二、工程验证（全部没有向现有 Core 写入）

1. 使用真实 Core **表结构、不导出正式数据行**，在一次性 Docker MySQL 容器中成功演练试点唯一名称和 20/400/2000/5 GiB 配额。
2. 验证 `status=0` 的未启用计划、名称唯一约束、激活后严格匹配四项额度、再停用；真实 `saas_plans` 和 `saas_provisioning_requests` 保持 0 行。
3. 修复 `desktop/scripts/internal-pilot-selected-plan-isolated.sh` 未带可执行位导致文档中的直接运行命令报 `Permission denied`；补充零参数、未知参数、多余参数全部在访问 Docker 前拒绝的测试。执行入口：`desktop/scripts/internal-pilot-selected-plan-isolated.sh --synthetic-only`。
4. 以 JDK 17 运行 `SaasProvisioningIdempotencySafetyTest`、`SaasPlatformAuthorityTest`，**11/11 PASS**；验证源码层面的同一机构 UUID、幂等重试、20 席上限、失败错误脱敏和平台权限规则。这些测试没有验证目前正在运行的旧 JAR。
5. 本轮 Node 桌面脚本回归覆盖新增安全入口；正式医疗/临床业务 182 项验收、5 项真人临床签核、Core 037/HEP 联动及 Apple 正式发行均**未因此通过**。

## 三、分段正式实施路线（每个生产变更单独批准）

**阶段 C1：批准一次维护和备份。** 锁定目标为 `test`，冻结 Owner 许可 20 席；定位运行时真实 Compose 环境配置和密钥路径，不复制凭据到源码/证据；进行新鲜的 Core MySQL、附件、Owner DB/配置加密备份，验证清单签名、哈希及解密，并在隔离目标执行独立恢复演练。确认退回旧运行版本的方式和可用磁盘、受控维护窗口。*备份完整性校验不等于恢复演练。*

**阶段 C2：验证服务端发行物。** 对将部署的 Core 进行源提交—构建收据—镜像摘要绑定，核对 20 席上限、UUID 归属、幂等/失败重试、Token 未授权拒绝、租户隔离及 TLS 校验。候选必须通过 Maven、镜像及接口定向回归。仅有源码测试 PASS 不能证明旧运行容器具有这些修复。数据库 037 主要关联后续 HEP，**不要仅为这次主系统开户擅自执行 037**；先依据实际候选镜像声明的迁移门槛决定。

**阶段 C3：受控创建唯一 SaaS 试点计划。** 使用有审计、权限和备份保障的正式维护机制创建 `rehab-internal-pilot-20-400-2000-5gib`。必须先以**未启用**状态暂存（`status=0`），核对唯一性和四项数值，再由有权人员批准启用，并将真实生成的 plan ID 和既存权限套餐 ID 绑定至 Owner 私有配置。当前旧平台 API 默认可能不启用且无 Platform Admin，**不得为了建计划开放它、添加未批准的管理员或绕过权限直接手工插入生产行**。如缺少受控维护机制，本阶段继续 NO-GO，而非猜测 plan ID。

**阶段 C4：受控启用机器开户链路。** 确认 Core HTTPS 和服务端发行物，使用专用凭据管理将 Owner/Core 两端机器令牌一致配置（至少 24 字符，绝不输出令牌）。先启用并验证 Core 的机器端接口：缺少/错误 Token 必须拒绝；只有带正确身份且与已启用 SaaS 套餐及权限包一致才通过只读前置检查。其后才允许打开 Owner 一侧开通开关。原有强制 MFA、HTTPS、主机来源检查、租户隔离均保持不变。

**阶段 C5：唯一机构开户及验收。** 由软件所有者本人在 Owner 控制台按已审批的 `test` 申请 MFA 确认，使用一次性合成管理员凭据开通一次。按 Owner UUID、Core tenant ID、有效席位 20、权限套餐、幂等键、真实登录、最小权限、跨机构访问拒绝、撤销与续签同步逐项验收。不能复用待审申请、批量导入历史三条 ACTIVE 旧映射，也不能导入真实患者资料。

**回滚规则。** 任一阶段不通过时暂停后续开通，关闭相应开关；未使用的试点计划优先停用而不是删除历史记录。真实机构或订阅一旦创建，不得直接删库“回滚”；需核对幂等、业务绑定、审计和恢复影响，在维护窗口执行经审批的恢复方案。不可使用 `docker compose down -v`。任何实际生产变更与回滚均应留存独立审计及通过/失败证据。

## 四、当前准入和授权界线

当前 **READY=false、NO-GO**。`test` 20 席授权已完成，不需重签。下一项真正需要单独审批的是 **C1 正式系统新鲜加密备份与独立恢复演练**，然后才有 C2/C3 的运行版本和计划创建变更。当前没有授权自行创建启用 SaaS 计划、启用机器令牌、升级服务、发放管理员密码或生产临床使用许可。

现有可重复只读检查：

```bash
node desktop/scripts/owner-core-provisioning-preflight.mjs --live
node desktop/scripts/owner-internal-pilot-readiness.mjs --live-readonly
node desktop/scripts/internal-pilot-plan-preview.mjs --selected-quota-draft
```

返回退出码 2 且标记缺少计划/机器身份是安全门禁的正确行为；不得为了“变绿”把缺口伪造为已通过。
