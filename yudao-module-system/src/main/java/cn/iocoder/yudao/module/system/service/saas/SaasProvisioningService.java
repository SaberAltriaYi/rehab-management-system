1: package cn.iocoder.yudao.module.system.service.saas;
2: 
3: import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
4: import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO;
5: import cn.iocoder.yudao.module.system.service.tenant.TenantService;
6: import org.springframework.jdbc.core.JdbcTemplate;
7: import org.springframework.stereotype.Service;
8: 
9: import java.sql.Timestamp;
10: import java.time.LocalDateTime;
11: import java.util.Collections;
12: import java.util.HashMap;
13: import java.util.List;
14: import java.util.Map;
15: import java.util.UUID;
16: import java.util.regex.Pattern;
17: 
18: /**
19:  * Idempotent handoff from the owner control plane to the main system.
20:  * The endpoint deliberately returns FAILED instead of rolling back the request row,
21:  * so an owner can retry a transient failure with the same idempotency key.
22:  */
23: @Service
24: public class SaasProvisioningService {
25:     private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");
26:     private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]{4,30}$");
27:     private final JdbcTemplate jdbc;
28:     private final TenantService tenants;
29: 
30:     public SaasProvisioningService(JdbcTemplate jdbc, TenantService tenants) {
31:         this.jdbc = jdbc;
32:         this.tenants = tenants;
33:     }
34: 
35:     public Map<String, Object> provision(SaasProvisioningRequest request) {
36:         validate(request);
37:         List<Map<String, Object>> existing = jdbc.queryForList(
38:                 "SELECT id,tenant_id,tenant_uuid,status,failure_reason FROM saas_provisioning_requests WHERE idempotency_key=?",
39:                 request.idempotencyKey);
40:         if (!existing.isEmpty()) {
41:             Map<String, Object> row = existing.get(0);
42:             String status = String.valueOf(row.get("status"));
43:             if ("ACTIVE".equals(status) || "PROVISIONING".equals(status)) return response(status, row);
44:             jdbc.update("UPDATE saas_provisioning_requests SET status='PROVISIONING',failure_reason=NULL WHERE id=?", row.get("id"));
45:         } else {
46:             jdbc.update("INSERT INTO saas_provisioning_requests(idempotency_key,tenant_uuid,organization_name,contact_name,contact_mobile,plan_id,package_id,status) VALUES (?,?,?,?,?,?,?,'PROVISIONING')",
47:                     request.idempotencyKey, request.tenantUuid, request.organizationName, request.contactName,
48:                     request.contactMobile, request.planId, request.packageId);
49:         }
50: 
51:         try {
52:             List<Map<String, Object>> byUuid = jdbc.queryForList("SELECT id FROM system_tenant WHERE tenant_uuid=? AND deleted=0", request.tenantUuid);
53:             Long tenantId;
54:             if (byUuid.isEmpty()) {
55:                 TenantSaveReqVO create = new TenantSaveReqVO();
56:                 create.setName(request.organizationName);
57:                 create.setContactName(request.contactName);
58:                 create.setContactMobile(request.contactMobile);
59:                 create.setStatus(0);
60:                 create.setPackageId(request.packageId);
61:                 create.setExpireTime(request.expiresAt);
62:                 create.setAccountCount(maxUsers(request.planId));
63:                 create.setUsername(request.adminUsername);
64:                 create.setPassword(request.adminPassword);
65:                 create.setWebsites(Collections.emptyList());
66:                 tenantId = TenantUtils.executeIgnore(() -> tenants.createTenant(create));
67:                 jdbc.update("UPDATE system_tenant SET tenant_uuid=?,registration_status='active',registration_source='owner-control-plane' WHERE id=?",
68:                         request.tenantUuid, tenantId);
69:             } else {
70:                 tenantId = ((Number) byUuid.get(0).get("id")).longValue();
71:             }
72:             jdbc.update("INSERT INTO tenant_subscriptions(tenant_id,plan_id,status,start_time,expire_time) VALUES (?,?,'active',CURRENT_TIMESTAMP,?) ON DUPLICATE KEY UPDATE plan_id=VALUES(plan_id),status='active',expire_time=VALUES(expire_time)",
73:                     tenantId, request.planId, Timestamp.valueOf(request.expiresAt));
74:             jdbc.update("INSERT INTO tenant_usage(tenant_id,user_count) VALUES (?,1) ON DUPLICATE KEY UPDATE tenant_id=VALUES(tenant_id)", tenantId);
75:             jdbc.update("UPDATE saas_provisioning_requests SET tenant_id=?,status='ACTIVE',failure_reason=NULL WHERE idempotency_key=?",
76:                     tenantId, request.idempotencyKey);
77:             return response("ACTIVE", map("tenant_id", tenantId, "tenant_uuid", request.tenantUuid));
78:         } catch (RuntimeException ex) {
79:             String reason = safeMessage(ex);
80:             jdbc.update("UPDATE saas_provisioning_requests SET status='FAILED',failure_reason=? WHERE idempotency_key=?", reason, request.idempotencyKey);
81:             return response("FAILED", map("tenant_uuid", request.tenantUuid, "failure_reason", reason));
82:         }
83:     }
84: 
85:     private int maxUsers(long planId) {
86:         List<Map<String, Object>> rows = jdbc.queryForList("SELECT max_users FROM saas_plans WHERE id=? AND status=1", planId);
87:         if (rows.isEmpty()) throw new IllegalArgumentException("套餐不可用");
88:         return ((Number) rows.get(0).get("max_users")).intValue();
89:     }
90: 
91:     private void validate(SaasProvisioningRequest request) {
92:         if (request == null || !UUID_PATTERN.matcher(value(request.idempotencyKey)).matches()
93:                 || !UUID_PATTERN.matcher(value(request.tenantUuid)).matches()) throw new IllegalArgumentException("幂等键或租户 UUID 不合法");
94:         if (blank(request.organizationName) || request.organizationName.length() > 120
95:                 || blank(request.contactName) || request.contactName.length() > 80) throw new IllegalArgumentException("机构信息不合法");
96:         if (request.planId < 1 || request.packageId < 1 || request.expiresAt == null || !request.expiresAt.isAfter(LocalDateTime.now())) throw new IllegalArgumentException("套餐或到期时间不合法");
97:         if (!USERNAME_PATTERN.matcher(value(request.adminUsername)).matches() || request.adminPassword == null || request.adminPassword.length() < 12 || request.adminPassword.length() > 72) throw new IllegalArgumentException("初始管理员凭据不合法");
98:     }
99: 
100:     private static String value(String value) { return value == null ? "" : value; }
101:     private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
102:     private static String safeMessage(RuntimeException ex) { String m = ex.getMessage(); return m == null ? ex.getClass().getSimpleName() : m.substring(0, Math.min(500, m.length())); }
103:     private static Map<String, Object> map(Object... values) { Map<String, Object> result = new HashMap<>(); for (int i = 0; i + 1 < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]); return result; }
104:     private static Map<String, Object> response(String status, Map<String, Object> row) { Map<String, Object> response = new HashMap<>(row); response.put("status", status); return response; }
105: }