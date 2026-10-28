package cn.iocoder.yudao.module.crm.service.statistics;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.crm.util.CrmPermissionUtils;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CrmStatisticsScopeTest {
    private AdminUserRespDTO user(Long id, Long dept) {
        AdminUserRespDTO result = new AdminUserRespDTO();
        result.setId(id);
        result.setDeptId(dept);
        return result;
    }

    @Test void ordinaryMemberCannotSelectAnotherDepartmentsStats() {
        AdminUserApi users = mock(AdminUserApi.class);
        DeptApi depts = mock(DeptApi.class);
        when(users.getUser(5L)).thenReturn(user(5L, 10L));
        when(users.getUser(6L)).thenReturn(user(6L, 20L));
        when(users.getUserListByDeptIds(Collections.singletonList(10L)))
                .thenReturn(Arrays.asList(user(5L, 10L), user(7L, 10L)));
        try (MockedStatic<SecurityFrameworkUtils> auth = mockStatic(SecurityFrameworkUtils.class);
             MockedStatic<CrmPermissionUtils> admin = mockStatic(CrmPermissionUtils.class)) {
            auth.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(5L);
            admin.when(CrmPermissionUtils::isCrmAdmin).thenReturn(false);
            assertEquals(Collections.emptyList(), CrmStatisticsScope.users(20L, null, users, depts));
            assertEquals(Collections.emptyList(), CrmStatisticsScope.users(10L, 6L, users, depts));
            assertEquals(Arrays.asList(5L, 7L), CrmStatisticsScope.users(10L, null, users, depts));
            verify(depts).getChildDeptList(20L);
        }
    }
}
