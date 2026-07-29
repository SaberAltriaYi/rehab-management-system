package cn.iocoder.yudao.module.rehab.service.ai;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.rehab.controller.admin.ai.vo.RehabAiGenerateAssessmentInterpretReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.ai.vo.RehabAiGenerateRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.ai.vo.RehabAiOutputAcceptReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.ai.vo.RehabAiOutputRespVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.ai.RehabAiConfigDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.ai.RehabAiJobDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.ai.RehabAiOutputDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentRecordDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.ai.*;
import cn.iocoder.yudao.module.rehab.dal.mysql.alert.RehabAlertEventMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assignment.RehabTherapistAssignmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentModuleDataMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.binding.RehabPatientUserBindingMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabDailyCheckinMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.progress.RehabProgressRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.report.RehabReportMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.trigger.RehabReassessmentTriggerMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabAiConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabAssessmentConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabRoleCodeConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.ai.client.OpenAiResponsesClient;
import cn.iocoder.yudao.module.rehab.service.ai.client.PlatformAiBridgeClient;
import cn.iocoder.yudao.module.rehab.service.log.RehabAuditLogService;
import cn.iocoder.yudao.module.rehab.service.notification.RehabNotificationService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.AI_REVIEW_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RehabAiServiceImplTest {

    private RehabAiServiceImpl service;

    @Mock
    private RehabAiJobMapper aiJobMapper;
    @Mock
    private RehabAiOutputMapper aiOutputMapper;
    @Mock
    private RehabAiPromptTemplateMapper promptTemplateMapper;
    @Mock
    private RehabAiConfigMapper aiConfigMapper;
    @Mock
    private RehabAiReviewLogMapper aiReviewLogMapper;
    @Mock
    private RehabAiSuggestionBundleMapper suggestionBundleMapper;
    @Mock
    private cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper patientMapper;
    @Mock
    private RehabAssessmentRecordMapper assessmentRecordMapper;
    @Mock
    private RehabAssessmentModuleDataMapper assessmentModuleDataMapper;
    @Mock
    private RehabReportMapper reportMapper;
    @Mock
    private RehabCarePlanMapper carePlanMapper;
    @Mock
    private RehabProgressRecordMapper progressRecordMapper;
    @Mock
    private RehabAlertEventMapper alertEventMapper;
    @Mock
    private RehabReassessmentTriggerMapper triggerMapper;
    @Mock
    private RehabDailyCheckinMapper checkinMapper;
    @Mock
    private PermissionApi permissionApi;
    @Mock
    private RehabTherapistAssignmentMapper assignmentMapper;
    @Mock
    private RehabPatientUserBindingMapper patientUserBindingMapper;
    @Mock
    private RehabAuditLogService auditLogService;
    @Mock
    private RehabNotificationService notificationService;
    @Mock
    private AdminUserApi adminUserApi;
    private OpenAiResponsesClient openAiClient;
    private PlatformAiBridgeClient platformAiBridgeClient;
    private RehabDataPermissionService dataPermissionService;

    @BeforeEach
    void setUp() {
        service = new RehabAiServiceImpl();
        openAiClient = new OpenAiResponsesClient();
        platformAiBridgeClient = new PlatformAiBridgeClient();
        dataPermissionService = new RehabDataPermissionService();
        ReflectionTestUtils.setField(dataPermissionService, "permissionApi", permissionApi);
        ReflectionTestUtils.setField(dataPermissionService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(dataPermissionService, "assignmentMapper", assignmentMapper);
        ReflectionTestUtils.setField(dataPermissionService, "patientUserBindingMapper", patientUserBindingMapper);

        ReflectionTestUtils.setField(service, "aiJobMapper", aiJobMapper);
        ReflectionTestUtils.setField(service, "aiOutputMapper", aiOutputMapper);
        ReflectionTestUtils.setField(service, "promptTemplateMapper", promptTemplateMapper);
        ReflectionTestUtils.setField(service, "aiConfigMapper", aiConfigMapper);
        ReflectionTestUtils.setField(service, "aiReviewLogMapper", aiReviewLogMapper);
        ReflectionTestUtils.setField(service, "suggestionBundleMapper", suggestionBundleMapper);
        ReflectionTestUtils.setField(service, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(service, "assessmentRecordMapper", assessmentRecordMapper);
        ReflectionTestUtils.setField(service, "assessmentModuleDataMapper", assessmentModuleDataMapper);
        ReflectionTestUtils.setField(service, "reportMapper", reportMapper);
        ReflectionTestUtils.setField(service, "carePlanMapper", carePlanMapper);
        ReflectionTestUtils.setField(service, "progressRecordMapper", progressRecordMapper);
        ReflectionTestUtils.setField(service, "alertEventMapper", alertEventMapper);
        ReflectionTestUtils.setField(service, "triggerMapper", triggerMapper);
        ReflectionTestUtils.setField(service, "checkinMapper", checkinMapper);
        ReflectionTestUtils.setField(service, "dataPermissionService", dataPermissionService);
        ReflectionTestUtils.setField(service, "auditLogService", auditLogService);
        ReflectionTestUtils.setField(service, "notificationService", notificationService);
        ReflectionTestUtils.setField(service, "adminUserApi", adminUserApi);
        ReflectionTestUtils.setField(service, "openAiClient", openAiClient);
        ReflectionTestUtils.setField(service, "platformAiBridgeClient", platformAiBridgeClient);
        ReflectionTestUtils.setField(service, "taskExecutor", null);
        ReflectionTestUtils.setField(service, "schemaValidator", new RehabAiSchemaValidator());
        ReflectionTestUtils.setField(service, "safetyGuard", new RehabAiSafetyGuard());
        ReflectionTestUtils.setField(service, "fallbackService", new RehabAiFallbackService());

        ReflectionTestUtils.setField(service, "aiAnalysisEnabled", false);
        ReflectionTestUtils.setField(service, "defaultModel", "gpt-4.1-mini");
        ReflectionTestUtils.setField(service, "defaultTemperature", 0.2D);
        ReflectionTestUtils.setField(service, "defaultMaxOutputTokens", 1200);
        ReflectionTestUtils.setField(service, "defaultReasoningEffort", "medium");
        ReflectionTestUtils.setField(service, "defaultTimeoutSeconds", 30);
        ReflectionTestUtils.setField(service, "defaultMaxRetries", 1);
        ReflectionTestUtils.setField(service, "mockMode", true);
        ReflectionTestUtils.setField(service, "usePlatformBridge", false);
    }

    @AfterEach
    void tearDown() {
        openAiClient = null;
        platformAiBridgeClient = null;
    }

    @Test
    void generateAssessmentInterpretation_shouldFallbackAndKeepMainFlow() {
        Long operatorId = 100L;
        Long assessmentId = 20001L;
        Long patientId = 10001L;

        when(permissionApi.hasAnyRoles(operatorId, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(permissionApi.hasAnyRoles(operatorId, RehabRoleCodeConstants.REHAB_CLERK)).thenReturn(false);

        when(assessmentRecordMapper.selectById(assessmentId)).thenReturn(RehabAssessmentRecordDO.builder()
                .id(assessmentId)
                .patientId(patientId)
                .episodeId(13001L)
                .status(RehabAssessmentConstants.STATUS_COMPLETED)
                .build());
        when(assessmentModuleDataMapper.selectListByAssessmentId(assessmentId)).thenReturn(Collections.emptyList());
        when(promptTemplateMapper.selectDefaultTemplate(anyString(), anyString())).thenReturn(null);
        when(aiConfigMapper.selectGlobalConfig()).thenReturn(defaultConfig());

        AtomicReference<RehabAiJobDO> jobRef = new AtomicReference<RehabAiJobDO>();
        AtomicReference<RehabAiOutputDO> outputRef = new AtomicReference<RehabAiOutputDO>();

        doAnswer(invocation -> {
            RehabAiJobDO job = invocation.getArgument(0);
            job.setId(9001L);
            jobRef.set(job);
            return 1;
        }).when(aiJobMapper).insert(any(RehabAiJobDO.class));
        when(aiJobMapper.selectById(9001L)).thenAnswer(invocation -> jobRef.get());
        doAnswer(invocation -> {
            RehabAiJobDO update = invocation.getArgument(0);
            mergeJob(jobRef.get(), update);
            return 1;
        }).when(aiJobMapper).updateById(any(RehabAiJobDO.class));

        doAnswer(invocation -> {
            RehabAiOutputDO output = invocation.getArgument(0);
            output.setId(9101L);
            outputRef.set(output);
            return 1;
        }).when(aiOutputMapper).insert(any(RehabAiOutputDO.class));
        when(aiOutputMapper.selectLatestByJobId(9001L)).thenAnswer(invocation -> outputRef.get());

        RehabAiGenerateAssessmentInterpretReqVO reqVO = new RehabAiGenerateAssessmentInterpretReqVO();
        reqVO.setAssessmentId(assessmentId);
        reqVO.setAsyncMode(false);
        RehabAiGenerateRespVO respVO = service.generateAssessmentInterpretation(reqVO, operatorId);

        assertNotNull(respVO);
        assertEquals("fallback_used", respVO.getJobStatus());
        assertEquals(Boolean.TRUE, respVO.getFallbackUsed());
        assertNotNull(respVO.getOutputId());
        assertTrue(respVO.getRenderedText().contains("证据不足"));
        verify(aiOutputMapper, times(1)).insert(any(RehabAiOutputDO.class));
        verify(auditLogService, atLeastOnce()).createAuditLog(anyString(), anyLong(), anyString(),
                anyLong(), anyString(), any(), any(), anyString(), anyString());
    }

    @Test
    void acceptOutput_shouldRejectClerk() {
        Long clerkUserId = 300L;
        when(permissionApi.hasAnyRoles(clerkUserId, RehabRoleCodeConstants.REHAB_CLERK)).thenReturn(true);

        RehabAiOutputAcceptReqVO reqVO = new RehabAiOutputAcceptReqVO();
        reqVO.setOutputId(1L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.acceptOutput(reqVO, clerkUserId));
        assertEquals(AI_REVIEW_FORBIDDEN.getCode(), ex.getCode());
        verify(aiOutputMapper, never()).selectById(anyLong());
    }

    @Test
    void getLatestPatientVisibleSummary_shouldOnlyReturnAcceptedVisibleOutput() {
        Long patientId = 10001L;
        Long appUserId = 90000001L;
        when(permissionApi.hasAnyRoles(appUserId, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);

        RehabAiJobDO job = RehabAiJobDO.builder()
                .id(9201L)
                .jobNo("AIJ202603100001")
                .patientId(patientId)
                .build();
        when(aiJobMapper.selectListByPatientId(patientId)).thenReturn(Collections.singletonList(job));
        when(aiJobMapper.selectListByIds(anyCollection())).thenReturn(Collections.singletonList(job));
        when(patientMapper.selectBatchIds(anyCollection())).thenReturn(Collections.singletonList(
                RehabPatientDO.builder().id(patientId).name("测试患者").build()));

        RehabAiOutputDO visibleOutput = RehabAiOutputDO.builder()
                .id(9301L)
                .aiJobId(9201L)
                .outputType(RehabAiConstants.OUTPUT_TYPE_PATIENT_SUMMARY)
                .reviewStatus(RehabAiConstants.REVIEW_STATUS_ACCEPTED)
                .patientVisible(true)
                .renderedText("这是患者可见摘要")
                .build();
        when(aiOutputMapper.selectList(any())).thenReturn(Arrays.asList(visibleOutput));

        RehabAiOutputRespVO respVO = service.getLatestPatientVisibleSummary(patientId, appUserId);
        assertNotNull(respVO);
        assertEquals(9301L, respVO.getId());
        assertEquals(patientId, respVO.getPatientId());
        assertEquals("测试患者", respVO.getPatientName());
    }

    private RehabAiConfigDO defaultConfig() {
        return RehabAiConfigDO.builder()
                .id(1L)
                .configScope(RehabAiConstants.CONFIG_SCOPE_GLOBAL)
                .scopeId(0L)
                .aiEnabled(true)
                .enableAssessmentInterpretation(true)
                .enableReportSummary(true)
                .enablePatientSummary(true)
                .enablePlanDraft(true)
                .enableFollowupWriter(true)
                .requireHumanReviewBeforeVisible(true)
                .visibleToPatientAfterReviewOnly(true)
                .preferredModelName("gpt-4.1-mini")
                .promptStyle(RehabAiConstants.PROMPT_STYLE_STANDARD)
                .safetyMode(RehabAiConstants.SAFETY_MODE_STRICT)
                .build();
    }

    private void mergeJob(RehabAiJobDO target, RehabAiJobDO update) {
        if (target == null || update == null) {
            return;
        }
        if (update.getJobNo() != null) target.setJobNo(update.getJobNo());
        if (update.getStatus() != null) target.setStatus(update.getStatus());
        if (update.getFallbackUsed() != null) target.setFallbackUsed(update.getFallbackUsed());
        if (update.getResponsePayloadJson() != null) target.setResponsePayloadJson(update.getResponsePayloadJson());
        if (update.getOutputHash() != null) target.setOutputHash(update.getOutputHash());
        if (update.getLatencyMs() != null) target.setLatencyMs(update.getLatencyMs());
        if (update.getTokenUsageJson() != null) target.setTokenUsageJson(update.getTokenUsageJson());
    }
}
