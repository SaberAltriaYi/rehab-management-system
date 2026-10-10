package cn.iocoder.yudao.module.system.framework.license;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only L4 tenant-scoped licensing + staff-seat summary.
 *
 * This service NEVER reads a seat limit from system_tenant.account_count or
 * from request parameters. It derives all licensed entitlements from L2's
 * owner-signed, issuer-pinned Java guard. Staff counts are tenant-constrained.
 */
@Service
public class StudioLicenseOverviewService {
    private final TenantLicenseGuard guard;
    private final JdbcTemplate jdbc;

    public StudioLicenseOverviewService(TenantLicenseGuard guard, JdbcTemplate jdbc) {
        this.guard = guard;
        this.jdbc = jdbc;
    }

    public Map<String, Object> readCurrentStudio() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId <= 0L) {
            throw new LicenseLeaseVerifier.LicenseFailure("TENANT_LICENSE_MISSING");
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("licenseEnforced", guard.isEnforced());
        if (!guard.isEnforced()) {
            // Existing development system cannot pretend to have a valid
            // paid entitlement or an unlimited employee seat allocation.
            response.put("status", "NOT_CONFIGURED");
            response.put("message", "当前环境未启用正式工作室许可证强制校验");
            return response;
        }
        // Server verifies the signed RHL1 + current RHR1 and tenant binding.
        // Any revoked/expired/invalid proof fails closed before the staff SQL.
        Map<String, Object> claims = guard.requireTenant(tenantId);
        Long active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM system_users WHERE tenant_id=? AND status=0 AND deleted=0",
                Long.class, tenantId);
        if (active == null || active < 0L) {
            throw new LicenseLeaseVerifier.LicenseFailure("STAFF_COUNT_UNAVAILABLE");
        }
        Object seat = claims.get("seatLimit");
        Long limit = seat == null ? null : ((Number) seat).longValue();
        if (limit != null && (limit < 1L || limit > 2147483647L)) {
            throw new LicenseLeaseVerifier.LicenseFailure("INVALID_SEAT_LIMIT");
        }
        response.put("status", "offline".equals(claims.get("mode")) ? "GRACE" : "ACTIVE");
        response.put("licenseEnforced", true);
        response.put("tenantId", claims.get("tenantId"));
        response.put("installationId", claims.get("installationId"));
        response.put("seatLimit", limit);  // null = explicitly signed unlimited
        response.put("activeStaff", active);
        response.put("availableSeats", limit == null ? null : Math.max(0L, limit - active));
        response.put("maxInstallations", claims.get("maxInstallations"));
        response.put("modules", claims.get("modules"));
        response.put("expiresAt", claims.get("expiresAt"));
        response.put("entitlementExpiresAt", claims.get("entitlementExpiresAt"));
        response.put("lastSignedIssueAt", claims.get("issuedAt"));
        return response;
    }
}
