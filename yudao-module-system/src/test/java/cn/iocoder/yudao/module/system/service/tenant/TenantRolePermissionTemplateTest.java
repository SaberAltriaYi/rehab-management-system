package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.enums.permission.DataScopeEnum;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TenantRolePermissionTemplateTest {

    private final List<MenuDO> menus = buildMenus();
    private final Set<Long> packageMenuIds = menus.stream().map(MenuDO::getId).collect(Collectors.toSet());

    @Test
    void definitionsHaveThreeDistinctLeastPrivilegeRoles() {
        assertEquals(3, TenantRolePermissionTemplate.definitions().size());
        assertEquals(30, definition(RoleCodeEnum.TENANT_ADMIN.getCode()).getPermissions().size());
        assertEquals(44, definition(RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode()).getPermissions().size());
        assertEquals(16, definition(RoleCodeEnum.REHAB_CLINICAL_REVIEWER.getCode()).getPermissions().size());
        assertEquals(DataScopeEnum.ALL.getScope(), definition(RoleCodeEnum.TENANT_ADMIN.getCode()).getDataScope());
        assertEquals(DataScopeEnum.SELF.getScope(), definition(RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode()).getDataScope());
        assertEquals(DataScopeEnum.ALL.getScope(), definition(RoleCodeEnum.REHAB_CLINICAL_REVIEWER.getCode()).getDataScope());
        assertTrue(TenantRolePermissionTemplate.isTenantAdminAssignableStaffRole(RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode()));
        assertFalse(TenantRolePermissionTemplate.isTenantAdminAssignableStaffRole(RoleCodeEnum.REHAB_CLINICAL_REVIEWER.getCode()));
        assertFalse(TenantRolePermissionTemplate.isTenantAdminAssignableStaffRole(RoleCodeEnum.TENANT_ADMIN.getCode()));
        assertFalse(TenantRolePermissionTemplate.isTenantAdminAssignableStaffRole(RoleCodeEnum.SUPER_ADMIN.getCode()));
    }

    @Test
    void institutionAdminCannotInheritPlatformOrClinicalApprovalPrivileges() {
        Set<Long> ids = resolve(RoleCodeEnum.TENANT_ADMIN.getCode(), packageMenuIds);
        assertTrue(ids.containsAll(Arrays.asList(1L, 100L, 1001L, 1002L, 1003L, 9000L, 9002L, 9101L, 9104L)));
        assertFalse(ids.contains(1004L)); // delete user
        assertFalse(ids.contains(1005L)); // export user
        assertFalse(ids.contains(1008L)); // role query
        assertFalse(ids.contains(1065L)); // generic user-role assignment
        assertFalse(ids.contains(1140L)); // tenant create
        assertFalse(ids.contains(1227L)); // package create
        assertFalse(ids.contains(1033L)); // infra config update
        assertFalse(definition(RoleCodeEnum.TENANT_ADMIN.getCode()).getPermissions().contains("rehab:assignment:create"));
        assertFalse(definition(RoleCodeEnum.TENANT_ADMIN.getCode()).getPermissions().contains("rehab:assignment:update"));
        assertFalse(ids.contains(9305L)); // report approval
        assertFalse(ids.contains(9715L)); // motion signature
        assertFalse(ids.contains(9895L)); // simulated data governance
    }

    @Test
    void therapistGetsAssignedPatientClinicalWorkflowButNotInstitutionOrApprovalRights() {
        Set<Long> ids = resolve(RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode(), packageMenuIds);
        assertTrue(ids.containsAll(Arrays.asList(9000L, 9002L, 9003L, 9004L,
                9102L, 9104L, 9201L, 9202L, 9203L, 9410L, 9421L, 9711L, 9712L, 9713L)));
        assertFalse(ids.contains(9101L)); // patient creation is institution-admin only
        assertFalse(ids.contains(9105L)); // patient assignment is institution-admin only
        assertFalse(ids.contains(9103L)); // patient delete
        assertFalse(ids.contains(9304L)); // report review
        assertFalse(ids.contains(9305L)); // report approval
        assertFalse(ids.contains(9307L)); // report unlock
        assertFalse(ids.contains(9714L)); // motion review
        assertFalse(ids.contains(9715L)); // motion signature
        assertFalse(ids.contains(1002L)); // user create
        assertFalse(ids.contains(1065L)); // generic role assignment
    }

    @Test
    void clinicalReviewerGetsReviewAndSignatureButNoClinicalCreationOrAdministration() {
        Set<Long> ids = resolve(RoleCodeEnum.REHAB_CLINICAL_REVIEWER.getCode(), packageMenuIds);
        assertTrue(ids.containsAll(Arrays.asList(9000L, 9002L, 9003L, 9004L,
                9302L, 9303L, 9304L, 9305L, 9306L, 9308L, 9710L, 9714L, 9715L, 9717L)));
        assertFalse(ids.contains(9101L)); // patient create
        assertFalse(ids.contains(9201L)); // assessment create
        assertFalse(ids.contains(9410L)); // plan create
        assertFalse(ids.contains(9711L)); // motion create
        assertFalse(ids.contains(9713L)); // motion process
        assertFalse(ids.contains(9307L)); // report unlock remains super-admin only
        assertFalse(ids.contains(9716L)); // motion export
        assertFalse(ids.contains(1140L)); // tenant create
        assertFalse(ids.contains(1065L)); // generic role assignment
    }

    @Test
    void rolePermissionsNeverExceedPackageAndRequireCompleteEnabledMenuPath() {
        Set<Long> reducedPackage = new HashSet<>(packageMenuIds);
        reducedPackage.remove(9104L);
        reducedPackage.remove(9000L);
        Set<Long> ids = resolve(RoleCodeEnum.TENANT_ADMIN.getCode(), reducedPackage);
        assertFalse(ids.contains(9104L));
        assertFalse(ids.contains(9002L));
        assertFalse(ids.contains(9000L));
        assertTrue(reducedPackage.containsAll(ids));

        List<MenuDO> disabledMenus = buildMenus();
        disabledMenus.stream().filter(menu -> menu.getId().equals(9002L))
                .findFirst().get().setStatus(CommonStatusEnum.DISABLE.getStatus());
        Set<Long> disabled = TenantRolePermissionTemplate.resolveMenuIds(
                RoleCodeEnum.TENANT_ADMIN.getCode(), packageMenuIds, disabledMenus);
        assertFalse(disabled.contains(9002L));
        assertFalse(disabled.contains(9104L));
    }

    @Test
    void unknownRoleAndMenuOutsidePackageFailClosed() {
        assertTrue(TenantRolePermissionTemplate.resolveMenuIds("unknown", packageMenuIds, menus).isEmpty());
        assertTrue(TenantRolePermissionTemplate.resolveMenuIds(
                RoleCodeEnum.TENANT_ADMIN.getCode(), Collections.<Long>emptySet(), menus).isEmpty());
        assertTrue(TenantRolePermissionTemplate.resolveMenuIds(
                RoleCodeEnum.TENANT_ADMIN.getCode(), packageMenuIds, null).isEmpty());
    }

    private static TenantRolePermissionTemplate.Definition definition(String code) {
        return TenantRolePermissionTemplate.definition(code);
    }

    private static Set<Long> resolve(String roleCode, Set<Long> packageIds) {
        return TenantRolePermissionTemplate.resolveMenuIds(roleCode, packageIds, buildMenus());
    }

    private static List<MenuDO> buildMenus() {
        List<MenuDO> list = new ArrayList<>();
        add(list, 1L, 0L, 1, null);
        add(list, 2L, 0L, 1, null);
        add(list, 9000L, 0L, 1, null);
        add(list, 100L, 1L, 2, "system:user:list");
        add(list, 1001L, 100L, 3, "system:user:query");
        add(list, 1002L, 100L, 3, "system:user:create");
        add(list, 1003L, 100L, 3, "system:user:update");
        add(list, 1004L, 100L, 3, "system:user:delete");
        add(list, 1005L, 100L, 3, "system:user:export");
        add(list, 101L, 1L, 2, null);
        add(list, 1008L, 101L, 3, "system:role:query");
        add(list, 1065L, 101L, 3, "system:permission:assign-user-role");
        add(list, 1033L, 2L, 3, "infra:config:update");
        add(list, 1140L, 1L, 3, "system:tenant:create");
        add(list, 1227L, 1L, 3, "system:tenant-package:create");
        add(list, 9001L, 9000L, 2, "rehab:dashboard:view");
        add(list, 9002L, 9000L, 2, "rehab:patient:view");
        add(list, 9003L, 9000L, 2, "rehab:assessment:view");
        add(list, 9004L, 9000L, 2, "rehab:report:view");
        add(list, 9101L, 9002L, 3, "rehab:patient:create");
        add(list, 9102L, 9002L, 3, "rehab:patient:update");
        add(list, 9103L, 9002L, 3, "rehab:patient:delete");
        add(list, 9104L, 9002L, 3, "rehab:patient:detail");
        add(list, 9105L, 9002L, 3, "rehab:patient:assign");
        add(list, 9201L, 9003L, 3, "rehab:assessment:create");
        add(list, 9202L, 9003L, 3, "rehab:assessment:update");
        add(list, 9203L, 9003L, 3, "rehab:assessment:detail");
        add(list, 9302L, 9004L, 3, "rehab:report:detail");
        add(list, 9303L, 9004L, 3, "rehab:report:preview");
        add(list, 9304L, 9004L, 3, "rehab:report:review");
        add(list, 9305L, 9004L, 3, "rehab:report:approve");
        add(list, 9306L, 9004L, 3, "rehab:report:lock");
        add(list, 9307L, 9004L, 3, "rehab:report:unlock");
        add(list, 9308L, 9004L, 3, "rehab:report:version:view");
        add(list, 9400L, 9000L, 2, "rehab:plan:view");
        add(list, 9410L, 9400L, 3, "rehab:plan:create");
        add(list, 9421L, 9400L, 3, "rehab:task:create");
        add(list, 9700L, 9000L, 2, "rehab:motion:query");
        add(list, 9710L, 9700L, 3, "rehab:motion:query");
        add(list, 9711L, 9700L, 3, "rehab:motion:create");
        add(list, 9712L, 9700L, 3, "rehab:motion:upload");
        add(list, 9713L, 9700L, 3, "rehab:motion:process");
        add(list, 9714L, 9700L, 3, "rehab:motion:review");
        add(list, 9715L, 9700L, 3, "rehab:motion:sign");
        add(list, 9716L, 9700L, 3, "rehab:motion:export");
        add(list, 9717L, 9700L, 3, "rehab:motion:audit");
        add(list, 9718L, 2397L, 2, "rehab:motion:workspace"); // missing parent: excluded
        add(list, 9895L, 9002L, 3, "rehab:patient:data-governance");
        return list;
    }

    private static void add(List<MenuDO> list, Long id, Long parentId, Integer type, String permission) {
        MenuDO menu = new MenuDO();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setType(type);
        menu.setPermission(permission);
        menu.setStatus(CommonStatusEnum.ENABLE.getStatus());
        list.add(menu);
    }
}
