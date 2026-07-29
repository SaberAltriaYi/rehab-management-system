package cn.iocoder.yudao.module.rehab.service.episode;

import cn.iocoder.yudao.module.rehab.controller.admin.episode.vo.RehabEpisodeChangeStageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.episode.RehabEpisodeDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.log.RehabPatientOperationLogDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.assignment.RehabTherapistAssignmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.episode.RehabEpisodeMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.log.RehabPatientOperationLogMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabEpisodeConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabPatientStatusConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabRoleCodeConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabStageConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RehabEpisodeServiceImplTest {

    private RehabEpisodeServiceImpl episodeService;

    @Mock
    private RehabEpisodeMapper episodeMapper;
    @Mock
    private RehabPatientMapper patientMapper;
    @Mock
    private RehabTherapistAssignmentMapper assignmentMapper;
    @Mock
    private RehabPatientOperationLogMapper operationLogMapper;
    @Mock
    private PermissionApi permissionApi;
    @Mock
    private AdminUserApi adminUserApi;

    @BeforeEach
    void setUp() {
        episodeService = new RehabEpisodeServiceImpl();
        RehabDataPermissionService dataPermissionService = new RehabDataPermissionService();
        ReflectionTestUtils.setField(dataPermissionService, "permissionApi", permissionApi);
        ReflectionTestUtils.setField(dataPermissionService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(dataPermissionService, "assignmentMapper", assignmentMapper);

        ReflectionTestUtils.setField(episodeService, "episodeMapper", episodeMapper);
        ReflectionTestUtils.setField(episodeService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(episodeService, "operationLogMapper", operationLogMapper);
        ReflectionTestUtils.setField(episodeService, "dataPermissionService", dataPermissionService);
        ReflectionTestUtils.setField(episodeService, "adminUserApi", adminUserApi);
    }

    @Test
    void changeStage_shouldUpdateEpisodeAndPatientStatus() {
        RehabEpisodeDO episodeDO = RehabEpisodeDO.builder()
                .id(13001L)
                .patientId(10001L)
                .currentStage(RehabStageConstants.PENDING_ASSESSMENT)
                .status(RehabEpisodeConstants.STATUS_ACTIVE)
                .build();
        when(episodeMapper.selectById(13001L)).thenReturn(episodeDO);
        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);

        RehabEpisodeChangeStageReqVO reqVO = new RehabEpisodeChangeStageReqVO();
        reqVO.setId(13001L);
        reqVO.setCurrentStage(RehabStageConstants.REASSESSING);
        reqVO.setStatus(RehabEpisodeConstants.STATUS_PAUSED);
        reqVO.setRemark("进入复评");

        episodeService.changeStage(reqVO, 1L);

        verify(episodeMapper).updateById(org.mockito.ArgumentMatchers.<RehabEpisodeDO>argThat(item ->
                Objects.equals(item.getId(), 13001L)
                        && Objects.equals(item.getCurrentStage(), RehabStageConstants.REASSESSING)
                        && Objects.equals(item.getStatus(), RehabEpisodeConstants.STATUS_PAUSED)));
        verify(patientMapper).updateById(org.mockito.ArgumentMatchers.<RehabPatientDO>argThat(item ->
                Objects.equals(item.getId(), 10001L)
                        && Objects.equals(item.getCurrentStage(), RehabStageConstants.REASSESSING)
                        && Objects.equals(item.getCurrentStatus(), RehabPatientStatusConstants.INACTIVE)));
        verify(operationLogMapper).insert(any(RehabPatientOperationLogDO.class));
    }

}
