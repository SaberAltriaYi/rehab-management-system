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

-- Opt-in page visibility alone is not sufficient: the SPA hides create/update/delete
-- controls unless the corresponding *button* permission is active and granted.
-- Only the reviewed pages below receive button access; grant it to the local
-- super administrator only, not every user or every optional business feature.
UPDATE system_menu AS button
INNER JOIN system_menu AS page ON button.parent_id = page.id
SET button.status = 0,
    button.updater = '1',
    button.update_time = NOW()
WHERE button.type = 3 AND button.deleted = b'0'
  AND page.deleted = b'0' AND page.status = 0
  AND (
    (page.component = 'crm/customer/index' AND button.permission IN
      ('crm:customer:query', 'crm:customer:create', 'crm:customer:update', 'crm:customer:delete'))
    OR (page.component = 'bpm/category/index' AND button.permission IN
      ('bpm:category:query', 'bpm:category:create', 'bpm:category:update', 'bpm:category:delete'))
    OR (page.component = 'erp/product/product/index' AND button.permission IN
      ('erp:product:query', 'erp:product:create', 'erp:product:update', 'erp:product:delete'))
    OR (page.component = 'erp/stock/stock/index' AND button.permission = 'erp:stock:query')
    OR (page.component = 'member/signin/config/index' AND button.permission IN
      ('point:sign-in-config:query', 'point:sign-in-config:create',
       'point:sign-in-config:update', 'point:sign-in-config:delete'))
    OR (page.component = 'report/goview/index' AND button.permission IN
      ('report:go-view-project:query', 'report:go-view-project:create',
       'report:go-view-project:update', 'report:go-view-project:delete'))
  );

-- Reactivate an existing soft-deleted grant before inserting missing grants.
UPDATE system_role_menu AS grant_row
INNER JOIN system_role AS role ON grant_row.role_id = role.id
INNER JOIN system_menu AS button ON grant_row.menu_id = button.id
INNER JOIN system_menu AS page ON button.parent_id = page.id
SET grant_row.deleted = b'0',
    grant_row.updater = '1',
    grant_row.update_time = NOW()
WHERE role.code = 'super_admin' AND role.status = 0 AND role.deleted = b'0'
  AND button.type = 3 AND button.status = 0 AND button.deleted = b'0'
  AND page.deleted = b'0' AND page.status = 0
  AND page.component IN ('crm/customer/index', 'bpm/category/index',
    'erp/product/product/index', 'erp/stock/stock/index',
    'member/signin/config/index', 'report/goview/index')
  AND (
    (page.component = 'crm/customer/index' AND button.permission IN
      ('crm:customer:query', 'crm:customer:create', 'crm:customer:update', 'crm:customer:delete'))
    OR (page.component = 'bpm/category/index' AND button.permission IN
      ('bpm:category:query', 'bpm:category:create', 'bpm:category:update', 'bpm:category:delete'))
    OR (page.component = 'erp/product/product/index' AND button.permission IN
      ('erp:product:query', 'erp:product:create', 'erp:product:update', 'erp:product:delete'))
    OR (page.component = 'erp/stock/stock/index' AND button.permission = 'erp:stock:query')
    OR (page.component = 'member/signin/config/index' AND button.permission IN
      ('point:sign-in-config:query', 'point:sign-in-config:create',
       'point:sign-in-config:update', 'point:sign-in-config:delete'))
    OR (page.component = 'report/goview/index' AND button.permission IN
      ('report:go-view-project:query', 'report:go-view-project:create',
       'report:go-view-project:update', 'report:go-view-project:delete'))
  );

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT role.id, button.id, '1', NOW(), '1', NOW(), b'0', role.tenant_id
FROM system_role AS role
INNER JOIN system_menu AS page ON page.deleted = b'0' AND page.status = 0
INNER JOIN system_menu AS button ON button.parent_id = page.id
  AND button.type = 3 AND button.deleted = b'0' AND button.status = 0
WHERE role.code = 'super_admin' AND role.status = 0 AND role.deleted = b'0'
  AND (
    (page.component = 'crm/customer/index' AND button.permission IN
      ('crm:customer:query', 'crm:customer:create', 'crm:customer:update', 'crm:customer:delete'))
    OR (page.component = 'bpm/category/index' AND button.permission IN
      ('bpm:category:query', 'bpm:category:create', 'bpm:category:update', 'bpm:category:delete'))
    OR (page.component = 'erp/product/product/index' AND button.permission IN
      ('erp:product:query', 'erp:product:create', 'erp:product:update', 'erp:product:delete'))
    OR (page.component = 'erp/stock/stock/index' AND button.permission = 'erp:stock:query')
    OR (page.component = 'member/signin/config/index' AND button.permission IN
      ('point:sign-in-config:query', 'point:sign-in-config:create',
       'point:sign-in-config:update', 'point:sign-in-config:delete'))
    OR (page.component = 'report/goview/index' AND button.permission IN
      ('report:go-view-project:query', 'report:go-view-project:create',
       'report:go-view-project:update', 'report:go-view-project:delete'))
  )
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu AS existing
    WHERE existing.role_id = role.id AND existing.menu_id = button.id
  );
-- Further reviewed paths in the dedicated synthetic database only.
WITH RECURSIVE menu_paths AS (
  SELECT id, CAST(path AS CHAR(512)) AS full_path FROM system_menu
  WHERE parent_id=0 AND deleted=b'0' AND path IN ('/bpm','/erp')
  UNION ALL
  SELECT child.id, CONCAT(parent.full_path,'/',child.path)
  FROM system_menu AS child JOIN menu_paths AS parent ON child.parent_id=parent.id
  WHERE child.deleted=b'0'
)
UPDATE system_menu AS menu JOIN menu_paths AS selected ON selected.id=menu.id
SET menu.status=0, menu.updater='1', menu.update_time=NOW()
WHERE selected.full_path IN (
  '/bpm/task', '/bpm/task/todo', '/bpm/task/done',
  '/erp/stock/warehouse', '/erp/stock/record', '/erp/stock/in', '/erp/stock/out'
);

CREATE TEMPORARY TABLE rehab_reviewed_extra_button (
  component varchar(255) NOT NULL,
  permission varchar(100) NOT NULL,
  PRIMARY KEY(component,permission)
);
INSERT INTO rehab_reviewed_extra_button VALUES
('bpm/task/todo/index','bpm:task:query'),
('bpm/task/todo/index','bpm:task:update'),
('erp/stock/warehouse/index','erp:warehouse:query'),
('erp/stock/warehouse/index','erp:warehouse:create'),
('erp/stock/warehouse/index','erp:warehouse:update'),
('erp/stock/warehouse/index','erp:warehouse:delete'),
('erp/stock/record/index','erp:stock-record:query'),
('erp/stock/in/index','erp:stock-in:query'),
('erp/stock/in/index','erp:stock-in:create'),
('erp/stock/in/index','erp:stock-in:update'),
('erp/stock/in/index','erp:stock-in:delete'),
('erp/stock/in/index','erp:stock-in:update-status'),
('erp/stock/out/index','erp:stock-out:query'),
('erp/stock/out/index','erp:stock-out:create'),
('erp/stock/out/index','erp:stock-out:update'),
('erp/stock/out/index','erp:stock-out:delete'),
('erp/stock/out/index','erp:stock-out:update-status');

UPDATE system_menu AS button JOIN system_menu AS page ON button.parent_id=page.id
JOIN rehab_reviewed_extra_button AS reviewed
  ON reviewed.component=page.component AND reviewed.permission=button.permission
SET button.status=0,button.updater='1',button.update_time=NOW()
WHERE button.type=3 AND button.deleted=b'0' AND page.deleted=b'0' AND page.status=0;

UPDATE system_role_menu AS existing JOIN system_role AS role ON existing.role_id=role.id
JOIN system_menu AS button ON existing.menu_id=button.id
JOIN system_menu AS page ON button.parent_id=page.id
JOIN rehab_reviewed_extra_button AS reviewed
  ON reviewed.component=page.component AND reviewed.permission=button.permission
SET existing.deleted=b'0', existing.updater='1',existing.update_time=NOW()
WHERE role.code='super_admin' AND role.status=0 AND role.deleted=b'0'
  AND page.status=0 AND page.deleted=b'0' AND button.status=0 AND button.deleted=b'0';

INSERT INTO system_role_menu (role_id,menu_id,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT role.id,button.id,'1',NOW(),'1',NOW(),b'0',role.tenant_id
FROM system_role AS role
JOIN system_menu AS page ON page.status=0 AND page.deleted=b'0'
JOIN system_menu AS button ON button.parent_id=page.id
  AND button.type=3 AND button.status=0 AND button.deleted=b'0'
JOIN rehab_reviewed_extra_button AS reviewed
  ON reviewed.component=page.component AND reviewed.permission=button.permission
WHERE role.code='super_admin' AND role.status=0 AND role.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu AS existing
    WHERE existing.role_id=role.id AND existing.menu_id=button.id);
DROP TEMPORARY TABLE rehab_reviewed_extra_button;
