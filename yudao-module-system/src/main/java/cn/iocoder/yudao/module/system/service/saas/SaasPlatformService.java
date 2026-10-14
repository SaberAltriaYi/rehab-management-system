package cn.iocoder.yudao.module.system.service.saas;

import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.*;

/** Control-plane operations have explicit authority checks, row locks and durable audit. */
@Service
public class SaasPlatformService {
    private final JdbcTemplate jdbc;
    private final SaasPlatformAuthority authority;
    private final TenantService tenants;
    public SaasPlatformService(JdbcTemplate jdbc, SaasPlatformAuthority authority, TenantService tenants) {
        this.jdbc=jdbc; this.authority=authority; this.tenants=tenants;
    }
    public String register(String organization, String contact, String email) {
        String requestId=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO saas_registrations(request_id,organization_name,contact_name,contact_email) VALUES (?,?,?,?)",
                requestId, organization.trim(), contact.trim(), email.trim());
        return requestId;
    }
    public List<Map<String,Object>> registrations(long afterId) {
        authority.requireAdmin();
        return jdbc.queryForList("SELECT id,request_id,organization_name,contact_name,contact_email,status,tenant_id,created_at "
                + "FROM saas_registrations WHERE id>? ORDER BY id LIMIT 100", afterId);
    }
    public List<Map<String,Object>> tenants(long afterId) {
        authority.requireAdmin();
        return jdbc.queryForList("SELECT t.id,t.name,t.registration_status,s.plan_id,s.status AS subscription_status,s.expire_time,"
                + "u.user_count,u.patient_count,u.motion_count,u.storage_bytes FROM system_tenant t "
                + "LEFT JOIN tenant_subscriptions s ON s.tenant_id=t.id LEFT JOIN tenant_usage u ON u.tenant_id=t.id "
                + "WHERE t.id>? AND t.deleted=0 ORDER BY t.id LIMIT 100", afterId);
    }
    public List<Map<String,Object>> plans() {
        authority.requireAdmin();
        return jdbc.queryForList("SELECT id,name,max_users,max_patients,motion_quota,storage_quota,status FROM saas_plans ORDER BY id LIMIT 100");
    }
    @DSTransactional
    public Long approve(long registrationId, long planId, long packageId, String username, String password, LocalDateTime expires) {
        Long actor=authority.requireAdmin();
        if (packageId <= 0 || expires == null || !expires.isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("必须选择非系统权限套餐和未来的到期时间");
        List<Map<String,Object>> applications=jdbc.queryForList(
                "SELECT organization_name,contact_name,status,tenant_id FROM saas_registrations WHERE id=? FOR UPDATE",registrationId);
        if (applications.size()!=1) throw new IllegalArgumentException("注册申请不存在");
        Map<String,Object> app=applications.get(0);
        if (!"pending".equals(app.get("status"))) throw new IllegalArgumentException("申请已处理，不能重复审批");
        List<Map<String,Object>> plans=jdbc.queryForList("SELECT max_users FROM saas_plans WHERE id=? AND status=1 FOR UPDATE",planId);
        if (plans.size()!=1 || ((Number)plans.get(0).get("max_users")).intValue()<1)
            throw new IllegalArgumentException("套餐不可用");
        TenantSaveReqVO req=new TenantSaveReqVO();
        req.setName((String)app.get("organization_name")); req.setContactName((String)app.get("contact_name"));
        req.setStatus(0); req.setPackageId(packageId); req.setExpireTime(expires);
        req.setAccountCount(((Number)plans.get(0).get("max_users")).intValue());
        req.setUsername(username); req.setPassword(password); req.setWebsites(Collections.emptyList());
        Long tenantId=TenantUtils.executeIgnore(() -> tenants.createTenant(req));
        jdbc.update("UPDATE system_tenant SET registration_status='active',registration_source='self-service' WHERE id=?",tenantId);
        jdbc.update("INSERT INTO tenant_subscriptions(tenant_id,plan_id,status,start_time,expire_time) VALUES (?,?,'active',CURRENT_TIMESTAMP,?)",
                tenantId,planId,Timestamp.valueOf(expires));
        jdbc.update("INSERT INTO tenant_usage(tenant_id,user_count) VALUES (?,1)",tenantId);
        jdbc.update("UPDATE saas_registrations SET status='approved',tenant_id=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=?",
                tenantId,actor,registrationId);
        audit(actor,"registration.approved",tenantId);
        return tenantId;
    }
    @DSTransactional
    public void reject(long id) {
        Long actor=authority.requireAdmin();
        if (jdbc.update("UPDATE saas_registrations SET status='rejected',reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=? AND status='pending'",actor,id)!=1)
            throw new IllegalArgumentException("申请不存在或已处理");
        audit(actor,"registration.rejected",id);
    }
    @DSTransactional
    public void suspend(long tenantId) {
        Long actor=authority.requireAdmin();
        List<Long> locks=jdbc.queryForList("SELECT tenant_id FROM tenant_subscriptions WHERE tenant_id=? FOR UPDATE",Long.class,tenantId);
        if(locks.size()!=1) throw new IllegalArgumentException("订阅不存在");
        jdbc.update("UPDATE tenant_subscriptions SET status='suspended' WHERE tenant_id=?",tenantId);
        jdbc.update("UPDATE system_tenant SET status=1,registration_status='suspended' WHERE id=?",tenantId);
        audit(actor,"tenant.suspended",tenantId);
    }
    @DSTransactional
    public void renew(long tenantId, LocalDateTime expires) {
        Long actor=authority.requireAdmin();
        if (expires==null || !expires.isAfter(LocalDateTime.now())) throw new IllegalArgumentException("到期时间必须在未来");
        List<Long> locks=jdbc.queryForList("SELECT tenant_id FROM tenant_subscriptions WHERE tenant_id=? AND status<>'cancelled' FOR UPDATE",Long.class,tenantId);
        if(locks.size()!=1) throw new IllegalArgumentException("订阅不存在或已取消");
        if(jdbc.update("UPDATE system_tenant SET status=0,registration_status='active',expire_time=? WHERE id=? AND deleted=0 AND registration_status IN ('active','suspended','expired')",Timestamp.valueOf(expires),tenantId)!=1)
            throw new IllegalArgumentException("机构不可恢复");
        jdbc.update("UPDATE tenant_subscriptions SET status='active',expire_time=? WHERE tenant_id=?",Timestamp.valueOf(expires),tenantId);
        audit(actor,"subscription.renewed",tenantId);
    }
    @DSTransactional
    public long createPlan(String name, int maxUsers, int maxPatients, long motionQuota, long storageQuota) {
        Long actor=authority.requireAdmin();
        if(name==null || name.trim().isEmpty() || name.length()>64 || maxUsers<1 || maxPatients<0 || motionQuota<0 || storageQuota<0)
            throw new IllegalArgumentException("套餐参数不合法");
        org.springframework.jdbc.support.KeyHolder key=new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            java.sql.PreparedStatement statement=connection.prepareStatement(
                    "INSERT INTO saas_plans(name,max_users,max_patients,motion_quota,storage_quota) VALUES (?,?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1,name.trim());statement.setInt(2,maxUsers);statement.setInt(3,maxPatients);
            statement.setLong(4,motionQuota);statement.setLong(5,storageQuota);return statement;
        },key);
        long id=Objects.requireNonNull(key.getKey()).longValue();
        audit(actor,"plan.created",id);
        return id;
    }
    private void audit(Long actor,String action,long target) {
        jdbc.update("INSERT INTO saas_platform_audit(actor_user_id,action,target_id) VALUES (?,?,?)",actor,action,target);
    }
}
