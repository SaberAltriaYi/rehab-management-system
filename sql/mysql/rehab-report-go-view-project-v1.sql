-- 020 | Report GoView project, additive schema only.
-- Apply via the reviewed migration runner ONLY after validating against a dedicated
-- synthetic MySQL 8.4 database. Never run the bootstrap scripts 001-019 on an old DB.
-- Deliberately no DROP, TRUNCATE, UPDATE, seed data, or IF NOT EXISTS: a pre-existing
-- incompatible report table must fail loudly and be reviewed rather than skipped.
CREATE TABLE `report_go_view_project` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目编号',
  `name` VARCHAR(255) NOT NULL COMMENT '项目名称',
  `pic_url` VARCHAR(1024) DEFAULT NULL COMMENT '预览图片 URL',
  `content` LONGTEXT COMMENT 'JSON 项目配置',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0 启用；1 停用',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `creator` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  `tenant_id` BIGINT NOT NULL COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_report_goview_tenant_creator` (`tenant_id`, `creator`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='GoView 项目';
