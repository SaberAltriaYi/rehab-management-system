package cn.iocoder.yudao.module.rehab.service.app;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.rehab.controller.app.patient.vo.AppPatientCheckinCreateReqVO;
import cn.iocoder.yudao.module.rehab.controller.app.patient.vo.AppPatientLoginReqVO;
import cn.iocoder.yudao.module.rehab.controller.app.patient.vo.AppPatientLoginRespVO;
import cn.iocoder.yudao.module.rehab.controller.app.patient.vo.AppPatientTaskExecutionItemVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.binding.RehabPatientUserBindingDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.checkin.RehabDailyCheckinDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.binding.RehabPatientUserBindingMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabDailyCheckinMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper;
import cn.iocoder.yudao.module.rehab.service.checkin.RehabDailyCheckinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.APP_PATIENT_DAILY_CHECKIN_EXISTS;

@ExtendWith(MockitoExtension.class)
class RehabAppPatientServiceImplTest {

    private RehabAppPatientServiceImpl appPatientService;

    @Mock
    private OAuth2TokenCommonApi oauth2TokenApi;
    @Mock
    private RehabPatientUserBindingMapper patientUserBindingMapper;
    @Mock
    private RehabPatientMapper patientMapper;
    @Mock
    private RehabCarePlanMapper planMapper;
    @Mock
    private RehabDailyCheckinMapper checkinMapper;
    @Mock
    private RehabDailyCheckinService checkinService;

    @BeforeEach
    void setUp() {
        appPatientService = new RehabAppPatientServiceImpl();
        ReflectionTestUtils.setField(appPatientService, "oauth2TokenApi", oauth2TokenApi);
        ReflectionTestUtils.setField(appPatientService, "patientUserBindingMapper", patientUserBindingMapper);
        ReflectionTestUtils.setField(appPatientService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(appPatientService, "planMapper", planMapper);
        ReflectionTestUtils.setField(appPatientService, "checkinMapper", checkinMapper);
        ReflectionTestUtils.setField(appPatientService, "checkinService", checkinService);
    }

    @Test
    void login_shouldReturnAccessTokenWhenBindingMatched() {
        AppPatientLoginReqVO reqVO = new AppPatientLoginReqVO();
        reqVO.setPhone("13800138000");
        reqVO.setBindCode("PT202603100001");

        RehabPatientUserBindingDO binding = RehabPatientUserBindingDO.builder()
                .id(1L)
                .patientId(10001L)
                .appUserId(90001L)
                .bindStatus("active")
                .phone("13800138000")
                .build();
        when(patientUserBindingMapper.selectActiveListByPhone("13800138000")).thenReturn(Collections.singletonList(binding));
        when(patientMapper.selectById(10001L)).thenReturn(RehabPatientDO.builder()
                .id(10001L)
                .patientNo("PT202603100001")
                .build());

        OAuth2AccessTokenRespDTO tokenDTO = new OAuth2AccessTokenRespDTO();
        tokenDTO.setUserId(90001L);
        tokenDTO.setAccessToken("patient-token");
        tokenDTO.setRefreshToken("refresh-token");
        tokenDTO.setExpiresTime(LocalDateTime.now().plusHours(12));
        when(oauth2TokenApi.createAccessToken(any())).thenReturn(tokenDTO);

        AppPatientLoginRespVO respVO = appPatientService.login(reqVO);

        assertEquals("patient-token", respVO.getAccessToken());
        verify(patientUserBindingMapper).updateById(any(RehabPatientUserBindingDO.class));
    }

    @Test
    void createCheckin_shouldRejectDuplicateDailyRecord() {
        AppPatientCheckinCreateReqVO reqVO = new AppPatientCheckinCreateReqVO();
        reqVO.setPlanId(40001L);
        reqVO.setCheckinDate(LocalDate.now());
        reqVO.setTaskExecutions(List.of(buildTaskExecution(41001L)));

        when(patientUserBindingMapper.selectActiveByAppUserId(90001L)).thenReturn(RehabPatientUserBindingDO.builder()
                .id(1L)
                .patientId(10001L)
                .appUserId(90001L)
                .bindStatus("active")
                .build());
        when(planMapper.selectById(40001L)).thenReturn(RehabCarePlanDO.builder()
                .id(40001L)
                .patientId(10001L)
                .episodeId(13001L)
                .build());
        when(checkinMapper.selectByPatientPlanAndDate(eq(10001L), eq(40001L), any(LocalDate.class)))
                .thenReturn(RehabDailyCheckinDO.builder().id(42001L).build());

        ServiceException ex = assertThrows(ServiceException.class, () -> appPatientService.createCheckin(reqVO, 90001L));

        assertEquals(APP_PATIENT_DAILY_CHECKIN_EXISTS.getCode(), ex.getCode());
        verify(checkinService, never()).createCheckin(any(), anyLong(), anyBoolean());
    }

    @Test
    void createCheckin_shouldWriteWhenNoDuplicate() {
        AppPatientCheckinCreateReqVO reqVO = new AppPatientCheckinCreateReqVO();
        reqVO.setPlanId(40001L);
        reqVO.setCheckinDate(LocalDate.now());
        reqVO.setPainScoreBefore(new BigDecimal("2"));
        reqVO.setPainScoreAfter(new BigDecimal("3"));
        reqVO.setTaskExecutions(List.of(buildTaskExecution(41001L)));

        when(patientUserBindingMapper.selectActiveByAppUserId(90001L)).thenReturn(RehabPatientUserBindingDO.builder()
                .id(1L)
                .patientId(10001L)
                .appUserId(90001L)
                .bindStatus("active")
                .build());
        when(planMapper.selectById(40001L)).thenReturn(RehabCarePlanDO.builder()
                .id(40001L)
                .patientId(10001L)
                .episodeId(13001L)
                .build());
        when(checkinMapper.selectByPatientPlanAndDate(eq(10001L), eq(40001L), any(LocalDate.class))).thenReturn(null);
        when(checkinService.createCheckin(any(), eq(90001L), eq(false))).thenReturn(43001L);

        Long id = appPatientService.createCheckin(reqVO, 90001L);

        assertEquals(43001L, id);
        verify(checkinService).createCheckin(any(), eq(90001L), eq(false));
    }

    private AppPatientTaskExecutionItemVO buildTaskExecution(Long taskId) {
        AppPatientTaskExecutionItemVO vo = new AppPatientTaskExecutionItemVO();
        vo.setTaskId(taskId);
        vo.setCompletionStatus("completed");
        vo.setCompletedSets(2);
        vo.setCompletedReps(10);
        return vo;
    }
}
