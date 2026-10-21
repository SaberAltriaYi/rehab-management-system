1: -- SaaS provisioning V3: immutable tenant UUID and idempotent owner -> main-system handoff.
2: -- Run after rehab-saas-platform-v2.sql and rehab-saas-onboarding-v2.sql.
3: 
4: SET @saas_ddl = IF(
5:   (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
6:    AND table_name='system_tenant' AND column_name='tenant_uuid') = 0,
7:   'ALTER TABLE system_tenant ADD COLUMN tenant_uuid CHAR(36) NULL',
8:   'SELECT 1');
9: PREPARE saas_statement FROM @saas_ddl; EXECUTE saas_statement; DEALLOCATE PREPARE saas_statement;
10: 
11: SET @saas_ddl = IF(
12:   (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
13:    AND table_name='system_tenant' AND index_name='uk_system_tenant_uuid') = 0,
14:   'ALTER TABLE system_tenant ADD UNIQUE KEY uk_system_tenant_uuid (tenant_uuid)',
15:   'SELECT 1');
16: PREPARE saas_statement FROM @saas_ddl; EXECUTE saas_statement; DEALLOCATE PREPARE saas_statement;
17: 
18: SET @saas_ddl = IF(
19:   (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
20:    AND table_name='saas_registrations' AND column_name='tenant_uuid') = 0,
21:   'ALTER TABLE saas_registrations ADD COLUMN tenant_uuid CHAR(36) NULL',
22:   'SELECT 1');
23: PREPARE saas_statement FROM @saas_ddl; EXECUTE saas_statement; DEALLOCATE PREPARE saas_statement;
24: SET @saas_ddl = IF(
25:   (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
26:    AND table_name='saas_registrations' AND column_name='provisioning_status') = 0,
27:   'ALTER TABLE saas_registrations ADD COLUMN provisioning_status ENUM(''NOT_STARTED'',''PROVISIONING'',''ACTIVE'',''FAILED'',''NOT_CONFIGURED'') NOT NULL DEFAULT ''NOT_STARTED''',
28:   'SELECT 1');
29: PREPARE saas_statement FROM @saas_ddl; EXECUTE saas_statement; DEALLOCATE PREPARE saas_statement;
30: SET @saas_ddl = IF(
31:   (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
32:    AND table_name='saas_registrations' AND column_name='provisioning_error') = 0,
33:   'ALTER TABLE saas_registrations ADD COLUMN provisioning_error VARCHAR(500) NULL',
34:   'SELECT 1');
35: PREPARE saas_statement FROM @saas_ddl; EXECUTE saas_statement; DEALLOCATE PREPARE saas_statement;
36: 
37: CREATE TABLE IF NOT EXISTS saas_provisioning_requests (
38:   id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
39:   idempotency_key CHAR(36) NOT NULL,
40:   tenant_uuid CHAR(36) NOT NULL,
41:   organization_name VARCHAR(120) NOT NULL,
42:   contact_name VARCHAR(80) NOT NULL,
43:   contact_mobile VARCHAR(40) NULL,
44:   tenant_id BIGINT NULL,
45:   plan_id BIGINT NOT NULL,
46:   package_id BIGINT NOT NULL,
47:   status ENUM('PROVISIONING','ACTIVE','FAILED') NOT NULL DEFAULT 'PROVISIONING',
48:   failure_reason VARCHAR(500) NULL,
49:   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
50:   updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
51:   UNIQUE KEY uk_saas_provisioning_idempotency (idempotency_key),
52:   UNIQUE KEY uk_saas_provisioning_tenant (tenant_uuid),
53:   KEY idx_saas_provisioning_status (status, updated_at)
54: ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;