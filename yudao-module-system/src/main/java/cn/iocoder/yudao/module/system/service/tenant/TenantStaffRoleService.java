package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
import cn.iocoder.yudao.module.system.enums.permission.RoleTypeEnum;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.permission.RoleService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.mzt.logapi.starter.annotation.LogRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.system.enums.LogRecordConstants.SYSTEM_USER_ASSIGN_REHAB_ROLE_SUB_TYPE;
import static cn.iocoder.yudao.module.system.enums.LogRecordConstants.SYSTEM_USER_ASSIGN_REHAB_ROLE_SUCCESS;
import static cn.iocoder.yudao.module.system.enums.LogRecordConstants.SYSTEM_USER_TYPE;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.TENANT_NOT_EXISTS;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

/**
 * Narrow tenant-admin operation for assigning the prebuilt therapist template.
 * Clinical-reviewer assignment requires separate credential approval; the
 * generic platform role-assignment permission is not exposed to tenant admins.
 */
@Service
@Slf4j
public class TenantStaffRoleService {

    @Resource
    private AdminUserService userService;
    @Resource
    private TenantService tenantService;
    @Resource
    private RoleService roleService;
    @Resource
    private PermissionService permissionService;

    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = SYSTEM_USER_TYPE, subType = SYSTEM_USER_ASSIGN_REHAB_ROLE_SUB_TYPE,
            bizNo = "{{#userId}}", success = SYSTEM_USER_ASSIGN_REHAB_ROLE_SUCCESS)
    public void assignRole(Long userId, String roleCode) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long operatorUserId = SecurityFrameworkUtils.getLoginUserId();
        if (TenantContextHolder.isIgnore() || operatorUserId == null
                || !permissionService.hasAnyRoles(operatorUserId, RoleCodeEnum.TENANT_ADMIN.getCode())) {
            throw exception(FORBIDDEN);
        }
        if (userId == null || userId <= 0) {
            throw invalidParamException("员工用户编号无效");
        }
        if (userId.equals(operatorUserId)) {
            throw exception(FORBIDDEN);
        }
        if (!TenantRolePermissionTemplate.isTenantAdminAssignableStaffRole(roleCode)) {
            throw invalidParamException("机构管理员仅可分配康复治疗师角色");
        }

        TenantDO tenant = tenantService.getTenant(tenantId);
        if (tenant == null) {
            throw new ServiceException(TENANT_NOT_EXISTS);
        }
        if (userId.equals(tenant.getContactUserId())) {
            throw exception(FORBIDDEN);
        }
        AdminUserDO user = userService.getUser(userId);
        if (user == null) {
            // Tenant-scoped user lookup intentionally hides foreign users. Treat
            // every target outside the caller's visible tenant as forbidden so
            // this endpoint cannot be used to enumerate user IDs across tenants.
            throw exception(FORBIDDEN);
        }
        if (!CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())) {
            throw invalidParamException("目标员工不存在或已停用");
        }
        if (!tenantId.equals(user.getTenantId())) {
            // Do not reveal whether a user ID belongs to another tenant.
            throw exception(FORBIDDEN);
        }

        Set<Long> currentRoleIds = permissionService.getUserRoleIdListByUserId(userId);
        if (currentRoleIds == null) {
            currentRoleIds = Collections.emptySet();
        }
        List<RoleDO> currentRoles = roleService.getRoleList(currentRoleIds);
        if (currentRoles.size() != currentRoleIds.size()
                || currentRoles.stream().anyMatch(role -> role == null
                || !tenantId.equals(role.getTenantId())
                || !TenantRolePermissionTemplate.isTenantAdminAssignableStaffRole(role.getCode())
                || !RoleTypeEnum.SYSTEM.getType().equals(role.getType()))) {
            throw exception(FORBIDDEN);
        }

        List<RoleDO> targetRoles = roleService.getRoleList().stream()
                .filter(role -> roleCode.equals(role.getCode()))
                .filter(role -> tenantId.equals(role.getTenantId()))
                .filter(role -> RoleTypeEnum.SYSTEM.getType().equals(role.getType()))
                .filter(role -> CommonStatusEnum.ENABLE.getStatus().equals(role.getStatus()))
                .collect(java.util.stream.Collectors.toList());
        if (targetRoles.size() != 1) {
            throw new IllegalStateException("机构员工角色模板缺失或重复，拒绝分配");
        }

        // Replace only an existing therapist template assignment; do
        // not append roles, which could accidentally preserve broader access.
        permissionService.assignUserRole(userId, Collections.singleton(targetRoles.get(0).getId()));
        log.info("[assignTenantStaffRole] tenantId={} operatorUserId={} userId={} roleCode={}",
                tenantId, operatorUserId, userId, roleCode);
    }
}
