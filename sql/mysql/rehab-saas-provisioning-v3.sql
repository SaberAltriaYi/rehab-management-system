-- SaaS provisioning V3: immutable tenant UUID and idempotent owner -> main-system handoff.
-- Run after rehab-saas-platform-v2.sql and rehab-saas-onboarding-v2.sql.

SET @saas_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
   AND table_name='system_tenant' AND column_name='tenant_uuid') = 0,
  'ALTER TABLE system_tenant ADD COLUMN tenant_uuid CHAR(36) NULL',
  'SELECT 1');
PREPARE saas_statement FROM @saas_ddl; EXECUTE saas_statement; DEALLOCATE PREPARE saas_statement;

SET @saas_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
   AND table_name='system_tenant' AND index_name='uk_system_tenant_uuid') = 0,
  'ALTER TABLE system_tenant ADD UNIQUE KEY uk_system_tenant_uuid (tenant_uuid)',
  'SELECT 1');
PREPARE saas_statement FROM @saas_ddl; EXECUTE saas_statement; DEALLOCATE PREPARE saas_statement;

SET @saas_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
   AND table_name='saas_registrations' AND column_name='tenant_uuid') = 0,
  'ALTER TABLE saas_registrations ADD COLUMN tenant_uuid CHAR(36) NULL',
  'SELECT 1');
PREPARE saas_statement FROM @saas_ddl; EXECUTE saas_statement; DEALLOCATE PREPARE saas_statement;
SET @saas_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
   AND table_name='saas_registrations' AND column_name='provisioning_status') = 0,
  'ALTER TABLE saas_registrations ADD COLUMN provisioning_status ENUM(''NOT_STARTED'',''PROVISIONING'',''ACTIVE'',''FAILED'',''NOT_CONFIGURED'') NOT NULL DEFAULT ''NOT_STARTED''',
  'SELECT 1');
PREPARE saas_statement FROM @saas_ddl; EXECUTE saas_statement; DEALLOCATE PREPARE saas_statement;
SET @saas_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
   AND table_name='saas_registrations' AND column_name='provisioning_error') = 0,
  'ALTER TABLE saas_registrations ADD COLUMN provisioning_error VARCHAR(500) NULL',
  'SELECT 1');
PREPARE saas_statement FROM @saas_ddl; EXECUTE saas_statement; DEALLOCATE PREPARE saas_statement;

CREATE TABLE IF NOT EXISTS saas_provisioning_requests (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  idempotency_key CHAR(36) NOT NULL,
  tenant_uuid CHAR(36) NOT NULL,
  organization_name VARCHAR(120) NOT NULL,
  contact_name VARCHAR(80) NOT NULL,
  contact_mobile VARCHAR(40) NULL,
  tenant_id BIGINT NULL,
  plan_id BIGINT NOT NULL,
  package_id BIGINT NOT NULL,
  status ENUM('PROVISIONING','ACTIVE','FAILED') NOT NULL DEFAULT 'PROVISIONING',
  failure_reason VARCHAR(500) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_saas_provisioning_idempotency (idempotency_key),
  UNIQUE KEY uk_saas_provisioning_tenant (tenant_uuid),
  KEY idx_saas_provisioning_status (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
