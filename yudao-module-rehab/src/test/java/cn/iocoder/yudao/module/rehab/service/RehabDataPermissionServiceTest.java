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
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RehabDataPermissionServiceTest {

    @InjectMocks
    private RehabDataPermissionService dataPermissionService;

    @Mock
    private PermissionApi permissionApi;
    @Mock
    private RehabPatientMapper patientMapper;
    @Mock
    private RehabTherapistAssignmentMapper assignmentMapper;
    @Mock
    private RehabPatientUserBindingMapper patientUserBindingMapper;

    @Test
    void getVisiblePatientIds_shouldReturnOwnedAndAssignedPatientsForTherapist() {
        Long therapistUserId = 100L;
        when(permissionApi.hasAnyRoles(therapistUserId, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(false);
        when(permissionApi.hasAnyRoles(therapistUserId, "tenant_admin")).thenReturn(false);
        when(permissionApi.hasAnyRoles(therapistUserId, RehabRoleCodeConstants.REHAB_CLERK)).thenReturn(false);
        when(permissionApi.hasAnyRoles(therapistUserId, RehabRoleCodeConstants.REHAB_THERAPIST)).thenReturn(true);

        when(patientMapper.selectList(any(SFunction.class), eq(therapistUserId)))
                .thenReturn(Collections.singletonList(RehabPatientDO.builder().id(10001L).build()));
        when(assignmentMapper.selectActiveListByTherapistUserId(therapistUserId))
                .thenReturn(Collections.singletonList(
                        RehabTherapistAssignmentDO.builder().patientId(10002L).build()));

        Set<Long> visibleIds = dataPermissionService.getVisiblePatientIds(therapistUserId);

        assertNotNull(visibleIds);
        assertEquals(2, visibleIds.size());
        assertTrue(visibleIds.contains(10001L));
        assertTrue(visibleIds.contains(10002L));
        assertTrue(dataPermissionService.canReadPatient(10001L, therapistUserId));
        assertFalse(dataPermissionService.canReadPatient(99999L, therapistUserId));
    }

    @Test
    void memberIdCollisionMustNeverInheritAdminVisibility() {
        Long collidingId = 7L;
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            WebFrameworkUtils.setLoginUserType(request, UserTypeEnum.MEMBER.getValue());
            assertEquals(Collections.emptySet(), dataPermissionService.getVisiblePatientIds(collidingId));
            verifyNoInteractions(permissionApi);
            assertFalse(dataPermissionService.canReadPatient(10001L, collidingId));
            verify(patientUserBindingMapper).selectActiveByPatientIdAndAppUserId(10001L, collidingId);
            WebFrameworkUtils.setLoginUserType(request, UserTypeEnum.ADMIN.getValue());
            when(permissionApi.hasAnyRoles(collidingId, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
            assertNull(dataPermissionService.getVisiblePatientIds(collidingId));
            assertTrue(dataPermissionService.canReadPatient(10001L, collidingId));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void missingIdentityCannotSeeOrReadPatients() {
        assertEquals(Collections.emptySet(), dataPermissionService.getVisiblePatientIds(null));
        assertFalse(dataPermissionService.canReadPatient(10001L, null));
        assertFalse(dataPermissionService.canReadPatient(null, 7L));
        verifyNoInteractions(permissionApi);
    }

}
