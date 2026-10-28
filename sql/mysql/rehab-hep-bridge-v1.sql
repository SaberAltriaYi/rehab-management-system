-- Core is the authority for tenant, patient, episode and therapist assignments.
-- Idempotent inbox only: HEP clinical feedback is never auto-diagnosed.
CREATE TABLE IF NOT EXISTS rehab_hep_bridge_event (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  event_id VARCHAR(80) NOT NULL,
  patient_id BIGINT NOT NULL,
  episode_id BIGINT NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  session_id VARCHAR(80) NOT NULL,
  payload_json TEXT NOT NULL,
  review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_hep_bridge_inbox (tenant_id,event_id),
  KEY ix_hep_bridge_patient (tenant_id,patient_id,created_at),
  KEY ix_hep_bridge_review (tenant_id,review_status,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
