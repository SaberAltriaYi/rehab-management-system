package cn.iocoder.yudao.module.system.service.saas;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.Objects;
@Service
public class SaasTenantAdministrator {
    private final JdbcTemplate jdbc;
    public SaasTenantAdministrator(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public long requireCurrent() {
        LoginUser user=SecurityFrameworkUtils.getLoginUser();
        Long tenant=TenantContextHolder.getTenantId();
        if(user==null || tenant==null || tenant<=0 || TenantContextHolder.isIgnore()
                || !Integer.valueOf(2).equals(user.getUserType()) || !Objects.equals(tenant,user.getTenantId()))
            throw new AccessDeniedException("机构管理员身份验证失败");
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM system_user_role ur "
                + "JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id "
                + "JOIN system_users u ON u.id=ur.user_id AND u.tenant_id=ur.tenant_id "
                + "WHERE ur.tenant_id=? AND ur.user_id=? AND ur.deleted=0 AND r.deleted=0 "
                + "AND r.status=0 AND r.code='tenant_admin' AND u.deleted=0 AND u.status=0",
                Integer.class,tenant,user.getId());
        if(count==null || count<1) throw new AccessDeniedException("需要当前机构管理员权限");
        return user.getId();
    }
}
