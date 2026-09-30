package cn.iocoder.yudao.module.rehab.service.motion;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTaskDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionAssessmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionManualEditMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionTaskMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.log.RehabAuditLogService;
import cn.iocoder.yudao.module.rehab.service.motion.task.RehabMotionTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.PATIENT_NO_PERMISSION;
import static cn.iocoder.yudao.module.rehab.enums.RehabMotionErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RehabMotionAccessAndTaskTest {

    @Mock
    private RehabMotionAssessmentMapper assessmentMapper;
    @Mock
    private RehabMotionManualEditMapper manualEditMapper;
    @Mock
    private RehabDataPermissionService dataPermissionService;
    @Mock
    private RehabAuditLogService auditLogService;
    @Mock
    private RehabMotionTaskMapper taskMapper;

    private RehabMotionAccess access;
    private RehabMotionTaskService taskService;

    @BeforeEach
    void setUp() {
        access = new RehabMotionAccess();
        ReflectionTestUtils.setField(access, "assessmentMapper", assessmentMapper);
        ReflectionTestUtils.setField(access, "manualEditMapper", manualEditMapper);
        ReflectionTestUtils.setField(access, "dataPermissionService", dataPermissionService);
        ReflectionTestUtils.setField(access, "auditLogService", auditLogService);
        taskService = new RehabMotionTaskService();
        ReflectionTestUtils.setField(taskService, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(taskService, "assessmentMapper", assessmentMapper);
    }

    private static int code(Runnable r) {
        return assertThrows(ServiceException.class, r::run).getCode();
    }

    // ---------------------------------------------------------------- 租户 / 数据权限

    @Test
    void everyMotionTableIsTenantScoped() throws Exception {
        URL url = RehabMotionAssessmentDO.class.getResource("RehabMotionAssessmentDO.class");
        File dir = new File(url.toURI()).getParentFile();
        int n = 0;
        for (String name : dir.list()) {
            if (name.endsWith("DO.class") && name.startsWith("RehabMotion")) {
                Class<?> c = Class.forName(RehabMotionAssessmentDO.class.getPackage().getName() + "."
                        + name.substring(0, name.length() - 6));
                assertTrue(TenantBaseDO.class.isAssignableFrom(c), c.getSimpleName() + " 必须继承 TenantBaseDO");
                n++;
            }
        }
        assertEquals(11, n, "11 张 rehab_motion_* 表");
    }

    @Test
    void otherTenantRowIsInvisibleAndOtherPatientIsForbidden() {
        // 租户拦截器下跨租户 selectById 返回 null
        when(assessmentMapper.selectById(1L)).thenReturn(null);
        assertEquals(MOTION_NOT_EXISTS.getCode(), code(() -> access.readable(1L, 5L)));
        RehabMotionAssessmentDO a = RehabMotionAssessmentDO.builder().id(2L).patientId(9L)
                .status(RehabMotionConstants.STATE_PENDING_REVIEW).build();
        when(assessmentMapper.selectById(2L)).thenReturn(a);
        when(dataPermissionService.canReadPatient(9L, 5L)).thenReturn(false);
        assertEquals(PATIENT_NO_PERMISSION.getCode(), code(() -> access.readable(2L, 5L)));
        when(dataPermissionService.canReadPatient(9L, 6L)).thenReturn(true);
        assertSame(a, access.readable(2L, 6L));
    }

    @Test
    void signedAssessmentIsReadOnly() {
        RehabMotionAssessmentDO a = RehabMotionAssessmentDO.builder().id(3L).patientId(9L)
                .status(RehabMotionConstants.STATE_COMPLETED).build();
        when(assessmentMapper.selectById(3L)).thenReturn(a);
        when(dataPermissionService.canReadPatient(9L, 6L)).thenReturn(true);
        assertEquals(MOTION_SIGNED_LOCKED.getCode(), code(() -> access.editable(3L, 6L)));
    }

    @Test
    void clerkCannotPerformClinicalActions() {
        when(dataPermissionService.isClerk(4L)).thenReturn(true);
        assertThrows(ServiceException.class, () -> access.requireClinician(4L));
        when(dataPermissionService.isClerk(5L)).thenReturn(true);
        when(dataPermissionService.isTherapist(5L)).thenReturn(true);
        access.requireClinician(5L);
    }

    // ---------------------------------------------------------------- 任务幂等 / 互斥

    private static RehabMotionAssessmentDO assessment() {
        return RehabMotionAssessmentDO.builder().id(10L).dataSource("upload").inputRevision(2).build();
    }

    @Test
    void sameIdempotencyKeyReturnsExistingTask() {
        RehabMotionTaskDO existing = RehabMotionTaskDO.builder().id(77L).build();
        when(taskMapper.selectByIdempotencyKey("10:PIPELINE:client-key-123")).thenReturn(existing);
        RehabMotionTaskDO t = taskService.enqueue(assessment(), RehabMotionConstants.TASK_PIPELINE,
                RehabMotionConstants.STATE_PARSING, "client-key-123", 1L, null);
        assertSame(existing, t);
        verify(taskMapper, never()).insert(any(RehabMotionTaskDO.class));
    }

    @Test
    void concurrentDuplicateInsertResolvesToExistingTask() {
        RehabMotionTaskDO existing = RehabMotionTaskDO.builder().id(78L).build();
        when(taskMapper.selectByIdempotencyKey(anyString())).thenReturn(null, existing);
        when(taskMapper.selectActiveByAssessment(eq(10L), any())).thenReturn(new ArrayList<RehabMotionTaskDO>());
        when(taskMapper.insert(any(RehabMotionTaskDO.class))).thenThrow(new DuplicateKeyException("dup"));
        assertSame(existing, taskService.enqueue(assessment(), RehabMotionConstants.TASK_PIPELINE,
                RehabMotionConstants.STATE_PARSING, "client-key-456", 1L, null));
    }

    @Test
    void activeTaskInSameGroupRejectedButPdfIndependent() {
        List<RehabMotionTaskDO> active = Collections.singletonList(
                RehabMotionTaskDO.builder().id(1L).taskType(RehabMotionConstants.TASK_AI).build());
        when(taskMapper.selectActiveByAssessment(eq(10L), any())).thenReturn(active);
        assertEquals(MOTION_TASK_ACTIVE.getCode(), code(() -> taskService.enqueue(assessment(),
                RehabMotionConstants.TASK_RESCORE, RehabMotionConstants.STATE_PARSING, null, 1L, null)));
        when(taskMapper.insert(any(RehabMotionTaskDO.class))).thenReturn(1);
        RehabMotionTaskDO pdf = taskService.enqueue(assessment(), RehabMotionConstants.TASK_PDF,
                RehabMotionConstants.STATE_PDF_RENDERING, null, 1L, null);
        assertEquals(RehabMotionConstants.STATE_PDF_RENDERING, pdf.getState());
        ArgumentCaptor<RehabMotionTaskDO> captor = ArgumentCaptor.forClass(RehabMotionTaskDO.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals(Integer.valueOf(2), captor.getValue().getInputRevision());
        assertEquals(Integer.valueOf(0), captor.getValue().getAttempts());
    }

    @Test
    void malformedIdempotencyKeyRejected() {
        assertEquals(MOTION_IDEMPOTENCY_KEY_INVALID.getCode(), code(() -> taskService.enqueue(assessment(),
                RehabMotionConstants.TASK_PIPELINE, RehabMotionConstants.STATE_PARSING, "bad key;drop", 1L, null)));
    }

}
