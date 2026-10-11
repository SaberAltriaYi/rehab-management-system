-- SaaS Platform V2 additive migration 027.
-- Synthetic/bootstrap-safe schema; no payment provider or patient data.
CREATE TABLE IF NOT EXISTS platform_admins (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_platform_admin_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS saas_plans (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  max_users INT NOT NULL,
  max_patients INT NOT NULL,
  motion_quota BIGINT NOT NULL,
  storage_quota BIGINT NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_saas_plan_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tenant_subscriptions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  status ENUM('trialing','active','suspended','expired','cancelled') NOT NULL DEFAULT 'trialing',
  start_time DATETIME NOT NULL,
  expire_time DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_subscription (tenant_id),
  KEY idx_subscription_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tenant_usage (
  tenant_id BIGINT NOT NULL PRIMARY KEY,
  user_count INT NOT NULL DEFAULT 0,
  patient_count INT NOT NULL DEFAULT 0,
  motion_count BIGINT NOT NULL DEFAULT 0,
  storage_bytes BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- MySQL 8.4 has no ADD COLUMN IF NOT EXISTS; inspect metadata before DDL.
SET @saas_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
   AND table_name='system_tenant' AND column_name='registration_status') = 0,
  'ALTER TABLE system_tenant ADD COLUMN registration_status ENUM(''pending'',''active'',''suspended'',''expired'',''deleted'') NOT NULL DEFAULT ''active''',
  'SELECT 1');
PREPARE saas_statement FROM @saas_ddl;
EXECUTE saas_statement;
DEALLOCATE PREPARE saas_statement;
SET @saas_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
   AND table_name='system_tenant' AND column_name='registration_source') = 0,
  'ALTER TABLE system_tenant ADD COLUMN registration_source VARCHAR(32) NOT NULL DEFAULT ''platform''',
  'SELECT 1');
PREPARE saas_statement FROM @saas_ddl;
EXECUTE saas_statement;
DEALLOCATE PREPARE saas_statement;

INSERT INTO saas_plans (name,max_users,max_patients,motion_quota,storage_quota)
VALUES ('synthetic-beta',10,100,1000,1073741824)
ON DUPLICATE KEY UPDATE status=1;
