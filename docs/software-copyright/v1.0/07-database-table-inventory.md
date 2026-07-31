<!-- Copyright (c) 2026 [软件著作权人名称]. -->

# 康复数据库表清单

V1.0 数据库迁移定义 34 张 `rehab_` 业务表。通用用户、角色、菜单、日志和基础设施表来自上游框架，不列为康复自研表。

| 表名 | 用途 |
| --- | --- |
| `rehab_patient` | 患者主档 |
| `rehab_patient_tag` | 患者标签 |
| `rehab_patient_operation_log` | 患者操作记录 |
| `rehab_patient_crm_binding` | 患者与 CRM 客户绑定 |
| `rehab_patient_user_binding` | 患者端用户绑定 |
| `rehab_patient_notification` | 患者历史通知 |
| `rehab_therapist_assignment` | 治疗师分配和转交 |
| `rehab_episode` | 康复病程 |
| `rehab_assessment_record` | 评估主记录 |
| `rehab_assessment_module_data` | 评估模块结构化数据 |
| `rehab_assessment_attachment` | 评估附件 |
| `rehab_assessment_operation_log` | 评估操作记录 |
| `rehab_report` | 评估报告 |
| `rehab_report_version` | 报告版本与锁版快照 |
| `rehab_care_plan` | 康复计划 |
| `rehab_plan_operation_log` | 计划操作记录 |
| `rehab_exercise_task` | 训练任务 |
| `rehab_task_schedule` | 训练排期 |
| `rehab_daily_checkin` | 日常签到 |
| `rehab_task_execution` | 任务执行记录 |
| `rehab_progress_record` | 阶段进度记录 |
| `rehab_reassessment_trigger` | 复评触发器 |
| `rehab_followup_note` | 随访记录 |
| `rehab_notification` | 统一业务通知 |
| `rehab_alert_rule` | 风险预警规则 |
| `rehab_alert_event` | 风险预警事件 |
| `rehab_audit_log` | 康复业务审计 |
| `rehab_dashboard_snapshot` | 工作台统计快照 |
| `rehab_ai_config` | AI 配置（V1.0 强制关闭） |
| `rehab_ai_prompt_template` | AI 提示模板（V1.0 不启用） |
| `rehab_ai_job` | AI 任务（V1.0 不运行） |
| `rehab_ai_output` | AI 输出（V1.0 不产生） |
| `rehab_ai_review_log` | AI 人工复核记录 |
| `rehab_ai_suggestion_bundle` | AI 建议汇总 |

迁移账本位于 `deploy/internal/migrations.manifest`，当前固定为 001–019。生产升级必须通过校验和迁移脚本执行，不能修改已在现有数据库登记的迁移内容。
