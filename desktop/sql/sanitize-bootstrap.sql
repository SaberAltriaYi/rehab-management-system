-- Copyright (c) 2026 杨玺龙
-- 桌面安装包构建专用：在隔离的临时 MySQL 中移除演示、患者、日志及业务交易数据。
-- 本脚本只用于生成发布快照，不用于现有生产数据库升级。
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP PROCEDURE IF EXISTS desktop_clear_non_reference_tables;
DELIMITER $$
CREATE PROCEDURE desktop_clear_non_reference_tables()
BEGIN
  DECLARE finished INTEGER DEFAULT 0;
  DECLARE current_table VARCHAR(128);
  DECLARE tables_cursor CURSOR FOR
    SELECT table_name
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_type = 'BASE TABLE'
      AND table_name NOT IN (
        'system_tenant_package',
        'system_tenant',
        'system_dept',
        'system_post',
        'system_users',
        'system_role',
        'system_user_role',
        'system_menu',
        'system_role_menu',
        'system_dict_type',
        'system_dict_data',
        'system_oauth2_client',
        'infra_file_config',
        'rehab_alert_rule',
        'rehab_ai_prompt_template',
        'rehab_ai_config',
        'internal_schema_history'
      );
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;

  OPEN tables_cursor;
  clear_loop: LOOP
    FETCH tables_cursor INTO current_table;
    IF finished = 1 THEN
      LEAVE clear_loop;
    END IF;
    SET @clear_sql = CONCAT('DELETE FROM `', REPLACE(current_table, '`', '``'), '`');
    PREPARE clear_statement FROM @clear_sql;
    EXECUTE clear_statement;
    DEALLOCATE PREPARE clear_statement;
  END LOOP;
  CLOSE tables_cursor;
END$$
DELIMITER ;

CALL desktop_clear_non_reference_tables();
DROP PROCEDURE desktop_clear_non_reference_tables;

DELETE FROM system_users
WHERE NOT (tenant_id = 1 AND username = 'admin' AND deleted = b'0');

UPDATE system_users
SET password = '!desktop-runtime-sets-password!',
    nickname = '工作室管理员',
    dept_id = 100,
    post_ids = '[1]',
    email = '',
    mobile = '',
    avatar = '',
    sex = 0,
    status = 0,
    login_ip = '',
    login_date = NULL,
    remark = '首次启动后请立即修改管理员密码',
    creator = 'desktop-build',
    updater = 'desktop-build',
    create_time = NOW(),
    update_time = NOW()
WHERE tenant_id = 1 AND username = 'admin' AND deleted = b'0';

DELETE FROM system_tenant WHERE id <> 1;
UPDATE system_tenant
SET name = '工作室内部',
    contact_user_id = 1,
    contact_name = '工作室管理员',
    contact_mobile = '',
    status = 0,
    websites = '',
    package_id = 0,
    expire_time = '2099-12-31 23:59:59',
    account_count = 20,
    creator = 'desktop-build',
    updater = 'desktop-build',
    create_time = NOW(),
    update_time = NOW()
WHERE id = 1;

DELETE FROM system_dept WHERE id <> 100;
UPDATE system_dept
SET name = '工作室',
    parent_id = 0,
    leader_user_id = 1,
    phone = '',
    email = '',
    status = 0,
    tenant_id = 1,
    creator = 'desktop-build',
    updater = 'desktop-build',
    create_time = NOW(),
    update_time = NOW()
WHERE id = 100;

DELETE FROM system_post WHERE id <> 1;
UPDATE system_post
SET name = '工作室成员',
    code = 'studio_member',
    status = 0,
    remark = '',
    tenant_id = 1,
    creator = 'desktop-build',
    updater = 'desktop-build',
    create_time = NOW(),
    update_time = NOW()
WHERE id = 1;

DELETE FROM system_role
WHERE code NOT IN (
  'super_admin',
  'rehab_admin',
  'rehab_therapist',
  'rehab_assistant',
  'rehab_ops',
  'rehab_auditor',
  'rehab_patient'
);
DELETE FROM system_user_role
WHERE user_id <> 1 OR role_id NOT IN (SELECT id FROM system_role);
DELETE FROM system_role_menu
WHERE role_id NOT IN (SELECT id FROM system_role);

DELETE FROM system_oauth2_client WHERE client_id <> 'default';
UPDATE system_oauth2_client
SET secret = 'desktop-runtime-disabled-client-secret',
    name = '康复管理系统内部登录',
    logo = '',
    description = '仅供本机内部登录',
    status = 0,
    redirect_uris = '[]',
    authorized_grant_types = '[]',
    scopes = '[]',
    auto_approve_scopes = '[]',
    authorities = '[]',
    resource_ids = '[]',
    additional_information = '{}',
    creator = 'desktop-build',
    updater = 'desktop-build',
    create_time = NOW(),
    update_time = NOW()
WHERE client_id = 'default';

DELETE FROM infra_file_config WHERE id <> 4;
UPDATE infra_file_config
SET name = '本机数据库存储',
    storage = 1,
    remark = '桌面内部版默认文件存储',
    master = b'1',
    config = '{"@class":"cn.iocoder.yudao.module.infra.framework.file.core.client.db.DBFileClientConfig","domain":"https://127.0.0.1:8443"}',
    creator = 'desktop-build',
    updater = 'desktop-build',
    create_time = NOW(),
    update_time = NOW()
WHERE id = 4;

UPDATE rehab_ai_config
SET ai_enabled = b'0',
    enable_assessment_interpretation = b'0',
    enable_report_summary = b'0',
    enable_patient_summary = b'0',
    enable_plan_draft = b'0',
    enable_followup_writer = b'0',
    updater = 'desktop-build',
    update_time = NOW();

SET FOREIGN_KEY_CHECKS = 1;
