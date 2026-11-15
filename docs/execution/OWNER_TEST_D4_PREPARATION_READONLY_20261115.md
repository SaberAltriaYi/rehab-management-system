# D4 准备：`test` 权限边界、额度激活和 Owner→Core 机器身份（2026-11-15）

**工作授权**：用户选择 A，要求先做**只读检查、隔离测试和可执行准备方案**；明确**不直接启用 D3 计划 2、D2 套餐 113、Owner/Core 机器通道或 `test` 机构开户**。本报告是准备阶段的证据，绝不构成生产写入、实名临床、HEP 或正式发布许可。

## 1. 正式现场只读基线

| 项目 | 核验结果 | D4 影响 |
|---|---|---|
| 指定 Owner `test` | `APPROVED / ACTIVE / 20 席 / NOT_CONFIGURED` | 有合法许可证，但无 Core 机构/管理员 |
| Core 运行镜像 | `sha256:b0f26de5e12d6cddd8a5fd2a0b1e7e000af6aa6e6f207da05c5ab0b798fca660`，healthy | C2 候选运行且版本锁定，不需为 D4 重做 036 迁移 |
| Core 账本 / 首次开户 | `036:36` / `saas_provisioning_requests=0` | 未迁移 HEP 037，也尚无正式开户事务 |
| 计划 **2** | `20 / 400 / 2000 / 5368709120`，**`status=0` = 停用** | 正式激活前不能调用 `SaasProvisioningService.maxUsers` 通过套餐验证（该服务要求 `status=1`） |
| 套餐 **113** | 全部 **274** 项现行启用菜单，**`status=1` = 停用，未绑定** | `TenantPackageServiceImpl.validTenantPackage` 会拒绝停用包；不能直接拿它开户 |
| 旧套餐 **111** | 已启用，257 菜单，未修改 | 不等于经批准的 `test` 全量康复套餐，不得继续误选 |
| Owner 配置 | HTTPS 目标是正确的本地 Core，当前仍引用**旧计划 1、套餐 111**；开通开关 **false** | 目标地址本身没问题，但指向无效业务选项，尚未准备开户 |
| Core 机器通道 | `YUDAO_PROVISIONING_ENABLED` 未启用；`YUDAO_PROVISIONING_TOKEN` 未配置成有效令牌 | 必须先经单独授权配置 Core，再考虑 Owner 启用 |
| 机器令牌 | Owner 本地私有令牌长度符合最低要求，但 Core 端未匹配 | 仅核查是否存在/是否匹配，不读取、输出或迁移原始秘密 |
| TLS | curl HTTPS 200 且证书验证成功；**Node.js 默认不信任 Core 自签名证书** | 不能靠“端口可达”判定机器通信可用 |

本次所有 Core/Owner/SaaS 元数据检查均为只读，**没有任何现网许可证、套餐、计划、账号、Token、HEP 私钥或服务状态变更**。

## 2. 套餐 113：订购“全部功能”不等于给每位管理员“全部权限”

正式 `system_tenant_package.menu_ids` SHA-256：`c9c535f94e754f439009d24ac8102d0e5249d4171e63c4cd8c692409a3b394d4`。274 项完整、均为现行启用菜单（不含 753 个停用菜单，不含未执行的 HEP 037）。按菜单**权限前缀**精确核查：康复业务 **74**、系统管理 **92**、基础设施 **55**、导航 **53**。

以下**全部位于套餐 113 内**，列为不允许未审查就分配给试点新机构 `tenant_admin` 的高风险示例：

| 权限领域 | 实际菜单 ID / 权限串 | 风险与审查结论 |
|---|---|---|
| 租户管理 | `1140 system:tenant:create`、`1142 system:tenant:delete`、`1230 system:tenant:owner` | 跨机构/平台级功能；部分 API 另有 `tenantOwnerAccess` 校验，但不能依赖这一局部限制代替统一授权 |
| 套餐管理 | `1227 system:tenant-package:create`、`1228 system:tenant-package:update` | 不应授予普通工作室管理员创建/更改 SaaS 授权的能力 |
| 角色与权限授予 | `1009 system:role:create`、`1011 system:role:delete`、`1063 system:permission:assign-role-menu`、`1065 system:permission:assign-user-role` | 存在权限放大/角色重分配风险，应独立审批和审计 |
| 基础设施操作 | `1033 infra:config:update`、`1075 infra:job:trigger`、`1258 infra:data-source-config:update` | 系统配置、计划任务和数据源控制不属于一般治疗业务 |
| 临床高权限 | `9305 rehab:report:approve`、`9714 rehab:motion:review`、`9895 rehab:patient:data-governance` | 报告批准、评估审核和患者治理必须受角色、资质、审计和人工临床审核约束 |

**源码关键事实**：`TenantServiceImpl#createTenant` 会调用 `createRole(tenantPackage)`，随后 `permissionService.assignRoleMenu(roleId, tenantPackage.getMenuIds())`，新管理员获得所选套餐的**全部菜单 ID**。因此若直接把 **113** 激活并用于 `test` 首次开户，默认 `tenant_admin` 将接收**全部 274 项**，并不是“订阅可用、管理员权限自动最小化”。虽然 `TenantOwnerAccess.isOwner()` 在部分平台级 API 上还有独立角色/租户判断，不能据此认定所有系统或临床接口都不会越权。

**D4 安全决策**：保留 113 作为“可订购功能目录”，**不要自动激活和绑定**；先以独立代码变更/隔离测试实现“订阅菜单上限 ≠ 首次管理员授予权限”的拆分，建立机构管理员、治疗师、临床审核人和平台拥有者的明确授权模板。首次合成试点仍以原 `[9000,9700,9710]` 三菜单只读验收范围作为安全起点；任何临床签发须有真人审核机制。HEP 037 独立审批后再规划。

## 3. 计划 2 激活的安全条件（**未执行**）

必须取得针对计划 **2** 的独立激活授权，再以经审计的维护/管理机制，将 `saas_plans.status` 从 **0** 改为 **1**。变更前应核实四个额度、唯一名称、`saas_provisioning_requests=0`、Owner 指定机构仍未绑定，备份新鲜且可在隔离环境恢复。激活后需用真实只读 SQL 验证 `id=2,status=1` 且四个值精确，并保证 `test` 与其他机构仍未创建/变更。

目前没有证据证明 400 患者、2000 次评估、5 GiB 存储在**所有**业务写入路径均有不可绕过的扣减与拒超额逻辑；未通过专项配额强制校验前不得开放这些写入口。计划字段正确不等于临床资源限制已可用。

## 4. Owner→Core 机器身份及 TLS 可信方案

**真实来源代码与接口约束**：

- Owner：`desktop/license-control/service.mjs`，私有变量 `REHAB_MAIN_PROVISIONING_ENABLED`、`REHAB_MAIN_PROVISIONING_URL`、`REHAB_MAIN_PROVISIONING_TOKEN`、`REHAB_MAIN_PROVISIONING_PLAN_ID`、`REHAB_MAIN_PROVISIONING_PACKAGE_ID`。当前 URL 已匹配 `https://127.0.0.1:18443`，计划/套餐仍旧值 **1/111**；只有经批准且业务角色门禁通过才可改为实际的 **2 / 经审核套餐 ID**。
- Core：`yudao.provisioning.enabled` → `YUDAO_PROVISIONING_ENABLED`、`yudao.provisioning.token` → `YUDAO_PROVISIONING_TOKEN`。接口为 `POST /admin-api/system/saas/internal/provisioning` 及 `POST /admin-api/system/saas/internal/entitlements/sync`，头 `X-Rehab-Provisioning-Token`，Token 最少 24 字符；错误 Token 和无 Token 在 C2 隔离 HTTP 中返回 **HTTP 401**，错误 UUID 返回 **HTTP 400**，每份开户申请受幂等键、UUID 绑定和 20 席约束。
- 机器配置建议：先经独立 D4 授权固定 Owner=false，安全配置 Core 受限私有令牌来源与必要的非 HEP 专用 Compose source-pinned 覆盖；以长度足够的同一机器秘密验证双方匹配，严格 HTTPS，避免任何日志/源代码/聊天输出令牌；再审查 Core 机器接口的启用与暴露网络范围；只有全部阻断清除并经额外授权才单独开放 Owner 侧开关。Owner MFA 仍留到 D5 本人执行。

**证书验证发现与修复验证**：

- Mac 本机 Core HTTPS 证书为自签名，SAN 包含 `127.0.0.1` 和 `localhost`，其**公开证书**的 SHA-256 指纹为 `65:98:D4:52:48:16:6F:A5:EA:FF:F5:06:C5:0E:61:3A:DF:35:CF:6C:58:6C:58:9D:B1:53:07:9E:01:D7:02:2A`。
- `curl` 的 TLS 验证成功，但本机 Node.js v22.15 默认 `fetch` 失败，错误类为 `DEPTH_ZERO_SELF_SIGNED_CERT`；`--use-system-ca` 也失败。测试进程显式设置 `NODE_EXTRA_CA_CERTS` 指向已提取、受权限保护的 Core **公开证书**后，以普通 HTTPS GET `/login` 成功返回 **200**。这是**未持久配置**的可信根测试，未使用 `NODE_TLS_REJECT_UNAUTHORIZED=0`、`curl -k` 或任何私钥；未来证书轮换时需重新核对公开指纹并更新信任锚。
- 当前 Launcher `core-identity.override.yml` 同时要求**HEP JWT 私钥挂载**，其中 `REHAB_HEP_JWT_HOST_FILE` 为必需参数，不能为启用 Owner/Core 而顺便导入/启用 HEP 密钥。Mac1 私有目录已制作**仅包含 `YUDAO_PROVISIONING_ENABLED` 与 `YUDAO_PROVISIONING_TOKEN` 两项**的审核草案；用现有 Compose 基础文件执行 `docker compose config`，逐字段比较证明**只改变 `server.environment`，默认关闭、令牌为空、无 HEP 挂载、无其他服务/卷/网络变更**。草案**没有安装到 Launcher 或现网 Core**。未来需要经单独批准把此能力受源码钉住并测试安装。

**需要特别避免的误判**：原只读 `owner-core-provisioning-preflight.mjs` 只检查套餐 ID 存在且未删除，却未检查是否停用，同时只验证 TCP 可达，没有验证 Node.js 对 Core 证书的 TLS 信任。这两个预检缺口已经**仅在仓库源码中修复**：现在查询 `system_tenant_package.status=0 AND deleted=0`，并用不携带机器令牌的本机 `/login` GET 校验 Node.js TLS；错误配置会安全返回 NO-GO。**没有修改正在运行的 Owner 服务或 Core 生产配置**。

## 5. 隔离与回归实测

| 检查 | 结果 |
|---|---|
| C2 独立 Docker 内网、tmpfs MySQL/Redis/Core、随机 Token/机构双租户 HTTP 端到端 | **58/58 PASS**（令牌、幂等、UUID、20 席、双租户 OAuth 和跨租户拒绝） |
| 配额计划激活/停用/唯一名称测试，隔离 MySQL | **PASS**，正式计划 2 未变 |
| Owner-only Compose 覆盖草案逐字段静态验收 | **PASS**，无网络、卷、HEP 私钥或其他服务修改 |
| Node 默认 TLS 与显式公钥信任对照 | 默认失败、加载公开 CA 后 HTTPS 200；错误路径不会被预检误报成功 |
| 新增 `status=0` 权限套餐门禁、有效 ID 拦截和 Node TLS 门禁的自动测试 | **PASS** |
| `node --test desktop/scripts/*.test.mjs` | **133/133 PASS**，0 失败、0 跳过 |
| `deploy/internal/migrate.sh verify-files` | **PASS**，迁移文件及固定 SHA-256 未更改 |
| D4 正式数据库、Owner `.env`、运行容器、计划、套餐、角色/机构 | **没有写入或激活**。隔离容器/网络全部清理 |

## 6. 交付物及下一步独立授权顺序

- Git 仓库报告：`docs/execution/OWNER_TEST_D4_PREPARATION_READONLY_20261115.md`。
- 只读预检安全补强：`desktop/scripts/owner-core-provisioning-preflight.mjs` 与对应测试。它只是门禁检查，**不是自动审批器**。
- Mac1 私有证据：`/Users/saber/my-project/rehab/validation-evidence/qa-20261115-d4-preparation/`，其中包含 Core 公开 CA 测试副本与 **review-only** Owner/Core 专用覆盖模板；它们没有加载到运行服务。
- 安全顺序：**① 先拆分 113 的功能目录与 `tenant_admin` 实际授予权限，并做合成权限回归；② 再完成不依赖 HEP 私钥的可信机器配置机制、证书与回滚包；③ 独立批准计划 2 激活和经审核套餐的启用/关联；④ 独立批准 Core 机器接口配置及启用；⑤ 最后另行批准 Owner 侧启用，并由用户在 D5 通过 MFA 执行唯一机构开户。**

**本轮结论：D4 准备与隔离测试完成，正式 Owner→Core 仍为 NO-GO。** 禁止因这份报告、58 项测试或 TLS 测试成功就把任何配置开关视为已获自动启用授权。

## 2026-11-15 D4-A 角色权限分离完成

用户随后明确选择 A，授权**套餐 113 功能目录与机构角色权限分离的源码修改及隔离测试**，仍禁止激活计划 2、套餐 113、Owner/Core 机器通道或创建机构账号。已完成 `tenant_admin`、`tenant_rehab_therapist`、`tenant_rehab_clinical_reviewer` 三种最小权限模板；首次开户只给管理员分配 `tenant_admin`，治疗师由机构管理员通过受限接口分配，临床审核人需由系统拥有者在专业资质核验后单独授权。历史 `rehab_therapist` 自定义角色保持不变，新模板使用独立编码，避免覆盖系统租户存量角色；新治疗师邀请只绑定新模板。套餐菜单或启用状态变化时会重新应用模板，自定义角色继续与套餐菜单求交集。按当前 274 项目录计算，三角色获得 35/46/18 项菜单；角色数据范围、患者分配过滤、临床审批/签署权限和动作人工输入权限均已纳入回归测试。后端全量 Maven、前端类型检查/构建/单元测试均通过。详细实现、菜单边界和限制见 [D4-A 角色权限分离报告](OWNER_TEST_D4_ROLE_PERMISSION_SEPARATION_20261115.md)。
