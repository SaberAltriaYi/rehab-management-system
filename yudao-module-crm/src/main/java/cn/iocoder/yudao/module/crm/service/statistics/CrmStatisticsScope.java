package cn.iocoder.yudao.module.crm.service.statistics;

import cn.iocoder.yudao.module.crm.util.CrmPermissionUtils;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/** 统计范围不能由前端任意 deptId/userId 越过部门隔离。 */
final class CrmStatisticsScope {
    private CrmStatisticsScope() {}

    static List<Long> users(Long deptId, Long requestedUserId, AdminUserApi users, DeptApi depts) {
        Long viewerId = getLoginUserId();
        if (viewerId == null || deptId == null) return Collections.emptyList();
        if (!CrmPermissionUtils.isCrmAdmin()) {
            AdminUserRespDTO viewer = users.getUser(viewerId);
            if (viewer == null || viewer.getDeptId() == null) {
                return Collections.emptyList();
            }
            // 查询父部门时只返回当前部门汇总，不泄露任何兄弟部门或下属部门。
            List<DeptRespDTO> children = depts.getChildDeptList(deptId);
            if (!Objects.equals(viewer.getDeptId(), deptId)
                    && (children == null || children.stream().noneMatch(child ->
                    Objects.equals(child.getId(), viewer.getDeptId())))) {
                return Collections.emptyList();
            }
            if (requestedUserId != null) {
                AdminUserRespDTO target = users.getUser(requestedUserId);
                return target != null && Objects.equals(target.getDeptId(), viewer.getDeptId())
                        ? Collections.singletonList(requestedUserId) : Collections.emptyList();
            }
            return users.getUserListByDeptIds(Collections.singletonList(viewer.getDeptId())).stream()
                    .map(AdminUserRespDTO::getId).collect(Collectors.toList());
        }
        if (requestedUserId != null) return Collections.singletonList(requestedUserId);
        List<Long> deptIds = new ArrayList<>();
        List<DeptRespDTO> children = depts.getChildDeptList(deptId);
        if (children != null) children.forEach(dept -> deptIds.add(dept.getId()));
        deptIds.add(deptId);
        return users.getUserListByDeptIds(deptIds).stream().map(AdminUserRespDTO::getId).collect(Collectors.toList());
    }
}
