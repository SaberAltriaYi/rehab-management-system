-- Candidate 022: eight BPM module tables from Bpm*DO (including JSON handler properties).
-- Requires the Flowable engine schema independently; this file NEVER creates ACT_/FLW_ tables.
-- Additive only. Multiple DDL statements are not atomic; test on dedicated synthetic MySQL
-- and review existing tables/permissions before any upgrade. No IF NOT EXISTS/DROP.

CREATE TABLE `bpm_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(255) DEFAULT NULL,
  `code` VARCHAR(255) DEFAULT NULL,
  `description` TEXT,
  `status` TINYINT DEFAULT NULL,
  `sort` INT DEFAULT NULL,
  `creator` VARCHAR(64) NOT NULL DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` VARCHAR(64) NOT NULL DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  `tenant_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_bpm_category_tenant` (`tenant_id`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM bpm_category';

CREATE TABLE `bpm_form` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(255) DEFAULT NULL,
  `status` TINYINT DEFAULT NULL,
  `conf` LONGTEXT,
  `fields` LONGTEXT,
  `remark` TEXT,
  `creator` VARCHAR(64) NOT NULL DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` VARCHAR(64) NOT NULL DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  `tenant_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_bpm_form_tenant` (`tenant_id`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM bpm_form';

CREATE TABLE `bpm_process_definition_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `process_definition_id` VARCHAR(255) DEFAULT NULL,
  `model_id` VARCHAR(255) DEFAULT NULL,
  `model_type` INT DEFAULT NULL,
  `category` VARCHAR(255) DEFAULT NULL,
  `icon` VARCHAR(255) DEFAULT NULL,
  `description` TEXT,
  `form_type` INT DEFAULT NULL,
  `form_id` BIGINT DEFAULT NULL,
  `form_conf` LONGTEXT,
  `form_fields` LONGTEXT,
  `form_custom_create_path` VARCHAR(255) DEFAULT NULL,
  `form_custom_view_path` VARCHAR(255) DEFAULT NULL,
  `simple_model` LONGTEXT,
  `visible` BIT(1) DEFAULT NULL,
  `sort` BIGINT DEFAULT NULL,
  `start_user_ids` TEXT,
  `start_dept_ids` TEXT,
  `manager_user_ids` TEXT,
  `allow_cancel_running_process` BIT(1) DEFAULT NULL,
  `allow_withdraw_task` BIT(1) DEFAULT NULL,
  `process_id_rule` LONGTEXT,
  `auto_approval_type` INT DEFAULT NULL,
  `title_setting` LONGTEXT,
  `summary_setting` LONGTEXT,
  `process_before_trigger_setting` LONGTEXT,
  `process_after_trigger_setting` LONGTEXT,
  `task_before_trigger_setting` LONGTEXT,
  `task_after_trigger_setting` LONGTEXT,
  `print_template_setting` LONGTEXT,
  `creator` VARCHAR(64) NOT NULL DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` VARCHAR(64) NOT NULL DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  `tenant_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_bpm_process_definition_info_tenant` (`tenant_id`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM bpm_process_definition_info';

CREATE TABLE `bpm_process_expression` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(255) DEFAULT NULL,
  `status` TINYINT DEFAULT NULL,
  `expression` TEXT,
  `creator` VARCHAR(64) NOT NULL DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` VARCHAR(64) NOT NULL DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  `tenant_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_bpm_process_expression_tenant` (`tenant_id`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM bpm_process_expression';

CREATE TABLE `bpm_process_listener` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(255) DEFAULT NULL,
  `status` TINYINT DEFAULT NULL,
  `type` VARCHAR(255) DEFAULT NULL,
  `event` VARCHAR(255) DEFAULT NULL,
  `value_type` VARCHAR(255) DEFAULT NULL,
  `value` TEXT,
  `creator` VARCHAR(64) NOT NULL DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` VARCHAR(64) NOT NULL DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  `tenant_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_bpm_process_listener_tenant` (`tenant_id`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM bpm_process_listener';

CREATE TABLE `bpm_user_group` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(255) DEFAULT NULL,
  `description` TEXT,
  `status` TINYINT DEFAULT NULL,
  `user_ids` LONGTEXT,
  `creator` VARCHAR(64) NOT NULL DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` VARCHAR(64) NOT NULL DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  `tenant_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_bpm_user_group_tenant` (`tenant_id`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM bpm_user_group';

CREATE TABLE `bpm_oa_leave` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT DEFAULT NULL,
  `type` VARCHAR(255) DEFAULT NULL,
  `reason` TEXT,
  `start_time` DATETIME DEFAULT NULL,
  `end_time` DATETIME DEFAULT NULL,
  `day` BIGINT DEFAULT NULL,
  `status` TINYINT DEFAULT NULL,
  `process_instance_id` VARCHAR(255) DEFAULT NULL,
  `creator` VARCHAR(64) NOT NULL DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` VARCHAR(64) NOT NULL DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  `tenant_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_bpm_oa_leave_tenant` (`tenant_id`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM bpm_oa_leave';

CREATE TABLE `bpm_process_instance_copy` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `start_user_id` BIGINT DEFAULT NULL,
  `process_instance_name` VARCHAR(255) DEFAULT NULL,
  `process_instance_id` VARCHAR(255) DEFAULT NULL,
  `process_definition_id` VARCHAR(255) DEFAULT NULL,
  `category` VARCHAR(255) DEFAULT NULL,
  `activity_id` VARCHAR(255) DEFAULT NULL,
  `activity_name` VARCHAR(255) DEFAULT NULL,
  `task_id` VARCHAR(255) DEFAULT NULL,
  `user_id` BIGINT DEFAULT NULL,
  `reason` TEXT,
  `creator` VARCHAR(64) NOT NULL DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` VARCHAR(64) NOT NULL DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  `tenant_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_bpm_process_instance_copy_tenant` (`tenant_id`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM bpm_process_instance_copy';
