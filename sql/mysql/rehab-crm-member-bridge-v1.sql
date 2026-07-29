-- Rehab + CRM + Member 联动修复（v1）
-- 目标：
-- 1) 统一康复菜单命名，清理历史遗留 RE 侧栏入口
-- 2) 为 rehab 角色补齐 CRM/会员查询权限（用于患者绑定与联动展示）

SET NAMES utf8mb4;
START TRANSACTION;

-- 1) 统一康复主菜单名称，避免历史英文/缩写菜单导致侧栏出现 RE
UPDATE `system_menu`
SET `name` = '康复管理',
    `path` = '/rehab',
    `updater` = 'script',
    `update_time` = NOW(),
    `deleted` = b'0'
WHERE `id` = 9000;

-- 2) 清理历史遗留的 RE 根菜单（仅处理顶级、非 rehab 主菜单的孤立项）
UPDATE `system_menu`
SET `status` = 1,
    `visible` = b'0',
    `deleted` = b'1',
    `updater` = 'script',
    `update_time` = NOW()
WHERE `parent_id` = 0
  AND `type` = 1
  AND (`name` = 'RE' OR `name` LIKE 'RE%')
  AND `id` <> 9000
  AND `deleted` = b'0';

-- 2.1) 同步清理 RE 模块下的子菜单和按钮，避免侧栏继续出现 /re 旧入口
UPDATE `system_menu`
SET `status` = 1,
    `visible` = b'0',
    `deleted` = b'1',
    `updater` = 'script',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (
    `id` IN (
      SELECT id FROM (
        SELECT m0.id
        FROM `system_menu` m0
        WHERE m0.`parent_id` = 0
          AND m0.`type` = 1
          AND (m0.`name` = 'RE' OR m0.`name` LIKE 'RE%' OR m0.`path` = '/re')
      ) x0
    )
    OR `parent_id` IN (
      SELECT id FROM (
        SELECT m1.id
        FROM `system_menu` m1
        WHERE m1.`parent_id` = 0
          AND m1.`type` = 1
          AND (m1.`name` = 'RE' OR m1.`name` LIKE 'RE%' OR m1.`path` = '/re')
      ) x1
    )
    OR `parent_id` IN (
      SELECT id FROM (
        SELECT c1.id
        FROM `system_menu` c1
        JOIN `system_menu` p1 ON p1.id = c1.parent_id
        WHERE p1.`parent_id` = 0
          AND (p1.`name` = 'RE' OR p1.`name` LIKE 'RE%' OR p1.`path` = '/re')
      ) x2
    )
  );

-- 3) rehab 角色补齐 CRM/会员查询型权限（只补查询，不补编辑）
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT r.id, m.id, 'script', 'script', b'0', 1
FROM `system_role` r
JOIN `system_menu` m ON m.deleted = b'0'
LEFT JOIN `system_role_menu` rm
  ON rm.role_id = r.id
 AND rm.menu_id = m.id
 AND rm.tenant_id = 1
 AND rm.deleted = b'0'
WHERE r.code IN ('rehab_therapist', 'rehab_clerk')
  AND r.tenant_id = 1
  AND r.deleted = b'0'
  AND m.permission IN ('crm:customer:query', 'member:user:query')
  AND rm.id IS NULL;

-- 4) 清理历史遗留的空白康复根菜单（保留 9000 作为唯一康复主入口）
UPDATE `system_menu` m
JOIN (
  SELECT x.id
  FROM (
    SELECT m1.`id`
    FROM `system_menu` m1
    LEFT JOIN `system_menu` c
      ON c.`parent_id` = m1.`id`
     AND c.`deleted` = b'0'
    WHERE m1.`parent_id` = 0
      AND m1.`path` = '/rehab'
      AND m1.`id` <> 9000
      AND m1.`deleted` = b'0'
    GROUP BY m1.`id`
    HAVING COUNT(c.`id`) = 0
  ) x
) t ON t.id = m.id
SET m.`status` = 1,
    m.`visible` = b'0',
    m.`deleted` = b'1',
    m.`updater` = 'script',
    m.`update_time` = NOW();

COMMIT;
