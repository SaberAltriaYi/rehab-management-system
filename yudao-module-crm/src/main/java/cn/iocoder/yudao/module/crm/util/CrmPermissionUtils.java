package cn.iocoder.yudao.module.crm.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.crm.dal.dataobject.permission.CrmPermissionDO;
import cn.iocoder.yudao.module.crm.enums.common.CrmBizTypeEnum;
import cn.iocoder.yudao.module.crm.enums.common.CrmSceneTypeEnum;
import cn.iocoder.yudao.module.crm.enums.permission.CrmPermissionLevelEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.github.yulichang.autoconfigure.MybatisPlusJoinProperties;
import com.github.yulichang.wrapper.MPJLambdaWrapper;

import java.util.List;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 数据权限工具类
 *
 * @author HUIHUI
 */
public class CrmPermissionUtils {

    /**
     * 校验用户是否是 CRM 管理员
     *
     * @return 是/否
     */
    public static boolean isCrmAdmin() {
        PermissionCommonApi permissionApi = SpringUtil.getBean(PermissionCommonApi.class);
        return permissionApi.hasAnyRoles(getLoginUserId(), RoleCodeEnum.CRM_ADMIN.getCode(),
                RoleCodeEnum.SUPER_ADMIN.getCode());
    }

    /**
     * 构造 CRM 数据类型数据【分页】查询条件
     *
     * @param query     连表查询对象
     * @param bizType   数据类型 {@link CrmBizTypeEnum}
     * @param bizId     数据编号
     * @param userId    用户编号
     * @param sceneType 场景类型
     */
    public static <T extends MPJLambdaWrapper<?>, S> void appendPermissionCondition(T query, Integer bizType, SFunction<S, ?> bizId,
                                                                                    Long userId, Integer sceneType) {
        MybatisPlusJoinProperties mybatisPlusJoinProperties = SpringUtil.getBean(MybatisPlusJoinProperties.class);
        final String ownerUserIdField = mybatisPlusJoinProperties.getTableAlias() + ".owner_user_id";
        appendPrivateDepartmentCondition(query, bizType, userId);
        // sceneType 为空是“全部与我有关”，绝不能解释为“全库”。这也保护精简列表和导出。
        if (sceneType == null) {
            if (isCrmAdmin()) {
                return;
            }
            AdminUserRespDTO viewer = SpringUtil.getBean(AdminUserApi.class).getUser(userId);
            Long deptId = viewer == null ? null : viewer.getDeptId();
            query.and(q -> {
                q.eq(ownerUserIdField, userId)
                        .or().apply("EXISTS (SELECT 1 FROM crm_permission cp WHERE cp.biz_type = {0} "
                                    + "AND cp.biz_id = t.id AND cp.user_id = {1} AND cp.tenant_id = {2} "
                                    + "AND cp.deleted = 0 AND cp.level IN ({3}, {4}, {5}))",
                                bizType, userId, TenantContextHolder.getTenantId(),
                                CrmPermissionLevelEnum.OWNER.getLevel(), CrmPermissionLevelEnum.READ.getLevel(),
                                CrmPermissionLevelEnum.WRITE.getLevel());
                // 同部门成员能在旧 CRM 客户列表找到已归属本部门的客户；其他部门仍被外层边界拦截。
                if (CrmBizTypeEnum.CRM_CUSTOMER.getType().equals(bizType) && deptId != null) {
                    q.or().apply("EXISTS (SELECT 1 FROM crm_department_case dc WHERE dc.tenant_id={0} "
                                    + "AND dc.customer_id=t.id AND dc.dept_id={1})",
                            TenantContextHolder.getRequiredTenantId(), deptId);
                }
            });
            return;
        }
        // 场景一：我负责的数据
        if (CrmSceneTypeEnum.isOwner(sceneType)) {
            query.eq(ownerUserIdField, userId);
        }
        // 场景二：我参与的数据（我有读或写权限，并且不是负责人）
        if (CrmSceneTypeEnum.isInvolved(sceneType)) {
            if (CrmPermissionUtils.isCrmAdmin()) { // 特殊逻辑：如果是超管，直接查询所有，不过滤数据权限
                return;
            }
            query.innerJoin(CrmPermissionDO.class, on -> on.eq(CrmPermissionDO::getBizType, bizType)
                    .eq(CrmPermissionDO::getBizId, bizId)
                    .in(CrmPermissionDO::getLevel, CrmPermissionLevelEnum.READ.getLevel(), CrmPermissionLevelEnum.WRITE.getLevel())
                    .eq(CrmPermissionDO::getUserId,userId));
            query.ne(ownerUserIdField, userId);
        }
        // 场景三：下属负责的数据（下属是负责人）
        if (CrmSceneTypeEnum.isSubordinate(sceneType)) {
            AdminUserApi adminUserApi = SpringUtil.getBean(AdminUserApi.class);
            List<AdminUserRespDTO> subordinateUsers = adminUserApi.getUserListBySubordinate(userId);
            if (CollUtil.isEmpty(subordinateUsers)) {
                query.eq(ownerUserIdField, -1); // 不返回任何结果
            } else {
                query.in(ownerUserIdField, convertSet(subordinateUsers, AdminUserRespDTO::getId));
            }
        }
    }

    private static <T extends MPJLambdaWrapper<?>> void appendPrivateDepartmentCondition(T query, Integer bizType, Long userId) {
        if (isCrmAdmin()) {
            return;
        }
        String relation;
        if (CrmBizTypeEnum.CRM_CLUE.getType().equals(bizType)) {
            relation = "SELECT t.customer_id";
        } else if (CrmBizTypeEnum.CRM_CONTACT.getType().equals(bizType)
                || CrmBizTypeEnum.CRM_BUSINESS.getType().equals(bizType)
                || CrmBizTypeEnum.CRM_CONTRACT.getType().equals(bizType)) {
            relation = "SELECT t.customer_id";
        } else if (CrmBizTypeEnum.CRM_RECEIVABLE.getType().equals(bizType)
                || CrmBizTypeEnum.CRM_RECEIVABLE_PLAN.getType().equals(bizType)) {
            relation = "SELECT c.customer_id FROM crm_contract c WHERE c.id=t.contract_id AND c.tenant_id={0}";
        } else {
            return;
        }
        AdminUserRespDTO user = SpringUtil.getBean(AdminUserApi.class).getUser(userId);
        Long deptId = user == null || user.getDeptId() == null ? -1L : user.getDeptId();
        query.apply("NOT EXISTS (SELECT 1 FROM crm_department_case dc WHERE dc.tenant_id={0} "
                        + "AND dc.customer_id=(" + relation + ") AND dc.dept_id<>{1})",
                TenantContextHolder.getRequiredTenantId(), deptId);
    }

}
