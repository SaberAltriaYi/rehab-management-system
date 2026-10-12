-- 029: tenant-scoped, one-use therapist enrollment. Raw tokens are never stored.
CREATE TABLE IF NOT EXISTS saas_staff_invitations (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 role_id BIGINT NOT NULL,
 recipient_email VARCHAR(50) NOT NULL,
 token_hash CHAR(64) NOT NULL,
 status ENUM('pending','accepted','revoked') NOT NULL DEFAULT 'pending',
 invited_by BIGINT NOT NULL,
 accepted_user_id BIGINT NULL,
 expires_at DATETIME NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 accepted_at DATETIME NULL,
 UNIQUE KEY uk_staff_invitation_token (tenant_id,token_hash),
 KEY idx_staff_invitation_status (tenant_id,status,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
