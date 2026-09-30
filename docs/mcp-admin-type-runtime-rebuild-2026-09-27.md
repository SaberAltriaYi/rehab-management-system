# 管理端类型修补与桌面资源重建（2026-09-27）

在 `master` HEAD `49b3e40395bcd37b37b30f3dfc49d27c28939188` 加既有未提交修改的工作树中修复源码。用户批准在同一台 Mac 的 `/tmp` 创建并于结束后只清理本轮一次性验证副本；构建、检查和本地 Docker E2E 在副本中进行。未提交、未部署、未执行生产迁移，患者入口未开启。参见[上一轮顺序复核](mcp-sequential-validation-2026-09-27.md)。

## 管理端：已缩减，但硬门禁仍失败

- 干净验证副本与开始时原树的 **171 个已改／未跟踪文件逐项字节相同**，HEAD 相同。冻结锁文件 `pnpm install --frozen-lockfile --offline` 退出 0；复制与原树字节相同的构建生成声明后，基线 `pnpm ts:check` **退出 2、600 条诊断**。
- 对照支付及交易模块的 Java 响应 VO，为支付订单／退款／通知／转账与售后详情增加对应的前端数据类型；修复空数据初始化、可空日期、金额展示、字典空值、旧版 `el-tag` 尺寸和售后凭证图片字符串误作对象的问题。日期工具的入参类型与已存在的空值／Dayjs 运行时处理保持一致。未增添 `any` 逃逸、跳过文件或降低检查严格度。
- 首轮全量复核 **退出 2、482 条**；修正本轮新增的 12 个字典空值诊断，重新 `pnpm build:internal` **退出 0**，末轮全量 `pnpm ts:check` **退出 2、470 条**，分布于 154 个文件。已改源码在隔离副本与原树的前端构建输入共 **1703 个文件逐项 SHA-256 一致**。`git diff --check` 对本轮所改源码退出 0。
- 剩余诊断主要为 TS2322 138、TS2339 130、TS6133 59、TS2345 57。高密度文件包括 `ElementForm.vue` 15、`ProcessViewer.vue` 14、AI 聊天会话与图片组件各 14、商城统计 13、快递模板 12。**类型门禁仍阻断发行，不因本轮的局部改进而放行。**

## 同源桌面资源：非发布候选已通过完整性与本地 E2E

从**该隔离副本的当前源码**重新执行 JDK 17 `deploy/internal/build-server-isolated.sh`（退出 0，生成后端 JAR）；Docker 临时 MySQL 生成并回灌验证脱敏桌面初始化快照（退出 0，仅隔离数据库）；`pnpm build:internal`（退出 0，生成本轮 1703 个源码文件／829 个前端产物文件的构建回执）。随后 `node desktop/scripts/build-runtime.mjs --output desktop/runtime` **退出 0**；`node desktop/scripts/check-runtime.mjs desktop/runtime/1.0.0` **退出 0**。

对 `VERSION.json`、`BUILD-INFO.txt`、前端回执和重新构建的 JAR 逐项比较：commit SHA、前端源码与产物 SHA-256、后端 JAR SHA-256 完全一致。回执中的前端源码 SHA-256 为 `67d261318a75836d369306218e078749a720a8af0c151934cd2d6a1fb0f02d67`，产物为 `57db68974430c06660c34eb67fccc7b2fd4e16f4ebb28a2c5f279fdbdf85c0bb`；来源源码也与目前原树的 1703 个前端构建输入文件一致。运行资源工具单测 **12/12 通过、退出 0**。`desktop/scripts/test-runtime-e2e.sh` 在随机命名、自动回收的本地 Docker 容器及卷中 **退出 0**：UI/代理健康、租户 1、患者记录 0、迁移账本 19，患者旧 `login` 和 `bind` 均为 HTTP 403。

在原工作树的被忽略目录保留非发布候选：`desktop/build/type-blocked-candidate-20260927/1.0.0` 及同级 `frontend-build-receipt.json`。拷贝后重新运行 `check-runtime` **退出 0**，manifest 与回执同隔离构建产物字节相同；原有 `desktop/runtime/1.0.0` 的 8 个未入 manifest 的历史文件仍在，**未覆盖、删除或修正旧资源**。新候选通过自检不等于可以发布：管理端 `ts:check` 仍退出 2；任何后续源码更改都应重新生成前端回执与资源候选，不得复用这份旧回执。

复核后只清理本轮获批的 `/tmp/rehab-repair.wGXIIS` 副本，清理命令退出 0；再次确认旧资源与新候选均保留。随机命名的本地 E2E 容器和测试卷数量复核均为 0，未操作其他容器或业务数据卷。

## 未完成事项

1. 优先按诊断密度和共享类型根因修复剩余 **470 条**并将全量 `ts:check` 跑到真实退出 0；保持 CI／桌面发行工作流的阻断式检查，不修改为软门禁。
2. 在最终源码冻结后重新构建后端／前端与桌面资源、复验 receipt／manifest／隔离 E2E，再经签名、真机与发布审批。患者强认证准备与生产迁移另行验收，原患者入口仍须保持 HTTP 403。

