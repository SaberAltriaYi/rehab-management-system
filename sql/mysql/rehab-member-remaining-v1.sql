-- Candidate 021: additive schema for the seven Member DO tables absent from bootstrap 009.
-- Derivation: yudao-module-member/*DO.java and its Mapper/VO contracts, NOT H2 test DDL.
-- Do not deploy to an existing database until complete isolated MySQL/App tests pass.
-- Multiple MySQL DDL statements are not transactional; run only with reviewed preflight.
-- No IF NOT EXISTS: an incompatible pre-existing table must fail, never be skipped.
CREATE TABLE `member_address` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `mobile` VARCHAR(20) NOT NULL,
  `area_id` BIGINT NOT NULL,
  `detail_address` VARCHAR(500) NOT NULL,
  `default_status` BIT(1) NOT NULL DEFAULT b'0',
  `creator` VARCHAR(64) NOT NULL DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` VARCHAR(64) NOT NULL DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  `tenant_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_member_address_user` (`tenant_id`, `user_id`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员收件地址';

CREATE TABLE `member_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `point_trade_deduct_enable` BIT(1) NOT NULL DEFAULT b'0',
  `point_trade_deduct_unit_price` INT NOT NULL DEFAULT 0,
  `point_trade_deduct_max_price` INT NOT NULL DEFAULT 0,
  `point_trade_give_point` INT NOT NULL DEFAULT 0,
  `creator` VARCHAR(64) NOT NULL DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` VARCHAR(64) NOT NULL DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  `tenant_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_member_config_tenant` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员积分配置';

CREATE TABLE `member_experience_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `biz_type` INT NOT NULL,
  `biz_id` VARCHAR(128) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` TEXT,
  `experience` INT NOT NULL,
  `total_experience` INT NOT NULL,
  `creator` VARCHAR(64) NOT NULL DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` VARCHAR(64) NOT NULL DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  `tenant_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_member_experience_user` (`tenant_id`, `user_id`, `deleted`, `id`),
  KEY `idx_member_experience_biz` (`tenant_id`, `biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员经验变更记录';

CREATE TABLE `member_level_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `level_id` BIGINT DEFAULT NULL COMMENT '取消等级时为空',
  `level` INT DEFAULT NULL,
  `discount_percent` INT DEFAULT NULL,
  `experience` INT NOT NULL,
  `user_experience` INT NOT NULL,
  `remark` VARCHAR(500) DEFAULT NULL,
  `description` TEXT,
  `creator` VARCHAR(64) NOT NULL DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` VARCHAR(64) NOT NULL DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  `tenant_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_member_level_record_user` (`tenant_id`, `user_id`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员等级变更记录';

CREATE TABLE `member_point_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `biz_id` VARCHAR(128) NOT NULL,
  `biz_type` INT NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` TEXT,
  `point` INT NOT NULL,
  `total_point` INT NOT NULL,
  `creator` VARCHAR(64) NOT NULL DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` VARCHAR(64) NOT NULL DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  `tenant_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_member_point_user` (`tenant_id`, `user_id`, `deleted`, `id`),
  KEY `idx_member_point_biz` (`tenant_id`, `biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员积分变更记录';

CREATE TABLE `member_sign_in_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `day` INT NOT NULL,
  `point` INT NOT NULL DEFAULT 0,
  `experience` INT NOT NULL DEFAULT 0,
  `status` TINYINT NOT NULL DEFAULT 1,
  `creator` VARCHAR(64) NOT NULL DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` VARCHAR(64) NOT NULL DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  `tenant_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_member_sign_config_day` (`tenant_id`, `day`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员签到规则';

CREATE TABLE `member_sign_in_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `day` INT NOT NULL,
  `point` INT NOT NULL DEFAULT 0,
  `experience` INT NOT NULL DEFAULT 0,
  `creator` VARCHAR(64) NOT NULL DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` VARCHAR(64) NOT NULL DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  `tenant_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_member_sign_record_user` (`tenant_id`, `user_id`, `deleted`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员签到记录';
