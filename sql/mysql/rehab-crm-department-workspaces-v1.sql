-- 增量迁移 031：机构共享客户目录与部门私有服务档案。
-- 历史 CRM 客户不改写；有负责人且负责人有部门的客户按现有归属建立部门档案。
CREATE TABLE IF NOT EXISTS `crm_department_workspace` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `dept_id` bigint NOT NULL,
  `display_name` varchar(100) NOT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_crm_workspace_tenant_dept` (`tenant_id`, `dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门客户工作台配置';

CREATE TABLE IF NOT EXISTS `crm_department_case` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `customer_id` bigint NOT NULL,
  `dept_id` bigint NOT NULL,
  `claimed_by` bigint NOT NULL,
  `claim_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_crm_case_tenant_customer` (`tenant_id`, `customer_id`),
  KEY `idx_crm_case_tenant_dept` (`tenant_id`, `dept_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户当前所属部门；独立于共享 CRM 目录';

CREATE TABLE IF NOT EXISTS `crm_department_case_note` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `case_id` bigint NOT NULL,
  `dept_id` bigint NOT NULL,
  `content` text NOT NULL,
  `updated_by` bigint NOT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_crm_case_note_dept` (`tenant_id`, `case_id`, `dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门私有客户记录；转接时不向目标部门披露';

CREATE TABLE IF NOT EXISTS `crm_department_transfer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `case_id` bigint NOT NULL,
  `from_dept_id` bigint NOT NULL,
  `to_dept_id` bigint NOT NULL,
  `requested_by` bigint NOT NULL,
  `resolved_by` bigint DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `pending_case_id` bigint GENERATED ALWAYS AS (CASE WHEN `status` = 'PENDING' THEN `case_id` ELSE NULL END) STORED,
  `reason` varchar(200) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolve_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_crm_transfer_incoming` (`tenant_id`, `to_dept_id`, `status`, `id`),
  UNIQUE KEY `uk_crm_transfer_one_pending` (`tenant_id`, `pending_case_id`),
  KEY `idx_crm_transfer_case` (`tenant_id`, `case_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨部门客户转接审计';

CREATE TABLE IF NOT EXISTS `crm_customer_pool_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `customer_id` bigint NOT NULL,
  `owner_user_id` bigint NOT NULL,
  `event_type` varchar(8) NOT NULL,
  `event_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_crm_pool_event_date` (`tenant_id`, `event_type`, `event_time`),
  KEY `idx_crm_pool_event_owner` (`tenant_id`, `owner_user_id`, `event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公海进入及领取真实事件；历史未知不补造';

-- 只迁移有明确部门的既有负责人；未分配客户继续在共享目录等待认领。
INSERT IGNORE INTO `crm_department_workspace` (`tenant_id`, `dept_id`, `display_name`, `enabled`)
SELECT DISTINCT c.tenant_id, u.dept_id, d.name, b'1'
FROM `crm_customer` c
JOIN `system_users` u ON u.id = c.owner_user_id AND u.tenant_id = c.tenant_id AND u.deleted = b'0'
JOIN `system_dept` d ON d.id = u.dept_id AND d.tenant_id = c.tenant_id AND d.deleted = b'0'
WHERE c.deleted = b'0' AND u.dept_id IS NOT NULL;

-- 复用已交付 CRM 客户的菜单权限；不向没有客户查询权限的角色开放。
INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,
  `component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '部门客户工作台','',2,11,p.id,'department','ep:office-building','crm/department/index',
  'CrmDepartment',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
FROM `system_menu` p
WHERE p.parent_id=0 AND p.path='/crm' AND p.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_menu` m WHERE m.parent_id=p.id AND m.path='department' AND m.deleted=b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id, page.id, '1', NOW(), '1', NOW(), b'0', r.tenant_id
FROM `system_role` r
JOIN `system_menu` page ON page.component='crm/department/index' AND page.deleted=b'0'
WHERE r.deleted=b'0' AND r.status=0
  AND (r.code='super_admin' OR EXISTS (
    SELECT 1 FROM `system_role_menu` granted
    JOIN `system_menu` permission_menu ON permission_menu.id=granted.menu_id
    WHERE granted.role_id=r.id AND granted.tenant_id=r.tenant_id AND granted.deleted=b'0'
      AND permission_menu.permission='crm:customer:query' AND permission_menu.deleted=b'0'))
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
    WHERE existing.role_id=r.id AND existing.menu_id=page.id AND existing.tenant_id=r.tenant_id AND existing.deleted=b'0');

INSERT IGNORE INTO `crm_department_case` (`tenant_id`, `customer_id`, `dept_id`, `claimed_by`, `claim_time`)
SELECT c.tenant_id, c.id, u.dept_id, u.id, COALESCE(c.owner_time, c.create_time)
FROM `crm_customer` c
JOIN `system_users` u ON u.id = c.owner_user_id AND u.tenant_id = c.tenant_id AND u.deleted = b'0'
JOIN `system_dept` d ON d.id = u.dept_id AND d.tenant_id = c.tenant_id AND d.deleted = b'0'
WHERE c.deleted = b'0' AND u.dept_id IS NOT NULL;
