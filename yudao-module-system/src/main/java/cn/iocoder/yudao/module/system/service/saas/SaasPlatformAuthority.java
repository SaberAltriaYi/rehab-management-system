package cn.iocoder.yudao.module.system.service.saas;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Platform privilege is explicit, never inferred from a tenant administrator role. */
@Service
public class SaasPlatformAuthority {
    private final JdbcTemplate jdbc;
    public SaasPlatformAuthority(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Long requireAdmin() {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || user.getId() == null || user.getTenantId() == null
                || !UserTypeEnum.ADMIN.getValue().equals(user.getUserType())) {
            throw new AccessDeniedException("平台管理员身份验证失败");
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM platform_admins a "
                + "JOIN system_users u ON u.id=a.user_id WHERE a.user_id=? AND a.status=1 "
                + "AND u.tenant_id=? AND u.status=0 AND u.deleted=0", Integer.class,
                user.getId(), user.getTenantId());
        if (count == null || count != 1) throw new AccessDeniedException("需要平台管理员权限");
        return user.getId();
    }
}
