package cn.iocoder.yudao.module.system.service.saas;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

/**
 * SaaS entitlement foundation. Does not disable or replace Enterprise guards.
 * Callers must authenticate first. Tenant identity is read from the verified context,
 * never from a request body. Wiring into HTTP/job/upload paths is a separate release gate.
 */
@Service
public class SaasTenantAccessService {
    private final JdbcTemplate jdbc;

    public SaasTenantAccessService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> requireCurrentTenant() {
        Long tenantId = currentTenant();
        // JdbcTemplate bypasses MyBatis tenant interception: every tenant query is explicitly scoped.
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT s.tenant_id,s.plan_id,CASE WHEN t.registration_source='owner-control-plane' THEN t.account_count ELSE p.max_users END AS max_users,p.max_patients,p.motion_quota,p.storage_quota "
                + "FROM tenant_subscriptions s JOIN system_tenant t ON t.id=s.tenant_id "
                + "JOIN saas_plans p ON p.id=s.plan_id WHERE s.tenant_id=? "
                + "AND t.deleted=0 AND t.status=0 AND t.registration_status='active' "
                + "AND t.expire_time>CURRENT_TIMESTAMP AND p.status=1 "
                + "AND s.status IN ('trialing','active') AND s.start_time<=CURRENT_TIMESTAMP "
                + "AND (s.expire_time IS NULL OR s.expire_time>CURRENT_TIMESTAMP)", tenantId);
        if (rows.size() != 1) throw new AccessDenied("SAAS_TENANT_NOT_ACTIVE");
        return rows.get(0);
    }

    /** Must participate in the same transaction as the corresponding business write. */
    public void reserve(Resource resource, long amount) {
        if (resource == null || amount <= 0) throw new IllegalArgumentException("Invalid quota reservation");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Quota reservation requires a business transaction");
        }
        Long tenantId = currentTenant();
        // The subscription row is the shared per-tenant mutex. All future lifecycle/plan writers
        // must take this same lock before changing the subscription.
        List<Long> locked = jdbc.queryForList(
                "SELECT tenant_id FROM tenant_subscriptions WHERE tenant_id=? FOR UPDATE", Long.class, tenantId);
        if (locked.size() != 1) throw new AccessDenied("SAAS_SUBSCRIPTION_MISSING");
        Map<String, Object> plan = requireCurrentTenant();
        Object raw = plan.get(resource.limit);
        if (!(raw instanceof Number) || ((Number) raw).longValue() < 0) {
            throw new AccessDenied("SAAS_INVALID_QUOTA");
        }
        long limit = ((Number) raw).longValue();
        if (amount > limit) throw new AccessDenied("SAAS_QUOTA_EXCEEDED");
        // Identifiers come only from the closed enum; user input is never interpolated.
        int updated = jdbc.update("UPDATE tenant_usage SET " + resource.column + "=" + resource.column
                + "+? WHERE tenant_id=? AND " + resource.column + ">=0 AND " + resource.column + "<=?",
                amount, tenantId, limit - amount);
        if (updated != 1) throw new AccessDenied("SAAS_QUOTA_EXCEEDED_OR_USAGE_MISSING");
    }

    private Long currentTenant() {
        Long id = TenantContextHolder.getTenantId();
        if (id == null || id <= 0 || TenantContextHolder.isIgnore()) {
            throw new AccessDenied("SAAS_TENANT_CONTEXT_REQUIRED");
        }
        return id;
    }

    public enum Resource {
        USER("user_count", "max_users"), PATIENT("patient_count", "max_patients"),
        MOTION("motion_count", "motion_quota"), STORAGE("storage_bytes", "storage_quota");
        final String column;
        final String limit;
        Resource(String column, String limit) { this.column = column; this.limit = limit; }
    }

    public static class AccessDenied extends RuntimeException {
        public AccessDenied(String code) { super(code); }
    }
}
