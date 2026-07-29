-- 内部部署阶段关闭 AI：保留表结构，停用配置与入口，便于未来显式启用。
SET NAMES utf8mb4;

UPDATE rehab_ai_config
SET ai_enabled = b'0',
    enable_assessment_interpretation = b'0',
    enable_report_summary = b'0',
    enable_patient_summary = b'0',
    enable_plan_draft = b'0',
    enable_followup_writer = b'0',
    updater = 'internal-deploy',
    update_time = NOW()
WHERE deleted = b'0';

UPDATE system_menu
SET status = 1,
    updater = 'internal-deploy',
    update_time = NOW()
WHERE id IN (
    9600, 9601, 9602,
    9610, 9611, 9612, 9613, 9614, 9615, 9616, 9617,
    9621, 9631, 9632, 9633
);
