-- L7 immutable incremental migration 026, AFTER motion-assessment 024 and 025.
-- Additive license metadata only, no patient fields, no destructive SQL.
-- NEVER run directly against original business DB; use verified migration ledger.
CREATE TABLE rehab_tenant_license_binding (
  tenant_id BIGINT NOT NULL PRIMARY KEY,
  tenant_uuid CHAR(36) NOT NULL,
  installation_uuid CHAR(36) NOT NULL,
  signed_lease TEXT NOT NULL,
  status_proof TEXT NOT NULL COMMENT 'Ed25519 signed RHR1 owner status snapshot',
  revocation_epoch BIGINT NOT NULL,
  trusted_time_ms BIGINT NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_rehab_license_tenant_uuid (tenant_uuid),
  UNIQUE KEY uk_rehab_license_install_uuid (installation_uuid),
  CONSTRAINT chk_rehab_license_epoch CHECK (revocation_epoch >= 0),
  CONSTRAINT chk_rehab_license_clock CHECK (trusted_time_ms >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
