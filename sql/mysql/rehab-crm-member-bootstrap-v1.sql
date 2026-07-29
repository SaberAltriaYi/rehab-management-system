-- Rehab / CRM / Member 最小联动建表脚本（v1）
-- 目的：
-- 1) 在当前本地库缺失 CRM、Member 表结构时快速补齐最小可用能力
-- 2) 支持 Rehab 患者页 CRM 绑定、会员绑定展示、CRM 客户下拉选择
-- 3) 幂等执行，不影响已有完整表结构

SET NAMES utf8mb4;
START TRANSACTION;

-- =========================
-- CRM 关键表（最小可用）
-- =========================

CREATE TABLE IF NOT EXISTS `crm_customer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NOT NULL COMMENT '客户名称',
  `follow_up_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '跟进状态',
  `contact_last_time` datetime DEFAULT NULL COMMENT '最后跟进时间',
  `contact_last_content` varchar(255) DEFAULT NULL COMMENT '最后跟进内容',
  `contact_next_time` datetime DEFAULT NULL COMMENT '下次联系时间',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人用户编号',
  `owner_time` datetime DEFAULT NULL COMMENT '成为负责人时间',
  `lock_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '锁定状态',
  `deal_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '成交状态',
  `mobile` varchar(20) DEFAULT NULL COMMENT '手机',
  `telephone` varchar(20) DEFAULT NULL COMMENT '电话',
  `qq` varchar(30) DEFAULT NULL COMMENT 'QQ',
  `wechat` varchar(64) DEFAULT NULL COMMENT '微信',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `area_id` int DEFAULT NULL COMMENT '所在地',
  `detail_address` varchar(255) DEFAULT NULL COMMENT '详细地址',
  `industry_id` int DEFAULT NULL COMMENT '所属行业',
  `level` int DEFAULT NULL COMMENT '客户等级',
  `source` int DEFAULT NULL COMMENT '客户来源',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_crm_customer_owner` (`owner_user_id`),
  KEY `idx_crm_customer_mobile` (`mobile`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 客户表';

CREATE TABLE IF NOT EXISTS `crm_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `biz_type` int NOT NULL COMMENT '数据类型',
  `biz_id` bigint NOT NULL COMMENT '数据编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `level` int NOT NULL COMMENT '权限级别',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_crm_perm_biz` (`biz_type`, `biz_id`),
  KEY `idx_crm_perm_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 数据权限表';

CREATE TABLE IF NOT EXISTS `crm_customer_pool_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否启用客户公海',
  `contact_expire_days` int DEFAULT NULL COMMENT '未跟进放入公海天数',
  `deal_expire_days` int DEFAULT NULL COMMENT '未成交放入公海天数',
  `notify_enabled` bit(1) DEFAULT NULL COMMENT '是否开启提前提醒',
  `notify_days` int DEFAULT NULL COMMENT '提前提醒天数',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户公海配置';

CREATE TABLE IF NOT EXISTS `crm_customer_limit_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `type` int NOT NULL COMMENT '规则类型',
  `user_ids` varchar(1024) DEFAULT NULL COMMENT '规则适用人群',
  `dept_ids` varchar(1024) DEFAULT NULL COMMENT '规则适用部门',
  `max_count` int DEFAULT NULL COMMENT '数量上限',
  `deal_count_enabled` bit(1) DEFAULT NULL COMMENT '成交客户是否计入',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户限制配置';

CREATE TABLE IF NOT EXISTS `crm_clue` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NOT NULL COMMENT '线索名称',
  `follow_up_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '跟进状态',
  `contact_last_time` datetime DEFAULT NULL COMMENT '最后跟进时间',
  `contact_last_content` varchar(255) DEFAULT NULL COMMENT '最后跟进内容',
  `contact_next_time` datetime DEFAULT NULL COMMENT '下次联系时间',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人用户编号',
  `transform_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已转化',
  `customer_id` bigint DEFAULT NULL COMMENT '转化客户编号',
  `mobile` varchar(20) DEFAULT NULL COMMENT '手机',
  `telephone` varchar(20) DEFAULT NULL COMMENT '电话',
  `qq` varchar(30) DEFAULT NULL COMMENT 'QQ',
  `wechat` varchar(64) DEFAULT NULL COMMENT '微信',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `area_id` int DEFAULT NULL COMMENT '所在地',
  `detail_address` varchar(255) DEFAULT NULL COMMENT '详细地址',
  `industry_id` int DEFAULT NULL COMMENT '行业',
  `level` int DEFAULT NULL COMMENT '等级',
  `source` int DEFAULT NULL COMMENT '来源',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_crm_clue_owner` (`owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 线索表';

CREATE TABLE IF NOT EXISTS `crm_contact` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NOT NULL COMMENT '联系人姓名',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `contact_last_time` datetime DEFAULT NULL COMMENT '最后联系时间',
  `contact_last_content` varchar(255) DEFAULT NULL COMMENT '最后联系内容',
  `contact_next_time` datetime DEFAULT NULL COMMENT '下次联系时间',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人用户编号',
  `mobile` varchar(20) DEFAULT NULL COMMENT '手机',
  `telephone` varchar(20) DEFAULT NULL COMMENT '电话',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `qq` bigint DEFAULT NULL COMMENT 'QQ',
  `wechat` varchar(64) DEFAULT NULL COMMENT '微信',
  `area_id` int DEFAULT NULL COMMENT '所在地',
  `detail_address` varchar(255) DEFAULT NULL COMMENT '详细地址',
  `sex` int DEFAULT NULL COMMENT '性别',
  `master` bit(1) DEFAULT NULL COMMENT '是否主联系人',
  `post` varchar(128) DEFAULT NULL COMMENT '职位',
  `parent_id` bigint DEFAULT NULL COMMENT '直属上级',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_crm_contact_customer` (`customer_id`),
  KEY `idx_crm_contact_owner` (`owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 联系人表';

CREATE TABLE IF NOT EXISTS `crm_contact_business` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `contact_id` bigint NOT NULL COMMENT '联系人编号',
  `business_id` bigint NOT NULL COMMENT '商机编号',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contact_business` (`contact_id`, `business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联系人关联商机表';

CREATE TABLE IF NOT EXISTS `crm_business_status_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NOT NULL COMMENT '商机状态组名称',
  `dept_ids` varchar(1024) DEFAULT NULL COMMENT '可见部门编号集合',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商机状态组';

CREATE TABLE IF NOT EXISTS `crm_business_status` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `type_id` bigint NOT NULL COMMENT '状态组编号',
  `name` varchar(255) NOT NULL COMMENT '状态名',
  `percent` int DEFAULT NULL COMMENT '赢单概率',
  `sort` int DEFAULT NULL COMMENT '排序',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_crm_business_status_type` (`type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商机状态';

CREATE TABLE IF NOT EXISTS `crm_business` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NOT NULL COMMENT '商机名称',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `follow_up_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '跟进状态',
  `contact_last_time` datetime DEFAULT NULL COMMENT '最后跟进时间',
  `contact_next_time` datetime DEFAULT NULL COMMENT '下次联系时间',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人',
  `status_type_id` bigint DEFAULT NULL COMMENT '商机状态组',
  `status_id` bigint DEFAULT NULL COMMENT '商机状态',
  `end_status` int DEFAULT NULL COMMENT '结束状态',
  `end_remark` varchar(255) DEFAULT NULL COMMENT '结束原因',
  `deal_time` datetime DEFAULT NULL COMMENT '预计成交时间',
  `total_product_price` decimal(24,6) DEFAULT NULL COMMENT '产品总金额',
  `discount_percent` decimal(10,4) DEFAULT NULL COMMENT '折扣',
  `total_price` decimal(24,6) DEFAULT NULL COMMENT '总金额',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_crm_business_customer` (`customer_id`),
  KEY `idx_crm_business_owner` (`owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 商机表';

CREATE TABLE IF NOT EXISTS `crm_business_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `business_id` bigint NOT NULL COMMENT '商机编号',
  `product_id` bigint NOT NULL COMMENT '产品编号',
  `product_price` decimal(24,6) DEFAULT NULL COMMENT '产品原价',
  `business_price` decimal(24,6) DEFAULT NULL COMMENT '商机价格',
  `count` decimal(24,6) DEFAULT NULL COMMENT '数量',
  `total_price` decimal(24,6) DEFAULT NULL COMMENT '总价',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_crm_business_product_biz` (`business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商机产品表';

CREATE TABLE IF NOT EXISTS `crm_contract_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `notify_enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用到期提醒',
  `notify_days` int NOT NULL DEFAULT 7 COMMENT '提前提醒天数',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同配置';

CREATE TABLE IF NOT EXISTS `crm_contract` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NOT NULL COMMENT '合同名称',
  `no` varchar(64) DEFAULT NULL COMMENT '合同编号',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `business_id` bigint DEFAULT NULL COMMENT '商机编号',
  `contact_last_time` datetime DEFAULT NULL COMMENT '最后跟进时间',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '流程实例编号',
  `audit_status` int DEFAULT 0 COMMENT '审批状态',
  `order_date` datetime DEFAULT NULL COMMENT '下单日期',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `total_product_price` decimal(24,6) DEFAULT NULL COMMENT '产品总金额',
  `discount_percent` decimal(10,4) DEFAULT NULL COMMENT '折扣',
  `total_price` decimal(24,6) DEFAULT NULL COMMENT '总金额',
  `sign_contact_id` bigint DEFAULT NULL COMMENT '签约联系人',
  `sign_user_id` bigint DEFAULT NULL COMMENT '签约人',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_crm_contract_customer` (`customer_id`),
  KEY `idx_crm_contract_owner` (`owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 合同表';

CREATE TABLE IF NOT EXISTS `crm_contract_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `contract_id` bigint NOT NULL COMMENT '合同编号',
  `product_id` bigint NOT NULL COMMENT '产品编号',
  `product_price` decimal(24,6) DEFAULT NULL COMMENT '产品原价',
  `contract_price` decimal(24,6) DEFAULT NULL COMMENT '合同价格',
  `count` decimal(24,6) DEFAULT NULL COMMENT '数量',
  `total_price` decimal(24,6) DEFAULT NULL COMMENT '总价',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_crm_contract_product_contract` (`contract_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同产品表';

CREATE TABLE IF NOT EXISTS `crm_receivable` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(64) DEFAULT NULL COMMENT '回款编号',
  `plan_id` bigint DEFAULT NULL COMMENT '回款计划编号',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `contract_id` bigint NOT NULL COMMENT '合同编号',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人',
  `return_time` datetime DEFAULT NULL COMMENT '回款日期',
  `return_type` int DEFAULT NULL COMMENT '回款方式',
  `price` decimal(24,6) DEFAULT NULL COMMENT '回款金额',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '流程实例编号',
  `audit_status` int DEFAULT 0 COMMENT '审批状态',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_crm_receivable_owner` (`owner_user_id`),
  KEY `idx_crm_receivable_contract` (`contract_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回款表';

CREATE TABLE IF NOT EXISTS `crm_receivable_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `period` int DEFAULT NULL COMMENT '期数',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `contract_id` bigint NOT NULL COMMENT '合同编号',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人',
  `return_time` datetime DEFAULT NULL COMMENT '预计回款时间',
  `return_type` int DEFAULT NULL COMMENT '回款方式',
  `price` decimal(24,6) DEFAULT NULL COMMENT '回款金额',
  `receivable_id` bigint DEFAULT NULL COMMENT '关联回款单',
  `remind_days` int DEFAULT NULL COMMENT '提前提醒天数',
  `remind_time` datetime DEFAULT NULL COMMENT '提醒时间',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_crm_receivable_plan_owner` (`owner_user_id`),
  KEY `idx_crm_receivable_plan_contract` (`contract_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回款计划表';

CREATE TABLE IF NOT EXISTS `crm_product_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NOT NULL COMMENT '分类名',
  `parent_id` bigint DEFAULT NULL COMMENT '父分类',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品分类';

CREATE TABLE IF NOT EXISTS `crm_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NOT NULL COMMENT '产品名',
  `no` varchar(64) DEFAULT NULL COMMENT '产品编号',
  `unit` int DEFAULT NULL COMMENT '单位',
  `price` decimal(24,6) DEFAULT NULL COMMENT '价格',
  `status` int DEFAULT 1 COMMENT '状态',
  `category_id` bigint DEFAULT NULL COMMENT '分类编号',
  `description` varchar(512) DEFAULT NULL COMMENT '描述',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_crm_product_owner` (`owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 产品';

CREATE TABLE IF NOT EXISTS `crm_follow_up_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `biz_type` int NOT NULL COMMENT '业务类型',
  `biz_id` bigint NOT NULL COMMENT '业务编号',
  `type` int DEFAULT NULL COMMENT '跟进方式',
  `content` varchar(1024) DEFAULT NULL COMMENT '内容',
  `next_time` datetime DEFAULT NULL COMMENT '下次联系时间',
  `pic_urls` text DEFAULT NULL COMMENT '图片集合',
  `file_urls` text DEFAULT NULL COMMENT '附件集合',
  `business_ids` varchar(1024) DEFAULT NULL COMMENT '关联商机',
  `contact_ids` varchar(1024) DEFAULT NULL COMMENT '关联联系人',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_crm_followup_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跟进记录';

-- 兼容：如果历史已建过表但缺失 tenant_id，则自动补列
SET @crm_customer_tenant_exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND COLUMN_NAME = 'tenant_id'
);
SET @sql := IF(@crm_customer_tenant_exists = 0,
  'ALTER TABLE `crm_customer` ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT ''租户编号''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @crm_permission_tenant_exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_permission' AND COLUMN_NAME = 'tenant_id'
);
SET @sql := IF(@crm_permission_tenant_exists = 0,
  'ALTER TABLE `crm_permission` ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT ''租户编号''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @crm_pool_cfg_tenant_exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer_pool_config' AND COLUMN_NAME = 'tenant_id'
);
SET @sql := IF(@crm_pool_cfg_tenant_exists = 0,
  'ALTER TABLE `crm_customer_pool_config` ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT ''租户编号''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @crm_limit_cfg_tenant_exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer_limit_config' AND COLUMN_NAME = 'tenant_id'
);
SET @sql := IF(@crm_limit_cfg_tenant_exists = 0,
  'ALTER TABLE `crm_customer_limit_config` ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT ''租户编号''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 默认 CRM 配置（仅初始化一次）
INSERT INTO `crm_customer_pool_config` (`id`, `enabled`, `contact_expire_days`, `deal_expire_days`, `notify_enabled`, `notify_days`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 1, b'0', 30, 90, b'1', 7, '1', '1', b'0', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `crm_customer_pool_config` WHERE `id` = 1);

-- 默认演示客户（用于 rehab 绑定下拉）
INSERT INTO `crm_customer` (`name`, `follow_up_status`, `owner_user_id`, `owner_time`, `lock_status`, `deal_status`, `mobile`, `industry_id`, `level`, `source`, `remark`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT '演示客户-康复01', b'0', 1, NOW(), b'0', b'0', '13800000001', 1, 1, 4, 'rehab bootstrap seed', '1', '1', b'0', 1
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `crm_customer`
  WHERE `name` = '演示客户-康复01' AND `deleted` = b'0'
);

INSERT INTO `crm_contract_config` (`id`, `notify_enabled`, `notify_days`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 1, b'1', 7, '1', '1', b'0', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `crm_contract_config` WHERE `id` = 1);

-- =========================
-- Member 关键表（最小可用）
-- =========================

CREATE TABLE IF NOT EXISTS `member_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(100) NOT NULL COMMENT '名称',
  `remark` varchar(255) DEFAULT '' COMMENT '备注',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员分组';

CREATE TABLE IF NOT EXISTS `member_level` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(100) NOT NULL COMMENT '等级名称',
  `level` int NOT NULL DEFAULT 1 COMMENT '等级',
  `experience` int NOT NULL DEFAULT 0 COMMENT '升级经验',
  `discount_percent` int NOT NULL DEFAULT 100 COMMENT '折扣百分比',
  `icon` varchar(255) DEFAULT '' COMMENT '等级图标',
  `background_url` varchar(255) DEFAULT '' COMMENT '等级背景图',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员等级';

CREATE TABLE IF NOT EXISTS `member_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(100) NOT NULL COMMENT '标签名称',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员标签';

CREATE TABLE IF NOT EXISTS `member_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `mobile` varchar(11) NOT NULL COMMENT '手机号',
  `password` varchar(100) NOT NULL DEFAULT '' COMMENT '密码',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态',
  `register_ip` varchar(64) DEFAULT '' COMMENT '注册 IP',
  `register_terminal` int DEFAULT NULL COMMENT '注册终端',
  `login_ip` varchar(64) DEFAULT '' COMMENT '最后登录 IP',
  `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',
  `nickname` varchar(64) NOT NULL DEFAULT '' COMMENT '昵称',
  `avatar` varchar(255) DEFAULT '' COMMENT '头像',
  `name` varchar(64) DEFAULT NULL COMMENT '真实姓名',
  `sex` tinyint DEFAULT NULL COMMENT '性别',
  `birthday` datetime DEFAULT NULL COMMENT '生日',
  `area_id` int DEFAULT NULL COMMENT '所在地',
  `mark` varchar(255) DEFAULT NULL COMMENT '备注',
  `point` int NOT NULL DEFAULT 0 COMMENT '积分',
  `tag_ids` varchar(255) DEFAULT NULL COMMENT '标签列表',
  `level_id` bigint DEFAULT NULL COMMENT '等级编号',
  `experience` int DEFAULT NULL COMMENT '经验',
  `group_id` bigint DEFAULT NULL COMMENT '分组编号',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_user_mobile` (`mobile`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员用户';

-- 兼容：历史 member 表缺 tenant_id 时补列
SET @member_group_tenant_exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member_group' AND COLUMN_NAME = 'tenant_id'
);
SET @sql := IF(@member_group_tenant_exists = 0,
  'ALTER TABLE `member_group` ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT ''租户编号''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @member_level_tenant_exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member_level' AND COLUMN_NAME = 'tenant_id'
);
SET @sql := IF(@member_level_tenant_exists = 0,
  'ALTER TABLE `member_level` ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT ''租户编号''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @member_tag_tenant_exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member_tag' AND COLUMN_NAME = 'tenant_id'
);
SET @sql := IF(@member_tag_tenant_exists = 0,
  'ALTER TABLE `member_tag` ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT ''租户编号''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @member_user_tenant_exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member_user' AND COLUMN_NAME = 'tenant_id'
);
SET @sql := IF(@member_user_tenant_exists = 0,
  'ALTER TABLE `member_user` ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT ''租户编号''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 默认会员等级、分组
INSERT INTO `member_level` (`id`, `name`, `level`, `experience`, `discount_percent`, `status`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 1, '普通会员', 1, 0, 100, 0, 'script', 'script', b'0', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `member_level` WHERE `id` = 1);

INSERT INTO `member_group` (`id`, `name`, `remark`, `status`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 1, '默认分组', 'rehab bootstrap', 0, 'script', 'script', b'0', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `member_group` WHERE `id` = 1);

-- 默认演示会员
INSERT INTO `member_user` (`mobile`, `password`, `status`, `register_ip`, `nickname`, `name`, `level_id`, `group_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT '13900000001', '', 0, '127.0.0.1', '演示会员01', '演示会员01', 1, 1, 'script', 'script', b'0', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `member_user` WHERE `mobile` = '13900000001' AND `deleted` = b'0');

COMMIT;
