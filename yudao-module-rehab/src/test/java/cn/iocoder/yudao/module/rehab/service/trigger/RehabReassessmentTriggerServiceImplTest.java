package cn.iocoder.yudao.module.rehab.service.trigger;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.rehab.controller.admin.trigger.vo.RehabReassessmentTriggerCreateReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.trigger.RehabReassessmentTriggerDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.log.RehabPlanOperationLogMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.progress.RehabProgressRecordMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RehabReassessmentTriggerServiceImplTest {

    private RehabReassessmentTriggerServiceImpl triggerService;

    @Mock
    private RehabReassessmentTriggerMapper triggerMapper;
    @Mock
    private RehabCarePlanMapper planMapper;
    @Mock
    private RehabPatientMapper patientMapper;
    @Mock
    private RehabProgressRecordMapper progressRecordMapper;
    @Mock
    private RehabPlanOperationLogMapper planOperationLogMapper;
    @Mock
    private PermissionApi permissionApi;
    @Mock
    private AdminUserApi adminUserApi;

    @BeforeEach
    void setUp() {
        triggerService = new RehabReassessmentTriggerServiceImpl();

        RehabDataPermissionService dataPermissionService = new RehabDataPermissionService();
        ReflectionTestUtils.setField(dataPermissionService, "permissionApi", permissionApi);
        ReflectionTestUtils.setField(dataPermissionService, "patientMapper", patientMapper);

        ReflectionTestUtils.setField(triggerService, "triggerMapper", triggerMapper);
        ReflectionTestUtils.setField(triggerService, "planMapper", planMapper);
        ReflectionTestUtils.setField(triggerService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(triggerService, "progressRecordMapper", progressRecordMapper);
        ReflectionTestUtils.setField(triggerService, "planOperationLogMapper", planOperationLogMapper);
        ReflectionTestUtils.setField(triggerService, "dataPermissionService", dataPermissionService);
        ReflectionTestUtils.setField(triggerService, "adminUserApi", adminUserApi);

        lenient().when(permissionApi.hasAnyRoles(anyLong(), anyString())).thenReturn(false);
    }

    @Test
    void createTrigger_shouldRejectForClerkUser() {
        RehabReassessmentTriggerCreateReqVO reqVO = new RehabReassessmentTriggerCreateReqVO();
        reqVO.setPatientId(10001L);
        reqVO.setEpisodeId(13001L);
        reqVO.setPlanId(40001L);
        reqVO.setTriggerType("time_due");
        reqVO.setDueDate(LocalDate.now());

        when(permissionApi.hasAnyRoles(2L, RehabRoleCodeConstants.REHAB_CLERK)).thenReturn(true);

        ServiceException ex = assertThrows(ServiceException.class, () -> triggerService.createTrigger(reqVO, 2L));
        assertEquals(1_011_011_000, ex.getCode());
    }

    @Test
    void createTrigger_shouldWorkForSuperAdmin() {
        RehabReassessmentTriggerCreateReqVO reqVO = new RehabReassessmentTriggerCreateReqVO();
        reqVO.setPatientId(10001L);
        reqVO.setEpisodeId(13001L);
        reqVO.setPlanId(40001L);
        reqVO.setTriggerType("time_due");
        reqVO.setDueDate(LocalDate.now());

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(planMapper.selectById(40001L)).thenReturn(RehabCarePlanDO.builder()
                .id(40001L).patientId(10001L).episodeId(13001L).build());
        when(triggerMapper.selectPendingByPlanAndType(40001L, "time_due")).thenReturn(null);
        doAnswer(invocation -> {
            RehabReassessmentTriggerDO item = invocation.getArgument(0);
            item.setId(45001L);
            return 1;
        }).when(triggerMapper).insert(any(RehabReassessmentTriggerDO.class));
        when(triggerMapper.selectById(45001L)).thenReturn(RehabReassessmentTriggerDO.builder()
                .id(45001L).planId(40001L).patientId(10001L).episodeId(13001L).build());

        Long id = triggerService.createTrigger(reqVO, 1L);

        assertEquals(45001L, id);
    }

}
