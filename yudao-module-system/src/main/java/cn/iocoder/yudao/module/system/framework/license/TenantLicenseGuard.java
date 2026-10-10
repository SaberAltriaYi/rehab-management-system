package cn.iocoder.yudao.module.system.framework.license;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJobExecutionGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.time.Clock;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fail-closed tenant signature guard. Customer DB values cannot supply
 * verification keys; the owner key is independently pinned by deployment.
 */
@Component
public class TenantLicenseGuard implements TenantJobExecutionGuard {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    @Value("${yudao.license.enforce:false}")
    private boolean enforce;
    @Value("${yudao.license.issuer-kid:}")
    private String kid;
    @Value("${yudao.license.issuer-public-key-base64url:}")
    private String encodedPublicKey;
    @Value("${yudao.license.trusted-public-keys:}")
    private String trustedPublicKeys;
    private Map<String, byte[]> trustedKeys = Collections.emptyMap();

    @Autowired
    public TenantLicenseGuard(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }
    TenantLicenseGuard(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }
    @PostConstruct
    public void start() {
        if (!enforce) return;
        Map<String, byte[]> roots = new HashMap<>();
        if (trustedPublicKeys != null && !trustedPublicKeys.trim().isEmpty()) {
            // Explicit operator-managed trust ring. Never download and trust
            // arbitrary keys from /issuers without a verified software update.
            for (String entry : trustedPublicKeys.split(",", -1)) {
                String[] fields = entry.trim().split("=", -1);
                if (fields.length != 2) throw new IllegalStateException("Invalid license keyring entry");
                putPinnedKey(roots, fields[0], fields[1]);
            }
        } else {
            putPinnedKey(roots, kid, encodedPublicKey);
        }
        trustedKeys = Collections.unmodifiableMap(roots);
    }
    public boolean isEnforced() { return enforce; }

    private static void putPinnedKey(Map<String, byte[]> roots, String keyId, String base64) {
        if (keyId == null || !keyId.matches("[A-Za-z0-9._-]{1,48}")
                || base64 == null || !base64.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalStateException("Pinned publisher kid/public key is required");
        }
        byte[] decoded;
        try { decoded = Base64.getUrlDecoder().decode(base64); }
        catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Pinned publisher key has malformed encoding");
        }
        if (decoded.length != 32 ||
                !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(base64)
                || roots.putIfAbsent(keyId, decoded) != null) {
            throw new IllegalStateException("Pinned publisher key is invalid or duplicated");
        }
    }

    private String issuer(String token, String protocol) {
        if (token == null || !token.startsWith(protocol + ".")) {
            throw new LicenseLeaseVerifier.LicenseFailure("UNTRUSTED_ISSUER_KEY");
        }
        String[] fields = token.split("\\.", -1);
        if (fields.length != 4 || !trustedKeys.containsKey(fields[1])) {
            throw new LicenseLeaseVerifier.LicenseFailure("UNTRUSTED_ISSUER_KEY");
        }
        return fields[1];
    }

    public Map<String, Object> requireTenant(Long tenantId) {
        if (!enforce) return java.util.Collections.emptyMap();
        if (tenantId == null || tenantId <= 0) {
            throw new LicenseLeaseVerifier.LicenseFailure("TENANT_LICENSE_MISSING");
        }
        final List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("SELECT tenant_uuid,installation_uuid,signed_lease,status_proof,"
                    + "revocation_epoch,trusted_time_ms FROM rehab_tenant_license_binding WHERE tenant_id=?", tenantId);
        } catch (RuntimeException ex) {
            throw new LicenseLeaseVerifier.LicenseFailure("TENANT_LICENSE_STORE_UNAVAILABLE");
        }
        if (rows.size() != 1) {
            throw new LicenseLeaseVerifier.LicenseFailure("TENANT_LICENSE_MISSING");
        }
        if (trustedKeys.isEmpty()) {
            throw new LicenseLeaseVerifier.LicenseFailure("LICENSE_TRUST_ANCHOR_MISSING");
        }
        Map<String, Object> row = rows.get(0);
        try {
            String tenant = Objects.toString(row.get("tenant_uuid"), "");
            String installation = Objects.toString(row.get("installation_uuid"), "");
            long previousClock = ((Number) Objects.requireNonNull(row.get("trusted_time_ms"))).longValue();
            String statusProof = Objects.toString(row.get("status_proof"), "");
            String statusIssuer = issuer(statusProof, "RHR1");
            Map<String, Object> status = LicenseStatusVerifier.verify(
                    statusProof, statusIssuer, trustedKeys.get(statusIssuer),
                    tenant, installation, clock.millis(), previousClock);
            if (!"ACTIVE".equals(status.get("status"))) {
                throw new LicenseLeaseVerifier.LicenseFailure("TENANT_NOT_ACTIVE");
            }
            long trustedEpoch = Math.max(
                    ((Number) Objects.requireNonNull(row.get("revocation_epoch"))).longValue(),
                    ((Number) status.get("revocationEpoch")).longValue());
            long trustedTime = Math.max(previousClock,
                    java.time.Instant.parse((String) status.get("observedAt")).toEpochMilli());
            String lease = Objects.toString(row.get("signed_lease"), "");
            String leaseIssuer = issuer(lease, "RHL1");
            return LicenseLeaseVerifier.verify(lease,
                    leaseIssuer, trustedKeys.get(leaseIssuer), tenant, installation, clock.millis(),
                    trustedEpoch, trustedTime);
        } catch (LicenseLeaseVerifier.LicenseFailure ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new LicenseLeaseVerifier.LicenseFailure("INVALID_LICENSE_BINDING");
        }
    }

    public Map<String, Object> requireCurrentTenant() {
        return requireTenant(TenantContextHolder.getTenantId());
    }

    /**
     * Verification primitive for the future privileged L3 activation channel.
     * It does not accept a customer-provided issuer or public key.
     */
    public Map<String, Object> verifyExternal(String tenantUuid, String installationUuid,
                                               String token, long epoch, long trustedTimeMs) {
        if (!enforce || trustedKeys.isEmpty()) {
            throw new LicenseLeaseVerifier.LicenseFailure("LICENSE_TRUST_ANCHOR_MISSING");
        }
        String matched = issuer(token, "RHL1");
        return LicenseLeaseVerifier.verify(token, matched, trustedKeys.get(matched), tenantUuid, installationUuid,
                clock.millis(), epoch, trustedTimeMs);
    }

    public Map<String, Object> verifyStatusExternal(String tenantUuid, String installationUuid,
                                                     String statusProof, long lastTrustedTimeMs) {
        if (!enforce || trustedKeys.isEmpty()) {
            throw new LicenseLeaseVerifier.LicenseFailure("LICENSE_TRUST_ANCHOR_MISSING");
        }
        String matched = issuer(statusProof, "RHR1");
        return LicenseStatusVerifier.verify(statusProof, matched, trustedKeys.get(matched),
                tenantUuid, installationUuid, clock.millis(), lastTrustedTimeMs);
    }

    @Override
    public void requireActiveTenant(Long tenantId) {
        requireTenant(tenantId);
    }

    /**
     * Only call under @Transactional. SELECT FOR UPDATE serializes all seat
     * reservations for the same tenant until the subsequent user write commits.
     */
    public void reserveSeat(Long tenantId, boolean addingActiveStaff) {
        if (!enforce || !addingActiveStaff) return;
        if (tenantId == null) throw new LicenseLeaseVerifier.LicenseFailure("TENANT_LICENSE_MISSING");
        List<Long> locks = jdbc.queryForList(
                "SELECT tenant_id FROM rehab_tenant_license_binding WHERE tenant_id=? FOR UPDATE",
                Long.class, tenantId);
        if (locks.size() != 1) throw new LicenseLeaseVerifier.LicenseFailure("TENANT_LICENSE_MISSING");
        Map<String, Object> claims = requireTenant(tenantId);
        Object limitValue = claims.get("seatLimit");
        if (limitValue == null) return; // Explicitly signed null = unlimited
        long limit = ((Number) limitValue).longValue();
        Long active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM system_users WHERE tenant_id=? AND status=0 AND deleted=0",
                Long.class, tenantId);
        if (active == null || active >= limit) {
            throw new LicenseLeaseVerifier.LicenseFailure("STAFF_SEAT_LIMIT_REACHED");
        }
    }
}
