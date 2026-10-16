package cn.iocoder.yudao.module.rehab.service;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assignment.RehabTherapistAssignmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.assignment.RehabTherapistAssignmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.binding.RehabPatientUserBindingMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabRoleCodeConstants;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Rehab 数据权限服务
 */
@Service
public class RehabDataPermissionService {

    @Resource
    private PermissionApi permissionApi;
    @Resource
    private RehabPatientMapper patientMapper;
    @Resource
    private RehabTherapistAssignmentMapper assignmentMapper;
    @Resource
    private RehabPatientUserBindingMapper patientUserBindingMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private AdminUserApi adminUserApi;

    public boolean isSuperAdmin(Long userId) {
        return permissionApi.hasAnyRoles(userId, RehabRoleCodeConstants.SUPER_ADMIN);
    }

    public boolean isClerk(Long userId) {
        return permissionApi.hasAnyRoles(userId, RehabRoleCodeConstants.REHAB_CLERK);
    }

    public boolean isTherapist(Long userId) {
        return permissionApi.hasAnyRoles(userId, RehabRoleCodeConstants.REHAB_THERAPIST);
    }

    /**
     * 返回当前用户可见患者编号集合。
     * null 表示可见全部（管理员/文员）。
     */
    public Set<Long> getVisiblePatientIds(Long userId) {
        // MEMBER and ADMIN account IDs use distinct namespaces. Do not query
        // ADMIN roles with a MEMBER id: a numeric collision might otherwise
        // return null, which represents unrestricted patient visibility.
        if (userId == null || Objects.equals(WebFrameworkUtils.getLoginUserType(), UserTypeEnum.MEMBER.getValue())) {
            return Collections.emptySet();
        }
        if (isSuperAdmin(userId)) {
            return null;
        }
        if (isClerk(userId)) {
            Set<Long> blocked = blockedByOtherDepartment(userId);
            if (blocked.isEmpty()) {
                return null;
            }
            // 只读取 ID，避免为部门权限过滤将全量患者敏感字段装入内存。
            return jdbcTemplate.query("SELECT id FROM rehab_patient WHERE tenant_id=? AND deleted=b'0'",
                            (rs, n) -> rs.getLong(1), TenantContextHolder.getRequiredTenantId())
                    .stream().filter(id -> !blocked.contains(id)).collect(Collectors.toSet());
        }
        if (!isTherapist(userId)) {
            return Collections.emptySet();
        }
        Set<Long> visible = new HashSet<>();

        List<RehabPatientDO> currentOwned = patientMapper.selectList(RehabPatientDO::getCurrentTherapistUserId, userId);
        currentOwned.forEach(patient -> visible.add(patient.getId()));

        List<RehabTherapistAssignmentDO> assignments = assignmentMapper.selectActiveListByTherapistUserId(userId);
        visible.addAll(assignments.stream().map(RehabTherapistAssignmentDO::getPatientId).collect(Collectors.toSet()));
        visible.removeAll(blockedByOtherDepartment(userId));
        return visible;
    }

    private Set<Long> blockedByOtherDepartment(Long userId) {
        // 纯单元测试通过直接构造本类；运行环境中这两个依赖为必填 Bean。
        if (jdbcTemplate == null || adminUserApi == null) {
            return Collections.emptySet();
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        Long deptId = user == null || user.getDeptId() == null ? -1L : user.getDeptId();
        return new HashSet<>(jdbcTemplate.query("SELECT DISTINCT b.patient_id FROM rehab_patient_crm_binding b "
                        + "JOIN crm_department_case dc ON dc.customer_id=b.crm_customer_id AND dc.tenant_id=? "
                        + "WHERE b.deleted=b'0' AND b.bind_status='bound' AND dc.dept_id<>?",
                (rs, n) -> rs.getLong(1), TenantContextHolder.getRequiredTenantId(), deptId));
    }

    public boolean canReadPatient(Long patientId, Long userId) {
        if (patientId == null || userId == null) {
            return false;
        }
        Integer userType = WebFrameworkUtils.getLoginUserType();
        if (Objects.equals(userType, UserTypeEnum.MEMBER.getValue())) {
            return patientUserBindingMapper.selectActiveByPatientIdAndAppUserId(patientId, userId) != null;
        }
        Set<Long> visibleIds = getVisiblePatientIds(userId);
        if (visibleIds == null) {
            return true;
        }
        return visibleIds.contains(patientId);
    }

}
