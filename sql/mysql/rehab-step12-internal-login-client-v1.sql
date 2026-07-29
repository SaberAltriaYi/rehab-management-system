-- 系统后台登录内部依赖 client_id=default 生成访问令牌。
-- 保留这一项内部客户端，但移除全部外部 OAuth2 授权类型并轮换上游演示 secret。
SET NAMES utf8mb4;

UPDATE `system_oauth2_client`
SET `status` = 0,
    `secret` = LOWER(HEX(RANDOM_BYTES(32))),
    `redirect_uris` = '[]',
    `authorized_grant_types` = '[]',
    `scopes` = '[]',
    `auto_approve_scopes` = '[]',
    `authorities` = '[]',
    `resource_ids` = '[]',
    `updater` = 'internal-migrate',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `client_id` = 'default';
