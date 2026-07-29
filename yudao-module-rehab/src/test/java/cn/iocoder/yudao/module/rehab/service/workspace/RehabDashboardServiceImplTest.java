package cn.iocoder.yudao.module.rehab.service.workspace;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import cn.iocoder.yudao.module.rehab.controller.admin.workspace.vo.RehabDashboardSummaryRespVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.alert.RehabAlertEventMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assignment.RehabTherapistAssignmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabDailyCheckinMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.report.RehabReportMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.trigger.RehabReassessmentTriggerMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabRoleCodeConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.notification.RehabNotificationService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.PATIENT_NO_PERMISSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RehabDashboardServiceImplTest {

    private RehabDashboardServiceImpl service;

    @Mock
    private PermissionApi permissionApi;
    @Mock
    private RehabPatientMapper patientMapper;
    @Mock
    private RehabTherapistAssignmentMapper assignmentMapper;
    @Mock
    private RehabCarePlanMapper planMapper;
    @Mock
    private RehabAssessmentRecordMapper assessmentRecordMapper;
    @Mock
    private RehabReportMapper reportMapper;
    @Mock
    private RehabDailyCheckinMapper checkinMapper;
    @Mock
    private RehabAlertEventMapper alertEventMapper;
    @Mock
    private RehabReassessmentTriggerMapper reassessmentTriggerMapper;
    @Mock
    private RehabNotificationService notificationService;
    @Mock
    private AdminUserApi adminUserApi;

    @BeforeEach
    void setUp() {
        service = new RehabDashboardServiceImpl();

        RehabDataPermissionService dataPermissionService = new RehabDataPermissionService();
        ReflectionTestUtils.setField(dataPermissionService, "permissionApi", permissionApi);
        ReflectionTestUtils.setField(dataPermissionService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(dataPermissionService, "assignmentMapper", assignmentMapper);

        ReflectionTestUtils.setField(service, "dataPermissionService", dataPermissionService);
        ReflectionTestUtils.setField(service, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(service, "planMapper", planMapper);
        ReflectionTestUtils.setField(service, "assessmentRecordMapper", assessmentRecordMapper);
        ReflectionTestUtils.setField(service, "reportMapper", reportMapper);
        ReflectionTestUtils.setField(service, "checkinMapper", checkinMapper);
        ReflectionTestUtils.setField(service, "alertEventMapper", alertEventMapper);
        ReflectionTestUtils.setField(service, "reassessmentTriggerMapper", reassessmentTriggerMapper);
        ReflectionTestUtils.setField(service, "notificationService", notificationService);
        ReflectionTestUtils.setField(service, "adminUserApi", adminUserApi);
    }

    @Test
    void getOpsSummary_shouldDenyTherapist() {
        when(permissionApi.hasAnyRoles(100L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(false);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.getOpsSummary(100L));
        assertEquals(PATIENT_NO_PERMISSION.getCode(), ex.getCode());
    }

    @Test
    void getTherapistSummary_shouldReturnZeroWhenNoVisiblePatients() {
        when(permissionApi.hasAnyRoles(100L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(false);
        when(permissionApi.hasAnyRoles(100L, RehabRoleCodeConstants.REHAB_CLERK)).thenReturn(false);
        when(permissionApi.hasAnyRoles(100L, RehabRoleCodeConstants.REHAB_THERAPIST)).thenReturn(true);
        when(patientMapper.selectList(org.mockito.Mockito.<SFunction<RehabPatientDO, ?>>any(), eq(100L)))
                .thenReturn(Collections.emptyList());
        when(assignmentMapper.selectActiveListByTherapistUserId(100L)).thenReturn(Collections.emptyList());

        RehabDashboardSummaryRespVO summary = service.getTherapistSummary(100L);

        assertEquals(0L, summary.getMyPatientCount());
        assertEquals(0L, summary.getActivePlanCount());
        assertEquals(0L, summary.getUnreadNotificationCount());
    }
}
