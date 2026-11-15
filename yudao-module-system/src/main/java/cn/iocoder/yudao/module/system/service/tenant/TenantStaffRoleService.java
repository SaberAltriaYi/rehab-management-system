package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
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
            throw new IllegalArgumentException("仅机构管理员可以分配机构员工角色");
        }
        if (userId == null || userId <= 0 || userId.equals(operatorUserId)) {
            throw new IllegalArgumentException("不能为当前登录账号分配员工角色");
        }
        if (!TenantRolePermissionTemplate.isTenantAdminAssignableStaffRole(roleCode)) {
            throw new IllegalArgumentException("机构管理员目前仅可分配康复治疗师；临床审核人须经独立资质核验和授权");
        }

        TenantDO tenant = tenantService.getTenant(tenantId);
        if (tenant == null || userId.equals(tenant.getContactUserId())) {
            throw new IllegalArgumentException("不能修改机构主管理员的角色");
        }
        AdminUserDO user = userService.getUser(userId);
        if (user == null || !tenantId.equals(user.getTenantId())
                || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())) {
            throw new IllegalArgumentException("目标员工不存在、已停用或不属于当前机构");
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
            throw new IllegalArgumentException("目标员工已有非标准角色，需由系统拥有者单独处理");
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
