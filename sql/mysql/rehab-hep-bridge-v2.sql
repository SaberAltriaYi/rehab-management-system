-- Record the human reviewer for Core-side HEP events; safe to re-run.
SET @add_reviewer = IF(
 (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='rehab_hep_bridge_event' AND column_name='reviewed_by')=0,
 'ALTER TABLE rehab_hep_bridge_event ADD COLUMN reviewed_by BIGINT DEFAULT NULL',
 'SELECT 1');
PREPARE hep_ddl FROM @add_reviewer; EXECUTE hep_ddl; DEALLOCATE PREPARE hep_ddl;
SET @add_reviewed_at = IF(
 (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='rehab_hep_bridge_event' AND column_name='reviewed_at')=0,
 'ALTER TABLE rehab_hep_bridge_event ADD COLUMN reviewed_at TIMESTAMP NULL DEFAULT NULL',
 'SELECT 1');
PREPARE hep_ddl FROM @add_reviewed_at; EXECUTE hep_ddl; DEALLOCATE PREPARE hep_ddl;
