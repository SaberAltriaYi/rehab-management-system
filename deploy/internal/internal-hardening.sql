-- 仅用于全新内部部署卷：保留唯一初始管理员，其余演示后台账号全部停用。
SET NAMES utf8mb4;

UPDATE system_users
SET status = 1,
    updater = 'internal-deploy',
    update_time = NOW()
WHERE deleted = b'0'
  AND NOT (tenant_id = 1 AND username = 'admin');

UPDATE system_tenant
SET name = '工作室内部',
    contact_name = '工作室管理员',
    contact_mobile = '',
    websites = '',
    updater = 'internal-deploy',
    update_time = NOW()
WHERE id = 1;

UPDATE system_tenant
SET status = 1,
    updater = 'internal-deploy',
    update_time = NOW()
WHERE id <> 1 AND deleted = b'0';

UPDATE system_users
SET nickname = '工作室管理员',
    remark = '内部系统初始管理员，请首次登录后立即修改密码',
    email = '',
    mobile = '',
    avatar = '',
    updater = 'internal-deploy',
    update_time = NOW()
WHERE tenant_id = 1 AND username = 'admin' AND deleted = b'0';

UPDATE system_dept
SET name = '工作室',
    phone = '',
    email = '',
    updater = 'internal-deploy',
    update_time = NOW()
WHERE id = 100 AND tenant_id = 1 AND deleted = b'0';
