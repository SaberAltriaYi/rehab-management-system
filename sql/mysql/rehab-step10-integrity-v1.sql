-- 康复管理系统：核心关系完整性约束（v1）
-- 适用 MySQL 8.0+。脚本可重复执行，逻辑删除不触发级联物理删除。

DROP PROCEDURE IF EXISTS add_rehab_fk_if_missing;

DELIMITER $$
CREATE PROCEDURE add_rehab_fk_if_missing(
    IN p_constraint_name VARCHAR(64),
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_reference_table VARCHAR(64)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = p_table_name
          AND constraint_name = p_constraint_name
          AND constraint_type = 'FOREIGN KEY'
    ) THEN
        SET @fk_sql = CONCAT(
            'ALTER TABLE `', p_table_name,
            '` ADD CONSTRAINT `', p_constraint_name,
            '` FOREIGN KEY (`', p_column_name,
            '`) REFERENCES `', p_reference_table,
            '` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT'
        );
        PREPARE fk_statement FROM @fk_sql;
        EXECUTE fk_statement;
        DEALLOCATE PREPARE fk_statement;
    END IF;
END$$
DELIMITER ;

CALL add_rehab_fk_if_missing('fk_rehab_episode_patient', 'rehab_episode', 'patient_id', 'rehab_patient');

CALL add_rehab_fk_if_missing('fk_rehab_assessment_patient', 'rehab_assessment_record', 'patient_id', 'rehab_patient');
CALL add_rehab_fk_if_missing('fk_rehab_assessment_episode', 'rehab_assessment_record', 'episode_id', 'rehab_episode');
CALL add_rehab_fk_if_missing('fk_rehab_module_assessment', 'rehab_assessment_module_data', 'assessment_id', 'rehab_assessment_record');
CALL add_rehab_fk_if_missing('fk_rehab_attach_assessment', 'rehab_assessment_attachment', 'assessment_id', 'rehab_assessment_record');
CALL add_rehab_fk_if_missing('fk_rehab_assess_log_assessment', 'rehab_assessment_operation_log', 'assessment_id', 'rehab_assessment_record');

CALL add_rehab_fk_if_missing('fk_rehab_plan_patient', 'rehab_care_plan', 'patient_id', 'rehab_patient');
CALL add_rehab_fk_if_missing('fk_rehab_plan_episode', 'rehab_care_plan', 'episode_id', 'rehab_episode');
CALL add_rehab_fk_if_missing('fk_rehab_plan_assessment', 'rehab_care_plan', 'source_assessment_id', 'rehab_assessment_record');

CALL add_rehab_fk_if_missing('fk_rehab_task_plan', 'rehab_exercise_task', 'plan_id', 'rehab_care_plan');
CALL add_rehab_fk_if_missing('fk_rehab_task_patient', 'rehab_exercise_task', 'patient_id', 'rehab_patient');
CALL add_rehab_fk_if_missing('fk_rehab_task_episode', 'rehab_exercise_task', 'episode_id', 'rehab_episode');

CALL add_rehab_fk_if_missing('fk_rehab_checkin_patient', 'rehab_daily_checkin', 'patient_id', 'rehab_patient');
CALL add_rehab_fk_if_missing('fk_rehab_checkin_episode', 'rehab_daily_checkin', 'episode_id', 'rehab_episode');
CALL add_rehab_fk_if_missing('fk_rehab_checkin_plan', 'rehab_daily_checkin', 'plan_id', 'rehab_care_plan');
CALL add_rehab_fk_if_missing('fk_rehab_execution_checkin', 'rehab_task_execution', 'checkin_id', 'rehab_daily_checkin');
CALL add_rehab_fk_if_missing('fk_rehab_execution_task', 'rehab_task_execution', 'task_id', 'rehab_exercise_task');
CALL add_rehab_fk_if_missing('fk_rehab_schedule_task', 'rehab_task_schedule', 'task_id', 'rehab_exercise_task');
CALL add_rehab_fk_if_missing('fk_rehab_schedule_plan', 'rehab_task_schedule', 'plan_id', 'rehab_care_plan');
CALL add_rehab_fk_if_missing('fk_rehab_schedule_patient', 'rehab_task_schedule', 'patient_id', 'rehab_patient');

CALL add_rehab_fk_if_missing('fk_rehab_progress_patient', 'rehab_progress_record', 'patient_id', 'rehab_patient');
CALL add_rehab_fk_if_missing('fk_rehab_progress_episode', 'rehab_progress_record', 'episode_id', 'rehab_episode');
CALL add_rehab_fk_if_missing('fk_rehab_progress_plan', 'rehab_progress_record', 'plan_id', 'rehab_care_plan');
CALL add_rehab_fk_if_missing('fk_rehab_reassess_patient', 'rehab_reassessment_trigger', 'patient_id', 'rehab_patient');
CALL add_rehab_fk_if_missing('fk_rehab_reassess_episode', 'rehab_reassessment_trigger', 'episode_id', 'rehab_episode');
CALL add_rehab_fk_if_missing('fk_rehab_reassess_plan', 'rehab_reassessment_trigger', 'plan_id', 'rehab_care_plan');

CALL add_rehab_fk_if_missing('fk_rehab_report_patient', 'rehab_report', 'patient_id', 'rehab_patient');
CALL add_rehab_fk_if_missing('fk_rehab_report_episode', 'rehab_report', 'episode_id', 'rehab_episode');
CALL add_rehab_fk_if_missing('fk_rehab_report_assessment', 'rehab_report', 'assessment_id', 'rehab_assessment_record');
CALL add_rehab_fk_if_missing('fk_rehab_report_ver_report', 'rehab_report_version', 'report_id', 'rehab_report');
CALL add_rehab_fk_if_missing('fk_rehab_report_ver_assessment', 'rehab_report_version', 'based_on_assessment_id', 'rehab_assessment_record');

CALL add_rehab_fk_if_missing('fk_rehab_followup_patient', 'rehab_followup_note', 'patient_id', 'rehab_patient');
CALL add_rehab_fk_if_missing('fk_rehab_followup_episode', 'rehab_followup_note', 'episode_id', 'rehab_episode');
CALL add_rehab_fk_if_missing('fk_rehab_patient_tag_patient', 'rehab_patient_tag', 'patient_id', 'rehab_patient');
CALL add_rehab_fk_if_missing('fk_rehab_patient_op_patient', 'rehab_patient_operation_log', 'patient_id', 'rehab_patient');
CALL add_rehab_fk_if_missing('fk_rehab_plan_op_plan', 'rehab_plan_operation_log', 'plan_id', 'rehab_care_plan');
CALL add_rehab_fk_if_missing('fk_rehab_crm_binding_patient', 'rehab_patient_crm_binding', 'patient_id', 'rehab_patient');
CALL add_rehab_fk_if_missing('fk_rehab_user_binding_patient', 'rehab_patient_user_binding', 'patient_id', 'rehab_patient');
CALL add_rehab_fk_if_missing('fk_rehab_patient_notice_patient', 'rehab_patient_notification', 'patient_id', 'rehab_patient');
CALL add_rehab_fk_if_missing('fk_rehab_patient_notice_episode', 'rehab_patient_notification', 'episode_id', 'rehab_episode');
CALL add_rehab_fk_if_missing('fk_rehab_assignment_patient', 'rehab_therapist_assignment', 'patient_id', 'rehab_patient');

DROP PROCEDURE IF EXISTS add_rehab_fk_if_missing;
