-- 康复模块 Step 4：计划执行闭环（plan -> task -> checkin -> progress -> reassessment-trigger）
-- 说明：可重复执行，保持幂等

SET NAMES utf8mb4;
START TRANSACTION;

-- ============================================================
-- 1) 业务表结构
-- ============================================================
CREATE TABLE IF NOT EXISTS `rehab_care_plan` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `plan_no` varchar(64) DEFAULT NULL,
    `patient_id` bigint NOT NULL,
    `episode_id` bigint NOT NULL,
    `source_assessment_id` bigint DEFAULT NULL,
    `primary_therapist_user_id` bigint DEFAULT NULL,
    `plan_name` varchar(255) NOT NULL,
    `plan_type` varchar(32) NOT NULL DEFAULT 'rehab',
    `status` varchar(32) NOT NULL DEFAULT 'draft',
    `start_date` date DEFAULT NULL,
    `end_date` date DEFAULT NULL,
    `cycle_days` int DEFAULT NULL,
    `short_term_goals_json` text,
    `mid_term_goals_json` text,
    `long_term_goals_json` text,
    `contraindications` varchar(1000) DEFAULT NULL,
    `precautions` varchar(1000) DEFAULT NULL,
    `home_program_enabled` bit(1) NOT NULL DEFAULT b'1',
    `clinic_program_enabled` bit(1) NOT NULL DEFAULT b'1',
    `intensity_level` varchar(16) DEFAULT 'medium',
    `review_cycle_days` int DEFAULT NULL,
    `note` varchar(1000) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_care_plan_no` (`plan_no`),
    KEY `idx_rehab_care_plan_patient` (`patient_id`),
    KEY `idx_rehab_care_plan_episode` (`episode_id`),
    KEY `idx_rehab_care_plan_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复计划主表';

CREATE TABLE IF NOT EXISTS `rehab_exercise_task` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `plan_id` bigint NOT NULL,
    `patient_id` bigint NOT NULL,
    `episode_id` bigint NOT NULL,
    `task_no` varchar(64) DEFAULT NULL,
    `sort_order` int NOT NULL DEFAULT 1,
    `task_name` varchar(255) NOT NULL,
    `module_type` varchar(32) DEFAULT NULL,
    `execution_type` varchar(16) DEFAULT 'both',
    `target_deficit` varchar(500) DEFAULT NULL,
    `body_region` varchar(128) DEFAULT NULL,
    `dosage_text` varchar(255) DEFAULT NULL,
    `repetitions` int DEFAULT NULL,
    `sets` int DEFAULT NULL,
    `hold_seconds` int DEFAULT NULL,
    `frequency_per_week` int DEFAULT NULL,
    `tempo` varchar(64) DEFAULT NULL,
    `pain_limit_rule` varchar(500) DEFAULT NULL,
    `stop_rule` varchar(500) DEFAULT NULL,
    `progression_rule` varchar(500) DEFAULT NULL,
    `regression_rule` varchar(500) DEFAULT NULL,
    `replacement_exercise` varchar(255) DEFAULT NULL,
    `instruction_text` text,
    `media_url` varchar(1000) DEFAULT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'active',
    `start_date` date DEFAULT NULL,
    `end_date` date DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_exercise_task_no` (`task_no`),
    KEY `idx_rehab_exercise_task_plan` (`plan_id`),
    KEY `idx_rehab_exercise_task_patient` (`patient_id`),
    KEY `idx_rehab_exercise_task_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复训练任务';

CREATE TABLE IF NOT EXISTS `rehab_task_schedule` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `task_id` bigint NOT NULL,
    `plan_id` bigint NOT NULL,
    `patient_id` bigint NOT NULL,
    `schedule_type` varchar(32) NOT NULL DEFAULT 'weekly',
    `weekday_mask` int DEFAULT NULL,
    `scheduled_date` date DEFAULT NULL,
    `target_sessions` int DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_task_schedule_task` (`task_id`),
    KEY `idx_rehab_task_schedule_plan` (`plan_id`),
    KEY `idx_rehab_task_schedule_date` (`scheduled_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='训练任务排程';

CREATE TABLE IF NOT EXISTS `rehab_daily_checkin` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `patient_id` bigint NOT NULL,
    `episode_id` bigint NOT NULL,
    `plan_id` bigint NOT NULL,
    `checkin_date` date NOT NULL,
    `submitted_by_user_id` bigint DEFAULT NULL,
    `submit_role_type` varchar(32) NOT NULL,
    `overall_completion_rate` decimal(6,2) DEFAULT NULL,
    `pain_score_before` decimal(4,1) DEFAULT NULL,
    `pain_score_after` decimal(4,1) DEFAULT NULL,
    `fatigue_level` int DEFAULT NULL,
    `confidence_level` int DEFAULT NULL,
    `overall_comment` varchar(1000) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_daily_checkin_plan_date` (`plan_id`, `checkin_date`),
    KEY `idx_rehab_daily_checkin_patient` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日打卡';

CREATE TABLE IF NOT EXISTS `rehab_task_execution` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `checkin_id` bigint NOT NULL,
    `task_id` bigint NOT NULL,
    `completion_status` varchar(32) NOT NULL,
    `completed_sets` int DEFAULT NULL,
    `completed_reps` int DEFAULT NULL,
    `perceived_exertion` decimal(4,1) DEFAULT NULL,
    `pain_score` decimal(4,1) DEFAULT NULL,
    `difficulty_level` int DEFAULT NULL,
    `symptom_flag` bit(1) NOT NULL DEFAULT b'0',
    `symptom_note` varchar(1000) DEFAULT NULL,
    `task_comment` varchar(1000) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_task_execution_checkin` (`checkin_id`),
    KEY `idx_rehab_task_execution_task` (`task_id`),
    KEY `idx_rehab_task_execution_status` (`completion_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打卡任务执行记录';

CREATE TABLE IF NOT EXISTS `rehab_progress_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `patient_id` bigint NOT NULL,
    `episode_id` bigint NOT NULL,
    `plan_id` bigint NOT NULL,
    `period_start` date NOT NULL,
    `period_end` date NOT NULL,
    `planned_task_count` int DEFAULT NULL,
    `completed_task_count` decimal(8,2) DEFAULT NULL,
    `completion_rate` decimal(6,2) DEFAULT NULL,
    `adherence_score` decimal(6,2) DEFAULT NULL,
    `average_pain_score` decimal(4,1) DEFAULT NULL,
    `pain_trend` varchar(32) DEFAULT NULL,
    `symptom_events_count` int DEFAULT NULL,
    `skipped_due_to_pain` int DEFAULT NULL,
    `skipped_due_to_schedule` int DEFAULT NULL,
    `clinician_impression` varchar(1000) DEFAULT NULL,
    `progress_status` varchar(32) DEFAULT NULL,
    `recommended_action` varchar(1000) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_progress_plan_period` (`plan_id`, `period_start`, `period_end`),
    KEY `idx_rehab_progress_patient` (`patient_id`),
    KEY `idx_rehab_progress_status` (`progress_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='阶段进度汇总';

CREATE TABLE IF NOT EXISTS `rehab_reassessment_trigger` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `patient_id` bigint NOT NULL,
    `episode_id` bigint NOT NULL,
    `plan_id` bigint NOT NULL,
    `trigger_type` varchar(32) NOT NULL,
    `trigger_level` varchar(16) NOT NULL DEFAULT 'medium',
    `trigger_status` varchar(32) NOT NULL DEFAULT 'pending',
    `trigger_message` varchar(1000) DEFAULT NULL,
    `suggested_action` varchar(1000) DEFAULT NULL,
    `due_date` date DEFAULT NULL,
    `acknowledged_by` bigint DEFAULT NULL,
    `acknowledged_time` datetime DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_trigger_plan` (`plan_id`),
    KEY `idx_rehab_trigger_patient` (`patient_id`),
    KEY `idx_rehab_trigger_status` (`trigger_status`, `trigger_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='复评触发记录';

CREATE TABLE IF NOT EXISTS `rehab_plan_operation_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `plan_id` bigint NOT NULL,
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
    KEY `idx_rehab_plan_log_plan` (`plan_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='训练计划操作日志';

-- ============================================================
-- 2) 菜单与权限（Step 4）
-- ============================================================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9400, '训练计划中心', 'rehab:plan:view', 2, 5, 9000, 'plan', 'ep:operation', 'rehab/plan/index', 'RehabPlan', 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),`component`=VALUES(`component`),`component_name`=VALUES(`component_name`),`status`=VALUES(`status`),`visible`=VALUES(`visible`),`keep_alive`=VALUES(`keep_alive`),`always_show`=VALUES(`always_show`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9401, '打卡记录', 'rehab:checkin:view', 2, 6, 9000, 'checkin', 'ep:checked', 'rehab/checkin/index', 'RehabCheckin', 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),`component`=VALUES(`component`),`component_name`=VALUES(`component_name`),`status`=VALUES(`status`),`visible`=VALUES(`visible`),`keep_alive`=VALUES(`keep_alive`),`always_show`=VALUES(`always_show`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9402, '复评触发', 'rehab:reassessment-trigger:view', 2, 7, 9000, 'reassessment-trigger', 'ep:warning', 'rehab/reassessment-trigger/index', 'RehabReassessmentTrigger', 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),`component`=VALUES(`component`),`component_name`=VALUES(`component_name`),`status`=VALUES(`status`),`visible`=VALUES(`visible`),`keep_alive`=VALUES(`keep_alive`),`always_show`=VALUES(`always_show`),`updater`='script',`deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES
(9410, '计划创建', 'rehab:plan:create', 3, 1, 9400, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
(9411, '计划更新', 'rehab:plan:update', 3, 2, 9400, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
(9412, '计划详情', 'rehab:plan:detail', 3, 3, 9400, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
(9413, '计划激活', 'rehab:plan:activate', 3, 4, 9400, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
(9414, '计划暂停', 'rehab:plan:pause', 3, 5, 9400, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
(9415, '计划完成', 'rehab:plan:complete', 3, 6, 9400, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
(9416, '计划复制', 'rehab:plan:copy', 3, 7, 9400, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
(9420, '任务查看', 'rehab:task:view', 3, 10, 9400, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
(9421, '任务创建', 'rehab:task:create', 3, 11, 9400, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
(9422, '任务更新', 'rehab:task:update', 3, 12, 9400, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
(9423, '任务排序', 'rehab:task:sort', 3, 13, 9400, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
(9424, '任务停用', 'rehab:task:disable', 3, 14, 9400, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
(9430, '打卡详情', 'rehab:checkin:detail', 3, 20, 9401, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
(9431, '打卡代录', 'rehab:checkin:create-manual', 3, 21, 9401, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
(9440, '进度查看', 'rehab:progress:view', 3, 30, 9400, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
(9441, '进度详情', 'rehab:progress:detail', 3, 31, 9400, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
(9450, '触发创建', 'rehab:reassessment-trigger:create', 3, 40, 9402, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
(9451, '触发处理', 'rehab:reassessment-trigger:handle', 3, 41, 9402, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`updater`='script',`deleted`=b'0';

-- ============================================================
-- 3) 角色菜单绑定（Step 4）
-- ============================================================
-- 超级管理员
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 1, t.menu_id, 'script', 'script', b'0', 1
FROM (
         SELECT 9400 AS menu_id UNION ALL SELECT 9401 UNION ALL SELECT 9402 UNION ALL
         SELECT 9410 UNION ALL SELECT 9411 UNION ALL SELECT 9412 UNION ALL SELECT 9413 UNION ALL SELECT 9414 UNION ALL SELECT 9415 UNION ALL SELECT 9416 UNION ALL
         SELECT 9420 UNION ALL SELECT 9421 UNION ALL SELECT 9422 UNION ALL SELECT 9423 UNION ALL SELECT 9424 UNION ALL
         SELECT 9430 UNION ALL SELECT 9431 UNION ALL
         SELECT 9440 UNION ALL SELECT 9441 UNION ALL
         SELECT 9450 UNION ALL SELECT 9451
     ) t
         LEFT JOIN `system_role_menu` rm ON rm.role_id = 1 AND rm.menu_id = t.menu_id AND rm.tenant_id = 1 AND rm.deleted = b'0'
WHERE rm.id IS NULL;

-- 康复治疗师
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT r.id, t.menu_id, 'script', 'script', b'0', 1
FROM `system_role` r
         JOIN (
    SELECT 9400 AS menu_id UNION ALL SELECT 9401 UNION ALL SELECT 9402 UNION ALL
    SELECT 9410 UNION ALL SELECT 9411 UNION ALL SELECT 9412 UNION ALL SELECT 9413 UNION ALL SELECT 9414 UNION ALL SELECT 9415 UNION ALL SELECT 9416 UNION ALL
    SELECT 9420 UNION ALL SELECT 9421 UNION ALL SELECT 9422 UNION ALL SELECT 9423 UNION ALL SELECT 9424 UNION ALL
    SELECT 9430 UNION ALL SELECT 9431 UNION ALL
    SELECT 9440 UNION ALL SELECT 9441 UNION ALL
    SELECT 9450 UNION ALL SELECT 9451
) t
              LEFT JOIN `system_role_menu` rm ON rm.role_id = r.id AND rm.menu_id = t.menu_id AND rm.tenant_id = 1 AND rm.deleted = b'0'
WHERE r.code = 'rehab_therapist' AND r.tenant_id = 1 AND r.deleted = b'0' AND rm.id IS NULL;

-- 文员（查看 + 代录打卡，不含临床处理权限）
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT r.id, t.menu_id, 'script', 'script', b'0', 1
FROM `system_role` r
         JOIN (
    SELECT 9400 AS menu_id UNION ALL SELECT 9401 UNION ALL SELECT 9402 UNION ALL
    SELECT 9412 UNION ALL
    SELECT 9420 UNION ALL
    SELECT 9430 UNION ALL SELECT 9431 UNION ALL
    SELECT 9440 UNION ALL SELECT 9441
) t
              LEFT JOIN `system_role_menu` rm ON rm.role_id = r.id AND rm.menu_id = t.menu_id AND rm.tenant_id = 1 AND rm.deleted = b'0'
WHERE r.code = 'rehab_clerk' AND r.tenant_id = 1 AND r.deleted = b'0' AND rm.id IS NULL;

-- ============================================================
-- 4) Step 4 演示数据
-- ============================================================
INSERT INTO `rehab_care_plan` (`id`, `plan_no`, `patient_id`, `episode_id`, `source_assessment_id`, `primary_therapist_user_id`, `plan_name`, `plan_type`, `status`, `start_date`, `end_date`, `cycle_days`, `short_term_goals_json`, `mid_term_goals_json`, `long_term_goals_json`, `contraindications`, `precautions`, `home_program_enabled`, `clinic_program_enabled`, `intensity_level`, `review_cycle_days`, `note`, `creator`, `updater`, `deleted`)
VALUES (40001, 'PLN202603100001', 10001, 13001, 20001, 100, '左膝稳定性四周计划', 'rehab', 'active', DATE_SUB(CURDATE(), INTERVAL 14 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 28,
        '["疼痛控制至NRS<=2","减少下肢内扣"]', '["YBT左右差<4cm"]', '["恢复跑跳专项负荷"]',
        '疼痛>6时暂停训练', '训练前后记录疼痛并控制动作质量', b'1', b'1', 'medium', 14, 'Step4演示计划', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `status`=VALUES(`status`), `start_date`=VALUES(`start_date`), `end_date`=VALUES(`end_date`), `source_assessment_id`=VALUES(`source_assessment_id`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_exercise_task` (`id`, `plan_id`, `patient_id`, `episode_id`, `task_no`, `sort_order`, `task_name`, `module_type`, `execution_type`, `target_deficit`, `body_region`, `dosage_text`, `repetitions`, `sets`, `hold_seconds`, `frequency_per_week`, `tempo`, `pain_limit_rule`, `stop_rule`, `progression_rule`, `regression_rule`, `replacement_exercise`, `instruction_text`, `status`, `start_date`, `end_date`, `creator`, `updater`, `deleted`) VALUES
(41001, 40001, 10001, 13001, 'TSK202603100001', 1, '踝背屈灵活性训练', 'mobility', 'both', '踝活动度不足', '踝', '每次8-10次x2组', 10, 2, 0, 5, '2-1-2', '疼痛>4立即降阶', '疼痛尖锐或不稳立即停止', '连续3天疼痛<=2可加组', '疼痛上升时减量50%', '小腿后侧放松', '保证膝盖对齐第二趾', 'active', DATE_SUB(CURDATE(), INTERVAL 14 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'script', 'script', b'0'),
(41002, 40001, 10001, 13001, 'TSK202603100002', 2, '单腿桥控制', 'stability', 'home', '髋控不足', '髋', '每侧8次x3组', 8, 3, 0, 4, '3-1-2', '疼痛>4暂停', '代偿明显/抽筋停止', '动作稳定2周后加弹力带', '动作抖动明显则降阶双腿桥', '双腿桥', '保持骨盆水平', 'active', DATE_SUB(CURDATE(), INTERVAL 14 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'script', 'script', b'0'),
(41003, 40001, 10001, 13001, 'TSK202603100003', 3, '分腿蹲模式重建', 'control', 'clinic', '膝轨迹控制', '下肢', '每侧6次x3组', 6, 3, 0, 3, '3-1-2', '疼痛>3改为箱式', '出现明显内扣停止', '无代偿两周后增加外负荷', '动作质量下降则回退徒手', '箱式分腿蹲', '镜像反馈保持膝髋对齐', 'active', DATE_SUB(CURDATE(), INTERVAL 14 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'script', 'script', b'0'),
(41004, 40001, 10001, 13001, 'TSK202603100004', 4, '呼吸-核心协同', 'breathing', 'home', '躯干稳定', '躯干', '60秒x3组', 0, 3, 60, 5, '匀速呼吸', '头晕不适停止', '胸闷/呼吸困难停止', '完成率>80%后加入动态动作', '保留静态版本', '腹式呼吸', '吸气扩张下位肋，呼气收紧腹壁', 'active', DATE_SUB(CURDATE(), INTERVAL 14 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `status`=VALUES(`status`), `sort_order`=VALUES(`sort_order`), `frequency_per_week`=VALUES(`frequency_per_week`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_task_schedule` (`id`, `task_id`, `plan_id`, `patient_id`, `schedule_type`, `weekday_mask`, `scheduled_date`, `target_sessions`, `creator`, `updater`, `deleted`) VALUES
(41101, 41001, 40001, 10001, 'weekly', 62, NULL, 5, 'script', 'script', b'0'),
(41102, 41002, 40001, 10001, 'weekly', 42, NULL, 4, 'script', 'script', b'0'),
(41103, 41003, 40001, 10001, 'weekly', 20, NULL, 3, 'script', 'script', b'0'),
(41104, 41004, 40001, 10001, 'daily', NULL, NULL, 1, 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `schedule_type`=VALUES(`schedule_type`), `target_sessions`=VALUES(`target_sessions`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_daily_checkin` (`id`, `patient_id`, `episode_id`, `plan_id`, `checkin_date`, `submitted_by_user_id`, `submit_role_type`, `overall_completion_rate`, `pain_score_before`, `pain_score_after`, `fatigue_level`, `confidence_level`, `overall_comment`, `creator`, `updater`, `deleted`) VALUES
(42001, 10001, 13001, 40001, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 100, 'therapist', 75.00, 4.0, 3.0, 3, 3, '院内完成度较好', 'script', 'script', b'0'),
(42002, 10001, 13001, 40001, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 10001, 'patient', 50.00, 4.0, 5.5, 5, 2, '家庭执行中膝前侧不适增加', 'script', 'script', b'0'),
(42003, 10001, 13001, 40001, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 10001, 'patient', 40.00, 5.5, 6.0, 6, 2, '同日二次打卡，因疼痛中断', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `overall_completion_rate`=VALUES(`overall_completion_rate`), `pain_score_after`=VALUES(`pain_score_after`), `overall_comment`=VALUES(`overall_comment`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_task_execution` (`id`, `checkin_id`, `task_id`, `completion_status`, `completed_sets`, `completed_reps`, `perceived_exertion`, `pain_score`, `difficulty_level`, `symptom_flag`, `symptom_note`, `task_comment`, `creator`, `updater`, `deleted`) VALUES
(43001, 42001, 41001, 'completed', 2, 10, 4.0, 3.0, 3, b'0', NULL, '动作质量可', 'script', 'script', b'0'),
(43002, 42001, 41002, 'completed', 3, 8, 5.0, 3.5, 4, b'0', NULL, '后两组质量下降', 'script', 'script', b'0'),
(43003, 42001, 41003, 'partial', 2, 6, 6.0, 4.0, 5, b'1', '膝内扣倾向', '需更多外部反馈', 'script', 'script', b'0'),
(43004, 42002, 41001, 'partial', 1, 8, 5.0, 5.0, 5, b'1', '左膝前侧酸胀', '晚间训练状态差', 'script', 'script', b'0'),
(43005, 42002, 41002, 'skipped', 0, 0, 0.0, 5.5, 0, b'0', NULL, '时间不足未完成', 'script', 'script', b'0'),
(43006, 42003, 41003, 'pain_stop', 1, 4, 7.0, 6.5, 7, b'1', '分腿蹲出现明显疼痛', '中断后改呼吸训练', 'script', 'script', b'0'),
(43007, 42003, 41004, 'completed', 3, 0, 3.0, 4.5, 2, b'0', NULL, '可完成', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `completion_status`=VALUES(`completion_status`), `pain_score`=VALUES(`pain_score`), `symptom_note`=VALUES(`symptom_note`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_progress_record` (`id`, `patient_id`, `episode_id`, `plan_id`, `period_start`, `period_end`, `planned_task_count`, `completed_task_count`, `completion_rate`, `adherence_score`, `average_pain_score`, `pain_trend`, `symptom_events_count`, `skipped_due_to_pain`, `skipped_due_to_schedule`, `clinician_impression`, `progress_status`, `recommended_action`, `creator`, `updater`, `deleted`)
VALUES (44001, 10001, 13001, 40001,
        DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY), INTERVAL 6 DAY),
        13, 5.00, 38.46, 38.46, 5.3, 'worsened', 3, 1, 1,
        '依从性下降且疼痛升高，需优先复核', 'worsened', '建议提前复评并调整计划', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `completion_rate`=VALUES(`completion_rate`), `average_pain_score`=VALUES(`average_pain_score`), `progress_status`=VALUES(`progress_status`), `recommended_action`=VALUES(`recommended_action`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_reassessment_trigger` (`id`, `patient_id`, `episode_id`, `plan_id`, `trigger_type`, `trigger_level`, `trigger_status`, `trigger_message`, `suggested_action`, `due_date`, `acknowledged_by`, `acknowledged_time`, `creator`, `updater`, `deleted`)
VALUES (45001, 10001, 13001, 40001, 'low_adherence', 'high', 'pending', '依从性持续偏低且疼痛有上升趋势', '建议48小时内人工复核并考虑提前复评', DATE_ADD(CURDATE(), INTERVAL 2 DAY), NULL, NULL, 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `trigger_status`=VALUES(`trigger_status`), `trigger_message`=VALUES(`trigger_message`), `suggested_action`=VALUES(`suggested_action`), `due_date`=VALUES(`due_date`), `updater`='script', `deleted`=b'0';

INSERT INTO `rehab_plan_operation_log` (`id`, `plan_id`, `operation_type`, `operator_user_id`, `before_data_json`, `after_data_json`, `remark`, `creator`, `updater`, `deleted`)
VALUES (46001, 40001, 'plan_create', 100, NULL, '{"planNo":"PLN202603100001"}', '示例计划创建', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE `remark`=VALUES(`remark`), `updater`='script', `deleted`=b'0';

COMMIT;
