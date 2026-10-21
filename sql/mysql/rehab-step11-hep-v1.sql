-- 康复模块 Step 11：患者端安全邀请与 HEP（家庭康复训练）主数据
-- 说明：幂等执行；业务数据按 tenant_id 隔离。AI 只生成草稿，处方必须由治疗师审核签发。

SET NAMES utf8mb4;
START TRANSACTION;

CREATE TABLE IF NOT EXISTS `rehab_patient_app_invitation` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL,
    `patient_id` bigint NOT NULL,
    `phone` varchar(32) NOT NULL,
    `invitation_digest` char(64) NOT NULL,
    `expires_at` datetime NOT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'PENDING',
    `issued_by` bigint NOT NULL,
    `redeemed_by_user_id` bigint DEFAULT NULL,
    `redeemed_at` datetime DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_patient_invite_digest` (`tenant_id`,`invitation_digest`),
    KEY `idx_rehab_patient_invite_status` (`tenant_id`,`patient_id`,`status`),
    KEY `idx_rehab_patient_invite_expiry` (`tenant_id`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='患者端一次性邀请';

CREATE TABLE IF NOT EXISTS `rehab_hep_exercise` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL,
    `exercise_code` varchar(64) NOT NULL,
    `name` varchar(255) NOT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
    `body_region` varchar(128) DEFAULT NULL,
    `target_deficit` varchar(500) DEFAULT NULL,
    `instruction_text` text,
    `contraindications` varchar(1000) DEFAULT NULL,
    `media_url` varchar(1000) DEFAULT NULL,
    `source_type` varchar(32) DEFAULT NULL,
    `source_reference` varchar(500) DEFAULT NULL,
    `license_status` varchar(32) DEFAULT 'PENDING',
    `reviewed_by` bigint DEFAULT NULL,
    `reviewed_time` datetime DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_hep_exercise_code` (`tenant_id`,`exercise_code`),
    KEY `idx_rehab_hep_exercise_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HEP 经治疗师审核动作库';

CREATE TABLE IF NOT EXISTS `rehab_hep_template` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL,
    `template_no` varchar(64) NOT NULL,
    `name` varchar(255) NOT NULL,
    `goal` varchar(500) DEFAULT NULL,
    `protocol_version` varchar(64) DEFAULT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
    `reviewed_by` bigint DEFAULT NULL,
    `reviewed_time` datetime DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_hep_template_no` (`tenant_id`,`template_no`),
    KEY `idx_rehab_hep_template_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HEP 训练模板';

CREATE TABLE IF NOT EXISTS `rehab_hep_template_item` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL,
    `template_id` bigint NOT NULL,
    `exercise_id` bigint NOT NULL,
    `training_day_no` int NOT NULL DEFAULT 1,
    `group_code` varchar(64) NOT NULL DEFAULT 'A',
    `sort_order` int NOT NULL DEFAULT 1,
    `dosage_json` text,
    `instruction_text` text,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_hep_template_item_template` (`tenant_id`,`template_id`),
    KEY `idx_rehab_hep_template_item_exercise` (`tenant_id`,`exercise_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HEP 模板动作';

CREATE TABLE IF NOT EXISTS `rehab_hep_prescription` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL,
    `prescription_no` varchar(64) NOT NULL,
    `patient_id` bigint NOT NULL,
    `episode_id` bigint NOT NULL,
    `core_plan_id` bigint DEFAULT NULL,
    `therapist_user_id` bigint NOT NULL,
    `template_id` bigint DEFAULT NULL,
    `current_version_id` bigint DEFAULT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
    `start_date` date DEFAULT NULL,
    `end_date` date DEFAULT NULL,
    `core_sync_status` varchar(16) NOT NULL DEFAULT 'PENDING',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_hep_prescription_no` (`tenant_id`,`prescription_no`),
    KEY `idx_rehab_hep_prescription_patient` (`tenant_id`,`patient_id`,`status`),
    KEY `idx_rehab_hep_prescription_core_plan` (`tenant_id`,`core_plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HEP 患者处方主表';

CREATE TABLE IF NOT EXISTS `rehab_hep_prescription_version` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL,
    `prescription_id` bigint NOT NULL,
    `version_no` int NOT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
    `change_reason` varchar(1000) DEFAULT NULL,
    `source_template_id` bigint DEFAULT NULL,
    `reviewed_by` bigint DEFAULT NULL,
    `reviewed_time` datetime DEFAULT NULL,
    `issued_by` bigint DEFAULT NULL,
    `issued_time` datetime DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_hep_prescription_version` (`tenant_id`,`prescription_id`,`version_no`),
    KEY `idx_rehab_hep_prescription_version_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HEP 处方版本';

CREATE TABLE IF NOT EXISTS `rehab_hep_prescription_item` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL,
    `version_id` bigint NOT NULL,
    `exercise_id` bigint NOT NULL,
    `training_day_no` int NOT NULL DEFAULT 1,
    `group_code` varchar(64) NOT NULL DEFAULT 'A',
    `sort_order` int NOT NULL DEFAULT 1,
    `dosage_json` text,
    `instruction_text` text,
    `pain_limit_rule` varchar(500) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_hep_prescription_item_version` (`tenant_id`,`version_id`),
    KEY `idx_rehab_hep_prescription_item_exercise` (`tenant_id`,`exercise_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HEP 处方版本动作';

CREATE TABLE IF NOT EXISTS `rehab_hep_training_day` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL,
    `version_id` bigint NOT NULL,
    `day_no` int NOT NULL,
    `scheduled_date` date DEFAULT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'PENDING',
    `completed_at` datetime DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_hep_training_day` (`tenant_id`,`version_id`,`day_no`),
    KEY `idx_rehab_hep_training_day_date` (`tenant_id`,`scheduled_date`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HEP 训练日';

CREATE TABLE IF NOT EXISTS `rehab_hep_training_group` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL,
    `training_day_id` bigint NOT NULL,
    `group_code` varchar(64) NOT NULL,
    `group_name` varchar(255) DEFAULT NULL,
    `sort_order` int NOT NULL DEFAULT 1,
    `rest_seconds` int DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_hep_training_group` (`tenant_id`,`training_day_id`,`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HEP 训练组';

CREATE TABLE IF NOT EXISTS `rehab_hep_execution_feedback` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL,
    `patient_id` bigint NOT NULL,
    `prescription_id` bigint NOT NULL,
    `version_id` bigint NOT NULL,
    `training_day_id` bigint DEFAULT NULL,
    `group_code` varchar(64) DEFAULT NULL,
    `submitted_by_member_id` bigint NOT NULL,
    `completion_status` varchar(16) NOT NULL DEFAULT 'COMPLETED',
    `pain_before` decimal(4,1) DEFAULT NULL,
    `pain_after` decimal(4,1) DEFAULT NULL,
    `rpe` decimal(4,1) DEFAULT NULL,
    `abnormal_flag` bit(1) NOT NULL DEFAULT b'0',
    `feedback_text` varchar(2000) DEFAULT NULL,
    `therapist_review_status` varchar(16) NOT NULL DEFAULT 'PENDING',
    `reviewed_by` bigint DEFAULT NULL,
    `reviewed_time` datetime DEFAULT NULL,
    `core_sync_status` varchar(16) NOT NULL DEFAULT 'PENDING',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_hep_feedback_patient` (`tenant_id`,`patient_id`,`create_time`),
    KEY `idx_rehab_hep_feedback_review` (`tenant_id`,`therapist_review_status`,`abnormal_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HEP 患者执行反馈';

-- 旧绑定表由 Step 9 补 tenant_id；这里兼容尚未执行 Step 9 的数据库，并约束一个会员只能绑定一个患者。
SET @has_binding_tenant := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='rehab_patient_user_binding' AND COLUMN_NAME='tenant_id');
SET @add_binding_tenant_sql := IF(@has_binding_tenant=0,
    'ALTER TABLE `rehab_patient_user_binding` ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1, ADD INDEX `idx_rehab_patient_binding_tenant` (`tenant_id`)',
    'SELECT 1');
PREPARE rehab_binding_tenant_stmt FROM @add_binding_tenant_sql;
EXECUTE rehab_binding_tenant_stmt;
DEALLOCATE PREPARE rehab_binding_tenant_stmt;

SET @has_binding_unique := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='rehab_patient_user_binding'
      AND INDEX_NAME='uk_rehab_patient_user_active');
SET @add_binding_unique_sql := IF(@has_binding_unique=0,
    'ALTER TABLE `rehab_patient_user_binding` ADD UNIQUE KEY `uk_rehab_patient_user_active` (`tenant_id`,`app_user_id`,`bind_status`)',
    'SELECT 1');
PREPARE rehab_binding_unique_stmt FROM @add_binding_unique_sql;
EXECUTE rehab_binding_unique_stmt;
DEALLOCATE PREPARE rehab_binding_unique_stmt;

-- 保证既有数据库也不会自动把 AI 草稿变成处方；解释草稿仍需治疗师审核。
UPDATE `rehab_ai_config` SET `enable_plan_draft`=b'0', `updater`='step11-hep'
WHERE `config_scope`='global' AND `deleted`=b'0';

-- 菜单权限：不把患者动作库暴露给患者端；所有 HEP 管理接口仍受后台权限控制。
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`updater`,`deleted`)
VALUES
(9201,'患者端邀请','rehab:patient:app-invite',3,18,9002,'','','',NULL,0,b'1',b'1',b'1','script','script',b'0'),
(9202,'HEP动作查看','rehab:hep:exercise:view',3,1,9002,'','','',NULL,0,b'1',b'1',b'1','script','script',b'0'),
(9203,'HEP动作创建','rehab:hep:exercise:create',3,2,9002,'','','',NULL,0,b'1',b'1',b'1','script','script',b'0'),
(9204,'HEP动作审核','rehab:hep:exercise:review',3,3,9002,'','','',NULL,0,b'1',b'1',b'1','script','script',b'0'),
(9205,'HEP处方创建','rehab:hep:prescription:create',3,4,9002,'','','',NULL,0,b'1',b'1',b'1','script','script',b'0'),
(9206,'HEP处方签发','rehab:hep:prescription:issue',3,5,9002,'','','',NULL,0,b'1',b'1',b'1','script','script',b'0'),
(9207,'HEP反馈审核','rehab:hep:feedback:review',3,6,9002,'','','',NULL,0,b'1',b'1',b'1','script','script',b'0'),
(9208,'HEP模板查看','rehab:hep:template:view',3,7,9002,'','','',NULL,0,b'1',b'1',b'1','script','script',b'0'),
(9209,'HEP模板创建','rehab:hep:template:create',3,8,9002,'','','',NULL,0,b'1',b'1',b'1','script','script',b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`updater`,`deleted`,`tenant_id`)
SELECT 1, t.menu_id, 'script', 'script', b'0', 1
FROM (SELECT 9201 AS menu_id UNION ALL SELECT 9202 UNION ALL SELECT 9203 UNION ALL SELECT 9204 UNION ALL SELECT 9205 UNION ALL SELECT 9206 UNION ALL SELECT 9207 UNION ALL SELECT 9208 UNION ALL SELECT 9209) t
LEFT JOIN `system_role_menu` rm ON rm.role_id=1 AND rm.menu_id=t.menu_id AND rm.tenant_id=1 AND rm.deleted=b'0'
WHERE rm.id IS NULL;

COMMIT;
