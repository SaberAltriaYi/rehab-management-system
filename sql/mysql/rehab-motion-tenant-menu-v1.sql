-- Motion assessment tenant-menu rollout.
-- Run after rehab-motion-assessment-v1.sql and rehab-motion-audit-columns-v1.sql.
-- Keeps the package and tenant-admin RBAC in sync with the motion assessment APIs.
-- Idempotent: the package update runs only when the complete motion menu set is absent.

SET NAMES utf8mb4;

SET @motion_menu_ids = JSON_ARRAY(9700, 9710, 9711, 9712, 9713, 9714, 9715, 9716, 9717);

UPDATE system_tenant_package
SET menu_ids = JSON_MERGE_PRESERVE(
  CAST(COALESCE(menu_ids, JSON_ARRAY()) AS JSON),
  @motion_menu_ids
)
WHERE id = 111
  AND NOT JSON_CONTAINS(CAST(COALESCE(menu_ids, JSON_ARRAY()) AS JSON), @motion_menu_ids);

INSERT INTO system_role_menu
  (role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT r.id, m.menu_id, 'migration', 'migration', b'0', r.tenant_id
FROM system_role r
JOIN (
  SELECT 9700 AS menu_id UNION ALL SELECT 9710 UNION ALL SELECT 9711 UNION ALL
  SELECT 9712 UNION ALL SELECT 9713 UNION ALL SELECT 9714 UNION ALL
  SELECT 9715 UNION ALL SELECT 9716 UNION ALL SELECT 9717
) m
LEFT JOIN system_role_menu rm
  ON rm.role_id = r.id
 AND rm.menu_id = m.menu_id
 AND rm.tenant_id = r.tenant_id
 AND rm.deleted = b'0'
WHERE r.code = 'tenant_admin'
  AND r.deleted = b'0'
  AND r.tenant_id > 0
  AND rm.id IS NULL;

