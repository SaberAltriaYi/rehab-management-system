-- 康复评估系统底座接入（v1）
-- 目标：接入菜单、权限、角色，并与现有 RBAC 体系打通
-- 说明：脚本可重复执行（幂等）

SET NAMES utf8mb4;
START TRANSACTION;

-- =========================
-- 1) 菜单与权限
-- =========================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9000, '康复管理', '', 1, 70, 0, '/rehab', 'ep:management', NULL, NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
`name` = VALUES(`name`),
`permission` = VALUES(`permission`),
`type` = VALUES(`type`),
`sort` = VALUES(`sort`),
`parent_id` = VALUES(`parent_id`),
`path` = VALUES(`path`),
`icon` = VALUES(`icon`),
`component` = VALUES(`component`),
`component_name` = VALUES(`component_name`),
`status` = VALUES(`status`),
`visible` = VALUES(`visible`),
`keep_alive` = VALUES(`keep_alive`),
`always_show` = VALUES(`always_show`),
`updater` = 'script',
`deleted` = b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9001, '康复工作台', 'rehab:dashboard:view', 2, 1, 9000, 'dashboard', 'ep:odometer', 'rehab/dashboard/index', 'RehabDashboard', 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
`name` = VALUES(`name`),
`permission` = VALUES(`permission`),
`type` = VALUES(`type`),
`sort` = VALUES(`sort`),
`parent_id` = VALUES(`parent_id`),
`path` = VALUES(`path`),
`icon` = VALUES(`icon`),
`component` = VALUES(`component`),
`component_name` = VALUES(`component_name`),
`status` = VALUES(`status`),
`visible` = VALUES(`visible`),
`keep_alive` = VALUES(`keep_alive`),
`always_show` = VALUES(`always_show`),
`updater` = 'script',
`deleted` = b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9002, '患者管理', 'rehab:patient:view', 2, 2, 9000, 'patient', 'ep:user', 'rehab/patient/index', 'RehabPatient', 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
`name` = VALUES(`name`),
`permission` = VALUES(`permission`),
`type` = VALUES(`type`),
`sort` = VALUES(`sort`),
`parent_id` = VALUES(`parent_id`),
`path` = VALUES(`path`),
`icon` = VALUES(`icon`),
`component` = VALUES(`component`),
`component_name` = VALUES(`component_name`),
`status` = VALUES(`status`),
`visible` = VALUES(`visible`),
`keep_alive` = VALUES(`keep_alive`),
`always_show` = VALUES(`always_show`),
`updater` = 'script',
`deleted` = b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9003, '评估管理', 'rehab:assessment:view', 2, 3, 9000, 'assessment', 'ep:data-analysis', 'rehab/assessment/index', 'RehabAssessment', 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
`name` = VALUES(`name`),
`permission` = VALUES(`permission`),
`type` = VALUES(`type`),
`sort` = VALUES(`sort`),
`parent_id` = VALUES(`parent_id`),
`path` = VALUES(`path`),
`icon` = VALUES(`icon`),
`component` = VALUES(`component`),
`component_name` = VALUES(`component_name`),
`status` = VALUES(`status`),
`visible` = VALUES(`visible`),
`keep_alive` = VALUES(`keep_alive`),
`always_show` = VALUES(`always_show`),
`updater` = 'script',
`deleted` = b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9004, '报告中心', 'rehab:report:view', 2, 4, 9000, 'report', 'ep:document', 'rehab/report/index', 'RehabReport', 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
`name` = VALUES(`name`),
`permission` = VALUES(`permission`),
`type` = VALUES(`type`),
`sort` = VALUES(`sort`),
`parent_id` = VALUES(`parent_id`),
`path` = VALUES(`path`),
`icon` = VALUES(`icon`),
`component` = VALUES(`component`),
`component_name` = VALUES(`component_name`),
`status` = VALUES(`status`),
`visible` = VALUES(`visible`),
`keep_alive` = VALUES(`keep_alive`),
`always_show` = VALUES(`always_show`),
`updater` = 'script',
`deleted` = b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9101, '患者新增', 'rehab:patient:create', 3, 1, 9002, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
`name` = VALUES(`name`),
`permission` = VALUES(`permission`),
`type` = VALUES(`type`),
`sort` = VALUES(`sort`),
`parent_id` = VALUES(`parent_id`),
`updater` = 'script',
`deleted` = b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9102, '患者编辑', 'rehab:patient:update', 3, 2, 9002, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
`name` = VALUES(`name`),
`permission` = VALUES(`permission`),
`type` = VALUES(`type`),
`sort` = VALUES(`sort`),
`parent_id` = VALUES(`parent_id`),
`updater` = 'script',
`deleted` = b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9201, '评估新增', 'rehab:assessment:create', 3, 1, 9003, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
`name` = VALUES(`name`),
`permission` = VALUES(`permission`),
`type` = VALUES(`type`),
`sort` = VALUES(`sort`),
`parent_id` = VALUES(`parent_id`),
`updater` = 'script',
`deleted` = b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9202, '评估编辑', 'rehab:assessment:update', 3, 2, 9003, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
`name` = VALUES(`name`),
`permission` = VALUES(`permission`),
`type` = VALUES(`type`),
`sort` = VALUES(`sort`),
`parent_id` = VALUES(`parent_id`),
`updater` = 'script',
`deleted` = b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES (9301, '报告导出', 'rehab:report:export', 3, 1, 9004, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
`name` = VALUES(`name`),
`permission` = VALUES(`permission`),
`type` = VALUES(`type`),
`sort` = VALUES(`sort`),
`parent_id` = VALUES(`parent_id`),
`updater` = 'script',
`deleted` = b'0';

-- =========================
-- 2) 角色初始化
-- =========================
INSERT INTO `system_role` (`name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT '康复治疗师', 'rehab_therapist', 50, 1, '', 0, 2, '康复业务角色', 'script', 'script', b'0', 1
WHERE NOT EXISTS (
    SELECT 1 FROM `system_role` WHERE `code` = 'rehab_therapist' AND `tenant_id` = 1 AND `deleted` = b'0'
);

INSERT INTO `system_role` (`name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT '文员', 'rehab_clerk', 51, 1, '', 0, 2, '康复业务文员角色', 'script', 'script', b'0', 1
WHERE NOT EXISTS (
    SELECT 1 FROM `system_role` WHERE `code` = 'rehab_clerk' AND `tenant_id` = 1 AND `deleted` = b'0'
);

INSERT INTO `system_role` (`name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT '普通用户（患者）', 'rehab_patient', 52, 1, '', 0, 2, '患者端预留角色（后台默认无菜单）', 'script', 'script', b'0', 1
WHERE NOT EXISTS (
    SELECT 1 FROM `system_role` WHERE `code` = 'rehab_patient' AND `tenant_id` = 1 AND `deleted` = b'0'
);

-- =========================
-- 3) 角色菜单绑定
-- =========================
-- 超级管理员：康复菜单全量可见
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 1, t.menu_id, 'script', 'script', b'0', 1
FROM (
         SELECT 9000 AS menu_id UNION ALL
         SELECT 9001 UNION ALL
         SELECT 9002 UNION ALL
         SELECT 9003 UNION ALL
         SELECT 9004 UNION ALL
         SELECT 9101 UNION ALL
         SELECT 9102 UNION ALL
         SELECT 9201 UNION ALL
         SELECT 9202 UNION ALL
         SELECT 9301
     ) t
         LEFT JOIN `system_role_menu` rm
                   ON rm.role_id = 1 AND rm.menu_id = t.menu_id AND rm.tenant_id = 1 AND rm.deleted = b'0'
WHERE rm.id IS NULL;

-- 康复治疗师：工作台 + 患者 + 评估 + 报告
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
    SELECT 9201 UNION ALL
    SELECT 9202 UNION ALL
    SELECT 9301
) t
         LEFT JOIN `system_role_menu` rm
                   ON rm.role_id = r.id AND rm.menu_id = t.menu_id AND rm.tenant_id = 1 AND rm.deleted = b'0'
WHERE r.code = 'rehab_therapist'
  AND r.tenant_id = 1
  AND r.deleted = b'0'
  AND rm.id IS NULL;

-- 文员：仅患者管理（可查看与维护患者）
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT r.id, t.menu_id, 'script', 'script', b'0', 1
FROM `system_role` r
         JOIN (
    SELECT 9000 AS menu_id UNION ALL
    SELECT 9002 UNION ALL
    SELECT 9101 UNION ALL
    SELECT 9102
) t
         LEFT JOIN `system_role_menu` rm
                   ON rm.role_id = r.id AND rm.menu_id = t.menu_id AND rm.tenant_id = 1 AND rm.deleted = b'0'
WHERE r.code = 'rehab_clerk'
  AND r.tenant_id = 1
  AND r.deleted = b'0'
  AND rm.id IS NULL;

-- 患者角色：后台不授予菜单，后续给小程序端使用

COMMIT;
