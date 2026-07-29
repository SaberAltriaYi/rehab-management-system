package cn.iocoder.yudao.module.rehab.service.task;

import cn.iocoder.yudao.module.rehab.controller.admin.task.vo.RehabExerciseTaskCreateReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.task.vo.RehabExerciseTaskSortReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.task.RehabExerciseTaskDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.log.RehabPlanOperationLogMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.task.RehabExerciseTaskMapper;
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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RehabExerciseTaskServiceImplTest {

    private RehabExerciseTaskServiceImpl taskService;

    @Mock
    private RehabExerciseTaskMapper taskMapper;
    @Mock
    private RehabCarePlanMapper planMapper;
    @Mock
    private RehabPatientMapper patientMapper;
    @Mock
    private RehabPlanOperationLogMapper planOperationLogMapper;
    @Mock
    private PermissionApi permissionApi;
    @Mock
    private AdminUserApi adminUserApi;

    @BeforeEach
    void setUp() {
        taskService = new RehabExerciseTaskServiceImpl();

        RehabDataPermissionService dataPermissionService = new RehabDataPermissionService();
        ReflectionTestUtils.setField(dataPermissionService, "permissionApi", permissionApi);
        ReflectionTestUtils.setField(dataPermissionService, "patientMapper", patientMapper);

        ReflectionTestUtils.setField(taskService, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(taskService, "planMapper", planMapper);
        ReflectionTestUtils.setField(taskService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(taskService, "planOperationLogMapper", planOperationLogMapper);
        ReflectionTestUtils.setField(taskService, "dataPermissionService", dataPermissionService);
        ReflectionTestUtils.setField(taskService, "adminUserApi", adminUserApi);

        lenient().when(permissionApi.hasAnyRoles(anyLong(), anyString())).thenReturn(false);
    }

    @Test
    void createTask_shouldGenerateTaskNo() {
        RehabExerciseTaskCreateReqVO reqVO = new RehabExerciseTaskCreateReqVO();
        reqVO.setPlanId(40001L);
        reqVO.setPatientId(10001L);
        reqVO.setEpisodeId(13001L);
        reqVO.setTaskName("分腿蹲控制");
        reqVO.setExecutionType("both");

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(planMapper.selectById(40001L)).thenReturn(RehabCarePlanDO.builder()
                .id(40001L).patientId(10001L).episodeId(13001L).build());
        when(taskMapper.selectListByPlanId(40001L)).thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            RehabExerciseTaskDO item = invocation.getArgument(0);
            item.setId(41001L);
            return 1;
        }).when(taskMapper).insert(any(RehabExerciseTaskDO.class));
        when(taskMapper.selectById(41001L)).thenReturn(RehabExerciseTaskDO.builder()
                .id(41001L).planId(40001L).patientId(10001L).build());

        Long taskId = taskService.createTask(reqVO, 1L);

        assertEquals(41001L, taskId);
        verify(taskMapper).updateById(org.mockito.ArgumentMatchers.<RehabExerciseTaskDO>argThat(item ->
                Objects.equals(item.getId(), 41001L)
                        && item.getTaskNo() != null
                        && item.getTaskNo().startsWith("TSK")));
    }

    @Test
    void sortTasks_shouldUpdateSortOrder() {
        RehabExerciseTaskSortReqVO reqVO = new RehabExerciseTaskSortReqVO();
        reqVO.setPlanId(40001L);
        RehabExerciseTaskSortReqVO.Item item = new RehabExerciseTaskSortReqVO.Item();
        item.setId(41001L);
        item.setSortOrder(3);
        reqVO.setItems(List.of(item));

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(planMapper.selectById(40001L)).thenReturn(RehabCarePlanDO.builder()
                .id(40001L).patientId(10001L).episodeId(13001L).build());
        when(taskMapper.selectById(41001L)).thenReturn(RehabExerciseTaskDO.builder()
                .id(41001L).planId(40001L).patientId(10001L).build());

        taskService.sortTasks(reqVO, 1L);

        verify(taskMapper).updateById(org.mockito.ArgumentMatchers.<RehabExerciseTaskDO>argThat(task ->
                Objects.equals(task.getId(), 41001L)
                        && Objects.equals(task.getSortOrder(), 3)));
        assertTrue(true);
    }

}
