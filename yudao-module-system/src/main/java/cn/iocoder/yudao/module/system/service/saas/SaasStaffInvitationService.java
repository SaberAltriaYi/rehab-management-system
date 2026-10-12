package cn.iocoder.yudao.module.system.service.saas;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service
public class SaasStaffInvitationService {
    private final JdbcTemplate jdbc;
    private final SaasTenantAdministrator administrator;
    private final SaasTenantAccessService access;
    private final AdminUserService users;
    private final PermissionService permissions;
    private static final SecureRandom RANDOM=new SecureRandom();
    public SaasStaffInvitationService(JdbcTemplate jdbc,SaasTenantAdministrator administrator,
            SaasTenantAccessService access,AdminUserService users,PermissionService permissions) {
        this.jdbc=jdbc;this.administrator=administrator;this.access=access;this.users=users;this.permissions=permissions;
    }
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> issue(String email) {
        long actor=administrator.requireCurrent(); long tenant=tenant(); access.requireCurrentTenant();
        if(email==null || email.length()>50 || (email.chars().anyMatch(Character::isWhitespace) || !email.matches("[^@]+@[^@]+[.][^@]+")))
            throw new IllegalArgumentException("邀请邮箱格式不正确");
        List<Long> roles=jdbc.queryForList("SELECT id FROM system_role WHERE tenant_id=? AND code='rehab_therapist' AND status=0 AND deleted=0",Long.class,tenant);
        if(roles.size()!=1) throw new IllegalStateException("请先为本机构配置唯一启用的 rehab_therapist 角色及康复权限");
        byte[] random=new byte[32];RANDOM.nextBytes(random);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        KeyHolder key=new GeneratedKeyHolder();
        jdbc.update(c->{java.sql.PreparedStatement s=c.prepareStatement("INSERT INTO saas_staff_invitations(tenant_id,role_id,recipient_email,token_hash,invited_by,expires_at) VALUES (?,?,?,?,?,TIMESTAMPADD(HOUR,72,CURRENT_TIMESTAMP))",new String[]{"id"});
            s.setLong(1,tenant);s.setLong(2,roles.get(0));s.setString(3,email.trim().toLowerCase(Locale.ROOT));
            s.setString(4,digest(tenant,token));s.setLong(5,actor);return s;},key);
        Map<String,Object> result=new LinkedHashMap<>(); result.put("id",Objects.requireNonNull(key.getKey()).longValue());
        result.put("tenantId",tenant);result.put("token",token);result.put("expiresInHours",72);
        return result; // The authenticated administrator must deliver this once via a trusted channel.
    }
    public List<Map<String,Object>> list(long afterId) {
        administrator.requireCurrent();access.requireCurrentTenant();
        return jdbc.queryForList("SELECT id,recipient_email,CASE WHEN status='pending' AND expires_at<=CURRENT_TIMESTAMP THEN 'expired' ELSE status END AS status,expires_at,accepted_user_id,created_at FROM saas_staff_invitations WHERE tenant_id=? AND id>? ORDER BY id LIMIT 100",tenant(),afterId);
    }
    @Transactional(rollbackFor=Exception.class)
    public void revoke(long id) {
        administrator.requireCurrent();access.requireCurrentTenant();
        if(jdbc.update("UPDATE saas_staff_invitations SET status='revoked' WHERE tenant_id=? AND id=? AND status='pending'",tenant(),id)!=1)
            throw new AccessDeniedException("邀请不存在或已经处理");
    }
    @Transactional(rollbackFor=Exception.class)
    public Long accept(String token,String username,String password,String nickname) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Invitation redemption requires a transaction");
        long tenant=tenant();String hash=digest(tenant,token);
        // Subscription mutex serializes invitation redemption seat reservations in this tenant.
        if(jdbc.queryForList("SELECT tenant_id FROM tenant_subscriptions WHERE tenant_id=? FOR UPDATE",Long.class,tenant).size()!=1)
            throw invalid();
        Map<String,Object> plan=access.requireCurrentTenant();
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,role_id,recipient_email FROM saas_staff_invitations WHERE tenant_id=? AND token_hash=? AND status='pending' AND expires_at>CURRENT_TIMESTAMP FOR UPDATE",tenant,hash);
        if(rows.size()!=1) throw invalid();Map<String,Object> invitation=rows.get(0);
        long role=((Number)invitation.get("role_id")).longValue();
        Integer validRole=jdbc.queryForObject("SELECT COUNT(*) FROM system_role WHERE tenant_id=? AND id=? AND code='rehab_therapist' AND status=0 AND deleted=0",Integer.class,tenant,role);
        if(!Integer.valueOf(1).equals(validRole)) throw invalid();
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM system_users WHERE tenant_id=? AND deleted=0",Long.class,tenant);
        Object limit=plan.get("max_users");
        if(count==null || !(limit instanceof Number) || count>=((Number)limit).longValue())
            throw new AccessDeniedException("机构账号配额不足");
        UserSaveReqVO req=new UserSaveReqVO();req.setUsername(username);req.setPassword(password);
        req.setNickname(nickname);req.setEmail((String)invitation.get("recipient_email"));
        Long user=users.createUser(req);
        permissions.assignUserRole(user,Collections.singleton(role));
        if(jdbc.update("UPDATE saas_staff_invitations SET status='accepted',accepted_user_id=?,accepted_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=? AND status='pending'",user,tenant,invitation.get("id"))!=1) throw invalid();
        if(jdbc.update("UPDATE tenant_usage SET user_count=(SELECT COUNT(*) FROM system_users WHERE tenant_id=? AND deleted=0) WHERE tenant_id=?",tenant,tenant)!=1) throw new IllegalStateException("Tenant usage ledger missing");
        return user;
    }
    private long tenant() {
        Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || TenantContextHolder.isIgnore()) throw invalid();return tenant;
    }
    static String digest(long tenant,String token) {
        if(tenant<=0 || token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(("rehab:staff:v1:"+tenant+":"+token).getBytes(StandardCharsets.US_ASCII));
            StringBuilder result=new StringBuilder();for(byte b:bytes) result.append(String.format("%02x",b&255));return result.toString();
        } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static AccessDeniedException invalid() {return new AccessDeniedException("邀请无效、已使用或已过期");}
}
