-- 康复模块 Step 5：管理端 UniApp + 患者端 UniApp 最小闭环
-- 说明：可重复执行，保持幂等

SET NAMES utf8mb4;
START TRANSACTION;

-- ============================================================
-- 1) Step 5 业务表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rehab_patient_user_binding` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `patient_id` bigint NOT NULL,
    `app_user_id` bigint NOT NULL COMMENT 'member_user.id',
    `bind_type` varchar(32) NOT NULL DEFAULT 'self',
    `bind_status` varchar(32) NOT NULL DEFAULT 'active',
    `phone` varchar(32) DEFAULT NULL,
    `nickname` varchar(128) DEFAULT NULL,
    `last_login_time` datetime DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_patient_user_binding_patient` (`patient_id`),
    KEY `idx_rehab_patient_user_binding_app_user` (`app_user_id`),
    KEY `idx_rehab_patient_user_binding_status` (`bind_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='患者与小程序账号绑定';

CREATE TABLE IF NOT EXISTS `rehab_followup_note` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `patient_id` bigint NOT NULL,
    `episode_id` bigint DEFAULT NULL,
    `therapist_user_id` bigint NOT NULL,
    `note_type` varchar(32) NOT NULL DEFAULT 'followup',
    `content` varchar(2000) NOT NULL,
    `visibility_type` varchar(32) NOT NULL DEFAULT 'internal',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_followup_note_patient` (`patient_id`),
    KEY `idx_rehab_followup_note_episode` (`episode_id`),
    KEY `idx_rehab_followup_note_visibility` (`visibility_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='治疗师随访备注';

CREATE TABLE IF NOT EXISTS `rehab_patient_notification` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `patient_id` bigint NOT NULL,
    `episode_id` bigint DEFAULT NULL,
    `notification_type` varchar(32) NOT NULL DEFAULT 'progress_update',
    `title` varchar(255) NOT NULL,
    `content` varchar(2000) NOT NULL,
    `read_status` varchar(32) NOT NULL DEFAULT 'unread',
    `sent_status` varchar(32) NOT NULL DEFAULT 'sent',
    `visible_from` datetime DEFAULT NULL,
    `expire_time` datetime DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_patient_notification_patient` (`patient_id`),
    KEY `idx_rehab_patient_notification_read` (`read_status`),
    KEY `idx_rehab_patient_notification_visible` (`visible_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='患者通知';

-- ============================================================
-- 2) Step 5 演示数据
-- 注：app_user_id 默认示例使用 1，请按实际 member_user.id 调整
-- ============================================================
INSERT INTO `rehab_patient_user_binding`
(`id`, `patient_id`, `app_user_id`, `bind_type`, `bind_status`, `phone`, `nickname`, `last_login_time`, `creator`, `updater`, `deleted`)
VALUES
    (47001, 10001, 1, 'self', 'active', '13800138000', '患者示例账号', NOW(), 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `patient_id`=VALUES(`patient_id`),
    `app_user_id`=VALUES(`app_user_id`),
    `bind_status`=VALUES(`bind_status`),
    `phone`=VALUES(`phone`),
    `nickname`=VALUES(`nickname`),
    `last_login_time`=VALUES(`last_login_time`),
    `updater`='script',
    `deleted`=b'0';

INSERT INTO `rehab_followup_note`
(`id`, `patient_id`, `episode_id`, `therapist_user_id`, `note_type`, `content`, `visibility_type`, `creator`, `updater`, `deleted`)
VALUES
    (47101, 10001, 13001, 100, 'followup', '本周重点：先保证动作质量，再逐步增加训练量。', 'internal', 'script', 'script', b'0'),
    (47102, 10001, 13001, 100, 'reminder', '家庭训练请优先完成踝背屈与髋稳定任务，疼痛>4请暂停并反馈。', 'patient_visible', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `content`=VALUES(`content`),
    `visibility_type`=VALUES(`visibility_type`),
    `updater`='script',
    `deleted`=b'0';

INSERT INTO `rehab_patient_notification`
(`id`, `patient_id`, `episode_id`, `notification_type`, `title`, `content`, `read_status`, `sent_status`, `visible_from`, `expire_time`, `creator`, `updater`, `deleted`)
VALUES
    (47201, 10001, 13001, 'reassessment_due', '复评提醒', '系统提示近期需要复评，请在 2 天内联系治疗师安排。', 'unread', 'sent', NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'script', 'script', b'0'),
    (47202, 10001, 13001, 'task_reminder', '今日训练提醒', '请按计划完成今日任务，提交打卡后治疗师将查看反馈。', 'unread', 'sent', NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `title`=VALUES(`title`),
    `content`=VALUES(`content`),
    `read_status`=VALUES(`read_status`),
    `sent_status`=VALUES(`sent_status`),
    `visible_from`=VALUES(`visible_from`),
    `expire_time`=VALUES(`expire_time`),
    `updater`='script',
    `deleted`=b'0';

COMMIT;
