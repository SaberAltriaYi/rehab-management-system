-- 康复模块 Step 2：患者主档案 + CRM 绑定 + 分配 + episode（v1）
-- 说明：可重复执行，保持幂等

SET NAMES utf8mb4;
START TRANSACTION;

-- ============================================================
-- 1) 业务表结构
-- ============================================================
CREATE TABLE IF NOT EXISTS `rehab_patient` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `patient_no` varchar(64) DEFAULT NULL,
    `name` varchar(64) NOT NULL,
    `gender` tinyint DEFAULT NULL,
    `birthday` date DEFAULT NULL,
    `age` int DEFAULT NULL,
    `phone` varchar(20) DEFAULT NULL,
    `id_card_masked` varchar(64) DEFAULT NULL,
    `contact_person` varchar(64) DEFAULT NULL,
    `contact_phone` varchar(20) DEFAULT NULL,
    `emergency_contact` varchar(64) DEFAULT NULL,
    `emergency_phone` varchar(20) DEFAULT NULL,
    `height_cm` decimal(8,2) DEFAULT NULL,
    `weight_kg` decimal(8,2) DEFAULT NULL,
    `bmi` decimal(8,2) DEFAULT NULL,
    `dominant_side` varchar(16) DEFAULT NULL,
    `sport_type` varchar(64) DEFAULT NULL,
    `school_or_company` varchar(128) DEFAULT NULL,
    `chief_complaint` varchar(500) DEFAULT NULL,
    `pain_area` varchar(255) DEFAULT NULL,
    `pain_score` decimal(4,1) DEFAULT NULL,
    `medical_history` text,
    `injury_history` text,
    `training_history` text,
    `source_channel` varchar(32) DEFAULT NULL,
    `remark` varchar(1000) DEFAULT NULL,
    `current_status` varchar(32) NOT NULL DEFAULT 'active',
    `current_stage` varchar(32) NOT NULL DEFAULT '初诊建档',
    `current_therapist_user_id` bigint DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_patient_no` (`patient_no`),
    KEY `idx_rehab_patient_name_phone` (`name`, `phone`),
    KEY `idx_rehab_patient_stage` (`current_stage`),
    KEY `idx_rehab_patient_therapist` (`current_therapist_user_id`),
    KEY `idx_rehab_patient_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复患者主档案';

CREATE TABLE IF NOT EXISTS `rehab_patient_crm_binding` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `patient_id` bigint NOT NULL,
    `crm_customer_id` bigint DEFAULT NULL,
    `bind_status` varchar(32) NOT NULL DEFAULT 'unbound',
    `bind_source` varchar(32) NOT NULL DEFAULT 'manual',
    `sync_status` varchar(32) DEFAULT NULL,
    `sync_message` varchar(1000) DEFAULT NULL,
    `bind_time` datetime DEFAULT NULL,
    `last_sync_time` datetime DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_patient_binding_patient` (`patient_id`),
    KEY `idx_rehab_patient_binding_crm` (`crm_customer_id`),
    KEY `idx_rehab_patient_binding_status` (`bind_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复患者 CRM 绑定';

CREATE TABLE IF NOT EXISTS `rehab_therapist_assignment` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `patient_id` bigint NOT NULL,
    `therapist_user_id` bigint NOT NULL,
    `role_type` varchar(32) NOT NULL,
    `assign_status` varchar(32) NOT NULL DEFAULT 'active',
    `assign_reason` varchar(255) DEFAULT NULL,
    `start_time` datetime NOT NULL,
    `end_time` datetime DEFAULT NULL,
    `assigned_by` bigint DEFAULT NULL,
    `transfer_from_user_id` bigint DEFAULT NULL,
    `transfer_to_user_id` bigint DEFAULT NULL,
    `remark` varchar(1000) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_assignment_patient` (`patient_id`),
    KEY `idx_rehab_assignment_therapist` (`therapist_user_id`),
    KEY `idx_rehab_assignment_active` (`assign_status`, `role_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复治疗师归属记录';

CREATE TABLE IF NOT EXISTS `rehab_episode` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `episode_no` varchar(64) DEFAULT NULL,
    `patient_id` bigint NOT NULL,
    `primary_therapist_user_id` bigint DEFAULT NULL,
    `episode_type` varchar(32) NOT NULL DEFAULT 'initial',
    `current_stage` varchar(32) NOT NULL DEFAULT '待评估',
    `start_date` date DEFAULT NULL,
    `end_date` date DEFAULT NULL,
    `primary_goal` varchar(500) DEFAULT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'active',
    `close_reason` varchar(255) DEFAULT NULL,
    `referral_reason` varchar(255) DEFAULT NULL,
    `note` varchar(1000) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_episode_no` (`episode_no`),
    KEY `idx_rehab_episode_patient` (`patient_id`),
    KEY `idx_rehab_episode_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复 episode';

CREATE TABLE IF NOT EXISTS `rehab_patient_tag` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `patient_id` bigint NOT NULL,
    `tag_name` varchar(64) NOT NULL,
    `tag_type` varchar(32) NOT NULL,
    `color` varchar(32) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_patient_tag_patient` (`patient_id`),
    KEY `idx_rehab_patient_tag_type` (`tag_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复患者标签';

CREATE TABLE IF NOT EXISTS `rehab_patient_operation_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `patient_id` bigint NOT NULL,
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
    KEY `idx_rehab_patient_operation_log_patient` (`patient_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复患者操作日志';

-- ============================================================
-- 2) 菜单权限补充（Step 2）
-- ============================================================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9103, '患者删除', 'rehab:patient:delete', 3, 3, 9002, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9104, '患者详情', 'rehab:patient:detail', 3, 4, 9002, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9105, '患者分配', 'rehab:patient:assign', 3, 5, 9002, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9106, '患者转交', 'rehab:patient:transfer', 3, 6, 9002, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9107, 'CRM绑定', 'rehab:patient:bind-crm', 3, 7, 9002, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9108, '患者导出', 'rehab:patient:export', 3, 8, 9002, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9111, 'Episode查看', 'rehab:episode:view', 3, 11, 9002, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9112, 'Episode创建', 'rehab:episode:create', 3, 12, 9002, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9113, 'Episode更新', 'rehab:episode:update', 3, 13, 9002, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9114, '分配查看', 'rehab:assignment:view', 3, 14, 9002, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9115, '分配创建', 'rehab:assignment:create', 3, 15, 9002, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9116, '分配更新', 'rehab:assignment:update', 3, 16, 9002, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

-- ============================================================
-- 3) 角色菜单绑定（Step 2）
-- ============================================================
-- 超级管理员：新增权限全部开放
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 1, t.menu_id, 'script', 'script', b'0', 1
FROM (
         SELECT 9103 AS menu_id UNION ALL
         SELECT 9104 UNION ALL
         SELECT 9105 UNION ALL
         SELECT 9106 UNION ALL
         SELECT 9107 UNION ALL
         SELECT 9108 UNION ALL
         SELECT 9111 UNION ALL
         SELECT 9112 UNION ALL
         SELECT 9113 UNION ALL
         SELECT 9114 UNION ALL
         SELECT 9115 UNION ALL
         SELECT 9116
     ) t
         LEFT JOIN `system_role_menu` rm
                   ON rm.role_id = 1 AND rm.menu_id = t.menu_id AND rm.tenant_id = 1 AND rm.deleted = b'0'
WHERE rm.id IS NULL;

-- 康复治疗师
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT r.id, t.menu_id, 'script', 'script', b'0', 1
FROM `system_role` r
         JOIN (
    SELECT 9000 AS menu_id UNION ALL
    SELECT 9001 UNION ALL
    SELECT 9002 UNION ALL
    SELECT 9003 UNION ALL
    SELECT 9004 UNION ALL
    SELECT 9101 UNION ALL
    SELECT 9102 UNION ALL
    SELECT 9104 UNION ALL
    SELECT 9107 UNION ALL
    SELECT 9108 UNION ALL
    SELECT 9111 UNION ALL
    SELECT 9112 UNION ALL
    SELECT 9113 UNION ALL
    SELECT 9114
) t
              LEFT JOIN `system_role_menu` rm
                        ON rm.role_id = r.id AND rm.menu_id = t.menu_id AND rm.tenant_id = 1 AND rm.deleted = b'0'
WHERE r.code = 'rehab_therapist'
  AND r.tenant_id = 1
  AND r.deleted = b'0'
  AND rm.id IS NULL;

-- 文员
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT r.id, t.menu_id, 'script', 'script', b'0', 1
FROM `system_role` r
         JOIN (
    SELECT 9000 AS menu_id UNION ALL
    SELECT 9002 UNION ALL
    SELECT 9101 UNION ALL
    SELECT 9102 UNION ALL
    SELECT 9104 UNION ALL
    SELECT 9107 UNION ALL
    SELECT 9114
) t
              LEFT JOIN `system_role_menu` rm
                        ON rm.role_id = r.id AND rm.menu_id = t.menu_id AND rm.tenant_id = 1 AND rm.deleted = b'0'
WHERE r.code = 'rehab_clerk'
  AND r.tenant_id = 1
  AND r.deleted = b'0'
  AND rm.id IS NULL;

-- ============================================================
-- 4) 演示数据（患者 + 绑定 + 分配 + episode）
-- ============================================================
INSERT INTO `rehab_patient` (`id`, `patient_no`, `name`, `gender`, `birthday`, `age`, `phone`, `contact_person`, `contact_phone`, `chief_complaint`, `pain_area`, `pain_score`, `source_channel`, `current_status`, `current_stage`, `current_therapist_user_id`, `creator`, `updater`, `deleted`)
VALUES (10001, 'PT202603080001', '王小明', 1, '2012-05-10', 13, '13812340001', '王女士', '13812340002', '训练后膝前侧不适', '左膝', 4.0, '门诊', 'active', '待评估', 100, 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `phone`=VALUES(`phone`), `current_stage`=VALUES(`current_stage`), `current_therapist_user_id`=VALUES(`current_therapist_user_id`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_patient` (`id`, `patient_no`, `name`, `gender`, `birthday`, `age`, `phone`, `contact_person`, `contact_phone`, `chief_complaint`, `pain_area`, `pain_score`, `source_channel`, `current_status`, `current_stage`, `current_therapist_user_id`, `creator`, `updater`, `deleted`)
VALUES (10002, 'PT202603080002', '李悦', 2, '2008-11-21', 17, '13812340003', '李先生', '13812340004', '跑跳后踝部酸痛', '右踝', 3.0, '转介绍', 'active', '执行中', 104, 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `phone`=VALUES(`phone`), `current_stage`=VALUES(`current_stage`), `current_therapist_user_id`=VALUES(`current_therapist_user_id`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_patient` (`id`, `patient_no`, `name`, `gender`, `birthday`, `age`, `phone`, `contact_person`, `contact_phone`, `chief_complaint`, `pain_area`, `pain_score`, `source_channel`, `current_status`, `current_stage`, `current_therapist_user_id`, `creator`, `updater`, `deleted`)
VALUES (10003, 'PT202603080003', '赵楠', 1, '2005-03-16', 21, '13812340005', '赵女士', '13812340006', '深蹲时腰背代偿明显', '下背部', 2.0, '线上', 'active', '复评中', 100, 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `phone`=VALUES(`phone`), `current_stage`=VALUES(`current_stage`), `current_therapist_user_id`=VALUES(`current_therapist_user_id`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_patient_crm_binding` (`id`, `patient_id`, `crm_customer_id`, `bind_status`, `bind_source`, `sync_status`, `sync_message`, `bind_time`, `last_sync_time`, `creator`, `updater`, `deleted`)
VALUES (11001, 10001, 50001, 'bound', 'manual', 'success', '已人工绑定', NOW(), NOW(), 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `crm_customer_id`=VALUES(`crm_customer_id`), `bind_status`=VALUES(`bind_status`), `last_sync_time`=VALUES(`last_sync_time`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_therapist_assignment` (`id`, `patient_id`, `therapist_user_id`, `role_type`, `assign_status`, `assign_reason`, `start_time`, `end_time`, `assigned_by`, `remark`, `creator`, `updater`, `deleted`)
VALUES (12001, 10001, 100, 'primary', 'active', '初始分配', NOW(), NULL, 1, '示例数据', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `therapist_user_id`=VALUES(`therapist_user_id`), `role_type`=VALUES(`role_type`), `assign_status`=VALUES(`assign_status`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_therapist_assignment` (`id`, `patient_id`, `therapist_user_id`, `role_type`, `assign_status`, `assign_reason`, `start_time`, `end_time`, `assigned_by`, `transfer_to_user_id`, `remark`, `creator`, `updater`, `deleted`)
VALUES (12002, 10002, 100, 'primary', 'transferred', '阶段转交', DATE_SUB(NOW(), INTERVAL 21 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), 1, 104, '历史转交记录', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `assign_status`=VALUES(`assign_status`), `end_time`=VALUES(`end_time`), `transfer_to_user_id`=VALUES(`transfer_to_user_id`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_therapist_assignment` (`id`, `patient_id`, `therapist_user_id`, `role_type`, `assign_status`, `assign_reason`, `start_time`, `assigned_by`, `transfer_from_user_id`, `remark`, `creator`, `updater`, `deleted`)
VALUES (12003, 10002, 104, 'primary', 'active', '转交接管', DATE_SUB(NOW(), INTERVAL 7 DAY), 1, 100, '当前主责', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `assign_status`=VALUES(`assign_status`), `therapist_user_id`=VALUES(`therapist_user_id`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_episode` (`id`, `episode_no`, `patient_id`, `primary_therapist_user_id`, `episode_type`, `current_stage`, `start_date`, `status`, `primary_goal`, `note`, `creator`, `updater`, `deleted`)
VALUES (13001, 'EP202603080001', 10001, 100, 'initial', '待评估', CURDATE(), 'active', '建立基线评估并明确干预优先级', '初始 episode', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `current_stage`=VALUES(`current_stage`), `status`=VALUES(`status`), `primary_therapist_user_id`=VALUES(`primary_therapist_user_id`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_episode` (`id`, `episode_no`, `patient_id`, `primary_therapist_user_id`, `episode_type`, `current_stage`, `start_date`, `status`, `primary_goal`, `note`, `creator`, `updater`, `deleted`)
VALUES (13002, 'EP202603080002', 10002, 104, 'followup', '执行中', DATE_SUB(CURDATE(), INTERVAL 28 DAY), 'active', '降低左右差并提升动作稳定性', '随访 episode', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `current_stage`=VALUES(`current_stage`), `status`=VALUES(`status`), `primary_therapist_user_id`=VALUES(`primary_therapist_user_id`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_patient_operation_log` (`id`, `patient_id`, `operation_type`, `operator_user_id`, `before_data_json`, `after_data_json`, `remark`, `creator`, `updater`, `deleted`)
VALUES (14001, 10001, 'create', 1, NULL, '{"patientNo":"PT202603080001"}', '示例建档日志', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `remark`=VALUES(`remark`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_patient_operation_log` (`id`, `patient_id`, `operation_type`, `operator_user_id`, `before_data_json`, `after_data_json`, `remark`, `creator`, `updater`, `deleted`)
VALUES (14002, 10002, 'transfer', 1, '{"from":100}', '{"to":104}', '示例转交日志', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `remark`=VALUES(`remark`), `updater`='script', `deleted`=b'0';

-- 将现有用户绑定到康复角色（用于演示）
INSERT INTO `system_user_role` (`user_id`, `role_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT u.id, r.id, 'script', 'script', b'0', 1
FROM `system_users` u
JOIN `system_role` r ON r.code = 'rehab_therapist' AND r.tenant_id = 1 AND r.deleted = b'0'
LEFT JOIN `system_user_role` ur ON ur.user_id = u.id AND ur.role_id = r.id AND ur.tenant_id = 1 AND ur.deleted = b'0'
WHERE u.id IN (100, 104)
  AND u.tenant_id = 1
  AND u.deleted = b'0'
  AND ur.id IS NULL;

INSERT INTO `system_user_role` (`user_id`, `role_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT u.id, r.id, 'script', 'script', b'0', 1
FROM `system_users` u
JOIN `system_role` r ON r.code = 'rehab_clerk' AND r.tenant_id = 1 AND r.deleted = b'0'
LEFT JOIN `system_user_role` ur ON ur.user_id = u.id AND ur.role_id = r.id AND ur.tenant_id = 1 AND ur.deleted = b'0'
WHERE u.id IN (103)
  AND u.tenant_id = 1
  AND u.deleted = b'0'
  AND ur.id IS NULL;

COMMIT;
