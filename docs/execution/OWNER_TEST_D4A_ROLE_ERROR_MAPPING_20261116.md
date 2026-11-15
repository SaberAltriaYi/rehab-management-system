# D4-A｜岗位权限错误码修复与双租户 HTTP 回归

执行日期：2026-11-16（Mac1）

## 1. 结论

**修复完成。** 岗位权限接口不再把可预期的越权/参数错误包装为 500。system 模块全量测试 **618 项，0 失败、0 错误、9 项原有跳过**；完整桌面回归 **139/139 PASS**；修订后的双租户真实 HTTP 验收独立运行两次，**90/90 + 90/90 PASS**。

本次仅修改源码、测试、测试脚本和验收文档；构建了仅供隔离验收使用的 QA 镜像。没有更新或重启正式 Core/Owner，没有启用正式计划 2、套餐 113、Owner→Core 机器通道，没有创建真实机构账号。

## 2. 根因

`TenantStaffRoleService.assignRole(...)` 原来用 Java 原生 `IllegalArgumentException` 表示权限拒绝和非法参数。项目全局 `GlobalExceptionHandler` 只会把 `ServiceException` 映射到 `CommonResult` 的业务错误码；原生异常走兜底处理，最终表现为业务码 `500`。因此越权请求虽被拒绝、没有改写角色，但客户端收到的是错误的系统异常码。

## 3. 修复后的业务码规则

项目现有 API 统一采用 HTTP 200 + `CommonResult.code` 表达业务结果。下表的 400/403 指**响应 JSON 的业务码**，不是本次擅自改变整个项目的 HTTP transport status。

| 请求情形 | 业务码 | 说明 |
|---|---:|---|
| 用户 ID 缺失、非正数；岗位角色值不在允许范围 | 400 | BAD_REQUEST |
| 目标用户不存在于当前租户可见范围 | 403 | 与跨租户目标使用同一拒绝语义，不暴露用户是否属于其他租户 |
| 跨租户分配治疗师岗位 | 403 | FORBIDDEN |
| 管理员给自己改岗 | 403 | FORBIDDEN |
| 修改机构主管理员岗位 | 403 | FORBIDDEN |
| 非机构管理员/治疗师调用岗位分配 | 403 | FORBIDDEN |
| 目标用户已有非标准/高权限岗位 | 403 | FORBIDDEN |
| 临床审核人角色通过此接口申请 | 400 | 不允许由机构管理员自助授予；DTO 校验与服务层均拒绝 |
| 内置岗位模板缺失或重复 | 500 | 保留为真实服务端配置故障，不伪装成客户端错误 |

其中“当前租户不可见用户”有意返回 403：`AdminUserService.getUser` 在当前租户上下文下会过滤其他机构用户，返回 `null`。若将 `null` 返回 400 或 404，会把“用户不存在”和“用户属于其他机构”区分开，增加账号枚举风险。故两种情况统一拒绝。

## 4. 代码变更

- `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/tenant/TenantStaffRoleService.java`
  - 权限拒绝统一使用 `ServiceExceptionUtil.exception(FORBIDDEN)`。
  - 非法参数使用 `ServiceExceptionUtil.invalidParamException(...)`。
  - 当前租户不可见目标统一 403；停用目标 400。
  - 岗位模板缺失/重复仍保留 `IllegalStateException`，确保系统配置错误继续暴露为 500。
- `TenantStaffRoleServiceTest.java`
  - 断言实际 `ServiceException.code`，覆盖机构管理员、非管理员、自我改岗、机构主管理员、跨租户用户、已有特权角色、非法 ID、不可见用户和停用用户。
- `TenantStaffRoleErrorMappingTest.java`
  - 通过 MockMvc + 全局 `GlobalExceptionHandler` 验证 400/403 业务码不再退化为 500。
- `desktop/scripts/d4a-role-isolated-http.py` 与配套 `.test.mjs`
  - 锁定本次源码提交、JAR SHA-256 和 QA 镜像 ID。
  - HTTP 回归从“请求不成功”升级为**必须精确等于 400 或 403**，并验证被拒绝操作后 A/B 用户岗位关联未改变。
  - 仍要求唯一参数 `--synthetic-only`；无参数、`--help`、`--live` 和混合非法参数均在任何 Docker 动作前退出。

## 5. 自动化测试证据

| 测试范围 | 实际结果 |
|---|---:|
| `TenantStaffRoleServiceTest` | 9/9 PASS |
| `TenantStaffRoleErrorMappingTest` | 2/2 PASS |
| `TenantStaffRoleControllerContractTest` | 1/1 PASS |
| 完整 `yudao-module-system` | 618 项；0 失败、0 错误、9 项跳过；BUILD SUCCESS |
| `node --test desktop/scripts/*.test.mjs` | 139/139 PASS |
| D4-A 隔离 HTTP 脚本门禁 | 6/6 PASS |

完整 system 模块测试使用本机已安装的 **JDK 17.0.15** 执行，未修改 macOS 默认 Java。JDK 23 下曾遇到旧版 Mockito/Byte Buddy 不支持 Java 23 class file 的既有工具链错误；切换到项目兼容的 JDK 17 后，618 项模块测试全部成功。此限制与本次业务代码无关。

## 6. 双租户真实 HTTP 回归

### 固定构建身份

| 项目 | 证据 |
|---|---|
| 角色错误码源码提交 | `8e070f4254a905752014f513c9d17cf085e8287f` |
| Maven | JDK 17，`mvn -B -pl yudao-server -am -DskipTests clean package`，BUILD SUCCESS |
| 新 JAR SHA-256 | `9d7a8d300d43d8a2f0ca73b15a572d0d708bf51021d93a3223c27891ff713d22` |
| QA 镜像 | `rehab-d4a-role-errors:8e070f4254` |
| 镜像 ID | `sha256:f840797d88ed003562891fdb6e0ee04a314af3af99d0375939168300b92d147b` |

### 关键 HTTP 结果

下列均通过真实隔离 Core HTTP 接口执行，响应 HTTP transport 为 200，检查的是 `CommonResult.code`：

| 场景 | 预期/实测业务码 | 结果 |
|---|---:|---|
| A 管理员请求授予临床审核人岗位 | 400 | PASS |
| A 管理员给 B 机构治疗师分配岗位 | 403 | PASS |
| A 管理员给自己改岗 | 403 | PASS |
| A 管理员传 `userId=0` | 400 | PASS |
| A 管理员传当前租户不可见的目标 ID | 403 | PASS |
| A 治疗师调用岗位分配接口 | 403 | PASS |
| 伪造 `tenant-id` 请求头尝试跨机构分配 | 403 | PASS |
| A/B 管理员分别给本机构治疗师分配岗位 | 0（成功） | PASS |

还通过隔离数据库断言跨机构目标用户原有治疗师岗位未变化、A 管理员自己的 `tenant_admin` 岗位未变化、未授权的审核人没有被自动赋权。

### 可重复执行

最终固定镜像的双租户 HTTP 测试独立执行两次：

- `d4a-role-http-result-206fad0719.json`：**90/90 PASS**。
- `d4a-role-http-result-5027483a36.json`：**90/90 PASS**。

第一次探索性运行发现了“跨机构用户被租户过滤成 null 后返回 400”的问题（35/36），据此调整为统一 403；该次失败收据保留为 `failed-initial-discovery-f929e63886.json`，不计入最终通过次数。修复后两次完整复验均通过。

每次运行均新建随机 `--internal` Docker 网络、tmpfs MySQL/Redis/Core 和随机合成机构账号，无 host port publish、无 bind mount、无持久业务 volume。每轮结束后自动删除容器和网络；最终复核为 **0 个测试容器、0 个测试网络**。仅保留带 SHA 固定标识的 QA 镜像供以后复验。

## 7. 正式环境未变更证明

复验结束后的只读检查：

- 正式 Core 与 MySQL 容器仍使用原镜像，均 Running/healthy。
- 正式计划 2 `status=0`（停用）。
- 正式套餐 113 `status=1`（停用，274 菜单未变化）。
- 正式 `saas_provisioning_requests` 记录数 0；真实租户/用户数仍为 1/1。
- Owner `test` 仍 `APPROVED / ACTIVE / 20 seats / NOT_CONFIGURED`。
- Owner `REHAB_MAIN_PROVISIONING_ENABLED` 与 Core `YUDAO_PROVISIONING_ENABLED` 均非 true。
- 没有创建正式机构账号，没有启动 Owner→Core 正式机器通道，没有向正式库写入测试数据。
- 本次 HTTP 测试在随机 tmpfs 数据库中建有合成测试计划和套餐供 provisioning 测试使用；它们随容器销毁，不是正式计划 2 或套餐 113，也没有激活正式订阅。
- `deploy/internal/migrate.sh verify-files` PASS；迁移文件和固定 SHA-256 清单未改动。

## 8. Git 与复验命令

源码修复提交：

- `b682a04c75` — `fix(system): return explicit 400 and 403 for staff role denials`
- `8e070f4254` — `fix(system): mask tenant-invisible staff targets as forbidden`

最终脚本使用当前源码 SHA、JAR SHA、QA 镜像 ID 固定校验。完整复验：

```bash
python3 -B desktop/scripts/d4a-role-isolated-http.py --synthetic-only
node --test desktop/scripts/d4a-role-isolated-http.test.mjs
```

**生产状态：NO-GO（按本次授权要求保持不启用）。** 本次验收只证明隔离环境中岗位错误码和 D4-A 双租户权限链路正确，不构成生产部署或正式启用授权。
