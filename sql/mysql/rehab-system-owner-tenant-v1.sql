-- 系统拥有者租户管理增强（v1）
-- 目标：为系统拥有者提供跨租户总览权限，同时复用现有租户 CRUD 页面。
-- 执行前提：已初始化标准 system_menu / system_role 表。
-- 脚本可重复执行。

SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO system_menu
  (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, updater, deleted)
VALUES
  (1230, '系统拥有者总览', 'system:tenant:owner', 3, 6, 1138, '', '', '', NULL,
   0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  permission = VALUES(permission),
  parent_id = VALUES(parent_id),
  sort = VALUES(sort),
  status = VALUES(status),
  visible = VALUES(visible),
  updater = 'script',
  deleted = b'0';

INSERT INTO system_role
  (name, code, sort, data_scope, data_scope_dept_ids, status, type, remark,
   creator, updater, deleted, tenant_id)
SELECT
  '系统拥有者', 'system_owner', 0, 1, '', 0, 2, '跨租户管理和平台运营总览',
  'script', 'script', b'0', 1
WHERE NOT EXISTS (
  SELECT 1 FROM system_role
  WHERE code = 'system_owner' AND tenant_id = 1 AND deleted = b'0'
);

INSERT INTO system_role_menu (role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT 1, 1230, 'script', 'script', b'0', 1
WHERE NOT EXISTS (
  SELECT 1 FROM system_role_menu
  WHERE role_id = 1 AND menu_id = 1230 AND tenant_id = 1 AND deleted = b'0'
);

INSERT INTO system_role_menu (role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT r.id, t.menu_id, 'script', 'script', b'0', 1
FROM system_role r
JOIN (
  SELECT 1 AS menu_id UNION ALL
  SELECT 1224 UNION ALL
  SELECT 1138 UNION ALL
  SELECT 1139 UNION ALL
  SELECT 1140 UNION ALL
  SELECT 1141 UNION ALL
  SELECT 1142 UNION ALL
  SELECT 1143 UNION ALL
  SELECT 1225 UNION ALL
  SELECT 1226 UNION ALL
  SELECT 1227 UNION ALL
  SELECT 1228 UNION ALL
  SELECT 1229 UNION ALL
  SELECT 1230
) t
LEFT JOIN system_role_menu rm
  ON rm.role_id = r.id AND rm.menu_id = t.menu_id
 AND rm.tenant_id = 1 AND rm.deleted = b'0'
WHERE r.code = 'system_owner'
  AND r.tenant_id = 1
  AND r.deleted = b'0'
  AND rm.id IS NULL;

COMMIT;
