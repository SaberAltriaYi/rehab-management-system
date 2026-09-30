# 项目完整性审查与优化建议（2026-09-27）

目标：`rehab-module-enable-clean`，Mac 工作树 `/Users/saber/Documents/Playground/rehab-module-enable-clean`；审查时 `master` HEAD `49b3e40`，浅克隆，Git 索引 6,812 个跟踪路径。审查为**抽样静态核对＋非破坏性定向测试**，不是全量构建、正式安装或真实患者环境验收。未运行数据库迁移 `apply`，未部署、推送、修改业务代码或接触业务卷。

> **后续复核说明：**本文件保存首次审查时点的发现，不代表修补后的实时工作树状态。已增补 CI/桌面发行 TS 硬门禁、UniApp 三目标源码构建 job、迁移说明/回归断言及患者 403/邀请摘要负例；隔离 Git 对象完整 fsck 已退出 0。修补过程见[门禁修补记录](mcp-integrity-checks-build-hardening-2026-09-27.md)，各项测试的最新真实终态见[顺序复核实测结果](mcp-sequential-validation-2026-09-27.md)；勿把首次审查的“未完成”误读为补丁已验证通过。

## 覆盖面与本轮证据

| 范围 | 核查结果 | 依据/限制 |
| --- | --- | --- |
| 组件与交付入口 | Maven 主工程、System/Infra/Rehab/Member/BPM/CRM 等模块，ERP/Report 的构建 opt-in、Vue 管理端、UniApp、Tauri、本机/LAN 部署和三条 CI workflow 均存在 | `pom.xml`、`.github/workflows/{ci,release,desktop-release}.yml`、`AGENTS.md`；目录存在不等于业务已实现或可发布 |
| 迁移清单 | `001`—`023` 文件校验通过；`test_migration_guard.py` 23 项运行，20 通过、3 跳过 | `deploy/internal/migrate.sh verify-files` 和 Python 单测本轮退出码 0；未对现有或隔离数据库执行迁移 |
| 桌面运行资源脚本 | `node --test desktop/scripts/*.test.mjs` 12/12 通过 | 覆盖构建凭据、旧产物拒绝、卷冲突保护等；不是原生安装、签名、真机或容器 E2E 通过证明 |
| 工作树与 Git | 存在未提交源码/文档变更；本轮仅取得部分路径的 `git diff --name-only`，未获得可信的**全仓**状态或 Git 对象完整性结论 | `git status` 全仓索引刷新很慢；`git fsck --connectivity-only` 30 秒无终态，已软中止；不得把 HEAD 上 CI/历史测试直接套用到当前工作树 |
| 文档引用 | 对 `docs/mcp-*.md` 的本地相对链接抽样解析 4 条，未发现不存在的目标 | 不覆盖普通文档、外链和锚点；历史对话提及的 `docs/mcp-full-build-validation-2026-09-26.md` 在此工作树未找到，需核查证据是否保存在其他位置 |
| 敏感材料检查 | 仅确认私钥与部署 secrets 目录中被 Git 跟踪的是 `README.md`；仓库敏感材料脚本和 Git 全量扫描未完成 | 扫描因文件系统慢而软中止；**不能**据此声称全仓无凭据或患者数据 |

## 主要缺口（按优先级）

1. **P0｜患者访问仍不可发布。** `yudao-module-rehab/.../RehabAppPatientController.java` 与 `docs/patient-auth-release-gates.md` 显示旧手机号＋患者编号登录/绑定及患者数据路由仍闭锁；邀请原语未连接短信、授权发放、原子消费、令牌撤销与微信真机。维持 403，先在隔离库完成身份/租户/权限与旧令牌负例；生产切换须获审批、备份恢复及平台验收，详见 [微信首发门禁](mcp-patient-wechat-release-readiness-2026-09-27.md)。
2. **P0｜质量门禁不覆盖实际交付面。** `.github/workflows/ci.yml` 的前端 job 仅运行 `build:internal` 与依赖审计，没有 `pnpm ts:check`；没有 UniApp `type-check`、H5/微信/App 构建 job。历史 `docs/build-verification-2026-09-23.md` 记载 1,129 条管理端类型诊断，但**不是本轮复测**。建议先保存按模块分类的当前基线，阻断新增诊断，逐模块清理后将全量零错误纳入合并与发布门禁；UniApp 增加独立 CI 矩阵，区分代码构建与平台发布。
3. **P0｜构建、迁移与文档状态未统一。** `deploy/internal/migrations.manifest` 已到 023，而 `deploy/internal/README.md` 的“19 个版本”和 `deploy/lan/README.md` 的“001–019”未区分“空库预置基线 001–019”和“需要实际执行的增量 020–023”；不能依据旧说明给数据库做 baseline。建议用单一生成的迁移状态表展示每个部署形态的支持版本和升级路径，并对文档数字做 CI 检查；增量先在隔离副本做前滚、失败和恢复演练。
4. **P1｜仓库卫生与供应链复现。** `git ls-files` 显示 `.DS_Store`、管理端 `.env.local` 被跟踪，即使 `.gitignore` 包含 `**/.DS_Store` 也不会解除已跟踪状态；不据此断言文件含秘密，应先在受控环境审查，若为机器专用配置则迁移为无密钥模板并停止跟踪，完善历史泄露评估。`.github/workflows/ci.yml` 与 `release.yml` 用 pnpm `10.11.0`，而 `desktop-release.yml`/UniApp/启动器用 `10.15.1`；统一工具链版本并在 CI 验证 lockfile、Node/JDK/Rust 与镜像摘要。完整敏感材料检查应在干净、非同步目录重跑并保留退出状态。
5. **P1｜可构建不等于可启用。** `pom.xml` 的 ERP/Report 仅 opt-in 构建；`docs/mcp-cross-platform-installer-release-plan-2026-09-26.md` 仍将机构客户端、Windows/macOS 原生安装验收及微信 AppID/真机列为待办。维持默认包与构建用可选包分离，逐模块补表结构、租户/角色/字段权限、迁移/备份恢复、端到端证据；机构客户端不能以本机 Docker 启动器替代。
6. **P1｜工作树与发布凭据无法闭环。** 本地有 `desktop/build/frontend-build-receipt.json` 与 `desktop/runtime/1.0.0`，但当前工作树存在未提交变更，不能仅凭文件存在认定匹配源码；历史对话所指全仓构建报告未在此树定位。建议冻结目标提交，保存实际命令退出码、测试跳过分类、JAR/前端/运行资源哈希，运行 `check-runtime` 和隔离 E2E，最后基于同一提交在 CI 生成签名发行物。勿用旧产物或另一检出的页面证明新补丁已部署。

## 推荐执行次序和可验收结果

1. **建立可信基线**：将未提交变更分组审阅；在非 iCloud/非同步干净副本重跑 `git fsck`、全仓状态和敏感扫描，固定 commit/SHA 与产物清单。验收：可复现且结果可追溯，不包含凭据/患者资料。
2. **补自动化门禁**：为管理端加当前诊断基线与“无新增”阶段门禁，逐模块清零；加入 UniApp 三目标构建/类型检查、数据库迁移测试及关键患者权限负例。验收：CI 失败确实阻止不合格变更发布，跳过测试单列而非记为通过。
3. **隔离发布演练**：按交付形态分别执行空库/已有库迁移、数据库＋附件恢复、脱敏数据 E2E、安装/升级/卸载；仅在证书、AppID、真机、审核、患者认证和责任人签署齐全后标记正式发行。验收证据应回写到本仓 `docs/mcp-*.md` 并指向对应提交与命令日志。

## 未完成的核查

本轮不认定管理端全量 `ts:check`、当前 UniApp 类型检查、运行包 `check-runtime`、全 Git 对象、敏感材料扫描、全部 Maven 模块、真实 Docker 链路或签名/真机状态为通过。UniApp `pnpm type-check` 与运行包 `check-runtime` 在本机超过两分钟未产生终态，已软中止；敏感材料扫描和 Git fsck 也因同类文件系统延迟中止。后续应在非同步隔离副本复跑并记录退出码，不以历史结果代替。
