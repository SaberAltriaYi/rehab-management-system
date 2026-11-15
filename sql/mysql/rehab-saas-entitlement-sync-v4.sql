-- Apply after rehab-saas-provisioning-v3.sql, before enabling Owner sync.
-- Revision prevents late deliveries from overwriting more recent entitlement updates.
SET @entitlement_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
   AND table_name='saas_provisioning_requests' AND column_name='entitlement_revision') = 0,
  'ALTER TABLE saas_provisioning_requests ADD COLUMN entitlement_revision BIGINT NOT NULL DEFAULT 0',
  'SELECT 1');
PREPARE entitlement_stmt FROM @entitlement_ddl;
EXECUTE entitlement_stmt;
DEALLOCATE PREPARE entitlement_stmt;
