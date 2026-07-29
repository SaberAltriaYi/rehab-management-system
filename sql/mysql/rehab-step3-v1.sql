-- 康复模块 Step 3：assessment -> report 主链路（v1）
-- 说明：可重复执行，保持幂等

SET NAMES utf8mb4;
START TRANSACTION;

-- ============================================================
-- 1) 业务表结构（评估 + 报告）
-- ============================================================
CREATE TABLE IF NOT EXISTS `rehab_assessment_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `assessment_no` varchar(64) DEFAULT NULL,
    `patient_id` bigint NOT NULL,
    `episode_id` bigint NOT NULL,
    `assessment_type` varchar(32) NOT NULL,
    `assessment_date` date NOT NULL,
    `assessor_user_id` bigint DEFAULT NULL,
    `location_type` varchar(32) DEFAULT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'draft',
    `chief_focus` varchar(255) DEFAULT NULL,
    `pain_score` decimal(4,1) DEFAULT NULL,
    `red_flag_notes` varchar(1000) DEFAULT NULL,
    `source_summary` varchar(1000) DEFAULT NULL,
    `raw_input_status` varchar(32) NOT NULL DEFAULT 'missing_items',
    `quality_grade` varchar(8) DEFAULT NULL,
    `confidence_grade` varchar(16) DEFAULT NULL,
    `summary_text` varchar(2000) DEFAULT NULL,
    `note` varchar(1000) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_assessment_no` (`assessment_no`),
    KEY `idx_rehab_assessment_patient` (`patient_id`),
    KEY `idx_rehab_assessment_episode` (`episode_id`),
    KEY `idx_rehab_assessment_status` (`status`),
    KEY `idx_rehab_assessment_date` (`assessment_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复评估主表';

CREATE TABLE IF NOT EXISTS `rehab_assessment_module_data` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `assessment_id` bigint NOT NULL,
    `module_type` varchar(32) NOT NULL,
    `module_status` varchar(32) NOT NULL DEFAULT 'not_started',
    `data_json` longtext,
    `source_type` varchar(32) DEFAULT 'manual',
    `version` varchar(32) DEFAULT 'v1',
    `note` varchar(1000) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_assessment_module` (`assessment_id`, `module_type`),
    KEY `idx_rehab_module_type` (`module_type`),
    KEY `idx_rehab_module_status` (`module_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复评估模块数据';

CREATE TABLE IF NOT EXISTS `rehab_assessment_attachment` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `assessment_id` bigint NOT NULL,
    `module_type` varchar(32) DEFAULT NULL,
    `file_name` varchar(255) NOT NULL,
    `file_type` varchar(128) DEFAULT NULL,
    `file_path` varchar(1024) NOT NULL,
    `file_size` bigint DEFAULT NULL,
    `upload_user_id` bigint DEFAULT NULL,
    `parse_status` varchar(32) DEFAULT NULL,
    `parse_message` varchar(1000) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_attachment_assessment` (`assessment_id`),
    KEY `idx_rehab_attachment_module` (`module_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复评估附件';

CREATE TABLE IF NOT EXISTS `rehab_assessment_operation_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `assessment_id` bigint NOT NULL,
    `operation_type` varchar(64) NOT NULL,
    `operator_user_id` bigint DEFAULT NULL,
    `before_data_json` longtext,
    `after_data_json` longtext,
    `remark` varchar(1000) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_assessment_log_assessment` (`assessment_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复评估操作日志';

CREATE TABLE IF NOT EXISTS `rehab_report` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `report_no` varchar(64) DEFAULT NULL,
    `patient_id` bigint NOT NULL,
    `episode_id` bigint NOT NULL,
    `assessment_id` bigint NOT NULL,
    `report_type` varchar(32) NOT NULL,
    `report_status` varchar(32) NOT NULL DEFAULT 'draft',
    `report_version` int NOT NULL DEFAULT 1,
    `generated_by` bigint DEFAULT NULL,
    `reviewed_by` bigint DEFAULT NULL,
    `approved_by` bigint DEFAULT NULL,
    `generation_mode` varchar(32) DEFAULT 'auto',
    `report_json` longtext,
    `docx_path` varchar(1024) DEFAULT NULL,
    `pdf_path` varchar(1024) DEFAULT NULL,
    `html_snapshot_path` varchar(1024) DEFAULT NULL,
    `last_generated_at` datetime DEFAULT NULL,
    `exported_at` datetime DEFAULT NULL,
    `note` varchar(1000) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_report_no` (`report_no`),
    KEY `idx_rehab_report_patient` (`patient_id`),
    KEY `idx_rehab_report_assessment` (`assessment_id`),
    KEY `idx_rehab_report_status` (`report_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复报告主表';

-- ============================================================
-- 2) 菜单与权限（Step 3）
-- ============================================================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9203, '评估详情', 'rehab:assessment:detail', 3, 3, 9003, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9204, '评估删除', 'rehab:assessment:delete', 3, 4, 9003, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9205, '评估归档', 'rehab:assessment:archive', 3, 5, 9003, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9206, '评估生成报告', 'rehab:assessment:generate-report', 3, 6, 9003, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9302, '报告详情', 'rehab:report:detail', 3, 2, 9004, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9303, '报告预览', 'rehab:report:preview', 3, 3, 9004, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9304, '报告复核', 'rehab:report:review', 3, 4, 9004, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9305, '报告审批', 'rehab:report:approve', 3, 5, 9004, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

-- 超级管理员
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 1, t.menu_id, 'script', 'script', b'0', 1
FROM (
         SELECT 9203 AS menu_id UNION ALL
         SELECT 9204 UNION ALL
         SELECT 9205 UNION ALL
         SELECT 9206 UNION ALL
         SELECT 9302 UNION ALL
         SELECT 9303 UNION ALL
         SELECT 9304 UNION ALL
         SELECT 9305
     ) t
         LEFT JOIN `system_role_menu` rm
                   ON rm.role_id = 1 AND rm.menu_id = t.menu_id AND rm.tenant_id = 1 AND rm.deleted = b'0'
WHERE rm.id IS NULL;

-- 康复治疗师
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT r.id, t.menu_id, 'script', 'script', b'0', 1
FROM `system_role` r
         JOIN (
    SELECT 9203 AS menu_id UNION ALL
    SELECT 9205 UNION ALL
    SELECT 9206 UNION ALL
    SELECT 9302 UNION ALL
    SELECT 9303 UNION ALL
    SELECT 9304 UNION ALL
    SELECT 9301
) t
              LEFT JOIN `system_role_menu` rm
                        ON rm.role_id = r.id AND rm.menu_id = t.menu_id AND rm.tenant_id = 1 AND rm.deleted = b'0'
WHERE r.code = 'rehab_therapist'
  AND r.tenant_id = 1
  AND r.deleted = b'0'
  AND rm.id IS NULL;

-- 文员（仅查看）
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT r.id, t.menu_id, 'script', 'script', b'0', 1
FROM `system_role` r
         JOIN (
    SELECT 9203 AS menu_id UNION ALL
    SELECT 9302 UNION ALL
    SELECT 9303
) t
              LEFT JOIN `system_role_menu` rm
                        ON rm.role_id = r.id AND rm.menu_id = t.menu_id AND rm.tenant_id = 1 AND rm.deleted = b'0'
WHERE r.code = 'rehab_clerk'
  AND r.tenant_id = 1
  AND r.deleted = b'0'
  AND rm.id IS NULL;

-- ============================================================
-- 3) Step 3 演示数据
-- ============================================================
INSERT INTO `rehab_assessment_record` (`id`, `assessment_no`, `patient_id`, `episode_id`, `assessment_type`, `assessment_date`, `assessor_user_id`, `location_type`, `status`, `chief_focus`, `pain_score`, `raw_input_status`, `quality_grade`, `confidence_grade`, `summary_text`, `note`, `creator`, `updater`, `deleted`)
VALUES (20001, 'ASM202603080001', 10001, 13001, 'comprehensive_assessment', CURDATE(), 100, 'clinic', 'completed', '下肢稳定与膝轨迹控制', 4.0, 'complete', 'B', 'medium', '初评：提示存在下肢控制不足与左右差线索', '示例初评', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `status`=VALUES(`status`), `raw_input_status`=VALUES(`raw_input_status`), `summary_text`=VALUES(`summary_text`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_assessment_record` (`id`, `assessment_no`, `patient_id`, `episode_id`, `assessment_type`, `assessment_date`, `assessor_user_id`, `location_type`, `status`, `chief_focus`, `pain_score`, `raw_input_status`, `quality_grade`, `confidence_grade`, `summary_text`, `note`, `creator`, `updater`, `deleted`)
VALUES (20002, 'ASM202603080002', 10001, 13001, 'opencap', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 100, 'remote', 'draft', 'OpenCap 复测', 3.0, 'partial', 'C', 'low', '复评准备中：等待补齐模块', '示例复评', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `status`=VALUES(`status`), `raw_input_status`=VALUES(`raw_input_status`), `summary_text`=VALUES(`summary_text`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_assessment_module_data` (`id`, `assessment_id`, `module_type`, `module_status`, `data_json`, `source_type`, `version`, `note`, `creator`, `updater`, `deleted`)
VALUES (21001, 20001, 'static', 'completed', '{"head_forward_angle":12.5,"pelvic_rotation":"L+"}', 'manual', 'v1', '静态四视图摘要', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `module_status`=VALUES(`module_status`), `data_json`=VALUES(`data_json`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_assessment_module_data` (`id`, `assessment_id`, `module_type`, `module_status`, `data_json`, `source_type`, `version`, `note`, `creator`, `updater`, `deleted`)
VALUES (21002, 20001, 'nasm', 'completed', '{"overhead_squat":{"qei":0.61,"severity":2,"side":"left"}}', 'manual', 'v1', 'NASM 初评', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `module_status`=VALUES(`module_status`), `data_json`=VALUES(`data_json`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_assessment_module_data` (`id`, `assessment_id`, `module_type`, `module_status`, `data_json`, `source_type`, `version`, `note`, `creator`, `updater`, `deleted`)
VALUES (21003, 20001, 'ybt', 'completed', '{"lq":{"left_cs":86.2,"right_cs":91.5,"asymmetry":5.3}}', 'manual', 'v1', 'YBT 初评', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `module_status`=VALUES(`module_status`), `data_json`=VALUES(`data_json`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_assessment_module_data` (`id`, `assessment_id`, `module_type`, `module_status`, `data_json`, `source_type`, `version`, `note`, `creator`, `updater`, `deleted`)
VALUES (21004, 20002, 'opencap', 'partial', '{"trial_metrics":[{"trial":"squat_01","rom_knee_l":58.3,"rom_knee_r":63.1}],"quality_flags":["partial"]}', 'parser', 'v1', 'OpenCap 复测片段', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `module_status`=VALUES(`module_status`), `data_json`=VALUES(`data_json`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_report` (`id`, `report_no`, `patient_id`, `episode_id`, `assessment_id`, `report_type`, `report_status`, `report_version`, `generated_by`, `reviewed_by`, `generation_mode`, `report_json`, `docx_path`, `html_snapshot_path`, `last_generated_at`, `note`, `creator`, `updater`, `deleted`)
VALUES (30001, 'REP202603080001', 10001, 13001, 20001, 'comprehensive', 'reviewed', 1, 100, 100, 'auto', '{"summary":"示例报告"}', '/tmp/rehab-demo/REP202603080001_v1.docx', '/tmp/rehab-demo/REP202603080001_v1.html', NOW(), '示例已复核报告', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `report_status`=VALUES(`report_status`), `report_version`=VALUES(`report_version`), `report_json`=VALUES(`report_json`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_assessment_operation_log` (`id`, `assessment_id`, `operation_type`, `operator_user_id`, `before_data_json`, `after_data_json`, `remark`, `creator`, `updater`, `deleted`)
VALUES (22001, 20001, 'assessment_create', 100, NULL, '{"assessmentNo":"ASM202603080001"}', '示例创建评估', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `remark`=VALUES(`remark`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_assessment_operation_log` (`id`, `assessment_id`, `operation_type`, `operator_user_id`, `before_data_json`, `after_data_json`, `remark`, `creator`, `updater`, `deleted`)
VALUES (22002, 20001, 'generate_report', 100, NULL, '{"reportNo":"REP202603080001"}', '示例生成报告', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `remark`=VALUES(`remark`), `updater`='script', `deleted`=b'0';

COMMIT;
