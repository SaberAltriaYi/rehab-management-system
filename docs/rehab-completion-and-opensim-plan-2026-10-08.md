# 康复管理系统待完成工作与 OpenSim 集成路线图

> 核对日期：2026-10-08（北京时间） · 类型：**项目工作清单与架构建议**，不是已完成验收报告。
> 本文基于本机 Git、仓库文档、2026-09-27 历史 Agent 交接、2026-09-30 动作评估联调记录与 2026-10-08 状态盘点。
> `已实现` 只表示指定分支上有代码；`历史通过` 只对原测试日期/提交/样本成立；任何发布均须从目标提交重新执行验收。**此轮仅调整文档和旧桌面发布附件，不执行源码合并、患者数据操作或服务部署。**

## 1. 产品定位与执行顺序

**定位**：一个以 `rehab-management-system` 为唯一患者/业务主系统的工作室康复平台；其运动分析由独立 `rehab-biomechanics-engine` 计算、OpenCap 提供动作重建结果，OpenSim 作为**可选**后处理/建模分析层；独立 `rehab-reporter` 的报告能力须统一入口，不能产生第二套独立临床结论。

**建议优先级**：

1. P0：稳定动作评估及两个未提交开发工作树 → 双仓契约/迁移/权限回归 → 评审合并。
2. P0：从合并后的固定提交执行发布门禁；真实患者登录在强身份门禁通过前保持关闭。
3. P1：统一报告数据源/审核锁版与验证；并行开展 OpenSim **离线技术验证**。
4. P1/P2：OpenSim 研究拓展、临床协议/青少年适用性与商业许可核查。
5. P2：完善训练执行器、患者与家长端、n8n/Agent 自动化和预约运营能力。

不以单一“项目完成百分比”报告进度；验收分别按代码、集成、临床、发行、患者访问五种证据核实。

## 2. 实际仓库基线与数据边界

| 组成 | 本机基线（2026-10-08） | 当前状态及不能推断的结论 |
| --- | --- | --- |
| `rehab/rehab-management-system` | `master`，`3d778538a3` | 工作树干净；已有患者、Episode、结构化评估、报告、训练计划/任务、打卡和复评；不等于患者 App 已开放或正式发行通过 |
| `rehab/worktrees/motion-assessment` | `feature/motion-assessment`，`2f7f515973` | 额外 4 个提交，**3 个 Java 文件未提交**；已有 OpenCap 导入、Trial、质控、异步任务、审核签署、报告与趋势；未合入主线 |
| `rehab/worktrees/engine-motion-assessment` | `feature/motion-assessment`，`a31deb2` | 工作树干净；用于临床动作评估的 OpenCap/指标/评分服务，独立于研究工作台 |
| `rehab/services/rehab-biomechanics-engine` | `research/phase5-7-workbench`，`ab6e3ac` | **4 个文件修改 + 1 个新测试文件未提交**；合成深蹲研究工作台，不等于真实人群临床验证 |
| `rehab/services/rehab-reporter` | `main`，`c1a0563` / tag `v1.0.0` | 独立 Python 报告服务；尚未统一 Java V4.1 正式报告入口 |
| `rehab-runtime` | 独立运行目录 | 包含报告/验证运行数据；不是源码归档，不读取、不移动、不清理患者数据、数据库或密钥 |

历史证据：2026-09-30 动作评估引擎 364 个测试、Java rehab 158 个测试通过，并在隔离容器/浏览器做过两种 OpenCap 数据导入路径；研究工作台的 297 个离线测试属于 2026-09-29 合成验收。2026-10-08 盘点另记录研究分支当前工作树 299 个测试、Ruff 通过，但均**不代替**合并后跨服务回归或临床效度。

### 2.1 算法部署拓扑：本地与云端分离

```text
多相机视频 / 被授权的 OpenCap Session
    → OpenCap 官方处理服务：姿态/标记推断与 OpenSim 逆运动学（目前通常在云端）
    → 结构化产物：.mot / .trc / .osim / 采集与标定元数据
    → 独立 Python Engine（本地 Mac、局域网服务器或受控私有容器）：
         导入安全检查 → 单位/模型/信号质量 → 运动学/分期/规则/来源
         └→ [可选 OpenSim Worker：模型几何、正向运动学、动力学研究]
    → Java Rehab 主系统：权限/患者归属 → 审核 → 签署与版本化报告
    → 治疗师建议的计划 → 训练记录 → 相同协议下复评
```

本地 Engine 的离线分析可以不联网；但是它**不会自动把原视频变成 3D 运动学**。若要让视频重建也完全离线，须另立自托管 OpenCap 处理项目，核对 GPU、依赖、许可证和隐私；不能因为 Docker Engine 能运行就声称采集/重建全本地化。真实患者视频/健康数据使用官方云端前，必须额外完成同意、数据处理与跨境/驻留审查。

## 3. P0：发布前必须收束的工程工作

| 任务 ID | 待完成工作 | 建议行动 | 完成/验收标准 |
| --- | --- | --- | --- |
| P0-01 | 动作评估 3 文件未提交 | 审阅 `MotionEngineRequestBuilder`、`RehabMotionAssessmentService`、`MotionTrialConditionTest`；分别覆盖 YBT 请求条件、临床字段更新时保留 Trial `conditionCode`；独立提交 | 未选 YBT 且无 YBT Trial/录入时不生成 YBT 待审核项；改变疼痛/有效性不丢试次条件；定向及 rehab 模块测试通过；签署阻断保持正确 |
| P0-02 | 研究工作台 5 项未提交 | 审阅本机无凭据演示开关 `--local-no-auth`；隔离 loopback、Host/Origin、真实连接来源，不信任伪造代理头；默认认证不能被自动绕过 | 仅明确指定的 loopback 合成演示可免凭据；默认 Bearer 模式不退化；完整离线测试、Ruff、安全负例通过；提交明确版本 |
| P0-03 | Java 与 Engine 跨仓契约 | 固定双方 commit；在**隔离数据卷**重走 OpenCap 导出上传、云端只读 Session/Trial 拉取、手工分级、重算、审核、签署、更正、两版报告、初评复评对比 | 业务 ID/租户、侧别、评分方案、模型、协议与结果版本匹配；超时重试/取消/重复触发/错误输入可解释；无旧结果被静默覆盖 |
| P0-04 | 数据库/部署迁移 | 验证 020–025 迁移的 `apply/adopt`、已存在数据库兼容、审计列、备份恢复；对 Docker 隔离环境做可重放预检 | `migrations.manifest` 与文件哈希一致；新旧库迁移测试、真实 MySQL 路径、回滚演练通过，不删除业务卷、不改已执行的迁移历史 |
| P0-05 | 主线合并与重新构建 | 通过 PR 评审合并，而非直接覆盖 master；新合并提交分别执行 JDK17 Maven、完整前端类型检查/构建、移动端、安全、桌面 runtime 同源校验与依赖审计 | 各必须门禁是**当前提交**退出码 0；记录前端实际诊断（历史 1127 等不能当当前数），不跳过失败、不过期复用旧构建回执 |
| P0-06 | 患者真实身份（开放前阻断） | 机构线下核验、短期单次邀请、短信/已登记号码验证、限频/防枚举、租户和监护人授权、会话轮换与撤销 | 并发/重放/跨租户/旧 token 负例、真机安全存储及平台验收通过前，患者 API 与登录仍关闭；不通过环境开关绕过 |
| P0-07 | 桌面发行和部署交付 | 规范版本—提交—源码哈希—安装包哈希映射，核对内嵌 Vue/Java/SQL/Runtime 的同源性；做 Windows/macOS 安装、升级、卸载、回滚、备份恢复、签名与公证 | 可信干净机器及原有数据升级验收；签名/公证、依赖/秘钥扫描与 SHA-256 完整；内部 unsigned preview 不称正式发行 |

### P0 的已知遗漏与特殊边界

- 2026-09-30 实样本没有覆盖全部 FMS 七项，也没有 3 次有效 LESS，不能用“有真实样本”替代协议完整验收。
- 真实云端 OpenCap 联调与本地研究 Squat Lab 属不同范围；Squat Lab 的合成准确率不能推广到门诊患者。
- 真实患者端保持安全关闭，已有基础 UniApp 页面不能算已经可以开放给患者使用。
- 不直接合并工作树脏改动；提交前保存差异、执行定向测试、人工审阅后再合并。

## 4. P1：评估与报告产品闭环

| 任务 ID | 工作 | 建议交付/完成标准 |
| --- | --- | --- |
| P1-01 | 协议和临床验证 | 冻结 FMS / NASM-CES / YBT / LESS / Tuck Jump 的动作定义、条件、评分版本；确认 NASM 肩水平外展、肩旋转方向；成人和青少年分别设计效度、信度、评估者一致性研究；未经验证标“描述性/待人工确认” |
| P1-02 | 统一 Java 与 Python 报告主链路 | 选定 Java 正式入口；定义 `Assessment Context Package`、质量/来源/缺失/冲突/人工观察模型，Python 作为可替换的受控生成服务；同一数据源输出治疗师完整版、客户/家长简版，禁止互相矛盾的两套解释 |
| P1-03 | 报告审核、锁版与复评 | 结果与来源 ID 可追溯；缺失不补造；AI 只生成草稿；治疗师签署后不可变，后续更正另立修订；复评区分测量误差、结构性改变与临床效果 |
| P1-04 | 真实客户服务安全和方法许可 | 核查 OpenCap 云服务、OpenPose/模型权重、FMS/NASM/SFMA 方法及商用授权；最小化视频留存、儿童监护同意、脱敏/跨境处理、删除和审计，未经放行只使用合成或合法授权数据 |
| P1-05 | 专业评估交互 | 在已有试次、曲线、证据、报告组件上增加采集 SOP、质量失败引导、缺失原因、需要复拍提示及协议版本对照；不要把 2D 代理量错误称为 3D 关节角或真实肌肉力 |

## 5. OpenCap → OpenSim：增量集成路线（新增规划，非已实现）

**架构规则**：OpenCap 已利用 OpenSim 模型和逆运动学得到关节坐标。独立 OpenSim Worker **不应无条件重跑 IK**；必须先说明要解决的指标问题、需要什么数据和如何验证。如果没有可靠外力数据，不给出已验证的逆动力学关节力矩或组织负荷。

| 里程碑 | 技术范围 | 输入前提 | 验收/退出条件 |
| --- | --- | --- | --- |
| OS-0：独立运行时 | `OpenSimAdapter` + 可选原生 Worker；锁定模型和 OpenSim 版本、Python 3.11 / macOS arm64 / Linux 容器兼容矩阵；单独测试，不污染基础引擎 | 合成 `.osim/.mot/.trc` 与合法元数据 | `opensim.GetVersionAndDate()`、模型加载、轨迹应用、跨平台 smoke、并发资源限额全部可重复；OpenSim 不可用时基础分析仍成功 |
| OS-1：几何与 FK | 检查模型缩放/坐标正方向；验证 Forward Kinematics、刚体/关节相对位置、肌肉肌腱长度及力臂等几何量（以模型支持为前提） | 已确认模型、坐标、单位、时间和缩放 | 与 OpenCap/参考 OpenSim 脚本数值比对；来源、误差、模型不适配原因可追溯，不虚构全局坐标指标 |
| OS-2：逆动力学研究 | 对有条件的步态、下蹲、落地等估计关节**净力矩**；区分实测 GRF/COP 与预测外力 | 同步且坐标正确的地面反作用力、作用点、外力矩，可信体重/模型/外部载荷；若仅估计 GRF，需额外校验 | 先用同步力台基准样本对照，开展敏感性/误差分析；缺外力自动 `unavailable`，不把模型预测称为直接测量 |
| OS-3：肌肉与高阶模拟 | 在经验证的动力学数据上试验 Static Optimization、肌肉激活/肌肉力估计，必要时再研究 Moco | 已通过 OS-2 的数据质量/模型验证及研究授权 | 有独立参照与参数敏感性报告；输出带 `model_estimate`，不自动下临床诊断或处方 |
| OS-4：业务接入与 UI | Java 发起可选 `analysis_type=opensim_*` 作业，独立队列/资源配额，产出版本化补充结果并关联已存在 assessment/trial；治疗师确认后纳入报告 | 固定 schema/服务身份/租户/来源及可追溯版本 | OpenSim 失败不阻断已通过的基础报告；签署结果不能原地覆盖；升级/回退与权限、审计测试通过 |

建议采用 `basic_kinematics`（默认）和 `opensim_advanced`（按需）双通道，并让分析结果标注 `measured / derived / model_estimate / proxy / unavailable`。应先做 OS-0/OS-1，不以 OS-2/OS-3 作为当前 P0 合并前置条件。

**临床安全边界**：肌肉长度变化、肌肉力估计、关节净力矩、组织应力是不同物理量；不能用 ROM、FPPA、关节净力矩互相冒名。未验证青少年参考值前不输出正常/异常和损伤风险阈值。

## 6. P2：客户可持续使用的产品能力

| 任务 ID | 建议功能 | 最小可验收流程 |
| --- | --- | --- |
| P2-01 | 训练执行器 | 复用 Plan/Task/Checkin；热身/主训练/拉伸、逐组确认、计时休息、动作变更、次数/负荷/RPE/疼痛记录、异常上报；断网重试与审计 |
| P2-02 | 客户/家长端 | 在 P0-06 门禁完成后，以经过授权的患者/监护人身份访问已签署报告、训练任务、随访和复评；家长授权与患者账号分离 |
| P2-03 | 预约/课包与机构运营 | 在现有患者/Episode/课程签到与 CRM 关联关系上形成独立数据模型，先定义预约、课时核销、改期、退款、权限及财务审计，不引入第二套患者主档 |
| P2-04 | n8n + Agent 首条工作流 | 先用合成评估输入：触发 → Schema 校验 → 缺失/冲突报告 → 结构化解释草稿 → 人工审核任务；保存版本、幂等键、失败重试、回滚与人工动作日志；AI 无签署/改分权限 |
| P2-05 | 运营与反馈 | 以评估/报告/训练完成率、异常、复评回访为 KPI；用户可见内容不含未经验证的临床判断，分析数据不混入营销用途 |

## 7. 目标客户旅程与实现状态

| 步骤 | 成品目标 | 现有事实 |
| --- | --- | --- |
| 建档与服务授权 | 治疗师或前台建患者/Episode、视频/监护授权 | 主系统已有建档与评估；完整移动患者身份尚关闭 |
| 采集动作 | 选择协议并引导 2+ 相机采集；必要时人工/离线数据 | OpenCap 采集通常在其工具完成；一体化拍摄引导仍是目标 |
| 获取/映射 Trial | 选本地导出或只读云端 Session，确认侧别/测试/重复/条件 | 动作评估开发分支已实现并在隔离环境联调 |
| 分析与质控 | Engine 解析、质量门控、运动学和明确的规则建议 | 临床动作评估分支有代码和历史验收；OpenSim 高级分析尚未实现 |
| 审核/交付 | 治疗师确认、签署，生成两版报告 | 分支实现动作报告；Java V4.1 与 Python Reporter 尚未统一 |
| 训练/反馈 | 客户端逐组训练记录，治疗师可调整 | 基础计划/任务/打卡已有，完整训练执行器待开发 |
| 复评/比较 | 相同采集协议下复测并解释可重复的变化 | 初评复评比较已有实现；误差、MDC/临床验证仍需补齐 |

## 8. 第一批建议实际领取的工作（避免范围失控）

**Batch A：稳定已建成的评估链路**

1. 保存/审阅动作评估三个未提交文件的 diff；补测“只填肢长不生成 YBT”和“临床字段更新不清除分级条件”，定向回归后形成独立 commit。
2. 对本地研究演示五项未提交内容进行安全审阅，确保仅本机显式合成模式可用；单独提交，**不**与临床分支混合发布。
3. 固定 Java/Engine 双 commit 与隔离环境：重放 OpenCap 上传/云端导入、患者与租户负例、人工分级、重算、签署、报告、020–025 迁移与回滚。
4. 完成 PR 评审后合并；在合并 commit 重新做全量构建和门禁。若任一项失败，留失败证据和阻断状态，不报告发布完成。

**Batch B：新能力设计，不抢跑**：形成 OpenSim OS-0/OS-1 ADR + 合成/有合法授权样本矩阵 + 结果扩展 schema 草案；与 P1 报告统一设计并行，但不将未验证 OpenSim 结果写入已签署报告。

每批记录：`repo / branch / commit / dirty diff / fixture 来源与授权 / 测试命令、退出码与失败项 / 版本化契约 / 审批与回滚步骤`。

## 9. 历史桌面包清理记录（仅 GitHub Release 附件）

- 2026-10-08：按要求从 [最早发布的 `desktop-v1.0.0-preview.1`](https://github.com/SaberAltriaYi/rehab-management-system/releases/tag/desktop-v1.0.0-preview.1) 删除 macOS unsigned DMG（157,903,942 B）和 Windows unsigned EXE（148,923,580 B），同时移除已失效的 `SHA256SUMS.txt`。
- **保留**：Release 页面及构建信息 `BUILD-INFO-*`、Git tag/commit、`preview.2/.3/.4` 其他桌面发布及其附件、本地 `archives/rehab-archive/` 历史保全；没有清理 Git 历史或数据卷。
- README 仍指向 [`preview.3`](https://github.com/SaberAltriaYi/rehab-management-system/releases/tag/desktop-v1.0.0-preview.3) 供可信内部测试，不代表签名/公证和正式上线门禁通过。

## 10. 关键文件与工作位置

- 主仓规范：`AGENTS.md`、`README.md`、`docs/releases/VERSIONS.md`、`docs/patient-auth-release-gates.md`。
- 临床动作评估：`rehab/worktrees/motion-assessment/docs/motion-assessment/{README,testing,security,clinical-validation-plan,open-questions}.md`（相对 `my-project`）。
- 引擎临床分支：`rehab/worktrees/engine-motion-assessment/docs/motion-assessment.md`。
- 引擎研究分支：`rehab/services/rehab-biomechanics-engine/{README,ROADMAP}.md` 与 `docs/{opencap-integration,architecture}.md`。
- 独立报告：`rehab/services/rehab-reporter/README.md` 和 `docs/qa/release_checklist_v1_0_0.md`。
- 历史交接资料：`/Users/saber/Downloads/arena/rehab-management-system/rehab-management-system-handoff-20260927/CONVERSATION_RECORD.md` 与 `project-status-roadmap-2026-10-08.md`（非逐字聊天导出）。

> **执行护栏**：所有患者数据、密钥、数据库、Docker volumes 保持原状；架构和临床验证要先定数据契约与失败语义，再开发自动分析；未完成的认证、伦理、许可、临床验证与签名状态不得以产品演示成功代替。
