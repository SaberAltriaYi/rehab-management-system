package cn.iocoder.yudao.module.rehab.service.checkin;

import cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo.RehabDailyCheckinCreateReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo.RehabTaskExecutionItemVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.checkin.RehabDailyCheckinDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.checkin.RehabTaskExecutionDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.progress.RehabProgressRecordDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.task.RehabExerciseTaskDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabDailyCheckinMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabTaskExecutionMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.log.RehabPlanOperationLogMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.task.RehabExerciseTaskMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabRoleCodeConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.progress.RehabProgressRecordService;
import cn.iocoder.yudao.module.rehab.service.trigger.RehabReassessmentTriggerService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RehabDailyCheckinServiceImplTest {

    private RehabDailyCheckinServiceImpl checkinService;

    @Mock
    private RehabDailyCheckinMapper checkinMapper;
    @Mock
    private RehabTaskExecutionMapper taskExecutionMapper;
    @Mock
    private RehabExerciseTaskMapper taskMapper;
    @Mock
    private RehabCarePlanMapper planMapper;
    @Mock
    private RehabPatientMapper patientMapper;
    @Mock
    private RehabPlanOperationLogMapper planOperationLogMapper;
    @Mock
    private RehabProgressRecordService progressRecordService;
    @Mock
    private RehabReassessmentTriggerService triggerService;
    @Mock
    private PermissionApi permissionApi;
    @Mock
    private AdminUserApi adminUserApi;

    @BeforeEach
    void setUp() {
        checkinService = new RehabDailyCheckinServiceImpl();

        RehabDataPermissionService dataPermissionService = new RehabDataPermissionService();
        ReflectionTestUtils.setField(dataPermissionService, "permissionApi", permissionApi);
        ReflectionTestUtils.setField(dataPermissionService, "patientMapper", patientMapper);

        ReflectionTestUtils.setField(checkinService, "checkinMapper", checkinMapper);
        ReflectionTestUtils.setField(checkinService, "taskExecutionMapper", taskExecutionMapper);
        ReflectionTestUtils.setField(checkinService, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(checkinService, "planMapper", planMapper);
        ReflectionTestUtils.setField(checkinService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(checkinService, "planOperationLogMapper", planOperationLogMapper);
        ReflectionTestUtils.setField(checkinService, "dataPermissionService", dataPermissionService);
        ReflectionTestUtils.setField(checkinService, "progressRecordService", progressRecordService);
        ReflectionTestUtils.setField(checkinService, "triggerService", triggerService);
        ReflectionTestUtils.setField(checkinService, "adminUserApi", adminUserApi);

        lenient().when(permissionApi.hasAnyRoles(anyLong(), anyString())).thenReturn(false);
    }

    @Test
    void createCheckin_shouldWriteExecutionAndTriggerRecalculate() {
        RehabDailyCheckinCreateReqVO reqVO = new RehabDailyCheckinCreateReqVO();
        reqVO.setPatientId(10001L);
        reqVO.setEpisodeId(13001L);
        reqVO.setPlanId(40001L);
        reqVO.setCheckinDate(LocalDate.now());
        reqVO.setSubmitRoleType("therapist");
        reqVO.setPainScoreBefore(new BigDecimal("3.0"));
        reqVO.setPainScoreAfter(new BigDecimal("2.5"));

        RehabTaskExecutionItemVO execution = new RehabTaskExecutionItemVO();
        execution.setTaskId(41001L);
        execution.setCompletionStatus("completed");
        execution.setCompletedSets(2);
        execution.setCompletedReps(10);
        reqVO.setTaskExecutions(List.of(execution));

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(planMapper.selectById(40001L)).thenReturn(RehabCarePlanDO.builder()
                .id(40001L).patientId(10001L).episodeId(13001L).build());
        when(taskMapper.selectListByIds(any(Set.class))).thenReturn(List.of(
                RehabExerciseTaskDO.builder().id(41001L).planId(40001L).patientId(10001L).build()));

        doAnswer(invocation -> {
            RehabDailyCheckinDO item = invocation.getArgument(0);
            item.setId(42001L);
            return 1;
        }).when(checkinMapper).insert(any(RehabDailyCheckinDO.class));
        when(checkinMapper.selectById(42001L)).thenReturn(RehabDailyCheckinDO.builder()
                .id(42001L).planId(40001L).patientId(10001L).build());
        when(progressRecordService.recalculateByPlan(eq(40001L), any(LocalDate.class), eq(1L), anyString()))
                .thenReturn(RehabProgressRecordDO.builder().id(44001L).planId(40001L).build());

        Long checkinId = checkinService.createCheckin(reqVO, 1L, true);

        assertEquals(42001L, checkinId);
        verify(taskExecutionMapper, atLeastOnce()).insert(org.mockito.ArgumentMatchers.<RehabTaskExecutionDO>any());
        verify(progressRecordService).recalculateByPlan(eq(40001L), any(LocalDate.class), eq(1L), anyString());
        verify(triggerService).evaluateByPlan(eq(40001L), any(RehabProgressRecordDO.class), eq(1L));
    }

}
