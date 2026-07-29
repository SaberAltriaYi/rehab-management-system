-- 内部康复版只交付系统管理、基础设施与康复管理。
-- 关闭所有未随生产 JAR 交付的可选模块菜单，避免出现可见但无法使用的入口。
-- 可重复执行。

SET NAMES utf8mb4;

WITH RECURSIVE menu_tree AS (
  SELECT id
  FROM system_menu
  WHERE deleted = b'0'
    AND parent_id = 0
    AND path IN (
      '/pay',
      '/report',
      '/bpm',
      '/member',
      '/mp',
      '/mall',
      '/erp',
      '/crm',
      '/ai',
      '/iot'
    )
  UNION ALL
  SELECT menu.id
  FROM system_menu menu
  INNER JOIN menu_tree parent_menu ON menu.parent_id = parent_menu.id
  WHERE menu.deleted = b'0'
)
UPDATE system_menu
SET status = 1,
    updater = '1',
    update_time = NOW()
WHERE id IN (SELECT id FROM menu_tree);
