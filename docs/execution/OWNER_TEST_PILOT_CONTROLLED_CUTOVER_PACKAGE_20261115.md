# `test` Owner→Core｜受控部署变更包（准备稿，未执行任何生产写入）

日期：2026-11-15。用户选择 A，授权**编制最小权限矩阵与受控变更方案**，没有授权实际升级、创建计划/套餐、激活机器身份或开户。目标是现有 Mac1 上 `test` 的 QA-only 首次使用，不包括真实患者、HEP 或正式签名公证发行。角色授权细节见 [最小权限矩阵](OWNER_TEST_PILOT_MINIMAL_PERMISSION_MATRIX_20261115.md)。

## 0. 冻结目标及事实证据

| 项目 | 唯一预期/已核验基线 | 当前状态 |
|---|---|---|
| Owner 申请 | `test`，`APPROVED`、`ACTIVE`、`rehab`、20 席；尚未 Core 绑定 | 核实，不能据此直接开户 |
| Core 正式镜像 | `sha256:065ce5fa65ee6467a68da56ecbfcb257bfc43ea0cfd4c32191f75b191427e809` | **旧镜像**，不含 C2 验证的全部开户安全修复 |
| Core C2 候选 | `rehab-c2-not-deployed:f3570d7b6db4`；镜像 ID `sha256:b0f26de5e12d6cddd8a5fd2a0b1e7e000af6aa6e6f207da05c5ab0b798fca660` | **本地 QA 候选**，已通过真实合成 HTTP 58/58，非正式签名发布 |
| 候选后端来源 | Git `f3570d7b6db4d7c4413bce0df20a4a5c266f0749`，JAR SHA-256 `46503ed03d0e63dbbb37ed51c9b19d17be78037a365b0e07346ea45eabeea09c` | 来源/嵌套安全类已验，仍需部署门禁 |
| DB 账本及应用结构 | 036/36，Owner UUID/SaaS V3/V4 表存在；HEP 037 未执行 | 037 不阻塞 P0 首次开户，不可擅自执行 |
| 正式 `saas_plans` / 开户 | 0 / 0 | 缺实际有效方案及 Core 机构账号 |
| 现有权限套餐 | `111`，JSON 257 项，已启用 `rehab:` 权限覆盖 0/88；有效在用机构 0 | **不可复用作试点，不可自动改写** |
| P0 权限草案 | 全新独立套餐，精确 `[9000,9700,9710]`；示例名称 `rehab-pilot-motion-readonly` | 已合成双租户验收 58/58，**正式套餐未创建** |
| SaaS 额度草案 | `rehab-internal-pilot-20-400-2000-5gib`，20/400/2000/5368709120，首存 `status=0` **待激活** | 额度经用户确认；仅隔离 DDL/唯一性/激活停用演练 PASS |
| C1 备份 | `/Users/saber/my-project/rehab-runtime/backups/c1-owner-core-20261115T023836Z-54fd4ba9` | AES-256-GCM 私有快照验证 PASS，独立 SQLite/MySQL/附件恢复 PASS；密钥不在备份目录 |
| Owner HTTPS | `https://127.0.0.1:18443` 指向现有 Core；Owner 控制台 9444 | TLS/健康核验已通过，真正机器开关仍关闭 |
| 历史绑定 | 3 条无对应 Core 的 Owner ACTIVE | 继续冻结，不自动重建/对账后重绑 |

**禁止把预先确认的 20 用户、400 患者、2,000 次评估、5 GiB 理解为“4 类资源写入已统一强制拦截”。** Core 的 SaaS 配额 `reserve` 框架已具备，但患者/动作评估/存储业务写入路径尚未验证调用；P0 不开放其写入口，后续 P1 必须增加真实业务配额的正反向测试并独立过门禁。

## 1. 阶段化实施顺序（逐项申请真实生产写入授权）

| 阶段 | 要求的独立批准 | 受控操作计划（当前一项都没有执行） | 验收条件与失败处理 |
|---|---|---|---|
| **D0：维护前冻结** | 只读预检 + 对 C1 后续新备份/维护窗口单独确认 | 冻结 Owner `test` 申请 UUID、最新撤销代际/期限与 20 席；冻结候选镜像 SHA、运行源版本；核查机器密钥**仅布尔比对不输出**；维护窗口开始前新鲜加密 Core SQL/附件和 Owner DB/配置备份，独立恢复，记录旧镜像/运行 Compose 摘要及回退门槛 | 任一项不通过则停止；C1 当前同机维护备份不能替代未来窗口的新鲜备份，且 C1 格式不能通过 Launcher `.age` 原生入口直接恢复 |
| **D1：Core 安全候选部署** | 单独批准**现有 Core 服务变更/短暂重启** | 从已核对摘要的本地 QA 镜像制订正式可追溯替换方案；优先使用 Launcher/受控维护流程，不直接覆盖容器 JAR 或编辑 Docker 内运行环境；保持原 MySQL、Redis、附件卷、TLS/端口、Owner flag=false、Core provisioning flag=false；此时**不运行 037** | 启动健康、正常登录/现有租户功能、默认机器接口关闭、源/JAR/镜像匹配和 036 账本无变化；失败时只在无新业务写入前按已审批方案恢复原镜像，不触碰 SQL/附件卷，不执行 `docker compose down -v` |
| **D2：独立 P0 权限包** | 单独批准新建**一条**严格范围的正式机构权限包 | 通过有权限、可审计的正式维护/管理接口建一个不同于 111 的权限包，名称需唯一且不超过 30 字，菜单 JSON **精确** `[9000,9700,9710]`，启用（`status=0`）且未删除；记录系统实际生成的 package ID，绝不沿用合成测试 ID `990111` | `JSON_LENGTH=3`、三菜单层级/状态一致，不继承其他 257 个系统菜单、不写原 111、不修改已有机构角色；无审计的管理接口/维护机制时继续 NO-GO，不使用任意生产 SQL 绕过授权 |
| **D3：SaaS 额度方案** | 单独批准新建/启用唯一试点计划（可拆为两次批准） | 通过受控审计写入机制创建 `rehab-internal-pilot-20-400-2000-5gib`，先保持 `status=0`，核查无同名重复、四项数值与 5GiB 字节准确；再经批准改为有效计划（`status=1`）；取系统生成 plan ID，并只在 Owner 私有环境关联此 plan ID 与 D2 的 package ID | 有且仅有 1 条符合四项额度的有效计划；切勿使用旧 `synthetic-beta` 10/100/1000/1GiB；未通过则计划维持未激活、Owner flag 不打开，保留审计，禁止删改生产历史 |
| **D4：先 Core 后 Owner 的机器身份** | 单独批准 Core 安全服务配置变更，Owner 开关再独立批准 | 仅以经核准的本机保密渠道传递 Owner→Core >=24 字符机器 Token、精确 HTTPS issuer 和必要只读挂载，不新生成或输出现有 Token；Core 开户端单独启用后验证无 Token/错 Token HTTP401、无未授权访问、正确 Token 仅合成请求可用；**Owner flag 在全程保持 false 直至全部前置通过** | TLS 严格校验不降级，机器身份一致，计划/权限包真实存在且 P0 精确；任一异常 Core flag 回退关闭且不开放 Owner，不能从登录浏览器提取 MFA/TOTP/Cookie 代替身份 |
| **D5：唯一 `test` 首次开户** | **用户本人**对指定已批准 `test` 申请完成真实 Owner MFA 并单独批准此次生产写入 | Owner 批准一次试点 P0 开通；在控制台临时凭据输入场景生成合成机构管理员，不将初始密码写聊天/源码/日志；只允许一个申请幂等键绑定一个 Owner UUID 与新生成 Core tenant ID；其他历史 ACTIVE 机构不触碰 | P0 管理员仅 3 菜单、20 席，`test` 实际登录及只读动作查询，其他租户访问被拒、未写患者业务数据，Owner/Core 单向和重复操作状态相符。失败冻结该申请及继续开通开关，走人工幂等/关联核对，**不得直接删除正式租户行“回滚”** |
| **D6：未来 P1/HEP** | 另行审批 037 与真实业务额度、角色、临床和 HEP 密钥/JWT 门禁 | 按经批准的 037 原 SQL（hash 固定）执行兼容迁移；现有租户 1 管理员 10 项/治疗师 8 项，其他租户 0 项；新 `test` 的治疗师角色另外创建、审查后授权，HEP 临床处方必须真人审核签发 | 新增 11 表、10 HEP 菜单、既有角色差异严格符合范围，所有临床与发行门禁通过；**不能因 D0–D5 通过而自动进入 D6** |

## 2. 实施前必须具有的可执行安全条件

1. **变更身份与项目固定**：当前 `test` 仅限 synthetic-only，Owner 申请唯一且未绑定 Core；不接受任意其他待审批机构充当试点。必要时由 Owner 本人再次核对实际申请，而不是从已登录浏览器读取 TOTP/会话。
2. **正式部署机制存在并已核验**：当前旧 Core Compose、Launcher 环境和新候选镜像需明确切换/回退接口、日志输出脱敏、运行端口与健康探针。旧容器不能仅改标签声称安全修复已生效。C2 候选是 QA 产物：正式发行另需可复现构建链、依赖安全审计、签名/发布门禁。
3. **备份恢复**：窗口前获取新鲜的、经认证的独立加密备份及 SQL+附件+Owner 一致性证明，保留 C1 原密钥和回滚工具；验证独立恢复并保留回滚指令给操作员。**恢复演练不等于已获在正式现场覆盖的批准**。不可删除旧数据库/卷、不可使用 `docker system prune` 或 `down -v`。
4. **审批单独列账**：D1 镜像升级、D2 权限包创建、D3 计划暂存与激活、D4 双侧机器启用、D5 单机构开户，每一步是单独真实变更。用户仅回复前一阶段“A”，不能授权全部后续生产写入。
5. **可用的最小权限管理机制**：`SaasPlatformController` 默认可能关闭、平台管理员正式库数 0；不能临时打开历史超管接口或直接创建新 Platform Admin。若没有具备现行授权、幂等、审计和回退的维护入口，D2/D3 必须视为**待开发/未批准**。
6. **风险隔离**：P0 不访问患者真实资料、不创建/修改评估、不上传文件、不执行临床签发、不发 HEP 或微信入口。任何错误必须保留可恢复状态和审计，不通过修改法定或临床授权绕过门禁。

## 3. 回退矩阵（当前均为待审批计划，不是已执行记录）

| 发生阶段 | 先行止损 | 可恢复范围/禁忌 |
|---|---|---|
| D1 核对或启动失败，**尚无新业务写入** | 维持 Owner=false、Core provisioning=false；停止新版流量 | 按已审核 Compose/Launcher 程序重启既有旧镜像并核对健康。必须确认 DB 与旧版二进制兼容，**不删卷** |
| D2/D3 包或计划异常，**尚无开户** | Owner=false，保持 Core 机器接口不可用于实际申请 | 未绑定计划可停止激活、权限包可停止启用并留审计记录；不删除已存在历史行、不修改 111 |
| D4 机器身份或 TLS 失败 | 先确保 Owner=false，随后关闭 Core 机器接口 | 保留密钥安全记录，严格 TLS 回归；不以禁用证书验证代替修复 |
| D5 已经产生 Core tenant/管理员/订阅/审计 | 立即关闭 Owner 继续开户，冻结该申请与关联 | 仅由经审批的恢复/补偿机制进行双端对账；**禁止直接 DELETE、覆盖现库或重建同一 UUID**。旧镜像可能缺少新列/安全逻辑，不能不做兼容性审查就强退 |
| D6 已执行 037 或写入 HEP 数据 | 冻结 HEP 写入口及临床签发 | 037 包含不可简单逆向的 DDL/角色变更；按单独正式备份和签核的恢复计划处理，不直接 DROP 表/删角色 |

## 4. 当前可重复运行的**只读/隔离**核查命令

在 `/Users/saber/my-project/rehab/rehab-management-system`：

```bash
node desktop/scripts/owner-internal-pilot-readiness.mjs --live-readonly
node desktop/scripts/owner-core-provisioning-preflight.mjs --live
node desktop/scripts/hep-037-permission-diff.mjs --live
node desktop/scripts/internal-pilot-plan-preview.mjs --selected-quota-draft
deploy/internal/migrate.sh verify-files
node --test desktop/scripts/*.test.mjs
bash desktop/scripts/internal-pilot-selected-plan-isolated.sh --synthetic-only
bash desktop/scripts/hep-037-approved-grants-isolated.sh --synthetic-only
```

预检中缺少正式计划、严格三菜单权限包或机器通道时，**退出码 2 / `ready=false` 是正确、安全的 NO-GO**，不是脚本故障。`hep-037-permission-diff.mjs` 当前报告源校验、结构先决条件通过，但同时主动拒绝未安排维护的角色扩权，亦是正确行为。两个 `--synthetic-only` 脚本只操作随机临时容器，不可修改成目标为正式容器的命令。

私有隔离 HTTP 复验：`/Users/saber/my-project/rehab/validation-evidence/qa-20261115-c2-http-isolated/c2-synthetic-http.py --synthetic-only`（需要 Mac1 Docker，本脚本本身会创建/清理一次性合成测试资源）；已实测**严格 3 菜单 58/58 PASS**。

## 5. 本次未解决的独立门禁与后续唯一申请

正式 P0 三项阻断仍为 `effectiveCorePilotQuotaVerified`、`corePermissionPackageHasExactPilotScope` 和 `ownerCoreMachineChannelReady`；即使三项最终通过，真实临床/HEP/正式 Apple 公证仍另有 182 项、5 份人工审核、签名证书等门禁。不要声称本受控变更包本身允许正式临床发行。

**推荐下一独立审批：D1 现有 Core 服务受控替换准备/维护窗口**，先固定新鲜备份与完整回滚证据；是否允许真正替换现网服务、以及后续 D2/D3/D4/D5 的生产写入必须按各阶段另行批准。当前阶段保持正式系统不变。

---

## 2026-11-15 D1 实际执行补充（覆盖上文 D1 待执行状态）

用户后续独立选择 `A` 批准 D1；已完成新鲜加密备份及独立恢复，并在原有 MySQL/Redis/Nginx 保持原容器的条件下，只将运行的 Core `server` 从旧镜像切换到固定 SHA-256 的 C2 QA 候选，Spring Boot 健康及 HTTPS API 通过；DB 仍 036/36，正式 SaaS 计划/开户记录 0/0，Owner 开关关闭。本次**没有实施 D2/D3/D4/D5/037**。

**重要限制**：Mac 现有 Launcher `start_inner` 仍用旧捆绑资源 `up --build`，未来点击其“启动”可能重建旧 Core；本次仅是运行容器 D1 QA 更新，并未完成 Launcher 发行版的持久性升级。只读试点预检现已额外核查真实候选镜像 ID，镜像回退时会 fail-closed。完整实际结果及私有回滚说明见 [D1 执行与验收](OWNER_CORE_D1_LIVE_BACKEND_CUTOVER_20261115.md)。
