# 康复项目完成度核验报告

核验日期：2026-09-27。范围：`/Users/saber/Documents/playground` 与 `/Users/saber/Downloads/arena`。

## 一、结论

**项目已经不是概念演示，而是有可运行后台、康复业务实现和部分隔离验收证据的工程内测版；但既未完成全部升级路线图，也未达到正式桌面发行或真实患者小程序开放条件。**

应分开评价三件事：

- **核心后台功能**：患者/Episode、评估、报告、计划、执行和复评已有实现；手动草稿等关键路径有历史验收。
- **完整产品范围**：高级草稿与并发、统一 Client/预约/课程包、完整原件资料包、机构客户端和跨设备闭环仍未交付完整证据。
- **可正式交付程度**：阻断。类型检查未有当前通过证据，当前源码与既存构建回执不匹配，患者强身份认证、签名/公证及真机/平台验收尚缺。

**不建议给出“整体完成 90%”之类单一数字。** 历史阶段表未同步所有后续进展，项目范围又包含不同成熟度的 Web、桌面、机构运营、手机端和发行工作；既没有经过确认的任务权重，也没有完整验收分母。下面以实际达到的阶段和证据说明完成度。

## 二、先明确是哪一套项目

| 目录 | 本次确认 | 使用口径 |
| --- | --- | --- |
| `rehab-module-enable-clean` | `master`，HEAD `49b3e40395bcd37b37b30f3dfc49d27c28939188`；本轮 `git status --porcelain=v1 -uno` 退出 0，返回 **329 条已跟踪修改**，均为未暂存修改 | 当前康复管理系统主线。329 不含未跟踪文件；不能仅凭 HEAD 重建当前工作树 |
| `rehab-management-integrate-all` | HEAD `bafc44104a`，2026-09-20 | 较早实施检出，不作为今日发布源 |
| `rehab-module-enable-work` | HEAD `bafc441` | 较早模块接入检出 |
| `rehab-management-desktop-packaging` | HEAD `fed4520eb2`，`agent/desktop-packaging-v1` | 较早桌面打包检出 |
| `ruoyi-vue-pro` | HEAD `89aef780b9`，`agent/lan-one-click-deployment` | 旧部署开发检出 |
| `ruoyi-vue-pro-github` | HEAD `d3400b70d6` | 上游参考检出 |
| 顶层 `rehab_reporter`、`templates`、`tests` | 独立 FastAPI 报告项目；README 描述“综合运动康复评估系统”；发现 28 个测试文件 | 与 Java/Vue 康复系统分开评价。本轮未运行该 Python 项目，不给它套用主线测试结果 |

顶层 Git 仓库显示 `No commits yet on master`，但各子仓库有自己的历史。不要把顶层未跟踪状态理解为所有子项目都没有提交。

## 三、历史 agent 材料核对

`Downloads/arena` 主要是 **工作区快照、工具输出、源码及阶段报告**，本次未发现可确认为完整问答逐字稿的聊天导出；不声称已通读所有历史对话。

已核对的主要材料：

1. `workspace-01a0be2e-6870-7d5d-ac8f-3f6ced05912f`：技术审计、分阶段计划、SFMA 安全草稿设计、批次 01 留存成果。
2. `workspace-01a0ccc3-a391-7dfb-88f0-b5b5a85e3fdf`：业务模块隔离验收、构建记录及源码快照。
3. `workspace-01a0e115-6616-7627-b889-a0851bafaa4b.zip`：2026-09-27 完整性审查、顺序复核、类型修补/资源重建报告及诊断日志。直接在 ZIP 中读取，未解压覆盖项目。
4. `workspace-01a0d230-2447-7720-ae9b-47b756e0dd38.zip`：网页和图片，与康复项目无关，未计入完成度。

历史记录的时间关系很重要：

- 9 月 23–24 日记录的管理端 1,129 条类型诊断、9 月 24 日 JSX 构建异常，不能直接当作今天的最终状态。
- 9 月 27 日顺序复核记录：标准内部构建成功；生成类型声明后，类型检查退出 2、600 条诊断。
- 同日后续修补记录：标准内部构建再次成功；类型检查仍退出 2、**470 条诊断、154 个文件**；另生成非发布候选资源。
- 本轮从 ZIP 的 `remaining-ts-diagnostics.log` 独立统计，确认为 470 条、154 个文件。这是**历史日志核对**，不是本轮全量类型检查结果。

## 四、分模块完成度

| 方向 | 已达到的阶段 | 尚未完成或不得扩大的结论 |
| --- | --- | --- |
| 康复 Web 后台 | 有患者、Episode、评估、报告、计划、打卡、复评等服务代码和测试；本轮首页 HTTP 200、后端健康 `UP` | 本轮未登录、未进行新业务 CRUD 或全角色 E2E；不能证明今天所有未提交补丁已部署 |
| SFMA / 综合评估 | 手动保存草稿、编辑归属保护、恢复内容、离开确认已有代码；9 月 20 日有合成数据浏览器验收 | 不能称自动保存完成；revision 乐观并发、弱网恢复、完整确认/修订流程及手机/平板现场验收仍缺 |
| 康复报告 | `RehabReportServiceImpl` 存在生成及 DOCX/PDF 导出实现；README 说明 17 页 V4.1 模板 | 不等于统一版本化快照、全部原件、多格式确定性导出和完整资料包已经完成；本轮未新生成报告 |
| 机构运营 | CRM/BPM/ERP/Member/GoView 的部分审阅路径有隔离测试证据；9 月 24 日记录 15/15 所选路径通过，库存超额出库拒绝等负例成立 | 不是所有模块、全部权限矩阵或真实旧库迁移均完成。第三方设计器、任意 SQL 和未审业务仍不能全部开放 |
| 构建与 CI | 已有标准管理端构建成功记录；CI/发布流程加入阻断式类型检查和 UniApp 构建检查；本轮资源工具测试 12/12 通过 | 管理端尚无当前全量类型检查通过证据；CI 配置存在不等于 GitHub runner 已执行通过 |
| 桌面运行资源 | 今天留有独立的“类型检查阻塞候选”；历史记录有同源构建、完整性和隔离 Docker E2E 通过 | 旧 runtime 有额外文件；新候选与本轮当前源码指纹不同；不能直接作为当前发行包 |
| Windows/macOS 本机一体包 | 已有 Tauri/Docker 方案、打包脚本及 unsigned 预发布说明 | 当前版本签名/公证、干净设备安装/升级/卸载、同源发行闭环未完成；不是正式桌面版已交付 |
| Windows/macOS 机构客户端 | 已有新增发行形态的设计目标 | 连接机构服务器、无需本地 Docker 的独立客户端形态仍属未交付；不能用现有本机启动器替代 |
| UniApp 员工/患者端 | MVP 源码存在；9 月 27 日记录 type-check 和 H5/微信/App-Plus 代码构建通过 | 本轮客户端目录未发现 APK/AAB/IPA/WGT；微信和 DCloud AppID 为空；无本轮平台发布/真机通过证据 |
| 患者真实环境开放 | 已加前后端拒绝门禁、旧患者会话拒用；有邀请令牌原语 | 仍不可开放。机构发放邀请、短信独立证明、原子消费、限频防枚举、令牌轮换/撤销和平台安全验收未完成 |
| 完整业务升级路线图 | 已有分阶段计划和部分增量实现 | 统一 Client、独立预约/课程包、完整原件资料管理、高级纵向闭环和全系统验收仍有明显缺口 |

### 历史验收数字的边界

- 56/56：当时可见菜单的只读导航，不是 56 个业务模块全部验收。
- 15/15：9 月 24 日选定页面/API 路径，不是全系统用例通过率。
- 库存“入 5、出 2、余 3，超额拒绝”：历史隔离库接口操作加浏览器余额复核，不是全部表单逐键 UI 测试。
- 9 月 27 日默认 Maven reactor：历史记录 **832 项，796 通过、36 跳过、0 失败/错误**。本轮未重跑；不能写成 832 项全部通过。
- 同日迁移单测：历史记录 **24 项，21 通过、3 跳过**；不是本轮生产数据库迁移完成。
- 9 月 24 日记录现行站点切换到隔离库，旧患者记录及附件未迁移，旧卷按当时用户选择清除。本轮没有操作任何数据库/数据卷，也不把该切换说成旧库无损升级。

## 五、本轮新增实测：必须重视的交付问题

### 1. 当前源码与两份前端构建回执均不一致

本轮按照 `desktop/scripts/frontend-receipt.mjs` 的输入清单、元数据排除规则、JavaScript 字符串排序及 SHA-256 聚合格式，用原生 Node 重新计算 **1,703 个前端构建输入文件**的摘要；不输出配置文件内容。

| 对象 | 源码 SHA-256 |
| --- | --- |
| 本轮当前前端源码 | `28574d1bb0c0660a38ef642b8550bc887617dc1595afdfbbc4b1b122fae5a20c` |
| `desktop/build/frontend-build-receipt.json` | `c6e5ac15b9fbcdee3129e4f1d100e705c5299cb833bb4d4cba5f663ae9cab3b8` |
| `desktop/build/type-blocked-candidate-20260927/frontend-build-receipt.json` | `67d261318a75836d369306218e078749a720a8af0c151934cd2d6a1fb0f02d67` |

**结论：两份既存回执都不能作为当前工作树的同源构建证明。** 这不否定它们在各自生成时的历史测试记录，也不推断差异由谁造成；但应重新冻结并验证源码，不能继续沿用旧回执发布。HEAD 相同不足以抵消数百处未提交修改。

### 2. 旧运行资源与新候选必须分开

- 旧 `desktop/runtime/1.0.0`：manifest 列 840 项，目录实有 849 个文件（含 manifest），没有清单所列文件缺失，但多出 **8 个未入清单文件**：`BUILD-INFO 2.txt`、`LICENSE 2`、`NOTICE 2.md`、`RUNTIME-README 2.txt`、`THIRD_PARTY_NOTICES 2.md`、`VERSION 2.json`、`docker-compose 2.yml`、`runtime-manifest 2.sha256`。这是本轮新盘点；历史完整校验对此退出 1。
- 新 `desktop/build/type-blocked-candidate-20260927/1.0.0`：manifest 列 841 项，目录实有 842 个文件（含 manifest），**文件集合没有缺项或额外项**。文件集合匹配不等于每个字节哈希均已复验通过。
- 新候选本轮 `check-runtime` 运行约 122 秒未正常结束，已软中止。虽然取消包装返回 exit code 0，MCP 状态是 `cancelled`，**不得记作校验通过**。

### 3. 患者端是硬关闭，不是一个可随意打开的开关

已读当前源码：

- `RehabAppPatientController.java:32–46`：旧登录/绑定显式返回 HTTP 403。
- 同文件 `:49–150`：患者摘要、报告、计划、任务、打卡、资料、通知等入口先执行拒绝逻辑。
- UniApp `src/lib/config.ts:5–7`：`PATIENT_LOGIN_ENABLED = false`，不允许环境变量恢复旧认证。
- UniApp `src/lib/session.ts:18–42`：拒用旧患者会话，并禁止持久化患者模式会话。
- UniApp `src/manifest.json:3,27`：DCloud 与微信 AppID 为空。

这些保护应保留。当前不能通过改一个布尔值把真实患者端“上线”。

### 4. 当前运行服务健康，但不等于当前源码已验收

- `https://127.0.0.1:8443/index`：本轮 HTTP 200。仅可达性检查使用了 `curl --insecure`，**不是 TLS 证书验收**。
- `http://127.0.0.1:48090/actuator/health`：HTTP 200，`status=UP`。
- Docker 列表显示后台、后端、MySQL、Redis 在运行；未重启或修改容器。
- 磁盘本轮显示约 **14 GiB 可用、97% 已用**。完整类型检查和资源校验没有在限定时间内结束，原因未在本轮确诊，不简单归咎于磁盘或同步服务。
- 当前受管 Shell 的 `command -v cargo` 未找到 Cargo；没有安装工具链或进行原生打包。

## 六、本轮执行结果与限制

| 检查 | 本轮结果 |
| --- | --- |
| 项目/归档定位、主线 HEAD、已跟踪修改状态 | 已完成只读核实；主线 329 条已跟踪修改，不含未跟踪文件 |
| 归档剩余 TS 日志统计 | 470 条、154 文件；明确为历史证据 |
| 当前原树 `pnpm ts:check` | 约 237 秒无正常终态，软中止，状态 cancelled / exit 130；没有新诊断总数，不记通过 |
| `node --test desktop/scripts/*.test.mjs` | **退出 0，12 通过，0 失败，0 跳过**；测试只使用自身临时夹具，无业务卷操作 |
| 新候选 `check-runtime` | 约 122 秒后软中止；没有通过结论 |
| 原生 Node 源码摘要复算 | 退出 0；1,703 输入文件；与现存两份回执都不匹配 |
| 旧/新 runtime 文件集合盘点 | 旧资源多 8 项；新候选文件集合匹配，未替代完整哈希验证 |
| 页面/后端健康 | HTTP 200 / UP；无登录与真实患者数据读取 |
| 移动产物与配置 | 客户端目录未发现 APK/AAB/IPA/WGT；AppID 空；患者入口关闭 |

本轮未重新执行全 Maven reactor、全前端构建、UniApp 构建、登录后浏览器 E2E、真实设备测试、全仓敏感材料扫描或公开 GitHub Release 核验。因此安装包预发布描述来自本地 README/历史记录，不声称今天重新验证了线上最新发行资产。

本轮未修改业务源码、依赖锁、数据库、运行配置、部署或数据卷；未暂存、提交或推送。最终仅新增本报告。尝试的两个长检查均已通过 MCP 软中止并取得终态，没有以取消结果冒充通过。

## 七、建议接续顺序

1. **P0：保护并冻结现有成果。** 先分组审阅 329 条已跟踪修改及未跟踪文件，确认哪部分作为交付基线；不要为“清理工作区”执行 reset/clean。只读核实后再决定提交策略。
2. **P0：修复当前构建质量。** 在有足够空间、非同步且受控的隔离副本/CI，按标准构建生成声明，再取得当前 `ts:check` 基线并清零。470 是历史值，不直接当成当前值；保持阻断式检查。
3. **P0：重建并验证同源产物。** 最终源码冻结后重新构建前后端、生成回执和 runtime，核对源/产物/JAR 摘要，再跑完整性和隔离 E2E。新旧目录、默认版与业务 opt-in 版都要明确区分。
4. **P0：患者安全单独验收。** 机构核验与邀请记录、原子消费、短信验证码、限频防枚举、患者范围会话及撤销/轮换完成前，继续关闭患者入口。
5. **P1：先完成最小可交付形态。** 先收口 Windows/macOS 本机一体版；机构客户端另列实现任务。签名、公证、证书/AppID/HTTPS 域名、真机和发布账号属于真实前置，不是代码生成可替代的工作。
6. **P1：按业务优先级补路线图。** SFMA 并发/自动保存 → 原件及确定性资料包 → 统一 Client/预约/课程包 → 全角色、跨设备、恢复与升级验收。不要把所有上游菜单打开当作交付完成。

**最准确的当前定位：核心后台已有基础且部分路径可内测，工程质量与同源交付尚未收口，患者移动端及正式跨平台发行仍未完成。**

## 八、主要仓库依据

以下路径相对 `playground` 根目录：

- [主线 README](../rehab-module-enable-clean/README.md)
- [工作规则](../rehab-module-enable-clean/AGENTS.md)
- [分阶段实施计划](../rehab-module-enable-clean/docs/mcp-implementation-plan.md)
- [9 月 27 日顺序复核](../rehab-module-enable-clean/docs/mcp-sequential-validation-2026-09-27.md)
- [9 月 27 日类型修补与资源重建](../rehab-module-enable-clean/docs/mcp-admin-type-runtime-rebuild-2026-09-27.md)
- [完整性门禁修补](../rehab-module-enable-clean/docs/mcp-integrity-checks-build-hardening-2026-09-27.md)
- [患者微信开放前核验](../rehab-module-enable-clean/docs/mcp-patient-wechat-release-readiness-2026-09-27.md)
- [跨平台发行增补计划](../rehab-module-enable-clean/docs/mcp-cross-platform-installer-release-plan-2026-09-26.md)
- [9 月 24 日本机路径验收](../rehab-module-enable-clean/docs/local-single-8443-acceptance-2026-09-24.md)
- [9 月 23 日机构模块隔离验收](../rehab-module-enable-clean/docs/isolated-browser-acceptance-2026-09-23.md)
- [SFMA 批次 02 记录](../rehab-module-enable-clean/docs/mcp-execution-batch-02.md)
- [桌面发布清单](../rehab-module-enable-clean/docs/desktop-release-checklist.md)
- 源码证据：`yudao-module-rehab/.../RehabAssessmentServiceImpl.java`、`RehabReportServiceImpl.java`、`RehabAppPatientController.java`；UniApp 的 `src/lib/{config,auth,session}.ts` 与 `src/manifest.json`；`desktop/scripts/{frontend-receipt,runtime-tools}.mjs`；`.github/workflows/{ci,release,desktop-release}.yml`。
