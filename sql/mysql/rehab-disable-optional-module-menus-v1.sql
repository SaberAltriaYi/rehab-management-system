-- 收敛工作室内部后台菜单，避免成员进入康复工作流之外的演示或可选模块
-- 保留：系统管理 / 基础设施 / 康复管理 / CRM
-- 隐藏：外部文档、支付、通用报表、工作流、会员、商城、公众号、ERP、AI、IoT
-- 可重复执行

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

WITH RECURSIVE menu_tree AS (
  -- 工作室内部部署不启用的顶级目录与外部链接
  SELECT id
  FROM system_menu
  WHERE deleted = b'0'
    AND parent_id = 0
    AND path IN (
      'https://www.iocoder.cn',
      'https://doc.iocoder.cn/',
      'https://cloud.iocoder.cn',
      '/pay',
      '/report',
      '/bpm',
      '/member',
      '/mp',
      '/mall',
      '/erp',
      '/ai',
      '/iot'
    )
  UNION ALL
  SELECT m.id
  FROM system_menu m
  INNER JOIN menu_tree t ON m.parent_id = t.id
  WHERE m.deleted = b'0'
)
UPDATE system_menu
SET status = 1,
    updater = '1',
    update_time = NOW()
WHERE id IN (SELECT id FROM menu_tree);

-- 角色菜单绑定不删除，只禁用菜单即可。若需彻底收敛权限，可取消以下注释：
-- WITH RECURSIVE menu_tree AS (
--   SELECT id FROM system_menu WHERE deleted = b'0' AND parent_id = 0
--     AND path IN ('/pay', '/report', '/bpm', '/member', '/mp', '/mall', '/erp', '/ai', '/iot')
--   UNION ALL
--   SELECT m.id FROM system_menu m INNER JOIN menu_tree t ON m.parent_id = t.id WHERE m.deleted = b'0'
-- )
-- DELETE FROM system_role_menu WHERE menu_id IN (SELECT id FROM menu_tree);

SET FOREIGN_KEY_CHECKS = 1;
