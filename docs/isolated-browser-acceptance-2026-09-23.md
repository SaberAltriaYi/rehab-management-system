# 康复与机构运营模块：隔离验收记录（2026-09-23）

状态：**阶段性验证，不等于全项目交付或现有患者库部署许可**。权威工作树为 Mac `/Users/saber/Documents/Playground/rehab-module-enable-clean`，Git 基线 `bafc441`。独立 Compose 项目 `rehab-verify-1790142215`，使用合成账号与合成数据；现有患者数据库、附件和运行卷未迁移或写入。

## 已完成

- 001–023 迁移在专用 MySQL 初始化并由只读迁移器核对 23 条账本；新增 BPM 8、ERP 33、Member 11、Report 1 张业务表；结构测试与门禁测试通过。菜单仅在独立数据库用 `sql/mysql/rehab-enable-reviewed-business-menus-opt-in-v1.sql` 手工启用已审页面，**该文件不在自动迁移清单**；积木报表与 AI、商城、支付、IoT 仍未开放。
- HTTPS 本机入口 `https://127.0.0.1:18443/` 通过 Chrome 153 的 DevTools Protocol 做了真实浏览器测试。登录过程使用临时关闭验证码的**测试专用前后端**及合成用户；原生产默认验证码设置未改，且默认后端缺验证码会拒绝登录。最终前端产物恢复生产默认验证码配置。
- 最终安全收口后的隔离后端上，六条真实页面与 API 响应业务码 0、无捕获到的浏览器异常：`/rehab/patient` → `rehab/patient/page`；`/crm/customer` → `crm/customer/page`；`/bpm/manager/category` → `bpm/category/page`；`/erp/stock/stock` → `erp/stock/page`；`/member/signin/config` → `member/sign-in/config/list`；`/report/go-view` → `report/go-view/project/my-page`。原内部前端构建遗漏 BPM、ERP、Member、Report 页面，会出现菜单标题但内容空白；已用精确页面白名单修复，并重新完成 `build:internal`。
- Report 页面不再用带访问/刷新令牌的 URL 嵌入未配置的外部设计器，而只提供本人项目的安全元数据页面。Chrome 实测页面新建→重命名→删除合成项目，七次关联 API 业务码均为 0，测试项目最终无残留。原始 SQL 数据集接口继续拒绝执行；跨租户读取 403，同租户其他用户无法读取、更新或删除首个用户项目。
- 第三方报表安全收口：旧隔离后端的 `/jmreport/list`、`/jimubi/list`、`/drag/list` 无凭据请求均曾返回 HTTP 200（当时未记录业务码，不能仅凭状态码认定数据泄露）。新版源码默认排除 JimuReport 与 MiniDao 两项自动配置、默认关闭本模块手工扫描，并删除三个路径的 blanket `permitAll` 及 `/jmreport/*` 租户忽略规则。新建的 **仅隔离测试镜像** `rehab-verify-server:025-gated` 已实测：以上三条路径无凭据请求返回业务码 401；持合成管理员令牌请求均返回 HTTP 404；GoView 项目 API 通过直连和 HTTPS 代理仍返回业务码 0；禁用原始 SQL 接口仍返回 1003000001，健康检查 `UP`。生产运行卷和配置未替换。
- Python 自动化 27 项：24 通过、3 项按设计跳过（含新增交付安全契约）；默认与机构模块 opt-in 的 JDK 17 Maven reactor 测试均 `BUILD SUCCESS`，opt-in Report 单测 9/9 通过；后端 opt-in JAR 重打包及内部前端 `build:internal` 通过。
- ERP 独立合成主数据（产品、单位、分类、仓库、供应商和客户）完成真实库存业务闭环：入库审批 5、出库审批 2 后库存为 3；额外出库 8 审批被业务码 `1030404000` 拒绝，库存保持 3。Chrome 真实导航 `/erp/stock/stock`，合成产品显示在库存表格中，关联权限及产品/仓库/库存 API 业务码均为 0。撤销出库和入库后库存依次回到 5、0，并删除合成单据；仅独立测试库保留合成主数据及零余额库存审计记录。
- BPM 分类在独立库完成创建→分页检索→修改→详情→删除→删除后查询为空；Chrome 实际 `/bpm/manager/category` 显示合成分类 1 行，完成后无活动测试分类残留。另创建一次**无角色**的临时合成账号，确认能登录、角色数 0，但 BPM 分类查询、ERP 库存查询与 BPM 分类创建均被业务码 `403` 拒绝；账号已删除。另一个原有合成账号带 `super_admin` 角色，不能用于非管理员拒绝用例。
- 2026-09-24 补验：Flowable 独立库模型创建和部署、合成流程发起→自动结束均返回业务码 0；重新部署有指定审批人的第二版后，由另一名合成用户在待办页 API 查到任务、审批通过，发起人查询的实例有结束时间。缺手机号时可选短信失败曾导致流程回滚（`1002013000`）；新增非阻断通知处理及 2 项单测通过后，以上两种流程闭环成功。仅清理唯一合成流程标识下的模型、历史及分类，未操作现有患者库。新独立镜像 `rehab-verify-server:027-bpm-sms-restored` 健康 `UP`，BPM/ERP/Report 关联 API 业务码 0；第三方报表三路径仍为无凭据业务码 401、持令牌 HTTP 404。JDK 17 opt-in 后端重打包通过，并核对最终 JAR 内 2523 个本项目字节码文件的魔数均有效。

## 尚未解除的发布门禁

- `pnpm ts:check` 仍失败：1129 条诊断；与变更前日志的诊断多重集**完全相同**，新建的报表 API/页面及修改的白名单文件均 0 条诊断。现有类型质量债仍需处理，前端打包通过不等于全量类型检查通过。
- BPM 流程的**后端合成审批**已验收，但完整流程设计/待办 UI 未纳入当前隔离菜单开放范围；第三方报表设计器维持关闭，不得误称完整模块开放。
- Git 差异、敏感信息和远端同步仍需提交时检查；本记录不授权升级现有患者库。外部 AI 调用尚缺供应商、费用、密钥和数据处理门禁，因此未启用。

仅测试容器所用凭据及令牌存放于 Mac `/tmp`，不记录于本报告或仓库。验收图像留于 Mac `/tmp/rehab-browser-route-*.png`，可能含合成页面信息，不纳入 Git。
