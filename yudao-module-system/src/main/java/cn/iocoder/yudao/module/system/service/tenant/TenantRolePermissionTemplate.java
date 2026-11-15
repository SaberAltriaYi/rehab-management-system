package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.enums.permission.DataScopeEnum;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;

import java.util.*;

/**
 * Least-privilege role templates for a tenant created through the Owner control plane.
 *
 * The tenant package is a feature ceiling, not a role grant. A role receives only
 * the explicitly listed permissions that are present in the tenant's enabled
 * package. Parent menu nodes are included only when the complete parent path is
 * also present in the package.
 */
public final class TenantRolePermissionTemplate {

    private static final List<Definition> DEFINITIONS = Collections.unmodifiableList(Arrays.asList(
            new Definition(RoleCodeEnum.TENANT_ADMIN.getCode(), "机构管理员", 0,
                    DataScopeEnum.ALL.getScope(), permissions(
                    "system:user:list", "system:user:query", "system:user:create", "system:user:update",
                    "system:dept:query", "system:post:query",
                    "rehab:dashboard:view",
                    "rehab:patient:view", "rehab:patient:create", "rehab:patient:update",
                    "rehab:patient:detail", "rehab:patient:assign", "rehab:patient:transfer",
                    "rehab:episode:view", "rehab:episode:create", "rehab:episode:update",
                    "rehab:assignment:view",
                    "rehab:report:view", "rehab:report:detail", "rehab:report:preview",
                    "rehab:report:version:view", "rehab:motion:query",
                    "rehab:notification:view", "rehab:notification:read", "rehab:notification:send",
                    "rehab:alert:view", "rehab:alert:acknowledge", "rehab:alert:resolve", "rehab:alert:ignore",
                    "rehab:ops-dashboard:view")),
            new Definition(RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode(), "康复治疗师", 10,
                    DataScopeEnum.SELF.getScope(), permissions(
                    "rehab:dashboard:view",
                    "rehab:patient:view", "rehab:patient:update", "rehab:patient:detail",
                    "rehab:episode:view", "rehab:episode:create", "rehab:episode:update",
                    "rehab:assessment:view", "rehab:assessment:create", "rehab:assessment:update",
                    "rehab:assessment:detail", "rehab:assessment:generate-report",
                    "rehab:report:view", "rehab:report:detail", "rehab:report:preview",
                    "rehab:plan:view", "rehab:plan:create", "rehab:plan:update", "rehab:plan:detail",
                    "rehab:plan:activate", "rehab:plan:pause", "rehab:plan:complete", "rehab:plan:copy",
                    "rehab:task:view", "rehab:task:create", "rehab:task:update", "rehab:task:sort", "rehab:task:disable",
                    "rehab:checkin:view", "rehab:checkin:detail",
                    "rehab:progress:view", "rehab:progress:detail",
                    "rehab:reassessment-trigger:view", "rehab:reassessment-trigger:create",
                    "rehab:reassessment-trigger:handle",
                    "rehab:notification:view", "rehab:notification:read",
                    "rehab:alert:view", "rehab:alert:acknowledge", "rehab:alert:resolve",
                    "rehab:motion:query", "rehab:motion:create", "rehab:motion:upload", "rehab:motion:process")),
            new Definition(RoleCodeEnum.REHAB_CLINICAL_REVIEWER.getCode(), "临床审核人", 20,
                    DataScopeEnum.ALL.getScope(), permissions(
                    "rehab:dashboard:view",
                    "rehab:patient:view", "rehab:patient:detail",
                    "rehab:assessment:view", "rehab:assessment:detail",
                    "rehab:report:view", "rehab:report:detail", "rehab:report:preview",
                    "rehab:report:review", "rehab:report:approve", "rehab:report:lock",
                    "rehab:report:version:view",
                    "rehab:motion:query", "rehab:motion:review", "rehab:motion:sign", "rehab:motion:audit"))
    ));

    private TenantRolePermissionTemplate() {
    }

    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    public static Definition definition(String roleCode) {
        for (Definition definition : DEFINITIONS) {
            if (definition.getCode().equals(roleCode)) {
                return definition;
            }
        }
        return null;
    }

    public static boolean isTemplateRole(String roleCode) {
        return definition(roleCode) != null;
    }

    /** Institution administrators may assign therapists; reviewer assignment requires separate credential approval. */
    public static boolean isTenantAdminAssignableStaffRole(String roleCode) {
        return RoleCodeEnum.TENANT_REHAB_THERAPIST.getCode().equals(roleCode);
    }

    /**
     * Resolve a role's permissions against the actual enabled menu tree and the
     * tenant's package entitlement. Unknown roles and incomplete menu paths fail
     * closed to an empty set / omitted branch.
     */
    public static Set<Long> resolveMenuIds(String roleCode, Set<Long> packageMenuIds, List<MenuDO> menus) {
        Definition definition = definition(roleCode);
        if (definition == null || packageMenuIds == null || packageMenuIds.isEmpty() || menus == null) {
            return Collections.emptySet();
        }

        Map<Long, MenuDO> menuById = new HashMap<>();
        for (MenuDO menu : menus) {
            if (menu != null && menu.getId() != null) {
                menuById.put(menu.getId(), menu);
            }
        }

        Set<Long> resolved = new LinkedHashSet<>();
        for (MenuDO menu : menus) {
            if (menu == null || menu.getId() == null || menu.getPermission() == null
                    || !definition.getPermissions().contains(menu.getPermission())
                    || !packageMenuIds.contains(menu.getId())
                    || !CommonStatusEnum.ENABLE.getStatus().equals(menu.getStatus())) {
                continue;
            }
            addMenuPath(menu.getId(), packageMenuIds, menuById, resolved, new HashSet<Long>());
        }
        return Collections.unmodifiableSet(resolved);
    }

    private static boolean addMenuPath(Long menuId, Set<Long> packageMenuIds,
                                       Map<Long, MenuDO> menuById, Set<Long> resolved,
                                       Set<Long> visiting) {
        if (menuId == null || !packageMenuIds.contains(menuId)) {
            return false;
        }
        if (resolved.contains(menuId)) {
            return true;
        }
        if (!visiting.add(menuId)) {
            return false; // malformed menu cycle: fail closed
        }
        MenuDO menu = menuById.get(menuId);
        if (menu == null || !CommonStatusEnum.ENABLE.getStatus().equals(menu.getStatus())) {
            return false;
        }
        Long parentId = menu.getParentId();
        if (parentId != null && !MenuDO.ID_ROOT.equals(parentId)
                && !addMenuPath(parentId, packageMenuIds, menuById, resolved, visiting)) {
            return false;
        }
        resolved.add(menuId);
        return true;
    }

    private static Set<String> permissions(String... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(values)));
    }

    public static final class Definition {
        private final String code;
        private final String name;
        private final int sort;
        private final Integer dataScope;
        private final Set<String> permissions;

        private Definition(String code, String name, int sort, Integer dataScope, Set<String> permissions) {
            this.code = code;
            this.name = name;
            this.sort = sort;
            this.dataScope = dataScope;
            this.permissions = permissions;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public int getSort() { return sort; }
        public Integer getDataScope() { return dataScope; }
        public Set<String> getPermissions() { return permissions; }
    }
}
