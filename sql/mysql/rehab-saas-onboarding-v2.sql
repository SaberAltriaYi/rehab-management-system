-- 028: additive registration queue and platform audit. No credentials or fixtures.
CREATE TABLE IF NOT EXISTS saas_registrations (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 request_id CHAR(36) NOT NULL,
 organization_name VARCHAR(80) NOT NULL,
 contact_name VARCHAR(50) NOT NULL,
 contact_email VARCHAR(120) NOT NULL,
 status ENUM('pending','approved','rejected') NOT NULL DEFAULT 'pending',
 tenant_id BIGINT NULL,
 reviewed_by BIGINT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 reviewed_at TIMESTAMP NULL,
 UNIQUE KEY uk_registration_request (request_id),
 KEY idx_registration_status (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS saas_platform_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 actor_user_id BIGINT NOT NULL,
 action VARCHAR(40) NOT NULL,
 target_id BIGINT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_platform_audit_target (target_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
