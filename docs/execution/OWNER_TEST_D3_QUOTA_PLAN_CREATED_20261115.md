# D3｜`test` 内部试点正式 Core SaaS 额度计划：受控建档验收（2026-11-15）

用户本轮明确授权：**为 `test` 创建唯一一条 20 用户、400 患者、2000 次评估、5 GiB 的正式 SaaS 额度计划；先加密备份、隔离恢复，再实施审计化创建；不更改套餐 113、Owner→Core 机器通道、Core 机构/账号，也不执行 HEP 037。**

## 1. 实际正式数据库写入

| 字段 | 实际值 |
|---|---|
| `saas_plans.id` | **2**（由 MySQL 实际返回，非预设） |
| `name` | `rehab-internal-pilot-20-400-2000-5gib` |
| `max_users` | **20** |
| `max_patients` | **400** |
| `motion_quota` | **2000** |
| `storage_quota` | **5368709120 字节（5 GiB）** |
| `status` | **0 = INACTIVE，待后续独立批准激活** |
| 创建时间 | **2026-11-15 19:23:21（UTC+8）** |
| 套餐关联/机构关联 | **无**；Owner 未更新任何计划 ID/套餐 ID |

`saas_plans` 当前只有 **1** 条记录、激活记录 **0** 条。此次创建没有授予患者/评估/存储业务操作权限，也**不能把配额字段登记等同于实际所有业务写入口均有限额拦截**。

## 2. 备份与安全门禁

- 在写入前于 2026-11-15 19:18（UTC+8）新建 **20 项 AES-256-GCM 加密 Owner/Core 备份**：`/Users/saber/my-project/rehab-runtime/backups/c1-owner-core-20261115T111811Z-c1df2e4d/`。
- 清单 HMAC、全部密文 AEAD 认证通过。已在不访问正式数据库的隔离环境中实际恢复 Owner SQLite、Core MySQL 和附件，结果全部 PASS；临时恢复容器已清理。
- 实际 Core 镜像 SHA 固定为 `sha256:b0f26de5e12d6cddd8a5fd2a0b1e7e000af6aa6e6f207da05c5ab0b798fca660`，运行健康；生产 Core DB 为 **036:36**。
- Owner `test` 仍 `APPROVED / ACTIVE / 20 席 / NOT_CONFIGURED`，`main_tenant_id` 为空。Owner 自动开通开关仍关闭；备份及本次操作均没有更改它。
- Core `system_tenant_package` 原套餐 **111** 仍 `status=0`、257 菜单、SHA-256 `a1ee57d06d4de796b29e8bb60593424f33fe98480d615ae4ef6899d16fcef478`；`test` 专属完整菜单套餐 **113** 仍 `status=1`（停用）、274 菜单、SHA-256 `c9c535f94e754f439009d24ac8102d0e5249d4171e63c4cd8c692409a3b394d4`。

## 3. 实际演练、创建方式及审计

1. 先用已有的 `internal-pilot-selected-plan-isolated.sh --synthetic-only` 在无网络、tmpfs MySQL 环境验证真实 `saas_plans` 表结构、额度值、数据库唯一名称约束及 `status=0→1→0` 状态语义；PASS。没有对正式计划表写入。
2. 再使用**本次与实际执行共用的事务 SQL**，在另一个完全隔离的 MySQL 8.4.10 上验证首次计划创建、`saas_platform_audit` 平台审计、`system_operate_log` 系统操作审计（两条审计均为 1），及重复执行零新增；全部 PASS，容器销毁。
3. 经用户授权，以本机专用维护身份在正式数据库中执行一次受控事务：冻结 036 迁移账本、既有套餐 111/113 的状态+菜单哈希、Owner 20 席及未开户状态、已有机构/用户/角色数、计划数为零、历史审计无同 trace 等条件；事务同时新增计划与两条维护审计。明确记录 `actor_user_id/user_id=0` 表示委托的系统维护，**没有冒充 Core OAuth Owner/MFA 会话**。
4. 正式返回新计划 ID **2**，`status=0`；两个审计检查各 **1** 条，trace `d3-20261115-test-quota-plan-stage`，平台 action `D3_QA_QUOTA_PLAN_STAGED`。重复执行在隔离环境已安全拒绝，**未在正式数据库重放写入**。
5. 实际只读复核：计划共 1 条、启用 0 条、Owner→Core 开户 0 条；Core 原机构 **1**、用户 **1**、角色 **3**，均保持原数量，原套餐 111/113 均未更改；所有 Core Docker 服务 healthy，Owner 9444/Core 18443 HTTPS 返回 200、TLS 验证 0。
6. 项目桌面脚本全套回归 **131/131 PASS**；已固定迁移文件 SHA-256 校验 PASS；D3 私有代码、执行收据、备份密钥、患者信息没有放到公开 Git 仓库。

## 4. 当前仍然 NO-GO：不能点击 Owner「开通主系统」

D3 完成的是**停用计划的正式建档**，不是 Owner/Core 自动开户配置。Owner 本地旧配置仍为计划 ID `1`、套餐 ID `111`、机器开通 `false`，且已创建的计划 ID **2** 当前为 `status=0`；完整权限套餐 **113** 也仍然停用。Owner 的“接口地址、内部令牌、计划 ID、套餐 ID 尚未配置”提示**此时依旧是正确拦截**。

只读试点门禁仍准确返回 `ready=false`、退出码 `2`，三个 blocker 分别为：`effectiveCorePilotQuotaVerified`、`corePermissionPackageHasExactPilotScope`、`ownerCoreMachineChannelReady`。原试点只读门禁要求的是仅 3 菜单 P0 权限，而用户后来选择全量菜单套餐 113；**不能直接把包含系统管理及临床签发的 274 项菜单全量授予机构管理员来绕过该门禁**。

## 5. 后续工作及审批边界

推荐下阶段先**单独审查套餐 113 的高权限角色范围**，为 `test` 明确可获得哪些权限；之后在独立授权下启用计划 2、选择经审查的套餐，安全配置 Core 和 Owner 的机器身份/令牌，最后由用户本人通过 Owner MFA 执行唯一 `test` 机构开户。每一步单独审批，绝不自动开启 D4/D5、执行 037 或导入真实患者资料。

所有患者数、动作评估次数、存储字节等资源配额，正式开放临床写入口前仍需逐项证明真实计数与拒超限调用已接入，不应仅依靠计划表配置。

## 6. 证据路径

- 私有 D3 事务/隔离演练/正式收据：`/Users/saber/my-project/rehab/validation-evidence/qa-20261115-d3-test-quota/`；收据 `d3-staged-plan-receipt.json` 权限 `0600`。
- 全新可隔离恢复的加密快照：`/Users/saber/my-project/rehab-runtime/backups/c1-owner-core-20261115T111811Z-c1df2e4d/`。
- 本仓库受控切换总计划：`docs/execution/OWNER_TEST_PILOT_CONTROLLED_CUTOVER_PACKAGE_20261115.md`。

**结论：D3 唯一正式额度计划 ID 2 已建档，停用且未绑定；两条审计已落库，D2 套餐/机构/角色/Owner 开关未变；正式开户继续 NO-GO。**
