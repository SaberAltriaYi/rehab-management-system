package cn.iocoder.yudao.module.rehab.service.alert;

import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.RehabAlertHandleReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.RehabAlertRefreshReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.alert.RehabAlertEventDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.alert.RehabAlertRuleDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.progress.RehabProgressRecordDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.alert.RehabAlertEventMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.alert.RehabAlertRuleMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assignment.RehabTherapistAssignmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabDailyCheckinMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.progress.RehabProgressRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.report.RehabReportMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.trigger.RehabReassessmentTriggerMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabAlertConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabRoleCodeConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.log.RehabAuditLogService;
import cn.iocoder.yudao.module.rehab.service.notification.RehabNotificationService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RehabAlertServiceImplTest {

    private RehabAlertServiceImpl service;

    @Mock
    private RehabAlertEventMapper alertEventMapper;
    @Mock
    private RehabAlertRuleMapper alertRuleMapper;
    @Mock
    private RehabCarePlanMapper planMapper;
    @Mock
    private RehabProgressRecordMapper progressRecordMapper;
    @Mock
    private RehabDailyCheckinMapper checkinMapper;
    @Mock
    private RehabReassessmentTriggerMapper triggerMapper;
    @Mock
    private RehabReportMapper reportMapper;
    @Mock
    private RehabPatientMapper patientMapper;
    @Mock
    private RehabNotificationService notificationService;
    @Mock
    private RehabAuditLogService auditLogService;
    @Mock
    private PermissionApi permissionApi;
    @Mock
    private RehabTherapistAssignmentMapper assignmentMapper;

    @BeforeEach
    void setUp() {
        service = new RehabAlertServiceImpl();

        RehabDataPermissionService dataPermissionService = new RehabDataPermissionService();
        ReflectionTestUtils.setField(dataPermissionService, "permissionApi", permissionApi);
        ReflectionTestUtils.setField(dataPermissionService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(dataPermissionService, "assignmentMapper", assignmentMapper);

        ReflectionTestUtils.setField(service, "alertEventMapper", alertEventMapper);
        ReflectionTestUtils.setField(service, "alertRuleMapper", alertRuleMapper);
        ReflectionTestUtils.setField(service, "planMapper", planMapper);
        ReflectionTestUtils.setField(service, "progressRecordMapper", progressRecordMapper);
        ReflectionTestUtils.setField(service, "checkinMapper", checkinMapper);
        ReflectionTestUtils.setField(service, "triggerMapper", triggerMapper);
        ReflectionTestUtils.setField(service, "reportMapper", reportMapper);
        ReflectionTestUtils.setField(service, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(service, "dataPermissionService", dataPermissionService);
        ReflectionTestUtils.setField(service, "notificationService", notificationService);
        ReflectionTestUtils.setField(service, "auditLogService", auditLogService);
    }

    @Test
    void refreshAlerts_shouldCreateLowAdherenceAlertAndNotifyTherapist() {
        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);

        RehabCarePlanDO plan = RehabCarePlanDO.builder()
                .id(40001L)
                .patientId(10001L)
                .episodeId(13001L)
                .primaryTherapistUserId(100L)
                .status("active")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(7))
                .build();
        when(planMapper.selectById(40001L)).thenReturn(plan);
        when(progressRecordMapper.selectLatestByPlanId(40001L)).thenReturn(RehabProgressRecordDO.builder()
                .planId(40001L)
                .completionRate(new BigDecimal("55"))
                .averagePainScore(new BigDecimal("3"))
                .painTrend("stable")
                .build());
        when(checkinMapper.selectListByPlanId(40001L)).thenReturn(Collections.emptyList());
        when(triggerMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(alertEventMapper.selectCount(any())).thenReturn(0L);
        when(alertEventMapper.selectActiveByPlanAndType(40001L, RehabAlertConstants.TYPE_LOW_ADHERENCE)).thenReturn(null);
        when(alertRuleMapper.selectByRuleCode("LOW_ADHERENCE")).thenReturn(RehabAlertRuleDO.builder().id(88001L).build());
        doAnswer(invocation -> {
            RehabAlertEventDO event = invocation.getArgument(0);
            event.setId(89001L);
            return 1;
        }).when(alertEventMapper).insert(any(RehabAlertEventDO.class));

        RehabAlertRefreshReqVO reqVO = new RehabAlertRefreshReqVO();
        reqVO.setPlanId(40001L);
        List<Long> touched = service.refreshAlerts(reqVO, 1L);

        assertEquals(1, touched.size());
        assertEquals(89001L, touched.get(0));
        verify(notificationService).createSystemNotification(eq("therapist"), eq(100L), eq(10001L), eq(13001L),
                eq("alert"), eq(89001L), eq("low_adherence"), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void acknowledgeAlert_shouldTransitToAcknowledged() {
        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(alertEventMapper.selectById(90001L)).thenReturn(RehabAlertEventDO.builder()
                .id(90001L)
                .patientId(10001L)
                .status(RehabAlertConstants.STATUS_ACTIVE)
                .build());

        RehabAlertHandleReqVO reqVO = new RehabAlertHandleReqVO();
        reqVO.setId(90001L);
        reqVO.setRemark("已处理");
        service.acknowledgeAlert(reqVO, 1L);

        verify(alertEventMapper).updateById(argThat((RehabAlertEventDO item) ->
                item.getId() != null && item.getId().equals(90001L)
                        && RehabAlertConstants.STATUS_ACKNOWLEDGED.equals(item.getStatus())
                        && item.getAcknowledgedBy() != null));
    }
}
