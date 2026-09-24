# 本机单版本 `:8443` 修复与浏览器验收（2026-09-24）

## 修复范围与原因

- 原 `https://127.0.0.1:8443/index` 指向 2026-09-20 旧 `rehab-internal` 前后端镜像及旧库：机构模块只有 4 张 `member_` 表，BPM、ERP、Report 业务表缺失；相关子菜单也被默认停用。旧前端产物的动态页面白名单与新版不同。
- 经用户明确选择，**不迁移旧患者记录及附件**。用已在独立库完成迁移与合成验收的新版接管同一地址，而非对旧患者库直接施加新 DDL。新库有 53 张以 `bpm_`、`erp_`、`member_` 或 `report_` 开头的表；其余康复/CRM/系统表另计。
- 在独立库执行审阅过的 opt-in 菜单 SQL；补齐此前遗漏的、仅限审阅页面的按钮权限与本地 `super_admin` 角色授权。ERP 的仓库、出入库单、明细和 BPM 的待办/已办也经过浏览器验证后加入 opt-in；未审阅的库存盘点/调拨、第三方报表设计器、任意 SQL 数据集、AI、商城、支付、IoT、公众号继续关闭。不对所有账号自动授权。
- 前端使用已验收的内部版打包产物重新制作 `rehab-internal-admin:reviewed-20260924` 镜像；后端为 `rehab-verify-server:027-bpm-sms-restored`。网页镜像含 `index-BnNpHm5a.js`，区别于旧版 `index-ByTFzJH2.js`。
- 新版 HTTPS 沿用原本机合法证书（含 `127.0.0.1` SAN，至 2028-11-07），复制到当前权威仓库被 Git 忽略的 `deploy/internal/certs/`；Mac `curl --cacert` 的证书/代理检查通过。该旧 CA 缺失 Python 3.13 严格验证要求的 key usage 扩展，故 Python urllib 默认信任库不适合作为这张既有 CA 的验收依据；不能据此宣称证书符合所有 TLS 客户端的最佳实践。

## 当前运行方式

- **唯一网页入口：** `https://127.0.0.1:8443/index`，仅监听本机回环地址；旧网页 `:18443` 和 `:8080` 不再运行。
- 网页容器 `rehab-local-admin-8443`（自动重启）；隔离后端 `rehab-verify-1790142215-test-server`（自动重启，本机诊断 `:48090`）；独立 MySQL/Redis 为 `rehab-verify-1790142215` 项目（自动重启）。浏览器只需访问 `:8443`，后端经同源 `/admin-api/` 代理。
- 测试登录账号与第二审批账号分别位于 Git 忽略且权限为 `0600` 的 `deploy/internal/secrets/local-8443-account.env`、`deploy/internal/secrets/local-8443-approval-account.env`；独立环境连接参数在同目录 `local-8443-stack.env`。**不在文档、Git、测试日志中记录密码或令牌。**旧 `:8443` 登录会话/旧用户不适用于已更换的数据库：浏览器若仍用旧缓存，请清理此本机站点的存储/缓存后重新登录。
- 不要从旧代码目录重启旧版 Docker Compose；当前单版本采用以上精确镜像和独立数据卷。Mac Docker 重新启动后，若自动重启未完成，可在 Docker Desktop 中启动上述四个新容器；以 `:8443` 页面和 `:48090/actuator/health` 复核。不要启动 `rehab-internal` 旧项目。

## `:8443` 真实浏览器验收

Chrome DevTools / Playwright 在 HTTPS `:8443` 上使用合成账号，完成页面原生登录及滑块验证码，然后逐层展开侧栏并**点击菜单项**；每条路径检查实际路由、预期 API 业务码 0、HTTP 错误与页面异常：

| 页面路径 | 后端列表路径 |
| --- | --- |
| `/rehab/patient` | `/rehab/patient/page` |
| `/crm/customer` | `/crm/customer/page` |
| `/bpm/manager/category` | `/bpm/category/page` |
| `/bpm/task/todo`、`/bpm/task/done` | `/bpm/task/todo-page`、`/bpm/task/done-page` |
| `/erp/product/product`、`/erp/stock/stock` | `/erp/product/page`、`/erp/stock/page` |
| `/erp/stock/warehouse`、`/erp/stock/record` | `/erp/warehouse/page`、`/erp/stock-record/page` |
| `/erp/stock/in`、`/erp/stock/out` | `/erp/stock-in/page`、`/erp/stock-out/page` |
| `/member/signin/config` | `/member/sign-in/config/list` |
| `/report/go-view` | `/report/go-view/project/my-page` |
| `/system/user`、`/infra/config` | `/system/user/page`、`/infra/config/page` |

**结果：15/15 路径通过**，预期列表 API 均返回业务码 0；导航期间无捕获的 HTTP ≥400、非零业务码或 JS 页面异常。浏览器登录经过验证码的获取、验证和正常的账号登录接口。测试报告（不含凭据）留在本机 `/tmp/rehab-browser-8443-routes.json`。

同一浏览器对报表项目、BPM 分类、CRM 客户、会员签到配置、ERP 产品分别执行 **新建 → 修改 → 删除**，共 **15 个 UI 操作**，相关 API 均业务码 0，测试项目/分类/客户/配置/产品最终无活动残留。一次早期 BPM 测试因等待编辑详情不充分留下的单条合成分类已按唯一标识清理；最终用只读库查询核对 E2E 前缀的活动记录均为 0。

库存闭环在同一 `:8443` HTTPS 源请求后端并由真实浏览器库存页复核余额：以既有合成产品及仓库初始 0 为起点，入库审批 5 → 出库审批 2 → 余额 3；额外出库 8 的审批被业务码 `1030404000` 拒绝且库存仍为 3；随后按相反顺序反审批、删除合成草稿，最终余额 0。**库存审批是 HTTPS 接口调用加浏览器余额复核，不是通过入库/出库表单逐键录入并点击审批按钮的 UI 测试。**原独立库先前还完成 Flowable 双合成用户审批；本轮在 `:8443` 仅验收待办/已办列表路由，不能误称重新在该地址走了完整双人流程 UI。

## 旧版数据清理边界

新站点验证通过后，按用户明确选定的不可恢复删除方式，只对标签为 `rehab-internal` 的旧项目执行：移除 4 个旧容器、旧项目网络、旧 MySQL/Redis/患者附件/日志共 4 个命名卷及两张旧镜像；删除旧代码目录下独立的 `backups/` 与 `data/` 文件夹。移除前逐项核对旧项目标签与新版镜像/卷归属；新版 3 个卷及其 MySQL/Redis/附件卷均保持存在，清理后 `:8443` 和新版健康检查再次通过。**未删除旧源代码仓库本身，也无法代替用户检查 Time Machine、云同步或其他外部设备的副本。**被移除的 Docker 旧库和附件卷不能作为回滚源。

## 仍未覆盖

- 只打开了与康复及机构运营相关且已审阅的页面；未通过门禁的第三方报表设计器、原始 SQL、AI、商城、支付、IoT、公众号及其他 ERP/BPM 页面没有被宣称为可用。
- 前端 `pnpm ts:check` 仍有 1129 条与先前基线一致的诊断，不能宣称全量类型检查通过。后端 opt-in 打包、内部前端打包和前一轮默认 fork BPM 单测已通过；本轮仅修改菜单 opt-in SQL、回归测试及交付文档，不修改 Java/SPA 源码。
