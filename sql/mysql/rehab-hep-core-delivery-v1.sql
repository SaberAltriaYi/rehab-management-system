-- 037: HEP in Core. Additive tables and explicitly scoped menu grants; no patient record migration.
-- Clinical prescriptions require therapist sign-off. HEP execution stays in its own database.
-- Strict CREATE TABLE intentionally fails on an incompatible pre-existing schema.
SET NAMES utf8mb4;

CREATE TABLE `rehab_patient_app_invitation` (
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

CREATE TABLE `rehab_hep_exercise` (
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

CREATE TABLE `rehab_hep_template` (
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

CREATE TABLE `rehab_hep_template_item` (
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

CREATE TABLE `rehab_hep_prescription` (
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

CREATE TABLE `rehab_hep_prescription_version` (
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

CREATE TABLE `rehab_hep_prescription_item` (
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

CREATE TABLE `rehab_hep_training_day` (
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

CREATE TABLE `rehab_hep_training_group` (
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

CREATE TABLE `rehab_hep_execution_feedback` (
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

-- Core is the authority for tenant, patient, episode and therapist assignments.
-- Idempotent inbox only: HEP clinical feedback is never auto-diagnosed.
CREATE TABLE rehab_hep_bridge_event (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  event_id VARCHAR(80) NOT NULL,
  patient_id BIGINT NOT NULL,
  episode_id BIGINT NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  session_id VARCHAR(80) NOT NULL,
  payload_json TEXT NOT NULL,
  review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  reviewed_by BIGINT DEFAULT NULL,
  reviewed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_hep_bridge_inbox (tenant_id,event_id),
  KEY ix_hep_bridge_patient (tenant_id,patient_id,created_at),
  KEY ix_hep_bridge_review (tenant_id,review_status,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Keep HEP under the Core rehabilitation navigation; no patient-side library access.
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
VALUES
(98900,'家庭康复 HEP','rehab:hep:workspace:view',2,15,9000,'hep','i-carbon-medical','rehab/hep-console/index','RehabHepConsole',0,b'1',b'1',b'1','migration','migration',b'0'),
(98901,'HEP动作库查看','rehab:hep:exercise:view',3,1,98900,'','','','',0,b'1',b'1',b'1','migration','migration',b'0'),
(98902,'HEP动作创建','rehab:hep:exercise:create',3,2,98900,'','','','',0,b'1',b'1',b'1','migration','migration',b'0'),
(98903,'HEP动作审核','rehab:hep:exercise:review',3,3,98900,'','','','',0,b'1',b'1',b'1','migration','migration',b'0'),
(98904,'HEP模板查看','rehab:hep:template:view',3,4,98900,'','','','',0,b'1',b'1',b'1','migration','migration',b'0'),
(98905,'HEP模板创建','rehab:hep:template:create',3,5,98900,'','','','',0,b'1',b'1',b'1','migration','migration',b'0'),
(98906,'HEP处方创建','rehab:hep:prescription:create',3,6,98900,'','','','',0,b'1',b'1',b'1','migration','migration',b'0'),
(98907,'HEP处方签发','rehab:hep:prescription:issue',3,7,98900,'','','','',0,b'1',b'1',b'1','migration','migration',b'0'),
(98908,'HEP反馈审核','rehab:hep:feedback:review',3,8,98900,'','','','',0,b'1',b'1',b'1','migration','migration',b'0'),
(98909,'HEP机构集成管理','rehab:hep:integration:admin',3,9,98900,'','','','',0,b'1',b'1',b'1','migration','migration',b'0');

-- Tenant 1 internal release: only authorized admin can manage mappings/approvals.
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT r.id,m.id,'migration','migration',b'0',r.tenant_id
FROM system_role r JOIN system_menu m ON m.id BETWEEN 98900 AND 98909
WHERE r.tenant_id=1 AND r.code='super_admin' AND r.deleted=b'0';

-- Clinicians may prescribe and review patient risks, not approve action-library assets.
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT r.id,m.id,'migration','migration',b'0',r.tenant_id
FROM system_role r JOIN system_menu m ON m.id IN (98900,98901,98902,98904,98905,98906,98907,98908)
WHERE r.tenant_id=1 AND r.code='rehab_therapist' AND r.deleted=b'0';
