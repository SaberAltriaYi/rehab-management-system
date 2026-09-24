-- OPT-IN ONLY. Execute only after schema/API/browser checks in a dedicated database.
-- Do not run on an existing patient database or production volume without a
-- separately reviewed rollout and backup. Not registered as an automatic migration.
-- Enables only menu pages compiled into the internal SPA and verified in isolation.
-- Third-party Jimu/JMReport, external designer, AI, mall, payments, IoT remain disabled.
SET NAMES utf8mb4;

WITH RECURSIVE menu_paths AS (
  SELECT id, CAST(path AS CHAR(512)) AS full_path
  FROM system_menu
  WHERE parent_id = 0 AND deleted = b'0'
    AND path IN ('/crm', '/bpm', '/erp', '/member', '/report')
  UNION ALL
  SELECT child.id, CONCAT(parent.full_path, '/', child.path)
  FROM system_menu AS child
  INNER JOIN menu_paths AS parent ON child.parent_id = parent.id
  WHERE child.deleted = b'0'
)
UPDATE system_menu AS menu
INNER JOIN menu_paths AS selected ON selected.id = menu.id
SET menu.status = 0,
    menu.updater = '1',
    menu.update_time = NOW()
WHERE selected.full_path IN (
  '/crm', '/crm/customer',
  '/bpm', '/bpm/manager', '/bpm/manager/category',
  '/erp', '/erp/product', '/erp/product/product',
  '/erp/stock', '/erp/stock/stock',
  '/member', '/member/signin', '/member/signin/config',
  '/report', '/report/go-view'
);

-- 仅修正默认旧标题；已自定义标题保留。不要把安全模式误写成开放第三方设计器。
UPDATE system_menu AS menu
INNER JOIN system_menu AS parent ON menu.parent_id = parent.id
SET menu.name = '报表项目（安全模式）',
    menu.updater = '1',
    menu.update_time = NOW()
WHERE parent.parent_id = 0 AND parent.path = '/report'
  AND menu.path = 'go-view' AND menu.name = '大屏设计器'
  AND menu.deleted = b'0';
