# 青少年运动康复评估 B 版生产契约与工程差距

> 2026-10-08 · 规范性产品设计，**不是**已经通过的临床/发行验收。
>
> 依据：`青少年运动康复评估报告_治疗师生产母版_B版.docx`（V1.0，2026-09-24；29 页）与一份本地 48 页青少年专项运动评估示例（仅在授权的本地目录核对结构）。**不得将本地个案姓名、原始测量、视频、照片、影像、报告正文或可识别内容放入 Git、CI、测试快照或日志。** 此文只提炼规则/结构与可复用的质量边界，不保存个案内容。

## 1. 决策：双格式、单证据源、逐项确认

- **当前已实现的 Java V4.1 报告**：`RehabReportServiceImpl` 将原始模块整理成 17 个章节，`contentPolicy=raw-data-only`，其中临床推理、四域风险、KPI、训练与复评正文目前留空。不能把该版本标记为符合 B 版的正式治疗师报告。报告 JSON 新增 `adolescentProductionQuality`，前端和 HTML 预览已加提示；**当前没有启用 B 版文档生成器、也没有切换模板**。
- **目标治疗师 B 版**：同一组经过复核/锁定的原始数值和证据，分别产生结构化临床事实、互证、反证、工作假设、风险管理、KPI 与复评决策；由治疗师负责临床判断并签署。章节是否可省略按母版说明，核心安全、关键评估缺失必须说明影响。
- **目标家长/客户 B 版**：沿用相同已审核证据 ID 和结论版本，使用可理解语言，不复制未审核模型指标、不进行新诊断；不得维护另一套自由编辑的判断。
- **源数据冻结**：接收 → 冲突/占位/无效审核 → 必要 A/B/C 确认 → 形成 Source ID 与锁定版本 → 生成 → 治疗师签署 → DOCX/PDF 逐页 QA。源数据更新后，旧草稿失效；签署后通过修订记录，不原地覆盖。

## 2. 母版章节映射：0–20 共 21 个主题

| 编号 | 母版章节 | 必须具备的证据与处理 | 研发状态 |
| --- | --- | --- | --- |
| 0 | 一页临床决策摘要 | 安全 → 3–5 核心发现 → 正常反证 → 四域风险/管理等级 → 优先级 → 4–6 周 KPI | 未实现 B 版 |
| 1 | 基本信息与生长发育 | 出生日期/月龄、增长速度、训练暴露、运动目标、缺失影响；儿童 BMI 不套成人标准 | 基础字段已有，缺 B 版语义 |
| 2 | 疼痛、红旗与安全 | 部位/性质/VAS/任务、红旗阴性**有记录**、训练停止与转诊触发 | 现有原始字段；结构化完成度门禁待建 |
| 3 | Visbody 原始 PDF | 整页保留原文、日期、单位及页脚；有适用资料才启用 | 未实现整页证据嵌入 |
| 4 | Visbody 关键发现 | 4–6 项决策相关指标、算法限制、体态与影像不混同，正常反证 | 解释与原文隔离待建 |
| 5 | ATR / 前屈筛查 | 胸/胸腰/腰三段角度和方向；不等于 Cobb、不得无影像诊断侧弯 | 专项采集与比对待完善 |
| 6 | 足部原始证据 | 原始足底压力 PDF、图例和日期；有资料时按页保留 | 未实现整页证据嵌入 |
| 7 | 足部深度解读 | 足弓/后足/压力、静态与闭链动态互证，正常反证和 KPI | 推理与定量质控待建 |
| 8 | NASM-CES | 逐项 0–3 等级、观察/互证/边界/KPI；与疼痛章节分离；未完成≠3级 | 已有原始/动作评估分支，尚未完成 B 版叙述 |
| 9 | SFMA | FN/FP/DN/DP 与 VAS 一致性审核，Breakout，疼痛优先 | 有原始模块；冲突审核流待建 |
| 10 | FMS | 7 项逐项、左右/清除测试/无效原因；儿童学习效应；总分不预测损伤 | 动作评估分支已实现部分规则，完整样本验收待补 |
| 11 | YBT-LQ | 左右独立腿长、A/PM/PL、有效尝试、规范化%、复合分、左右差 cm/pp | 已有公式；录入验证和 B 版展示待完善 |
| 12 | YBT-UQ | 有效成绩与失败区别；全 0 不能自动当有效；缺失不生成 CS | 现行报告/分支覆盖不完整 |
| 13 | 力量/耐力/平衡/步态/OpenCap | 方法、体位、单位、左右与质量限制，不能混比较不同设备/方法 | 已有部分模块和引擎定量，证据转换待建 |
| 14 | 跑跳与落地专项 | 代表帧、时相、疼痛、定性 0–3；无完整协议禁止虚构 LESS/TJA 总分 | 有部分分析，代表帧与协议完备性需确认 |
| 15 | 历史纵向对比 | 方法可比性、日期、绝对变化、生长/学习/测量误差；变化≠疗效 | 有对比页面，信度/MDC 与语义待验证 |
| 16 | 综合临床推理 | 证据链 → 反证 → 可检验假设（高/中/低置信）→ 替代解释 | **尚未实现** |
| 17 | 四域风险分层 | 医疗安全、症状、动作代偿、生长与测量分别评级并连接动作；不预测损伤概率 | **尚未实现** |
| 18 | 4–6 周 KPI | 有效基线、方法、阶段目标、复评条件及未达标处理 | **尚未实现** |
| 19 | 治疗师训练方向 | 系统/方法/进阶/回退/停止/KPI；无明确需求不生成动作清单和组次 | **尚未实现** |
| 20 | 复评、补测与转诊 | 必测 vs 补测、机位/口令/设备一致性、明确升级条件 | **尚未实现** |

母版附录 A–F：A 数据质量完整度与采信；B QEI 关键证据（value/unit/method/scale/source_id/date）；C 冲突审核；D 缺失及最小补采；E Source ID 清单；F SFMA 等术语。报告质量检查须能从正文一键跳回证据来源，不只记录笼统的模块名称。

## 3. 临床安全红线（源于母版与案例结构核对）

1. **区分六类证据**：原始数据、现场观察、设备算法、功能推断、医学影像、工作假设。设备输出 ≠ 临床诊断。QEI 必须有真实来源、日期、条件和单位。
2. **症状与任务的特异性**：某一低速/双足动作无痛，不代表未采集的专项高速、单腿或旋转任务无痛；不得根据非同一动作做因果归因。
3. **质量失败不能被掩盖**：OpenCap 模型峰值封顶、坐标绕回、缺 MOT/TRC、转弯混入直线步态、未核查的坐标系，应逐项降级为描述/不可用，不以模型估计替代临床观察。
4. **疼痛/代码冲突**：SFMA DN+疼痛、FN+疼痛必须列出冲突并由治疗师确认，不得悄悄改原始代码；红旗未问不等于阴性。
5. **YBT 失败与占位**：只有明确有效的距离才计算规范化、CS；未测、失败、录入全 0 要区分。需保留左右独立肢长和每次试次。
6. **儿童发育背景**：儿童 BMI 不用成人诊断阈值；无出生年月、骨龄或生长速度就写缺失，不推测。
7. **视频与原始 PDF**：照片/视频代表帧要标时间、侧别和来源；Visbody/足部原始 PDF 整页嵌入，保持页码、图例、单位，且原始资料与解释分章。
8. **风险与训练**：动作代偿风险与未来损伤概率不是同一概念；缺乏验证不能自动预测损伤。治疗师报告中的训练方向不自动变成处方动作清单。

## 4. 报告数据契约（拟议；需服务间 schema 验收）

建议新增 `AdolescentAssessmentEvidencePackage`，包含：

```text
case_ref: 去标识关联符
assessment: id, date, stage, episode_ref, protocol_versions, locked_revision
growth_context: birth_date? / age_months? / stature_change? / sport_exposure? / missing_reasons
symptoms: location, pain_or_nonpain, VAS?, provocation, red_flag_screen_status, stop_and_referral_triggers
sources[]: source_id, type, capture_date, method, unit, side?, task?, device_version?, file_hash?, permission_scope
measurements[]: source_id, value?, unit, side?, phase?, validity, reliability, method_version
observations[]: task, side?, score?, pain?, raw_observation, evidence_ids[]
conflicts[]: code, conflicting_sources[], state, resolution?, resolved_by?, timestamp?
interpretations[]: statement, evidence_ids[], contradictory_evidence_ids[], confidence, reviewer_status
hypotheses[]: falsifiable_statement, alternative_explanations[], verification_tasks[], confidence
risks: medical_safety, symptom, movement_control, growth_and_measurement, management_grade
kpis[]: baseline, target?, unit, method, next_review, reason_if_unavailable
review: qa_status, reviewer, signature, source_revision, report_revision, immutable_output_hash
```

所有值允许不可用，但必须带缺失原因。未审核的解释不能生成已签署的客户端报告。生成器应使用已审核的结构化源数据，不让 AI 直接吞原始患者视频或产生新的评分/诊断。

## 5. 本次已落地的最小增量（仅源审查警告，不是 B 版交付）

- `AdolescentReportQualityPolicy`：针对出生日期、实际评估日期、红旗记录、来源摘要、缺少原始模块和未填综合临床章节生成结构化待审检查；所有 V4.1 原始数据报告均返回 `clinicalReady=false`。
- 原报告 JSON：新增 `adolescentProductionQuality`；不改变既有 `contentPolicy=raw-data-only` 和二进制导出权限。
- HTML 预览和报告中心：明确提示“资料整理版 ≠ B 版正式治疗师报告”。
- Java 独立单测：缺失资料/未问红旗不能被假定为正常；齐备基本字段仍不能因而自动判定为临床就绪。

### 2026-10-09 新增证据包草案（尚未启用正式生成）

- `docs/report/schemas/adolescent-evidence-b-v1.schema.json`：定义去标识的 `case_ref`、冻结来源版本、六类来源、有效/失败/未测/占位测量、临床观察、冲突与解释审核字段。Schema 标识为 `ADOLESCENT_EVIDENCE_B_V1_DRAFT`，与 V4.1 原报告 JSON 分离；仅用于可重复的合成契约验证。
- `AdolescentEvidencePackagePolicy`：纯函数核查 Source ID 唯一性及引用关系、日期/文件哈希、数值和单位、YBT 距离零值、SFMA FN/FP/DN/DP 与疼痛一致性、冲突解决时间戳及来源审核修订版。不自动更改原始评分、医学解释或签署状态；核查结果中的 `clinicalReady` 与 `signatureAuthorized` **始终为 false**。
- `AdolescentEvidencePackagePolicyTest`：全部使用合成标识及数值，不包含真实患者资料。独立 Python JSON Schema 校验同样采用合成输入，并检验非法日期和失败试次错误编码。
- **严格限制**：这是拟议的证据交换契约和结构校验器，尚未接入数据库、正式 B 版报告路由、家长版、Source ID 文件授权或治疗师签署事务，绝不能将单测通过视为 C01–C05 临床门禁通过。后续必须先冻结来源修订及其签名/文件权限，再实施版本化存储、人工审核、双版本生成和逐页验收。

### 后续增量：QEI、四域风险、症状门禁与 KPI

- 草案证据包现要求显式提供 `qei[]`（审查后的 Source ID 证据索引）、`safety`（红旗筛查、症状记录和受控训练决策）、`risks`（medical_safety / symptom / movement_control / growth_and_measurement 四域）及 `kpis[]`（有来源的基线、复测方法、单位、4–6 周复评时间）。缺失、重复 ID、悬空引用、尚未审查的索引和异常时间窗以固定错误码阻断。
- 红旗未记录或已阳性、症状未记录时，`CONDITIONAL_TRAINING` 会触发 `TRAINING_WITH_UNRESOLVED_SAFETY`。这些状态只表示原始临床记录已被标记和审核，**不代表自动判定医学安全**。
- 风险域的 `ASSESSED` 并不表示低风险、正常或可训练；只有记录该域的来源证据才可通过结构检查。KPI 不强制系统生成目标值；未有基线时必须说明缺失原因。
- JSON Schema 2020-12 负责类型、枚举与基本必填约束，Java Policy 负责跨 Source ID 引用、时间窗和红旗与训练决策冲突。两个校验通过都**不解锁** `clinicalReady` / `signatureAuthorized`，后续仍需具备角色/租户校验、并发签署事务、证据哈希和临床签名流程。
- 输入资源限额（草案）：最多 32 个顶层字段、每个证据记录最多 64 个字段、每个列表最多 512 条、单个证据引用列表最多 64 个 Source ID。Java 校验遇到超限时返回固定阻断码，不逐项遍历超限列表；JSON Schema 对应限制顶层字段及列表数量。正式 API 落地时还需补充 HTTP 请求字节上限、JSON 深度限制、租户配额和授权文件大小门禁，不能用此纯函数替代网络入口限流。
- Schema 与服务端引用校验保持一致：`evidence_ids`、`conflicting_sources` 等主要来源链必须显式提供；`contradictory_evidence_ids` 与 `counter_evidence_ids` 属于可选反证引用，缺席等同于未引用反证，**不等于已经完成正常反证**。两字段一旦出现则必须为有效数组，并遵守 Source ID 校验、去重与单列表最多 64 个引用的门禁。已新增 Java 合成数据一致性回归。

## 6. B 版真正上线前仍必须完成的验收

- [ ] B 版完整章节生成器 + 真实原始 PDF 整页/视频代表帧 + 逐页 Word/PDF 布局测试；家长版结构另需与家长 B 版母版核对。
- [ ] `Source ID`、QEI、冲突审核、资料锁定、证据关联、反证与解释置信度的数据库/接口契约。
- [ ] 患者安全：症状筛查缺失时阻止标“可训练”；未锁定或冲突未解决时不能进行 B 版签署；签署后修订不覆盖历史。
- [ ] YBT-UQ 失败/未测/占位、FMS 有效性/疼痛、SFMA 代码与 VAS 冲突、儿童发育背景、影像与设备算法界限的合成自动回归。
- [ ] 使用**合成/脱敏**数据验证初评、复评、无效测量、缺失影像和异常 OpenCap 各分支；真实案例仅在有授权的受控离线临床审核环境中核查，任何姓名、图像、视频不进 CI。
- [ ] 治疗师确认两版语义一致；渲染逐页 QA、PDF 校验、权限/租户/审计/导出全链路、最小权限和密钥/患者数据扫描。

## 7. 真实结构示例审阅的工程启发（仅规则，不存个人结果）

该 48 页示例同时展示了原始设备证据、多种动作协议、OpenCap 信号质量限制、非专项测试与专项任务的差异、落地分期、运动表现及沟通摘要。验收应覆盖：**输入有效性不是医学结论**、模型封顶与缺文件如何降级、无痛慢速动作对专项任务的外推边界，以及报告章节前后结论是否自洽。某页是否出现措辞更强的风险推断，不等于母版允许自动复制该结论；一律以可追溯证据、临床审核和适用研究为准。
