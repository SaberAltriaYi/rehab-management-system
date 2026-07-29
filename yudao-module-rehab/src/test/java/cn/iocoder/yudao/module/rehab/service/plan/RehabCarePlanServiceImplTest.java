package cn.iocoder.yudao.module.rehab.service.plan;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.rehab.controller.admin.plan.vo.RehabCarePlanCreateReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.plan.vo.RehabCarePlanCreateRespVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.episode.RehabEpisodeDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabDailyCheckinMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.episode.RehabEpisodeMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.log.RehabPlanOperationLogMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.progress.RehabProgressRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.task.RehabExerciseTaskMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.task.RehabTaskScheduleMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.trigger.RehabReassessmentTriggerMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabRoleCodeConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RehabCarePlanServiceImplTest {

    private RehabCarePlanServiceImpl carePlanService;

    @Mock
    private RehabCarePlanMapper planMapper;
    @Mock
    private RehabPlanOperationLogMapper planOperationLogMapper;
    @Mock
    private RehabPatientMapper patientMapper;
    @Mock
    private RehabEpisodeMapper episodeMapper;
    @Mock
    private RehabAssessmentRecordMapper assessmentRecordMapper;
    @Mock
    private RehabExerciseTaskMapper taskMapper;
    @Mock
    private RehabTaskScheduleMapper taskScheduleMapper;
    @Mock
    private RehabDailyCheckinMapper checkinMapper;
    @Mock
    private RehabProgressRecordMapper progressRecordMapper;
    @Mock
    private RehabReassessmentTriggerMapper triggerMapper;
    @Mock
    private PermissionApi permissionApi;
    @Mock
    private AdminUserApi adminUserApi;

    @BeforeEach
    void setUp() {
        carePlanService = new RehabCarePlanServiceImpl();

        RehabDataPermissionService dataPermissionService = new RehabDataPermissionService();
        ReflectionTestUtils.setField(dataPermissionService, "permissionApi", permissionApi);
        ReflectionTestUtils.setField(dataPermissionService, "patientMapper", patientMapper);

        ReflectionTestUtils.setField(carePlanService, "planMapper", planMapper);
        ReflectionTestUtils.setField(carePlanService, "planOperationLogMapper", planOperationLogMapper);
        ReflectionTestUtils.setField(carePlanService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(carePlanService, "episodeMapper", episodeMapper);
        ReflectionTestUtils.setField(carePlanService, "assessmentRecordMapper", assessmentRecordMapper);
        ReflectionTestUtils.setField(carePlanService, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(carePlanService, "taskScheduleMapper", taskScheduleMapper);
        ReflectionTestUtils.setField(carePlanService, "checkinMapper", checkinMapper);
        ReflectionTestUtils.setField(carePlanService, "progressRecordMapper", progressRecordMapper);
        ReflectionTestUtils.setField(carePlanService, "triggerMapper", triggerMapper);
        ReflectionTestUtils.setField(carePlanService, "dataPermissionService", dataPermissionService);
        ReflectionTestUtils.setField(carePlanService, "adminUserApi", adminUserApi);

        lenient().when(permissionApi.hasAnyRoles(anyLong(), anyString())).thenReturn(false);
    }

    @Test
    void createPlan_shouldGeneratePlanNo() {
        RehabCarePlanCreateReqVO reqVO = new RehabCarePlanCreateReqVO();
        reqVO.setPatientId(10001L);
        reqVO.setEpisodeId(13001L);
        reqVO.setPlanName("四周功能恢复计划");
        reqVO.setPlanType("rehab");
        reqVO.setStatus("draft");
        reqVO.setStartDate(LocalDate.now());

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(patientMapper.selectById(10001L)).thenReturn(RehabPatientDO.builder().id(10001L).build());
        when(episodeMapper.selectById(13001L)).thenReturn(RehabEpisodeDO.builder().id(13001L).patientId(10001L).build());

        doAnswer(invocation -> {
            RehabCarePlanDO item = invocation.getArgument(0);
            item.setId(30001L);
            return 1;
        }).when(planMapper).insert(any(RehabCarePlanDO.class));
        when(planMapper.selectById(30001L)).thenReturn(RehabCarePlanDO.builder().id(30001L).patientId(10001L).episodeId(13001L).build());

        RehabCarePlanCreateRespVO respVO = carePlanService.createPlan(reqVO, 1L);

        assertNotNull(respVO);
        assertEquals(30001L, respVO.getId());
        assertTrue(respVO.getPlanNo().startsWith("PLN"));
        verify(planMapper).updateById(org.mockito.ArgumentMatchers.<RehabCarePlanDO>argThat(item ->
                Objects.equals(item.getId(), 30001L)
                        && item.getPlanNo() != null
                        && item.getPlanNo().startsWith("PLN")));
    }

    @Test
    void createPlan_shouldRejectWhenActivePlanAlreadyExists() {
        RehabCarePlanCreateReqVO reqVO = new RehabCarePlanCreateReqVO();
        reqVO.setPatientId(10001L);
        reqVO.setEpisodeId(13001L);
        reqVO.setPlanName("执行中计划");
        reqVO.setPlanType("rehab");
        reqVO.setStatus("active");
        reqVO.setStartDate(LocalDate.now());

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(patientMapper.selectById(10001L)).thenReturn(RehabPatientDO.builder().id(10001L).build());
        when(episodeMapper.selectById(13001L)).thenReturn(RehabEpisodeDO.builder().id(13001L).patientId(10001L).build());
        when(planMapper.selectActiveByPatientEpisode(10001L, 13001L)).thenReturn(
                RehabCarePlanDO.builder().id(88L).patientId(10001L).episodeId(13001L).status("active").build());

        ServiceException ex = assertThrows(ServiceException.class, () -> carePlanService.createPlan(reqVO, 1L));
        assertEquals(1_011_006_002, ex.getCode());
        verify(planMapper, never()).insert(any(RehabCarePlanDO.class));
    }

}
