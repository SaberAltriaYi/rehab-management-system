package cn.iocoder.yudao.module.rehab.service;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assignment.RehabTherapistAssignmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.assignment.RehabTherapistAssignmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.binding.RehabPatientUserBindingMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabRoleCodeConstants;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
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
        if (isSuperAdmin(userId) || isClerk(userId)) {
            return null;
        }
        if (!isTherapist(userId)) {
            return Collections.emptySet();
        }
        Set<Long> visible = new HashSet<>();

        List<RehabPatientDO> currentOwned = patientMapper.selectList(RehabPatientDO::getCurrentTherapistUserId, userId);
        currentOwned.forEach(patient -> visible.add(patient.getId()));

        List<RehabTherapistAssignmentDO> assignments = assignmentMapper.selectActiveListByTherapistUserId(userId);
        visible.addAll(assignments.stream().map(RehabTherapistAssignmentDO::getPatientId).collect(Collectors.toSet()));
        return visible;
    }

    public boolean canReadPatient(Long patientId, Long userId) {
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
