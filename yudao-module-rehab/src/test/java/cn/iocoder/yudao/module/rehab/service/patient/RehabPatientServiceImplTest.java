package cn.iocoder.yudao.module.rehab.service.patient;

import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabCrmConflictCheckRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientCheckCrmConflictReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientCreateReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientCreateRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientTransferReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assignment.RehabTherapistAssignmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.binding.RehabPatientCrmBindingDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.log.RehabPatientOperationLogDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.assignment.RehabTherapistAssignmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.binding.RehabPatientCrmBindingMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.episode.RehabEpisodeMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.log.RehabPatientOperationLogMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabAssignmentConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabCrmBindingConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabRoleCodeConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.episode.RehabEpisodeService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RehabPatientServiceImplTest {

    private RehabPatientServiceImpl patientService;

    @Mock
    private RehabPatientMapper patientMapper;
    @Mock
    private RehabPatientCrmBindingMapper crmBindingMapper;
    @Mock
    private RehabTherapistAssignmentMapper assignmentMapper;
    @Mock
    private RehabEpisodeMapper episodeMapper;
    @Mock
    private RehabPatientOperationLogMapper operationLogMapper;
    @Mock
    private RehabEpisodeService episodeService;
    @Mock
    private PermissionApi permissionApi;
    @Mock
    private AdminUserApi adminUserApi;

    @BeforeEach
    void setUp() {
        patientService = new RehabPatientServiceImpl();
        RehabDataPermissionService dataPermissionService = new RehabDataPermissionService();
        ReflectionTestUtils.setField(dataPermissionService, "permissionApi", permissionApi);
        ReflectionTestUtils.setField(dataPermissionService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(dataPermissionService, "assignmentMapper", assignmentMapper);

        ReflectionTestUtils.setField(patientService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(patientService, "crmBindingMapper", crmBindingMapper);
        ReflectionTestUtils.setField(patientService, "assignmentMapper", assignmentMapper);
        ReflectionTestUtils.setField(patientService, "episodeMapper", episodeMapper);
        ReflectionTestUtils.setField(patientService, "operationLogMapper", operationLogMapper);
        ReflectionTestUtils.setField(patientService, "episodeService", episodeService);
        ReflectionTestUtils.setField(patientService, "dataPermissionService", dataPermissionService);
        ReflectionTestUtils.setField(patientService, "adminUserApi", adminUserApi);
    }

    @Test
    void createPatient_shouldGeneratePatientNoAndReturnDuplicateHint() {
        RehabPatientCreateReqVO reqVO = new RehabPatientCreateReqVO();
        reqVO.setName("王小明");
        reqVO.setPhone("13800138000");
        reqVO.setInitEpisode(false);

        when(patientMapper.selectListByNameAndPhone("王小明", "13800138000"))
                .thenReturn(Collections.singletonList(RehabPatientDO.builder().id(999L).build()));
        doAnswer(invocation -> {
            RehabPatientDO patient = invocation.getArgument(0);
            patient.setId(123L);
            return 1;
        }).when(patientMapper).insert(any(RehabPatientDO.class));
        when(episodeService.createInitialEpisodeIfNeeded(anyLong(), any(), any(), any(), any(), anyLong()))
                .thenReturn(null);

        RehabPatientCreateRespVO respVO = patientService.createPatient(reqVO, 1L);

        assertNotNull(respVO);
        assertTrue(respVO.getSuspectedDuplicate());
        assertEquals(Collections.singletonList(999L), respVO.getDuplicatePatientIds());
        String expectedPrefix = "PT" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertTrue(respVO.getPatientNo().startsWith(expectedPrefix));
        assertTrue(respVO.getPatientNo().endsWith("0123"));
        verify(patientMapper).updateById(org.mockito.ArgumentMatchers.<RehabPatientDO>argThat(update ->
                Objects.equals(update.getId(), 123L)
                        && Objects.equals(update.getPatientNo(), respVO.getPatientNo())));
        verify(operationLogMapper).insert(any(RehabPatientOperationLogDO.class));
    }

    @Test
    void checkCrmConflict_shouldReturnConflictWhenOtherPatientAlreadyBound() {
        RehabPatientCheckCrmConflictReqVO reqVO = new RehabPatientCheckCrmConflictReqVO();
        reqVO.setPatientId(10001L);
        reqVO.setCrmCustomerId(50001L);

        List<RehabPatientCrmBindingDO> bindings = List.of(
                RehabPatientCrmBindingDO.builder().patientId(10001L).bindStatus(RehabCrmBindingConstants.STATUS_BOUND).build(),
                RehabPatientCrmBindingDO.builder().patientId(10002L).bindStatus(RehabCrmBindingConstants.STATUS_BOUND).build(),
                RehabPatientCrmBindingDO.builder().patientId(10003L).bindStatus(RehabCrmBindingConstants.STATUS_CONFLICT).build()
        );
        when(crmBindingMapper.selectListByCrmCustomerId(50001L)).thenReturn(bindings);

        RehabCrmConflictCheckRespVO respVO = patientService.checkCrmConflict(reqVO);

        assertTrue(respVO.getConflict());
        assertEquals(Collections.singletonList(10002L), respVO.getConflictPatientIds());
    }

    @Test
    void transferTherapist_shouldCloseOldPrimaryAndCreateNewPrimary() {
        RehabPatientTransferReqVO reqVO = new RehabPatientTransferReqVO();
        reqVO.setPatientId(10001L);
        reqVO.setToTherapistUserId(104L);
        reqVO.setReason("阶段转交");

        when(patientMapper.selectById(10001L)).thenReturn(RehabPatientDO.builder().id(10001L).build());
        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);

        RehabTherapistAssignmentDO oldPrimary = RehabTherapistAssignmentDO.builder()
                .id(12001L)
                .patientId(10001L)
                .therapistUserId(100L)
                .roleType(RehabAssignmentConstants.ROLE_PRIMARY)
                .assignStatus(RehabAssignmentConstants.STATUS_ACTIVE)
                .build();
        when(assignmentMapper.selectActivePrimaryByPatientId(10001L)).thenReturn(oldPrimary);

        patientService.transferTherapist(reqVO, 1L);

        verify(assignmentMapper).updateById(org.mockito.ArgumentMatchers.<RehabTherapistAssignmentDO>argThat(item ->
                Objects.equals(item.getId(), 12001L)
                        && Objects.equals(item.getAssignStatus(), RehabAssignmentConstants.STATUS_TRANSFERRED)
                        && Objects.equals(item.getTransferToUserId(), 104L)
                        && item.getEndTime() != null));
        verify(assignmentMapper).insert(org.mockito.ArgumentMatchers.<RehabTherapistAssignmentDO>argThat(item ->
                Objects.equals(item.getPatientId(), 10001L)
                        && Objects.equals(item.getTherapistUserId(), 104L)
                        && Objects.equals(item.getRoleType(), RehabAssignmentConstants.ROLE_PRIMARY)
                        && Objects.equals(item.getTransferFromUserId(), 100L)));
        verify(patientMapper).updateById(org.mockito.ArgumentMatchers.<RehabPatientDO>argThat(item ->
                Objects.equals(item.getId(), 10001L)
                        && Objects.equals(item.getCurrentTherapistUserId(), 104L)));
        verify(operationLogMapper).insert(any(RehabPatientOperationLogDO.class));
    }

}
