package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Objects;

/** Platform administration is bound to the authenticated home tenant, never a request header. */
@Component("tenantOwnerAccess")
public class TenantOwnerAccess {

    @Resource
    @Lazy
    private TenantService tenantService;
    @Resource
    private PermissionService permissionService;

    public boolean isOwner() {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || user.getId() == null || user.getTenantId() == null
                || !Objects.equals(user.getUserType(), UserTypeEnum.ADMIN.getValue())) {
            return false;
        }
        TenantDO tenant = tenantService.getTenant(user.getTenantId());
        if (tenant == null || !Objects.equals(tenant.getPackageId(), TenantDO.PACKAGE_ID_SYSTEM)) {
            return false;
        }
        return TenantUtils.execute(user.getTenantId(), () -> permissionService.hasAnyRoles(
                user.getId(), "system_owner", "super_admin"));
    }
}
