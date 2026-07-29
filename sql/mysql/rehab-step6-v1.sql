-- 康复模块 Step 6：通知中心 + 自动提醒 + Dashboard + 报告审计锁版
-- 说明：可重复执行，保持幂等

SET NAMES utf8mb4;
START TRANSACTION;

-- ============================================================
-- 1) Step 6 业务表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rehab_notification` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `notification_no` varchar(64) DEFAULT NULL,
    `target_type` varchar(32) NOT NULL COMMENT 'therapist/patient/admin/system',
    `target_user_id` bigint DEFAULT NULL,
    `patient_id` bigint DEFAULT NULL,
    `episode_id` bigint DEFAULT NULL,
    `related_type` varchar(32) DEFAULT NULL COMMENT 'plan/checkin/trigger/report/assessment/alert/system',
    `related_id` bigint DEFAULT NULL,
    `notification_type` varchar(32) NOT NULL COMMENT 'task_reminder/reassessment_due/low_adherence/pain_alert/report_ready/plan_updated/trigger_created/system_notice',
    `title` varchar(255) NOT NULL,
    `content` varchar(2000) NOT NULL,
    `severity` varchar(16) NOT NULL DEFAULT 'info' COMMENT 'info/warning/high',
    `delivery_channel` varchar(32) NOT NULL DEFAULT 'multi' COMMENT 'web/app_admin/app_patient/multi',
    `read_status` varchar(16) NOT NULL DEFAULT 'unread' COMMENT 'unread/read',
    `read_time` datetime DEFAULT NULL,
    `send_status` varchar(16) NOT NULL DEFAULT 'sent' COMMENT 'pending/sent/failed/canceled',
    `visible_from` datetime DEFAULT NULL,
    `expire_time` datetime DEFAULT NULL,
    `action_url` varchar(512) DEFAULT NULL,
    `action_text` varchar(64) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_notification_no` (`notification_no`),
    KEY `idx_rehab_notification_target` (`target_type`, `target_user_id`),
    KEY `idx_rehab_notification_patient` (`patient_id`, `episode_id`),
    KEY `idx_rehab_notification_type` (`notification_type`, `severity`),
    KEY `idx_rehab_notification_read` (`read_status`, `visible_from`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复统一通知中心';

CREATE TABLE IF NOT EXISTS `rehab_alert_rule` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `rule_code` varchar(64) NOT NULL,
    `rule_name` varchar(128) NOT NULL,
    `alert_type` varchar(32) NOT NULL,
    `enabled` bit(1) NOT NULL DEFAULT b'1',
    `scope_type` varchar(32) NOT NULL DEFAULT 'plan',
    `condition_json` varchar(2000) DEFAULT NULL,
    `severity` varchar(16) NOT NULL DEFAULT 'warning',
    `target_role_type` varchar(32) DEFAULT NULL,
    `notify_channels_json` varchar(500) DEFAULT NULL,
    `cooldown_hours` int DEFAULT 24,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_alert_rule_code` (`rule_code`),
    KEY `idx_rehab_alert_rule_type` (`alert_type`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复提醒规则';

CREATE TABLE IF NOT EXISTS `rehab_alert_event` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `rule_id` bigint DEFAULT NULL,
    `patient_id` bigint NOT NULL,
    `episode_id` bigint DEFAULT NULL,
    `plan_id` bigint DEFAULT NULL,
    `related_type` varchar(32) DEFAULT NULL,
    `related_id` bigint DEFAULT NULL,
    `alert_type` varchar(32) NOT NULL,
    `severity` varchar(16) NOT NULL DEFAULT 'warning',
    `trigger_message` varchar(1000) NOT NULL,
    `trigger_metric` varchar(128) DEFAULT NULL,
    `trigger_value` varchar(255) DEFAULT NULL,
    `threshold_value` varchar(255) DEFAULT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT 'active/acknowledged/resolved/ignored',
    `created_from` varchar(32) NOT NULL DEFAULT 'auto_engine',
    `acknowledged_by` bigint DEFAULT NULL,
    `acknowledged_time` datetime DEFAULT NULL,
    `resolved_by` bigint DEFAULT NULL,
    `resolved_time` datetime DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_alert_event_patient` (`patient_id`, `status`),
    KEY `idx_rehab_alert_event_plan` (`plan_id`, `alert_type`, `status`),
    KEY `idx_rehab_alert_event_rule` (`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复提醒事件';

CREATE TABLE IF NOT EXISTS `rehab_report_version` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `report_id` bigint NOT NULL,
    `version_no` int NOT NULL,
    `report_status` varchar(32) NOT NULL,
    `generation_mode` varchar(32) DEFAULT 'auto',
    `report_json` longtext,
    `docx_path` varchar(1024) DEFAULT NULL,
    `pdf_path` varchar(1024) DEFAULT NULL,
    `html_snapshot_path` varchar(1024) DEFAULT NULL,
    `based_on_assessment_id` bigint DEFAULT NULL,
    `change_summary` varchar(1000) DEFAULT NULL,
    `generated_by` bigint DEFAULT NULL,
    `reviewed_by` bigint DEFAULT NULL,
    `approved_by` bigint DEFAULT NULL,
    `locked_by` bigint DEFAULT NULL,
    `locked_time` datetime DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_report_version_report` (`report_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复报告版本历史';

CREATE TABLE IF NOT EXISTS `rehab_audit_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `module_type` varchar(32) NOT NULL COMMENT 'patient/episode/assessment/report/plan/checkin/trigger/alert/notification/auth',
    `module_id` bigint NOT NULL,
    `operation_type` varchar(64) NOT NULL,
    `operator_user_id` bigint DEFAULT NULL,
    `operator_role` varchar(32) DEFAULT NULL,
    `before_data_json` longtext,
    `after_data_json` longtext,
    `ip` varchar(128) DEFAULT NULL,
    `user_agent` varchar(512) DEFAULT NULL,
    `result_status` varchar(32) DEFAULT NULL,
    `remark` varchar(1000) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_audit_module` (`module_type`, `module_id`, `create_time`),
    KEY `idx_rehab_audit_operator` (`operator_user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复统一审计日志';

CREATE TABLE IF NOT EXISTS `rehab_dashboard_snapshot` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `snapshot_date` date NOT NULL,
    `owner_scope` varchar(32) NOT NULL COMMENT 'global/therapist',
    `owner_user_id` bigint DEFAULT NULL,
    `patient_total` int DEFAULT 0,
    `active_patient_total` int DEFAULT 0,
    `active_plan_total` int DEFAULT 0,
    `pending_reassessment_total` int DEFAULT 0,
    `high_risk_total` int DEFAULT 0,
    `report_generated_total` int DEFAULT 0,
    `report_exported_total` int DEFAULT 0,
    `avg_checkin_completion_rate` decimal(8,2) DEFAULT NULL,
    `low_adherence_total` int DEFAULT 0,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_dashboard_snapshot` (`snapshot_date`, `owner_scope`, `owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复 dashboard 快照';

-- ============================================================
-- 2) 既有表升级
-- ============================================================
SET @rehab_report_locked_by_exists = (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'rehab_report'
      AND column_name = 'locked_by'
);
SET @rehab_report_add_locked_by_sql = IF(
    @rehab_report_locked_by_exists = 0,
    'ALTER TABLE `rehab_report` ADD COLUMN `locked_by` bigint DEFAULT NULL AFTER `approved_by`',
    'SELECT 1'
);
PREPARE rehab_report_add_locked_by_stmt FROM @rehab_report_add_locked_by_sql;
EXECUTE rehab_report_add_locked_by_stmt;
DEALLOCATE PREPARE rehab_report_add_locked_by_stmt;

SET @rehab_report_locked_time_exists = (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'rehab_report'
      AND column_name = 'locked_time'
);
SET @rehab_report_add_locked_time_sql = IF(
    @rehab_report_locked_time_exists = 0,
    'ALTER TABLE `rehab_report` ADD COLUMN `locked_time` datetime DEFAULT NULL AFTER `locked_by`',
    'SELECT 1'
);
PREPARE rehab_report_add_locked_time_stmt FROM @rehab_report_add_locked_time_sql;
EXECUTE rehab_report_add_locked_time_stmt;
DEALLOCATE PREPARE rehab_report_add_locked_time_stmt;

-- ============================================================
-- 3) Step5 旧患者通知迁移到统一通知中心（旧表保留，不再主读写）
-- ============================================================
SET @rehab_old_notification_exists = (
    SELECT COUNT(1)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'rehab_patient_notification'
);
SET @rehab_old_notification_migrate_sql = IF(
        @rehab_old_notification_exists > 0,
        'INSERT INTO `rehab_notification`
(`id`, `notification_no`, `target_type`, `target_user_id`, `patient_id`, `episode_id`,
 `related_type`, `related_id`, `notification_type`, `title`, `content`, `severity`,
 `delivery_channel`, `read_status`, `read_time`, `send_status`, `visible_from`, `expire_time`,
 `action_url`, `action_text`, `creator`, `updater`, `deleted`)
SELECT
    990000 + p.id AS id,
    CONCAT(''NTF'', DATE_FORMAT(COALESCE(p.create_time, NOW()), ''%Y%m%d''), LPAD(MOD(p.id, 10000), 4, ''0'')) AS notification_no,
    ''patient'' AS target_type,
    NULL AS target_user_id,
    p.patient_id,
    p.episode_id,
    ''legacy_patient_notification'' AS related_type,
    p.id AS related_id,
    p.notification_type,
    p.title,
    p.content,
    CASE
        WHEN p.notification_type IN (''pain_alert'') THEN ''high''
        WHEN p.notification_type IN (''reassessment_due'', ''low_adherence'') THEN ''warning''
        ELSE ''info''
        END AS severity,
    ''app_patient'' AS delivery_channel,
    CASE WHEN p.read_status = ''read'' THEN ''read'' ELSE ''unread'' END AS read_status,
    CASE WHEN p.read_status = ''read'' THEN p.update_time ELSE NULL END AS read_time,
    CASE WHEN p.sent_status IN (''pending'', ''sent'', ''failed'', ''canceled'') THEN p.sent_status ELSE ''sent'' END AS send_status,
    p.visible_from,
    p.expire_time,
    ''/pages/notification/index'' AS action_url,
    ''查看通知'' AS action_text,
    ''script'',
    ''script'',
    b''0''
FROM `rehab_patient_notification` p
         LEFT JOIN `rehab_notification` n
                   ON n.related_type = ''legacy_patient_notification''
                       AND n.related_id = p.id
                       AND n.deleted = b''0''
WHERE p.deleted = b''0''
  AND n.id IS NULL',
        'SELECT 1'
                                      );
PREPARE rehab_migrate_stmt FROM @rehab_old_notification_migrate_sql;
EXECUTE rehab_migrate_stmt;
DEALLOCATE PREPARE rehab_migrate_stmt;

-- ============================================================
-- 4) 菜单与权限（Step 6）
-- ============================================================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES
    (9500, '通知中心', 'rehab:notification:view', 2, 8, 9000, 'notification', 'ep:bell', 'rehab/notification/index', 'RehabNotification', 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9501, '风险提醒中心', 'rehab:alert:view', 2, 9, 9000, 'alert', 'ep:warning', 'rehab/alert/index', 'RehabAlert', 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9502, '机构运营看板', 'rehab:ops-dashboard:view', 2, 10, 9000, 'ops-dashboard', 'ep:pie-chart', 'rehab/ops-dashboard/index', 'RehabOpsDashboard', 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9503, '审计日志', 'rehab:audit-log:view', 2, 11, 9000, 'audit-log', 'ep:document-checked', 'rehab/audit-log/index', 'RehabAuditLog', 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `permission` = VALUES(`permission`),
    `type` = VALUES(`type`),
    `sort` = VALUES(`sort`),
    `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`),
    `icon` = VALUES(`icon`),
    `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`),
    `status` = VALUES(`status`),
    `visible` = VALUES(`visible`),
    `keep_alive` = VALUES(`keep_alive`),
    `always_show` = VALUES(`always_show`),
    `updater` = 'script',
    `deleted` = b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES
    (9510, '通知已读', 'rehab:notification:read', 3, 1, 9500, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9511, '通知发送', 'rehab:notification:send', 3, 2, 9500, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9520, '提醒确认', 'rehab:alert:acknowledge', 3, 1, 9501, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9521, '提醒解决', 'rehab:alert:resolve', 3, 2, 9501, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9522, '提醒忽略', 'rehab:alert:ignore', 3, 3, 9501, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9306, '报告锁版', 'rehab:report:lock', 3, 6, 9004, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9307, '报告解锁', 'rehab:report:unlock', 3, 7, 9004, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9308, '报告版本查看', 'rehab:report:version:view', 3, 8, 9004, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `permission` = VALUES(`permission`),
    `type` = VALUES(`type`),
    `sort` = VALUES(`sort`),
    `parent_id` = VALUES(`parent_id`),
    `updater` = 'script',
    `deleted` = b'0';

-- ============================================================
-- 5) 角色菜单绑定（Step 6）
-- ============================================================
-- 超级管理员：新增菜单与权限全量
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 1, t.menu_id, 'script', 'script', b'0', 1
FROM (
         SELECT 9500 AS menu_id UNION ALL
         SELECT 9501 UNION ALL
         SELECT 9502 UNION ALL
         SELECT 9503 UNION ALL
         SELECT 9510 UNION ALL
         SELECT 9511 UNION ALL
         SELECT 9520 UNION ALL
         SELECT 9521 UNION ALL
         SELECT 9522 UNION ALL
         SELECT 9306 UNION ALL
         SELECT 9307 UNION ALL
         SELECT 9308
     ) t
         LEFT JOIN `system_role_menu` rm
                   ON rm.role_id = 1 AND rm.menu_id = t.menu_id AND rm.tenant_id = 1 AND rm.deleted = b'0'
WHERE rm.id IS NULL;

-- 康复治疗师：通知、风险处理、报告锁版（不可解锁）、版本查看
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT r.id, t.menu_id, 'script', 'script', b'0', 1
FROM `system_role` r
         JOIN (
    SELECT 9500 AS menu_id UNION ALL
    SELECT 9501 UNION ALL
    SELECT 9510 UNION ALL
    SELECT 9520 UNION ALL
    SELECT 9521 UNION ALL
    SELECT 9522 UNION ALL
    SELECT 9306 UNION ALL
    SELECT 9308
) t
              LEFT JOIN `system_role_menu` rm
                        ON rm.role_id = r.id AND rm.menu_id = t.menu_id AND rm.tenant_id = 1 AND rm.deleted = b'0'
WHERE r.code = 'rehab_therapist'
  AND r.tenant_id = 1
  AND r.deleted = b'0'
  AND rm.id IS NULL;

-- 文员：通知中心与风险查看（不处理）、报告版本查看
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT r.id, t.menu_id, 'script', 'script', b'0', 1
FROM `system_role` r
         JOIN (
    SELECT 9500 AS menu_id UNION ALL
    SELECT 9501 UNION ALL
    SELECT 9510 UNION ALL
    SELECT 9308
) t
              LEFT JOIN `system_role_menu` rm
                        ON rm.role_id = r.id AND rm.menu_id = t.menu_id AND rm.tenant_id = 1 AND rm.deleted = b'0'
WHERE r.code = 'rehab_clerk'
  AND r.tenant_id = 1
  AND r.deleted = b'0'
  AND rm.id IS NULL;

-- ============================================================
-- 6) 规则与样例数据（Step 6）
-- ============================================================
INSERT INTO `rehab_alert_rule`
(`id`, `rule_code`, `rule_name`, `alert_type`, `enabled`, `scope_type`, `condition_json`, `severity`, `target_role_type`, `notify_channels_json`, `cooldown_hours`, `creator`, `updater`, `deleted`)
VALUES
    (98001, 'REASSESSMENT_DUE', '复评到期提醒', 'reassessment_due', b'1', 'plan', '{"logic":"review_cycle_days_due_or_trigger_due"}', 'warning', 'therapist', '["web","app_admin","app_patient"]', 24, 'script', 'script', b'0'),
    (98002, 'LOW_ADHERENCE', '低依从性提醒', 'low_adherence', b'1', 'plan', '{"logic":"completion_rate_lt_60"}', 'high', 'therapist', '["web","app_admin"]', 24, 'script', 'script', b'0'),
    (98003, 'PAIN_UPGRADE', '疼痛升级提醒', 'pain_upgrade', b'1', 'plan', '{"logic":"pain_increase_ge_2_or_avg_pain_ge_6"}', 'high', 'therapist', '["web","app_admin"]', 12, 'script', 'script', b'0'),
    (98004, 'PLAN_DUE', '计划到期提醒', 'plan_due', b'1', 'plan', '{"logic":"plan_end_date_due"}', 'warning', 'therapist', '["web","app_admin","app_patient"]', 24, 'script', 'script', b'0'),
    (98005, 'REPORT_READY', '报告可查看提醒', 'report_ready', b'1', 'report', '{"logic":"report_status_in_approved_exported_locked"}', 'info', 'therapist', '["web","app_admin","app_patient"]', 6, 'script', 'script', b'0'),
    (98006, 'HIGH_RISK_UNRESOLVED', '高风险未处理提醒', 'high_risk_unresolved', b'1', 'plan', '{"logic":"active_high_risk_gt_72h"}', 'warning', 'therapist', '["web","app_admin"]', 12, 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `rule_name` = VALUES(`rule_name`),
    `alert_type` = VALUES(`alert_type`),
    `enabled` = VALUES(`enabled`),
    `scope_type` = VALUES(`scope_type`),
    `condition_json` = VALUES(`condition_json`),
    `severity` = VALUES(`severity`),
    `target_role_type` = VALUES(`target_role_type`),
    `notify_channels_json` = VALUES(`notify_channels_json`),
    `cooldown_hours` = VALUES(`cooldown_hours`),
    `updater` = 'script',
    `deleted` = b'0';

INSERT INTO `rehab_alert_event`
(`id`, `rule_id`, `patient_id`, `episode_id`, `plan_id`, `related_type`, `related_id`, `alert_type`, `severity`, `trigger_message`, `trigger_metric`, `trigger_value`, `threshold_value`, `status`, `created_from`, `creator`, `updater`, `deleted`)
VALUES
    (98101, 98001, 10001, 13001, 40001, 'plan', 40001, 'reassessment_due', 'warning', '当前周期已到复评节点，建议尽快复评。', 'review_cycle_days', '14', '>=14', 'active', 'auto_engine', 'script', 'script', b'0'),
    (98102, 98002, 10001, 13001, 40001, 'plan', 40001, 'low_adherence', 'high', '最近训练完成率偏低，需优先处理依从性问题。', 'completion_rate', '52', '>=60', 'active', 'auto_engine', 'script', 'script', b'0'),
    (98103, 98003, 10001, 13001, 40001, 'plan', 40001, 'pain_upgrade', 'high', '疼痛反馈较前上升，建议人工复核并调整计划。', 'pain_delta', '2.5', '<2', 'active', 'auto_engine', 'script', 'script', b'0'),
    (98104, 98004, 10001, 13001, 40001, 'plan', 40001, 'plan_due', 'warning', '计划到期但尚未完成，建议确认下一阶段安排。', 'plan_end_date', DATE_FORMAT(CURDATE(), '%Y-%m-%d'), '>today', 'active', 'auto_engine', 'script', 'script', b'0'),
    (98105, 98006, 10001, 13001, 40001, 'plan', 40001, 'high_risk_unresolved', 'warning', '高风险提醒持续未处理超过 72 小时。', 'high_risk_pending_hours', '72', '<72', 'active', 'auto_engine', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `rule_id` = VALUES(`rule_id`),
    `severity` = VALUES(`severity`),
    `trigger_message` = VALUES(`trigger_message`),
    `trigger_metric` = VALUES(`trigger_metric`),
    `trigger_value` = VALUES(`trigger_value`),
    `threshold_value` = VALUES(`threshold_value`),
    `status` = VALUES(`status`),
    `updater` = 'script',
    `deleted` = b'0';

-- 报告状态与锁版演示
UPDATE `rehab_report`
SET `report_status` = 'approved',
    `approved_by` = 100,
    `locked_by` = NULL,
    `locked_time` = NULL,
    `updater` = 'script'
WHERE `id` = 30001;

INSERT INTO `rehab_report`
(`id`, `report_no`, `patient_id`, `episode_id`, `assessment_id`, `report_type`, `report_status`, `report_version`,
 `generated_by`, `reviewed_by`, `approved_by`, `locked_by`, `locked_time`, `generation_mode`,
 `report_json`, `docx_path`, `pdf_path`, `html_snapshot_path`, `last_generated_at`, `exported_at`, `note`,
 `creator`, `updater`, `deleted`)
VALUES
    (30002, 'REP202603100002', 10001, 13001, 20001, 'comprehensive', 'locked', 2,
     100, 100, 100, 1, NOW(), 'auto',
     '{"summary":"锁版版本示例"}', '/tmp/rehab-demo/REP202603100002_v2.docx', NULL, '/tmp/rehab-demo/REP202603100002_v2.html',
     NOW(), NOW(), '示例：已锁版版本', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `report_status` = VALUES(`report_status`),
    `report_version` = VALUES(`report_version`),
    `locked_by` = VALUES(`locked_by`),
    `locked_time` = VALUES(`locked_time`),
    `note` = VALUES(`note`),
    `updater` = 'script',
    `deleted` = b'0';

INSERT INTO `rehab_report_version`
(`id`, `report_id`, `version_no`, `report_status`, `generation_mode`, `report_json`, `docx_path`, `pdf_path`, `html_snapshot_path`,
 `based_on_assessment_id`, `change_summary`, `generated_by`, `reviewed_by`, `approved_by`, `locked_by`, `locked_time`,
 `creator`, `updater`, `deleted`)
VALUES
    (98301, 30001, 1, 'approved', 'auto', '{"summary":"示例报告"}', '/tmp/rehab-demo/REP202603080001_v1.docx', NULL, '/tmp/rehab-demo/REP202603080001_v1.html',
     20001, '报告审批通过', 100, 100, 100, NULL, NULL, 'script', 'script', b'0'),
    (98302, 30002, 2, 'locked', 'auto', '{"summary":"锁版版本示例"}', '/tmp/rehab-demo/REP202603100002_v2.docx', NULL, '/tmp/rehab-demo/REP202603100002_v2.html',
     20001, '报告锁版', 100, 100, 100, 1, NOW(), 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `report_status` = VALUES(`report_status`),
    `change_summary` = VALUES(`change_summary`),
    `locked_by` = VALUES(`locked_by`),
    `locked_time` = VALUES(`locked_time`),
    `updater` = 'script',
    `deleted` = b'0';

INSERT INTO `rehab_notification`
(`id`, `notification_no`, `target_type`, `target_user_id`, `patient_id`, `episode_id`, `related_type`, `related_id`,
 `notification_type`, `title`, `content`, `severity`, `delivery_channel`, `read_status`, `send_status`,
 `visible_from`, `expire_time`, `action_url`, `action_text`, `creator`, `updater`, `deleted`)
VALUES
    (98201, 'NTF202603100201', 'therapist', 100, 10001, 13001, 'alert', 98102, 'low_adherence', '低依从性提醒',
     '患者近期完成率偏低，请优先人工复核执行障碍。', 'high', 'multi', 'unread', 'sent',
     NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), '/rehab/alert', '查看提醒', 'script', 'script', b'0'),
    (98202, 'NTF202603100202', 'patient', NULL, 10001, 13001, 'report', 30001, 'report_ready', '报告摘要可查看',
     '您的最新评估报告已更新，可在患者端查看摘要。', 'info', 'app_patient', 'unread', 'sent',
     NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), '/pages/report/index', '查看报告', 'script', 'script', b'0'),
    (98203, 'NTF202603100203', 'admin', 1, NULL, NULL, 'system', 0, 'system_notice', '系统运营提示',
     '今日康复提醒刷新任务已完成。', 'info', 'web', 'unread', 'sent',
     NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), '/rehab/ops-dashboard', '查看看板', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`),
    `content` = VALUES(`content`),
    `severity` = VALUES(`severity`),
    `delivery_channel` = VALUES(`delivery_channel`),
    `read_status` = VALUES(`read_status`),
    `send_status` = VALUES(`send_status`),
    `visible_from` = VALUES(`visible_from`),
    `expire_time` = VALUES(`expire_time`),
    `action_url` = VALUES(`action_url`),
    `action_text` = VALUES(`action_text`),
    `updater` = 'script',
    `deleted` = b'0';

INSERT INTO `rehab_audit_log`
(`id`, `module_type`, `module_id`, `operation_type`, `operator_user_id`, `operator_role`,
 `before_data_json`, `after_data_json`, `ip`, `user_agent`, `result_status`, `remark`,
 `creator`, `updater`, `deleted`)
VALUES
    (98401, 'report', 30001, 'report_approve', 100, 'therapist', '{"status":"reviewed"}', '{"status":"approved"}', '127.0.0.1', 'script', 'success', '示例报告审批', 'script', 'script', b'0'),
    (98402, 'report', 30002, 'report_lock', 1, 'admin', '{"status":"approved"}', '{"status":"locked"}', '127.0.0.1', 'script', 'success', '示例报告锁版', 'script', 'script', b'0'),
    (98403, 'plan', 40001, 'plan_update', 100, 'therapist', '{"status":"active"}', '{"status":"active","note":"调整剂量"}', '127.0.0.1', 'script', 'success', '示例计划调整', 'script', 'script', b'0'),
    (98404, 'patient', 10002, 'transfer', 1, 'admin', '{"from":100}', '{"to":104}', '127.0.0.1', 'script', 'success', '示例患者转交', 'script', 'script', b'0'),
    (98405, 'alert', 98102, 'alert_acknowledge', 100, 'therapist', '{"status":"active"}', '{"status":"acknowledged"}', '127.0.0.1', 'script', 'success', '示例提醒确认', 'script', 'script', b'0'),
    (98406, 'notification', 98201, 'notification_create', 100, 'therapist', NULL, '{"id":98201}', '127.0.0.1', 'script', 'success', '示例通知发送', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `operation_type` = VALUES(`operation_type`),
    `result_status` = VALUES(`result_status`),
    `remark` = VALUES(`remark`),
    `updater` = 'script',
    `deleted` = b'0';

COMMIT;
