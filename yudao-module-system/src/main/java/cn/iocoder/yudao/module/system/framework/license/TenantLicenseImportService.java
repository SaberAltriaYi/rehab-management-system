package cn.iocoder.yudao.module.system.framework.license;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.Instant;

/**
 * Internal-only future L3 activation interface (NOT a web endpoint).
 * The caller must independently authenticate the local administrator /
 * installation and the owner's signed epoch + trusted clock update.
 */
@Service
public class TenantLicenseImportService {
    private final JdbcTemplate jdbc;
    private final TenantLicenseGuard verifier;

    public TenantLicenseImportService(JdbcTemplate jdbc, TenantLicenseGuard verifier) {
        this.jdbc = jdbc;
        this.verifier = verifier;
    }

    @Transactional(rollbackFor = Exception.class)
    public void importSignedLease(Long tenantId, String tenantUuid, String installationUuid,
                                  String token, String statusProof) {
        if (tenantId == null || tenantId <= 0) {
            throw new LicenseLeaseVerifier.LicenseFailure("INVALID_VERIFICATION_CONTEXT");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT tenant_uuid,installation_uuid,revocation_epoch,trusted_time_ms"
                        + " FROM rehab_tenant_license_binding WHERE tenant_id=? FOR UPDATE", tenantId);
        // Never accept customer-provided "trusted epoch" or clock. Both
        // originate in the owner's verified RHL1 signature (or a previously
        // persisted, monotonically increasing owner-signed baseline).
        Map<String, Object> verified = verifier.verifyExternal(
                tenantUuid, installationUuid, token, 0, 0);
        long nextEpoch = ((Number) verified.get("revocationEpoch")).longValue();
        long nextClock = Instant.parse((String) verified.get("issuedAt")).toEpochMilli();
        Map<String, Object> ownerStatus = verifier.verifyStatusExternal(
                tenantUuid, installationUuid, statusProof, 0);
        if (!"ACTIVE".equals(ownerStatus.get("status"))) {
            throw new LicenseLeaseVerifier.LicenseFailure("TENANT_NOT_ACTIVE");
        }
        nextEpoch = Math.max(nextEpoch, ((Number) ownerStatus.get("revocationEpoch")).longValue());
        nextClock = Math.max(nextClock,
                Instant.parse((String) ownerStatus.get("observedAt")).toEpochMilli());
        if (!rows.isEmpty()) {
            Map<String, Object> previous = rows.get(0);
            if (!tenantUuid.equals(Objects.toString(previous.get("tenant_uuid"), ""))
                    || !installationUuid.equals(Objects.toString(previous.get("installation_uuid"), ""))) {
                // Explicit owner-approved instance replacement needs a separate
                // supervised rebind procedure (L3) and a fresh signed epoch.
                throw new LicenseLeaseVerifier.LicenseFailure("LICENSE_REBIND_REQUIRES_APPROVAL");
            }
            nextEpoch = Math.max(nextEpoch, ((Number) previous.get("revocation_epoch")).longValue());
            nextClock = Math.max(nextClock, ((Number) previous.get("trusted_time_ms")).longValue());
        }
        verifier.verifyExternal(tenantUuid, installationUuid, token, nextEpoch, nextClock);
        verifier.verifyStatusExternal(tenantUuid, installationUuid, statusProof, nextClock);
        if (rows.size() > 1) throw new LicenseLeaseVerifier.LicenseFailure("INVALID_LICENSE_BINDING");
        if (rows.isEmpty()) {
            jdbc.update("INSERT INTO rehab_tenant_license_binding"
                    + "(tenant_id,tenant_uuid,installation_uuid,signed_lease,status_proof,revocation_epoch,trusted_time_ms)"
                    + " VALUES(?,?,?,?,?,?,?)",
                    tenantId, tenantUuid, installationUuid, token, statusProof, nextEpoch, nextClock);
        } else {
            jdbc.update("UPDATE rehab_tenant_license_binding"
                    + " SET signed_lease=?,status_proof=?,revocation_epoch=?,trusted_time_ms=? WHERE tenant_id=?",
                    token, statusProof, nextEpoch, nextClock, tenantId);
        }
    }

    /** Store fresh owner-signed revocation state even for SUSPENDED/REVOKED tenants. */
    @Transactional(rollbackFor = Exception.class)
    public void refreshSignedStatus(Long tenantId, String statusProof) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT tenant_uuid,installation_uuid,revocation_epoch,trusted_time_ms"
                        + " FROM rehab_tenant_license_binding WHERE tenant_id=? FOR UPDATE", tenantId);
        if (rows.size() != 1) {
            throw new LicenseLeaseVerifier.LicenseFailure("TENANT_LICENSE_MISSING");
        }
        Map<String, Object> previous = rows.get(0);
        long oldEpoch = ((Number) previous.get("revocation_epoch")).longValue();
        long oldTime = ((Number) previous.get("trusted_time_ms")).longValue();
        Map<String, Object> verified = verifier.verifyStatusExternal(
                Objects.toString(previous.get("tenant_uuid"), ""),
                Objects.toString(previous.get("installation_uuid"), ""), statusProof, oldTime);
        long newEpoch = ((Number) verified.get("revocationEpoch")).longValue();
        long newTime = Instant.parse((String) verified.get("observedAt")).toEpochMilli();
        if (newEpoch < oldEpoch || newTime < oldTime) {
            throw new LicenseLeaseVerifier.LicenseFailure("STATUS_ROLLBACK");
        }
        jdbc.update("UPDATE rehab_tenant_license_binding"
                + " SET status_proof=?,revocation_epoch=?,trusted_time_ms=? WHERE tenant_id=?",
                statusProof, newEpoch, newTime, tenantId);
    }
}
