# 阶段 4｜306 项验收与来源证据规范（2026-11-13）

本文件是后续真实验收操作规约，不是已完成的临床批准。

当前范围：306 个原始编号不变；182 条必测（97 业务、85 安全），124 条保留为 DEFERRED。T-107、T-113、T-114、T-129、T-189 必须由真实、合格的临床人员监督验收；模型、合成账号或自动化脚本不能代替人工签发。

## 测试证据原则

1. 按原始台账独立执行每条用例的实际步骤与预期结果，不把 Java 单元测试或一次全流程合成 E2E 视为全部 182 条通过。
2. 记录准确的冻结 Git 提交、Runtime/JAR 哈希、受控测试环境、角色、测试人、执行日期、脱敏结果、独立日志 SHA-256。只有完全匹配当次版本才能用于发行门禁。
3. 单项日志须有独立的一行，格式为 T-001|PASS|实际观察到的断言与结果简述。T-0010、T-001|FAIL|... 或仅提及编号都不能作为 T-001 通过。此格式本身不证明内容真实，仍须核对实际操作证据。
4. 人工签发的 5 项还需真实的 clinicianReviewedBy 及 supervised_clinical_e2e 审核来源；机器只检查格式，不能凭字段证明资质、审核实际发生或临床判断正确。
5. QA 日志保存在 Core 源码目录的同级 validation-evidence 下，不提交 Git；如意外包含真实患者、儿童资料或访问凭据，须先隔离脱敏。

## 受控检查命令（只读）

先在 Core 仓库根目录运行以下命令：

  node desktop/scripts/mvp306-acceptance-worklist.mjs --markdown

  node desktop/scripts/mvp306-release-gate.mjs /Users/saber/my-project/rehab/validation-evidence/审核批次/mvp306-evidence.json --evidence-root=/Users/saber/my-project/rehab/validation-evidence/审核批次

  node desktop/scripts/prepackage-readiness.mjs

证据路径的读取限制为真实 sibling validation-evidence 目录，拒绝离开目录的软链接、外部位置和相对路径越界。保留原仓库内日志格式的向后兼容性，但推荐受控 QA 证据独立放置，不把临床日志混入源代码。

**发行需要同时满足**：所有 182 项独立证据及临床人工审核、Git 版本冻结且无未提交改动、12 项安装前门禁、真实安装/升级/回滚与合法代码签名；否则继续 NO-GO。任何测试输出和自动生成的工作清单不得自行提升为正式批准。
