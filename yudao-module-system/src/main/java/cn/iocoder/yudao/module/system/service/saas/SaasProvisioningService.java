package cn.iocoder.yudao.module.system.service.saas;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Idempotent handoff from the owner control plane to the main system.
 * The endpoint deliberately returns FAILED instead of rolling back the request row,
 * so an owner can retry a transient failure with the same idempotency key.
 */
@Service
public class SaasProvisioningService {
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]{4,30}$");
    private final JdbcTemplate jdbc;
    private final TenantService tenants;

    public SaasProvisioningService(JdbcTemplate jdbc, TenantService tenants) {
        this.jdbc = jdbc;
        this.tenants = tenants;
    }

    public Map<String, Object> provision(SaasProvisioningRequest request) {
        validate(request);
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT id,tenant_id,tenant_uuid,status,failure_reason FROM saas_provisioning_requests WHERE idempotency_key=?",
                request.idempotencyKey);
        if (!existing.isEmpty()) {
            Map<String, Object> row = existing.get(0);
            String status = String.valueOf(row.get("status"));
            if ("ACTIVE".equals(status) || "PROVISIONING".equals(status)) return response(status, row);
            jdbc.update("UPDATE saas_provisioning_requests SET status='PROVISIONING',failure_reason=NULL WHERE id=?", row.get("id"));
        } else {
            jdbc.update("INSERT INTO saas_provisioning_requests(idempotency_key,tenant_uuid,organization_name,contact_name,contact_mobile,plan_id,package_id,status) VALUES (?,?,?,?,?,?,?,'PROVISIONING')",
                    request.idempotencyKey, request.tenantUuid, request.organizationName, request.contactName,
                    request.contactMobile, request.planId, request.packageId);
        }

        try {
            List<Map<String, Object>> byUuid = jdbc.queryForList("SELECT id FROM system_tenant WHERE tenant_uuid=? AND deleted=0", request.tenantUuid);
            Long tenantId;
            if (byUuid.isEmpty()) {
                TenantSaveReqVO create = new TenantSaveReqVO();
                create.setName(request.organizationName);
                create.setContactName(request.contactName);
                create.setContactMobile(request.contactMobile);
                create.setStatus(0);
                create.setPackageId(request.packageId);
                create.setExpireTime(request.expiresAt);
                create.setAccountCount(maxUsers(request.planId));
                create.setUsername(request.adminUsername);
                create.setPassword(request.adminPassword);
                create.setWebsites(Collections.emptyList());
                tenantId = TenantUtils.executeIgnore(() -> tenants.createTenant(create));
                jdbc.update("UPDATE system_tenant SET tenant_uuid=?,registration_status='active',registration_source='owner-control-plane' WHERE id=?",
                        request.tenantUuid, tenantId);
            } else {
                tenantId = ((Number) byUuid.get(0).get("id")).longValue();
            }
            jdbc.update("INSERT INTO tenant_subscriptions(tenant_id,plan_id,status,start_time,expire_time) VALUES (?,?,'active',CURRENT_TIMESTAMP,?) ON DUPLICATE KEY UPDATE plan_id=VALUES(plan_id),status='active',expire_time=VALUES(expire_time)",
                    tenantId, request.planId, Timestamp.valueOf(request.expiresAt));
            jdbc.update("INSERT INTO tenant_usage(tenant_id,user_count) VALUES (?,1) ON DUPLICATE KEY UPDATE tenant_id=VALUES(tenant_id)", tenantId);
            jdbc.update("UPDATE saas_provisioning_requests SET tenant_id=?,status='ACTIVE',failure_reason=NULL WHERE idempotency_key=?",
                    tenantId, request.idempotencyKey);
            return response("ACTIVE", map("tenant_id", tenantId, "tenant_uuid", request.tenantUuid));
        } catch (RuntimeException ex) {
            String reason = safeMessage(ex);
            jdbc.update("UPDATE saas_provisioning_requests SET status='FAILED',failure_reason=? WHERE idempotency_key=?", reason, request.idempotencyKey);
            return response("FAILED", map("tenant_uuid", request.tenantUuid, "failure_reason", reason));
        }
    }

    private int maxUsers(long planId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT max_users FROM saas_plans WHERE id=? AND status=1", planId);
        if (rows.isEmpty()) throw new IllegalArgumentException("套餐不可用");
        return ((Number) rows.get(0).get("max_users")).intValue();
    }

    private void validate(SaasProvisioningRequest request) {
        if (request == null || !UUID_PATTERN.matcher(value(request.idempotencyKey)).matches()
                || !UUID_PATTERN.matcher(value(request.tenantUuid)).matches()) throw new IllegalArgumentException("幂等键或租户 UUID 不合法");
        if (blank(request.organizationName) || request.organizationName.length() > 120
                || blank(request.contactName) || request.contactName.length() > 80) throw new IllegalArgumentException("机构信息不合法");
        if (request.planId < 1 || request.packageId < 1 || request.expiresAt == null || !request.expiresAt.isAfter(LocalDateTime.now())) throw new IllegalArgumentException("套餐或到期时间不合法");
        if (!USERNAME_PATTERN.matcher(value(request.adminUsername)).matches() || request.adminPassword == null || request.adminPassword.length() < 12 || request.adminPassword.length() > 72) throw new IllegalArgumentException("初始管理员凭据不合法");
    }

    private static String value(String value) { return value == null ? "" : value; }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static String safeMessage(RuntimeException ex) { String m = ex.getMessage(); return m == null ? ex.getClass().getSimpleName() : m.substring(0, Math.min(500, m.length())); }
    private static Map<String, Object> map(Object... values) { Map<String, Object> result = new HashMap<>(); for (int i = 0; i + 1 < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]); return result; }
    private static Map<String, Object> response(String status, Map<String, Object> row) { Map<String, Object> response = new HashMap<>(row); response.put("status", status); return response; }
}
