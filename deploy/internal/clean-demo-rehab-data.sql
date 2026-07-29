-- 仅在 Docker 首次初始化全新数据库时执行，清除 SQL 脚本中的康复演示业务数据。
-- 表结构、菜单、角色、评估类型、AI 配置和预警规则保留。
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM rehab_ai_review_log;
DELETE FROM rehab_ai_output;
DELETE FROM rehab_ai_job;
DELETE FROM rehab_ai_suggestion_bundle;
DELETE FROM rehab_patient_notification;
DELETE FROM rehab_notification;
DELETE FROM rehab_followup_note;
DELETE FROM rehab_alert_event;
DELETE FROM rehab_reassessment_trigger;
DELETE FROM rehab_task_execution;
DELETE FROM rehab_daily_checkin;
DELETE FROM rehab_progress_record;
DELETE FROM rehab_task_schedule;
DELETE FROM rehab_exercise_task;
DELETE FROM rehab_plan_operation_log;
DELETE FROM rehab_care_plan;
DELETE FROM rehab_report_version;
DELETE FROM rehab_report;
DELETE FROM rehab_assessment_attachment;
DELETE FROM rehab_assessment_operation_log;
DELETE FROM rehab_assessment_module_data;
DELETE FROM rehab_assessment_record;
DELETE FROM rehab_therapist_assignment;
DELETE FROM rehab_patient_crm_binding;
DELETE FROM rehab_patient_user_binding;
DELETE FROM rehab_patient_tag;
DELETE FROM rehab_patient_operation_log;
DELETE FROM rehab_audit_log;
DELETE FROM rehab_episode;
DELETE FROM rehab_dashboard_snapshot;
DELETE FROM rehab_patient;

SET FOREIGN_KEY_CHECKS = 1;
