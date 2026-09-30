# 智能动作评估与量化分析（motion assessment）

本目录是“运动康复评估与业务管理系统”内“智能动作评估与量化分析”子系统的交付文档。子系统复用现有
Spring Boot / MyBatis-Plus / Vue3 架构、租户与机构隔离、菜单权限与审计，不更换数据库引擎。

| 文档 | 内容 |
|---|---|
| [official-alignment.md](official-alignment.md) | 用户表格（FMS V0.2 / NASM-CES V0.2）与官方资料差异记录、处理决定 |
| [open-questions.md](open-questions.md) | NASM 表 15 待确认项与默认决定 |
| [testing.md](testing.md) | 测试矩阵、执行命令与本次结果 |
| [security.md](security.md) | 安全、隐私、AI 护栏、上传与日志 |
| [deployment.md](deployment.md) | 迁移 024、引擎部署、上传上限与串行上传、回滚 |
| [clinical-validation-plan.md](clinical-validation-plan.md) | 临床验证计划与已知限制 |
| [changed-files.md](changed-files.md) | 变更文件清单 |

## 职责边界

| 角色 | 负责 | 不做 |
|---|---|---|
| OpenCap（LaiUhlrich2022） | 多相机视频 → `.mot` / `.trc` / 模型 | 评分 |
| 引擎 `rehab-biomechanics-engine`（`rehab_biomechanics.motion`） | 按表头解析、QC、阶段/重复识别、角度/ROM/时序/不对称/稳定性、确定性官方评分、证据链 | 诊断、改写治疗师结论 |
| 后端（`yudao-module-rehab` motion 包） | 建档、上传安全、异步任务（进度/重试/取消/幂等/重算）、结果落库、审核签署、报告、对比、趋势、审计 | 自行计算临床分数 |
| AI（可选，默认关闭） | 仅基于去标识指标写解释/总结/建议/报告草稿，每条引用证据 | 计算或修改分数与原始数据、编造阈值、诊断、签署、覆盖治疗师 |
| 治疗师 | 确认 / 修改（必填原因）/ 签署 / 更正 | — |

## 数据流

```
患者详情 → 新建动作评估（体系/就诊类型/相机数/肢长/视频授权）
  → 上传 OpenCap 导出文件夹（前端按服务端策略过滤并逐个串行上传）或 OpenCap 会话拉取
  → Trial 映射（测试项目/侧别/官方分级条件/第几次）
  → 人工录入（疼痛、有效性与原因、清除测试、肩灵活性拳距/手长、YBT 距离、TJA/LESS 评定、NASM 观察）
  → 处理任务：待上传 → 上传中 → OpenCap处理中 → 下载结果 → 数据解析 → 规则计算 → AI报告生成 → 待治疗师审核
  → 审核（系统分/最终分/原因）→ 签署（阻断条件见 security.md）→ 已完成
  → 报告（治疗师版/患者版/JSON/网页/PDF）、初评/复评对比、趋势
```

## 体系与版本

* FMS：7 项 + 3 个通过性测试，协议 `FMS_OPENCAP V0.2.1-official-aligned`（官方对齐见 official-alignment.md）。
* NASM-CES：OHSA、单腿蹲、俯卧撑、站立划船、站立哑铃过头举、肩水平外展、肩旋转、肩屈曲、步态，协议
  `NASM_CES_OPENCAP V0.2-revised-pending`（定性标签 + 描述性量化 + 治疗师确认，不设硬阈值）。
* 扩展（非 NASM 官方评分体系，各自带来源与版本）：YBT-LQ（Plisky 2009 公式）、10 s 团身跳（Myer 2008 二分法
  或修订版 0/1/2，默认修订版）、LESS（Padua 2009，17 项，3 次有效试次取均值）。

每份结果保存 `engine_version`、`rule_engine_version`、各协议 `protocol_version` 与协议文件 SHA-256、
请求/输入文件 SHA-256，报告与 PDF 均显示版本，保证可追溯到 trial → 指标 → 规则 → 证据。

## 方法内容授权说明

`CHANGELOG.md` 已记录：SFMA、FMS、NASM-CES 方法内容尚无确认的授权或其他使用依据，不纳入软件著作权主张与鉴别材料。
本子系统只实现评分流程与可追溯记录，协议 JSON 中的条目说明为概括性描述，不复制原书图文；FMS/NASM 名称与方法的
对外使用（尤其商业化）需先取得权利人授权或确认使用依据。
