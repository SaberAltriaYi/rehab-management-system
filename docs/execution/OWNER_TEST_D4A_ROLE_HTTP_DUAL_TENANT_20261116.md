# D4-A｜新角色权限版本：独立 HTTP 双租户端到端验收

执行日期：2026-11-16（Mac 本地 UTC+8，验证完成 00:41）

## 1. 验收目标与授权边界

用户明确选择 **A**：基于已提交的 D4-A 源码，用合成机构/患者在完全隔离的 Core 中验证三角色 OAuth 登录、接口授权、患者数据隔离、越权拒绝和临床审核/签署操作。**不启用正式计划 2、套餐 113 或 Owner→Core 机器通道；不创建真实机构账号，不部署生产系统，不碰真实患者数据。**

结论：**86/86 项真实 HTTP／数据库联动验收通过，零失败。** 与此前 C2 的 58 项（旧版三菜单）不同，这次确实使用**本次角色权限分离后的新 JAR**；报告审查、审批和动作签署均实测成功并核验审计表，不能用静态权限断言冒充。

## 2. 构建来源与执行环境

| 项目 | 实际证据 |
|---|---|
| 源码提交 | `79cf161e4b6e8e8eab5e8567fa353f2187286f09`（D4-A 权限分离） |
| Maven 命令 | `mvn -B -pl yudao-server -am -DskipTests package`，**BUILD SUCCESS**，生成 JAR，仅供隔离验收；不代表跳过了上阶段已通过的全部 972 项 Java 测试 |
| 新 JAR SHA-256 | `0c5e46d00ec844d75ccb3ea5132b43442899bf2d5c99fe2ab27b03cbd4cbeebe` |
| 测试镜像 | `rehab-d4a-synthetic:79cf161e4b`，镜像 ID `sha256:cefbe6846abd086e88c0f0ad2889b037ee5137c142e813f3b2c1dd01229553c7` |
| 镜像创建 | 以已固定 C2 运行镜像为基础，只替换源于 D4-A 提交的 `/app/app.jar`；用 `docker run --network none --entrypoint sha256sum` 独立验证镜像内 JAR SHA-256 与本地一致 |
| 数据库 | **独立、随机名字、tmpfs MySQL 8.4.10**；仅导入脱敏 `desktop-bootstrap.sql`（版本账本 036:36）与 V3/V4 SQL；初始患者 0 |
| 缓存与 Core | 独立 tmpfs Redis 7.4 + 独立 D4-A Core，`--restart no` |
| 网络 | 随机 Docker `--internal` bridge；三个容器**无 host port publish、无业务磁盘 bind mount、无 named volume** |
| 身份 | 机器令牌、管理员/员工密码、UUID 全部**本次随机生成**，不读取生产令牌；仅测试 Core 的临时 `YUDAO_PROVISIONING_ENABLED=true` |
| 菜单与额度 | 直接从**隔离脱敏数据库**提取 274 个启用菜单生成合成全量菜单套餐 `990113`；另建合成 20 / 400 / 2000 / 5 GiB 计划，**不是正式 113/2** |

只有本机测试镜像标签保留以供复验；所有本次创建的独立容器和网络均已删除，tmpfs 合成数据随容器销毁。该镜像不是签名发布包，绝不可直接部署到正式服务。

## 3. 双租户与角色权限实测

经由真实 `POST /admin-api/system/saas/internal/provisioning`，在**隔离库中**创建两家合成机构 A/B。随后真实 OAuth 管理员登录、真实用户创建/岗位接口分配、独立模拟专业资质核验后角色绑定、再完成员工登录。

| 验收项 | 结果 |
|---|---|
| 两家合成机构开户成功且 UUID/tenant ID 不同 | PASS |
| 不带机器 Token 调用开户接口 | HTTP 401，PASS |
| 每家机构自动创建 `tenant_admin`／`tenant_rehab_therapist`／`tenant_rehab_clinical_reviewer` 三个内置系统角色 | PASS |
| 初始管理员只绑定 `tenant_admin`，不自动获得治疗师或审核人角色 | PASS |
| 机构管理员：数据范围 ALL（仅本机构），35 个菜单 | PASS |
| 治疗师：数据范围 SELF，46 个菜单 | PASS |
| 临床审核人：数据范围 ALL（仅本机构），18 个菜单 | PASS |
| A/B 管理员、A/B 治疗师、A 临床审核人分别进行真实 OAuth 登录与权限信息请求 | PASS |
| 机构管理员通过新 HTTP 岗位接口分配治疗师 | A/B 均 PASS |
| 机构管理员自行分配临床审核人、跨机构修改用户、自行更改主管理员岗位 | 均拒绝，PASS |
| 治疗师调用岗位分配接口或伪造租户请求头 | 返回应用码 403，PASS |
| 两家合成机构均未产生历史 `rehab_therapist` 角色 | PASS；仅使用新 `tenant_rehab_therapist` |

**资质核验说明**：A 临床审核人的账号经真实管理员 API 创建，但其审核人角色仅通过**隔离 MySQL 中的合成测试装置**模拟独立审批后关联。此过程**没有验证正式的执业资质审核、系统拥有者 MFA/角色审批或双人签署流程**；不可把这条模拟操作理解为真实临床资质认证完成。

## 4. 患者与报告隔离

在合成临床数据中创建 A 机构两位虚构患者（其中一位分配给 A 治疗师）、B 机构一位虚构患者，各自有虚构 Episode、评估、报告和动作评估。通过真实 `GET /admin-api/rehab/patient/page` 和 `GET /admin-api/rehab/report/page` 核查返回列表 ID：

| 身份 | 实际患者可见范围 | 实际报告可见范围 |
|---|---|---|
| A 机构管理员 | A 的 2 位 | A 的 2 份 |
| A 康复治疗师 | **仅分配给他的 1 位** | **仅对应的 1 份** |
| A 临床审核人 | A 的 2 位 | A 的 2 份 |
| B 机构管理员 | **仅 B 的 1 位** | **仅 B 的 1 份** |
| B 治疗师（没有患者分配） | **0 位** | 未要求该接口范围断言 |

此外，跨机构患者详情、治疗师访问同机构未分配患者、审核人伪造 B 租户头，以及 A 审核人对 B 报告/动作评估的操作均被拒绝。跨机构 B 报告保持 `draft`，无意外状态修改。**注意：部分拒绝以业务错误码和 HTTP 200 返回，是现有 API 的业务错误封装约定；报告没有将这些误写为 HTTP 403。**

## 5. 临床复核与签署：真实成功，而非仅权限判定

1. 机构管理员、治疗师调用 `POST /admin-api/rehab/report/review`、`/approve`：均返回应用码 **403**，无临床审核权限，PASS。
2. 临床审核人调用真实 `review`：**HTTP 200 / 业务码 0**，合成报告状态从 `draft` → `reviewed`，`reviewed_by` 为审核人合成用户 ID。
3. 同一审核人调用真实 `approve`：**HTTP 200 / 业务码 0**，`reviewed` → `approved`，`approved_by` 为审核人；隔离 `rehab_audit_log` 有**2 条对应审查/审批记录**。
4. 机构管理员、治疗师调用 `POST /admin-api/rehab/motion/sign`：均返回应用码 **403**。
5. 审核人尝试签署 B 机构动作评估：被业务级拒绝，无跨租户修改。
6. 审核人签署已具备合成完成评分、有效修订号与 `PENDING_REVIEW` 状态的 A 机构动作评估：**HTTP 200 / 业务码 0**；`rehab_motion_assessment` 更新为 `COMPLETED` 且 `signed_user_id` 与审核人相符；生成**治疗师版 + 患者版两条 signed 报告**；隔离统一审计表新增对应 `motion/sign` 记录。

所有签署仅发生在虚构患者及隔离数据库；没有生成真实诊疗意见、没有上传真人影像，也没有触及正式患者系统。

## 6. 安全门禁、资源清理与回归

- 测试脚本：`desktop/scripts/d4a-role-isolated-http.py`。必须携带单一参数 **`--synthetic-only`**；无参数、`--help`、`--live`、混合非法标记全部在调用 Docker **之前**退出码 2。
- 对应自动测试：`desktop/scripts/d4a-role-isolated-http.test.mjs`，**5/5 PASS**。
- 全部桌面测试：`node --test desktop/scripts/*.test.mjs`，**138/138 PASS**，0 failed/skip。
- Python AST 语法验证 PASS；迁移清单 `deploy/internal/migrate.sh verify-files` PASS。
- 两个生产容器的镜像与 Running 状态已在测试前后比对，保持不变；测试后只读复核 Core healthy、Owner `test` APPROVED/ACTIVE/20 席但 `NOT_CONFIGURED`，计划 2 `status=0`、套餐 113 `status=1`、真实开户请求 0、Owner/Core 机器开关均 **false**，正式数据库的租户和账号仍为 1/1。
- 临时测试容器 **0**、临时测试网络 **0**；合成患者数据不会持久留在本机生产数据库。
- 本机 QA 明确列出每条检查、HTTP 状态/业务错误码与通过标志的 JSON 收据，0600 权限。测试凭据、Token、登录密码与真人患者信息**不写入提交仓库/审计收据**。

### 复验命令与要求

本机工作目录：`/Users/saber/my-project/rehab/rehab-management-system`。

```bash
python3 -B desktop/scripts/d4a-role-isolated-http.py --synthetic-only
node --test desktop/scripts/d4a-role-isolated-http.test.mjs
```

完整 HTTP 复验需要：Mac1 Docker 正常、保持此 D4-A **独立 QA 镜像**，且该镜像/JAR 的 SHA-256 与本报告相同。脚本固定校验镜像、JAR 和源码祖先提交，不接受随意切换为正式镜像或把数据库连接指向生产数据库。测试镜像若被删，需从 D4-A 源提交重建并重新核实哈希及镜像，不能绕过固定身份检查。

## 7. 已知限制与下一步阻断

1. **越权错误码质量问题**：机构管理员试图跨租户分配员工或修改自身角色，实际都被拒绝、角色记录未被不当更改，但接口返回 `HTTP 200 / 业务码 500`（不是明确的 400/403）；建议下一阶段将相关领域错误映射为稳定、脱敏的 400/403 并进行回归。这不影响本次“没有越权写入”的结果，但应在正式 UI 使用前修正。
2. **临床资质仍是假定前置条件**：审核人模板/报告签署能力通过，但正式资质核验、拥有者授权、复核审批记录及职业资质有效期控制未自动化；QA 的审核人角色由隔离测试装置手工绑定。
3. **真实商业限额仍需专项证明**：计划中的 400 患者、2000 次评估、5 GiB 存储是否在所有写入口执行拦截，不在本次角色权限 HTTP 验收的范围；不能因为额度值正确就宣称资源限额全面生效。
4. **未进行生产部署或正式 Owner MFA**：`test` 正式开户仍为 NO-GO；本次仅验证新代码在隔离网络/合成数据中的真实 HTTP 链路，没有更新 Live Core JAR、Owner `.env`、机器通道或套餐绑定。
5. **保留 QA 镜像**：本机保留 `rehab-d4a-synthetic:79cf161e4b` 供复验，镜像本身占用 Docker 存储空间；已删除的仅是本次的临时容器和网络。

## 8. 证据位置

- 私有 QA JSON：`/Users/saber/my-project/rehab/validation-evidence/qa-20261116-d4a-http-e2e/d4a-role-http-result-c90945c0c1.json`，**86/86 PASS**。
- **提交后独立复跑**：在测试脚本已提交为 GitHub 提交 `b72ab3b18dd04da21761a5cffc58be252d535c84` 后，再完整执行一次实际 HTTP/SQL 链路，得到 `d4a-role-http-result-7624c326c1.json`，**再次 86/86 PASS**，0 失败；两次测试均使用各自随机网络、合成账号和 tmpfs 数据库，结束后临时容器与网络均为 0。复跑后再次只读确认正式 Owner/Core、计划 2、套餐 113 仍未变化。
- 脚本源码：`desktop/scripts/d4a-role-isolated-http.py`。
- 安全门禁测试：`desktop/scripts/d4a-role-isolated-http.test.mjs`。
- 构建执行日志（仅本机）：`/private/tmp/rehab-d4a-httpe2e-server-build.log`、`/private/tmp/rehab-d4a-image-build.log`；HTTP 运行摘要 `/private/tmp/rehab-d4a-httpe2e-first.log`。
- 上一阶段角色权限交付：`docs/execution/OWNER_TEST_D4_ROLE_PERMISSION_SEPARATION_20261115.md`。

**验收结论：D4-A 新权限版本真实 HTTP 双租户验收 PASS，可进入下一阶段安全准备；不构成生产启用授权。**

## 2026-11-16 岗位权限错误码修复补充

随后针对本报告中记录的“部分越权岗位操作返回业务码 500”限制完成了修复。最终规则为：非法请求参数使用业务码 400；跨机构、本人改岗、非管理员调用及租户不可见用户统一使用 403；内置模板缺失/重复仍保留 500。修复后 system 模块全量 618 项（0 失败、0 错误、9 项跳过）、桌面脚本 139/139，以及新版本双租户 HTTP 两次各 90/90 通过。完整根因、响应语义、源码/JAR/镜像 SHA 和正式环境未变更证据见 [D4-A 岗位权限错误码修复与双租户 HTTP 回归](OWNER_TEST_D4A_ROLE_ERROR_MAPPING_20261116.md)。
