package cn.iocoder.yudao.module.rehab.service.progress;

import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.progress.RehabProgressRecordDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.task.RehabExerciseTaskDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabDailyCheckinMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabTaskExecutionMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.log.RehabPlanOperationLogMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.progress.RehabProgressRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.task.RehabExerciseTaskMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.task.RehabTaskScheduleMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabPlanConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabRoleCodeConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RehabProgressRecordServiceImplTest {

    private RehabProgressRecordServiceImpl progressService;

    @Mock
    private RehabProgressRecordMapper progressRecordMapper;
    @Mock
    private RehabCarePlanMapper planMapper;
    @Mock
    private RehabPatientMapper patientMapper;
    @Mock
    private RehabExerciseTaskMapper taskMapper;
    @Mock
    private RehabTaskScheduleMapper taskScheduleMapper;
    @Mock
    private RehabDailyCheckinMapper checkinMapper;
    @Mock
    private RehabTaskExecutionMapper taskExecutionMapper;
    @Mock
    private RehabPlanOperationLogMapper planOperationLogMapper;
    @Mock
    private PermissionApi permissionApi;

    @BeforeEach
    void setUp() {
        progressService = new RehabProgressRecordServiceImpl();

        RehabDataPermissionService dataPermissionService = new RehabDataPermissionService();
        ReflectionTestUtils.setField(dataPermissionService, "permissionApi", permissionApi);
        ReflectionTestUtils.setField(dataPermissionService, "patientMapper", patientMapper);

        ReflectionTestUtils.setField(progressService, "progressRecordMapper", progressRecordMapper);
        ReflectionTestUtils.setField(progressService, "planMapper", planMapper);
        ReflectionTestUtils.setField(progressService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(progressService, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(progressService, "taskScheduleMapper", taskScheduleMapper);
        ReflectionTestUtils.setField(progressService, "checkinMapper", checkinMapper);
        ReflectionTestUtils.setField(progressService, "taskExecutionMapper", taskExecutionMapper);
        ReflectionTestUtils.setField(progressService, "planOperationLogMapper", planOperationLogMapper);
        ReflectionTestUtils.setField(progressService, "dataPermissionService", dataPermissionService);

        lenient().when(permissionApi.hasAnyRoles(anyLong(), anyString())).thenReturn(false);
    }

    @Test
    void recalculateByPlan_shouldMarkInsufficientPainTrendWhenSampleNotEnough() {
        LocalDate today = LocalDate.now();
        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(planMapper.selectById(40001L)).thenReturn(RehabCarePlanDO.builder()
                .id(40001L).patientId(10001L).episodeId(13001L).build());
        when(taskMapper.selectListByPlanId(40001L)).thenReturn(List.of(
                RehabExerciseTaskDO.builder().id(41001L).planId(40001L).status("active").frequencyPerWeek(3).build()));
        when(taskScheduleMapper.selectListByPlanId(40001L)).thenReturn(Collections.emptyList());
        when(checkinMapper.selectListByPlanIdAndDateRange(any(), any(), any())).thenReturn(Collections.emptyList());
        when(taskExecutionMapper.selectListByCheckinIds(any())).thenReturn(Collections.emptyList());
        when(progressRecordMapper.selectByPlanAndPeriod(any(), any(), any())).thenReturn(null);

        doAnswer(invocation -> {
            RehabProgressRecordDO item = invocation.getArgument(0);
            item.setId(44001L);
            return 1;
        }).when(progressRecordMapper).insert(any(RehabProgressRecordDO.class));
        when(progressRecordMapper.selectById(44001L)).thenReturn(RehabProgressRecordDO.builder()
                .id(44001L)
                .planId(40001L)
                .painTrend(RehabPlanConstants.PAIN_TREND_INSUFFICIENT)
                .build());

        RehabProgressRecordDO record = progressService.recalculateByPlan(40001L, today, 1L, "test");

        assertEquals(RehabPlanConstants.PAIN_TREND_INSUFFICIENT, record.getPainTrend());
    }

}
