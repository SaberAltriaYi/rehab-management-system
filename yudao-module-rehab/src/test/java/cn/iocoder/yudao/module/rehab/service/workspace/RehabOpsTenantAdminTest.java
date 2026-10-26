package cn.iocoder.yudao.module.rehab.service.workspace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

/** Organization statistics are not clinical-write privileges; tenant filtering stays in MyBatis. */
class RehabOpsTenantAdminTest {
    @Test
    void tenantAdminCanReadStatisticsWithoutBecomingSuperAdmin() {
        RehabDataPermissionService permissions = mock(RehabDataPermissionService.class);
        when(permissions.isTenantAdmin(21L)).thenReturn(true);
        RehabDashboardServiceImpl service = new RehabDashboardServiceImpl();
        RehabPatientMapper patients = mock(RehabPatientMapper.class);
        ReflectionTestUtils.setField(service, "dataPermissionService", permissions);
        ReflectionTestUtils.setField(service, "patientMapper", patients);
        when(patients.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());
        assertDoesNotThrow(() -> service.getOpsWorkload(21L));
        assertFalse(permissions.isSuperAdmin(21L));
    }

    @Test
    void unprivilegedRoleStillCannotReadOrganizationStatistics() {
        RehabDashboardServiceImpl service = new RehabDashboardServiceImpl();
        ReflectionTestUtils.setField(
                service, "dataPermissionService", mock(RehabDataPermissionService.class));
        assertThrows(ServiceException.class, () -> service.getOpsSummary(22L));
    }
}
