package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
import cn.iocoder.yudao.module.system.enums.permission.RoleTypeEnum;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.permission.RoleService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;

@ExtendWith(MockitoExtension.class)
class TenantStaffRoleServiceTest {

    @InjectMocks
    private TenantStaffRoleService service;
    @Mock
    private AdminUserService userService;
    @Mock
    private TenantService tenantService;
    @Mock
    private RoleService roleService;
    @Mock
    private PermissionService permissionService;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void tenantAdminCanAssignOnlyOneStandardStaffTemplateWithinSameTenant() {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            prepareAdminContext(security);
            TenantDO tenant = new TenantDO();
            tenant.setContactUserId(10L);
            when(tenantService.getTenant(100L)).thenReturn(tenant);
            AdminUserDO user = new AdminUserDO();
            user.setId(20L);
            user.setTenantId(100L);
            user.setStatus(CommonStatusEnum.ENABLE.getStatus());
            when(userService.getUser(20L)).thenReturn(user);
            when(permissionService.getUserRoleIdListByUserId(20L)).thenReturn(Collections.emptySet());
            when(roleService.getRoleList(Collections.<Long>emptySet())).thenReturn(Collections.<RoleDO>emptyList());
            RoleDO therapist = role(301L, 100L, RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode());
            RoleDO reviewer = role(302L, 100L, RoleCodeEnum.REHAB_CLINICAL_REVIEWER.getCode());
            when(roleService.getRoleList()).thenReturn(Arrays.asList(therapist, reviewer));

            service.assignRole(20L, RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode());

            verify(permissionService).assignUserRole(20L, Collections.singleton(301L));
        }
    }

    @Test
    void cannotAssignTenantAdminOrPlatformRoleThroughStaffEndpoint() {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            prepareAdminContext(security);
            assertErrorCode(BAD_REQUEST.getCode(),
                    () -> service.assignRole(20L, RoleCodeEnum.TENANT_ADMIN.getCode()));
            assertErrorCode(BAD_REQUEST.getCode(),
                    () -> service.assignRole(20L, RoleCodeEnum.SUPER_ADMIN.getCode()));
            assertErrorCode(BAD_REQUEST.getCode(),
                    () -> service.assignRole(20L, RoleCodeEnum.REHAB_CLINICAL_REVIEWER.getCode()));
            assertErrorCode(BAD_REQUEST.getCode(),
                    () -> service.assignRole(20L, RoleCodeEnum.REHAB_THERAPIST.getCode()));
            verifyNoInteractions(tenantService, userService, roleService);
            verify(permissionService, never()).assignUserRole(anyLong(), anySet());
        }
    }

    @Test
    void nonAdminCannotAssignStaffRole() {
        TenantContextHolder.setTenantId(100L);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(11L);
            when(permissionService.hasAnyRoles(11L, RoleCodeEnum.TENANT_ADMIN.getCode())).thenReturn(false);
            assertErrorCode(FORBIDDEN.getCode(),
                    () -> service.assignRole(20L, RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode()));
            verifyNoInteractions(tenantService, userService, roleService);
        }
    }

    @Test
    void cannotAssignSelfOrInstitutionOwner() {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            prepareAdminContext(security);
            assertErrorCode(FORBIDDEN.getCode(),
                    () -> service.assignRole(10L, RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode()));
            TenantDO tenant = new TenantDO();
            tenant.setContactUserId(20L);
            when(tenantService.getTenant(100L)).thenReturn(tenant);
            assertErrorCode(FORBIDDEN.getCode(),
                    () -> service.assignRole(20L, RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode()));
            verifyNoInteractions(userService, roleService);
        }
    }

    @Test
    void crossTenantUserOrExistingPrivilegedRoleIsRejected() {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            prepareAdminContext(security);
            TenantDO tenant = new TenantDO();
            tenant.setContactUserId(10L);
            when(tenantService.getTenant(100L)).thenReturn(tenant);
            AdminUserDO foreignUser = new AdminUserDO();
            foreignUser.setId(20L);
            foreignUser.setTenantId(200L);
            foreignUser.setStatus(CommonStatusEnum.ENABLE.getStatus());
            when(userService.getUser(20L)).thenReturn(foreignUser);
            assertErrorCode(FORBIDDEN.getCode(),
                    () -> service.assignRole(20L, RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode()));

            AdminUserDO localUser = new AdminUserDO();
            localUser.setId(21L);
            localUser.setTenantId(100L);
            localUser.setStatus(CommonStatusEnum.ENABLE.getStatus());
            when(userService.getUser(21L)).thenReturn(localUser);
            when(permissionService.getUserRoleIdListByUserId(21L)).thenReturn(Collections.singleton(401L));
            RoleDO adminRole = role(401L, 100L, RoleCodeEnum.TENANT_ADMIN.getCode());
            when(roleService.getRoleList(Collections.singleton(401L))).thenReturn(Collections.singletonList(adminRole));
            assertErrorCode(FORBIDDEN.getCode(),
                    () -> service.assignRole(21L, RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode()));
            verify(permissionService, never()).assignUserRole(anyLong(), anySet());
        }
    }

    @Test
    void existingCrossTenantRoleIsRejectedEvenWhenRoleCodeMatchesTemplate() {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            prepareAdminContext(security);
            TenantDO tenant = new TenantDO();
            tenant.setContactUserId(10L);
            when(tenantService.getTenant(100L)).thenReturn(tenant);
            AdminUserDO user = new AdminUserDO();
            user.setId(21L);
            user.setTenantId(100L);
            user.setStatus(CommonStatusEnum.ENABLE.getStatus());
            when(userService.getUser(21L)).thenReturn(user);
            when(permissionService.getUserRoleIdListByUserId(21L)).thenReturn(Collections.singleton(402L));
            when(roleService.getRoleList(Collections.singleton(402L)))
                    .thenReturn(Collections.singletonList(role(402L, 200L, RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode())));
            assertErrorCode(FORBIDDEN.getCode(),
                    () -> service.assignRole(21L, RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode()));
            verify(roleService, never()).getRoleList();
            verify(permissionService, never()).assignUserRole(anyLong(), anySet());
        }
    }

    @Test
    void duplicateTenantTherapistTemplatesFailClosedWithoutRoleAssignment() {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            prepareAdminContext(security);
            TenantDO tenant = new TenantDO();
            tenant.setContactUserId(10L);
            when(tenantService.getTenant(100L)).thenReturn(tenant);
            AdminUserDO user = new AdminUserDO();
            user.setId(21L);
            user.setTenantId(100L);
            user.setStatus(CommonStatusEnum.ENABLE.getStatus());
            when(userService.getUser(21L)).thenReturn(user);
            when(permissionService.getUserRoleIdListByUserId(21L)).thenReturn(Collections.emptySet());
            when(roleService.getRoleList(Collections.<Long>emptySet())).thenReturn(Collections.emptyList());
            when(roleService.getRoleList()).thenReturn(Arrays.asList(
                    role(301L, 100L, RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode()),
                    role(302L, 100L, RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode())));
            assertThrows(IllegalStateException.class,
                    () -> service.assignRole(21L, RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode()));
            verify(permissionService, never()).assignUserRole(anyLong(), anySet());
        }
    }

    @Test
    void invalidTargetIdsUseBadRequestAndNeverReachRoleMutation() {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            prepareAdminContext(security);
            assertErrorCode(BAD_REQUEST.getCode(),
                    () -> service.assignRole(null, RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode()));
            assertErrorCode(BAD_REQUEST.getCode(),
                    () -> service.assignRole(0L, RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode()));
            assertErrorCode(BAD_REQUEST.getCode(),
                    () -> service.assignRole(-1L, RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode()));
            verifyNoInteractions(tenantService, userService, roleService);
            verify(permissionService, never()).assignUserRole(anyLong(), anySet());
        }
    }

    @Test
    void disabledOrMissingTargetUsesBadRequestWithoutRoleMutation() {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            prepareAdminContext(security);
            TenantDO tenant = new TenantDO();
            tenant.setContactUserId(10L);
            when(tenantService.getTenant(100L)).thenReturn(tenant);
            assertErrorCode(BAD_REQUEST.getCode(),
                    () -> service.assignRole(20L, RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode()));

            AdminUserDO disabled = new AdminUserDO();
            disabled.setId(21L);
            disabled.setTenantId(100L);
            disabled.setStatus(CommonStatusEnum.DISABLE.getStatus());
            when(userService.getUser(21L)).thenReturn(disabled);
            assertErrorCode(BAD_REQUEST.getCode(),
                    () -> service.assignRole(21L, RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode()));
            verify(permissionService, never()).assignUserRole(anyLong(), anySet());
        }
    }

    private static void assertErrorCode(Integer expectedCode, org.junit.jupiter.api.function.Executable action) {
        ServiceException exception = assertThrows(ServiceException.class, action);
        assertEquals(expectedCode, exception.getCode());
    }

    private void prepareAdminContext(MockedStatic<SecurityFrameworkUtils> security) {
        TenantContextHolder.setTenantId(100L);
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
        when(permissionService.hasAnyRoles(10L, RoleCodeEnum.TENANT_ADMIN.getCode())).thenReturn(true);
    }

    private static RoleDO role(Long id, Long tenantId, String code) {
        RoleDO role = new RoleDO();
        role.setId(id);
        role.setTenantId(tenantId);
        role.setCode(code);
        role.setType(RoleTypeEnum.SYSTEM.getType());
        role.setStatus(CommonStatusEnum.ENABLE.getStatus());
        return role;
    }
}
