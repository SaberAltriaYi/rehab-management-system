-- 工作室内部版不开放 OAuth2 客户端。停用上游演示客户端，并替换弱演示 secret。
-- 幂等：迁移账本保证正式升级只执行一次；重复执行仍保持禁用状态。
SET NAMES utf8mb4;

UPDATE `system_oauth2_client`
SET `status` = 1,
    `secret` = LOWER(HEX(RANDOM_BYTES(32))),
    `redirect_uris` = '[]',
    `authorized_grant_types` = '[]',
    `scopes` = '[]',
    `auto_approve_scopes` = '[]',
    `authorities` = '[]',
    `resource_ids` = '[]',
    `updater` = 'internal-migrate',
    `update_time` = NOW()
WHERE `deleted` = b'0';
