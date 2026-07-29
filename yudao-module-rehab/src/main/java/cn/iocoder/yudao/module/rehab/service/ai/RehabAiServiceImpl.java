package cn.iocoder.yudao.module.rehab.service.ai;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.ai.vo.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.ai.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.alert.RehabAlertEventDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentModuleDataDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentRecordDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.checkin.RehabDailyCheckinDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.progress.RehabProgressRecordDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.report.RehabReportDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.trigger.RehabReassessmentTriggerDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.ai.*;
import cn.iocoder.yudao.module.rehab.dal.mysql.alert.RehabAlertEventMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentModuleDataMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabDailyCheckinMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.progress.RehabProgressRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.report.RehabReportMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.trigger.RehabReassessmentTriggerMapper;
import cn.iocoder.yudao.module.rehab.enums.*;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.ai.client.OpenAiResponsesClient;
import cn.iocoder.yudao.module.rehab.service.ai.client.PlatformAiBridgeClient;
import cn.iocoder.yudao.module.rehab.service.ai.client.RehabAiClient;
import cn.iocoder.yudao.module.rehab.service.ai.client.RehabAiClientOptions;
import cn.iocoder.yudao.module.rehab.service.ai.client.RehabAiClientResponse;
import cn.iocoder.yudao.module.rehab.service.ai.model.RehabAiPromptRegistry;
import cn.iocoder.yudao.module.rehab.service.log.RehabAuditLogService;
import cn.iocoder.yudao.module.rehab.service.notification.RehabNotificationService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

/**
 * AI 增强层 Service
 */
@Service
@Validated
@Slf4j
public class RehabAiServiceImpl implements RehabAiService {

    private static final DateTimeFormatter JOB_NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Value("${yudao.rehab.ai.enable-analysis:${OPENAI_ENABLE_AI_ANALYSIS:false}}")
    private Boolean aiAnalysisEnabled;
    @Value("${yudao.rehab.ai.default-model:${OPENAI_MODEL:gpt-4.1-mini}}")
    private String defaultModel;
    @Value("${yudao.rehab.ai.default-temperature:0.2}")
    private Double defaultTemperature;
    @Value("${yudao.rehab.ai.default-max-output-tokens:1200}")
    private Integer defaultMaxOutputTokens;
    @Value("${yudao.rehab.ai.default-reasoning-effort:${OPENAI_REASONING_EFFORT:medium}}")
    private String defaultReasoningEffort;
    @Value("${yudao.rehab.ai.default-timeout-seconds:${OPENAI_TIMEOUT_SECONDS:45}}")
    private Integer defaultTimeoutSeconds;
    @Value("${yudao.rehab.ai.default-max-retries:${OPENAI_MAX_RETRIES:2}}")
    private Integer defaultMaxRetries;
    @Value("${yudao.rehab.ai.mock-mode:false}")
    private Boolean mockMode;
    @Value("${yudao.rehab.ai.use-platform-bridge:false}")
    private Boolean usePlatformBridge;

    @Resource
    private RehabAiJobMapper aiJobMapper;
    @Resource
    private RehabAiOutputMapper aiOutputMapper;
    @Resource
    private RehabAiPromptTemplateMapper promptTemplateMapper;
    @Resource
    private RehabAiConfigMapper aiConfigMapper;
    @Resource
    private RehabAiReviewLogMapper aiReviewLogMapper;
    @Resource
    private RehabAiSuggestionBundleMapper suggestionBundleMapper;
    @Resource
    private RehabPatientMapper patientMapper;
    @Resource
    private RehabAssessmentRecordMapper assessmentRecordMapper;
    @Resource
    private RehabAssessmentModuleDataMapper assessmentModuleDataMapper;
    @Resource
    private RehabReportMapper reportMapper;
    @Resource
    private RehabCarePlanMapper carePlanMapper;
    @Resource
    private RehabProgressRecordMapper progressRecordMapper;
    @Resource
    private RehabAlertEventMapper alertEventMapper;
    @Resource
    private RehabReassessmentTriggerMapper triggerMapper;
    @Resource
    private RehabDailyCheckinMapper checkinMapper;
    @Resource
    private RehabDataPermissionService dataPermissionService;
    @Resource
    private RehabAuditLogService auditLogService;
    @Resource
    private RehabNotificationService notificationService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private OpenAiResponsesClient openAiClient;
    @Autowired(required = false)
    private PlatformAiBridgeClient platformAiBridgeClient;
    @Autowired(required = false)
    private TaskExecutor taskExecutor;
    @Resource
    private RehabAiSchemaValidator schemaValidator;
    @Resource
    private RehabAiSafetyGuard safetyGuard;
    @Resource
    private RehabAiFallbackService fallbackService;

    @Override
    public PageResult<RehabAiJobRespVO> getJobPage(RehabAiJobPageReqVO reqVO, Long operatorUserId) {
        Set<Long> visiblePatientIds = dataPermissionService.getVisiblePatientIds(operatorUserId);
        if (visiblePatientIds != null && visiblePatientIds.isEmpty()) {
            return PageResult.empty();
        }
        if (reqVO.getPatientId() != null) {
            validatePatientReadable(reqVO.getPatientId(), operatorUserId);
            visiblePatientIds = Collections.singleton(reqVO.getPatientId());
        }
        PageResult<RehabAiJobDO> pageResult = aiJobMapper.selectPage(reqVO, visiblePatientIds);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        return new PageResult<RehabAiJobRespVO>(toJobRespList(pageResult.getList()), pageResult.getTotal());
    }

    @Override
    public RehabAiJobRespVO getJob(Long id, Long operatorUserId) {
        RehabAiJobDO job = validateJobReadable(id, operatorUserId);
        return toJobRespList(Collections.singletonList(job)).get(0);
    }

    @Override
    public PageResult<RehabAiOutputRespVO> getOutputPage(RehabAiOutputPageReqVO reqVO, Long operatorUserId) {
        Collection<Long> visibleJobIds = resolveVisibleJobIds(reqVO.getPatientId(), operatorUserId);
        if (visibleJobIds != null && visibleJobIds.isEmpty()) {
            return PageResult.empty();
        }
        PageResult<RehabAiOutputDO> pageResult = aiOutputMapper.selectPage(reqVO, visibleJobIds);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        return new PageResult<RehabAiOutputRespVO>(toOutputRespList(pageResult.getList()), pageResult.getTotal());
    }

    @Override
    public RehabAiOutputRespVO getOutput(Long id, Long operatorUserId) {
        RehabAiOutputDO output = validateOutputReadable(id, operatorUserId);
        return toOutputRespList(Collections.singletonList(output)).get(0);
    }

    @Override
    public RehabAiGenerateRespVO generateAssessmentInterpretation(RehabAiGenerateAssessmentInterpretReqVO reqVO, Long operatorUserId) {
        RehabAssessmentRecordDO assessment = validateAssessmentReady(reqVO.getAssessmentId(), operatorUserId);
        Map<String, Object> payload = buildAssessmentPayload(assessment);
        payload.put("assessment_id", assessment.getId());
        return createAndRunJob(RehabAiConstants.JOB_TYPE_ASSESSMENT_INTERPRETATION, payload, reqVO.getAsyncMode(), operatorUserId);
    }

    @Override
    public RehabAiGenerateRespVO generateReportSummary(RehabAiGenerateReportSummaryReqVO reqVO, Long operatorUserId) {
        RehabReportDO report = validateReportReadable(reqVO.getReportId(), operatorUserId);
        Map<String, Object> payload = buildReportPayload(report);
        payload.put("report_id", report.getId());
        return createAndRunJob(RehabAiConstants.JOB_TYPE_REPORT_SUMMARY, payload, reqVO.getAsyncMode(), operatorUserId);
    }

    @Override
    public RehabAiGenerateRespVO generateRiskExplanation(RehabAiGenerateRiskExplanationReqVO reqVO, Long operatorUserId) {
        Long patientId = resolveRiskPatientId(reqVO);
        validatePatientReadable(patientId, operatorUserId);
        Map<String, Object> payload = buildRiskPayload(reqVO, patientId);
        payload.put("patient_id", patientId);
        return createAndRunJob(RehabAiConstants.JOB_TYPE_RISK_EXPLANATION, payload, reqVO.getAsyncMode(), operatorUserId);
    }

    @Override
    public RehabAiGenerateRespVO generatePlanDraft(RehabAiGeneratePlanDraftReqVO reqVO, Long operatorUserId) {
        if (reqVO.getAssessmentId() == null && reqVO.getReportId() == null && reqVO.getProgressId() == null
                && reqVO.getPatientId() == null) {
            throw exception(AI_PRECONDITION_FAILED);
        }
        Long patientId = resolvePlanDraftPatientId(reqVO);
        validatePatientReadable(patientId, operatorUserId);
        Map<String, Object> payload = buildPlanDraftPayload(reqVO, patientId);
        payload.put("patient_id", patientId);
        return createAndRunJob(RehabAiConstants.JOB_TYPE_PLAN_DRAFT_GENERATION, payload, reqVO.getAsyncMode(), operatorUserId);
    }

    @Override
    public RehabAiGenerateRespVO generateFollowupMessage(RehabAiGenerateFollowupMessageReqVO reqVO, Long operatorUserId) {
        if (reqVO.getProgressId() == null && reqVO.getTriggerId() == null && reqVO.getPatientId() == null) {
            throw exception(AI_PRECONDITION_FAILED);
        }
        Long patientId = resolveFollowupPatientId(reqVO);
        validatePatientReadable(patientId, operatorUserId);
        Map<String, Object> payload = buildFollowupPayload(reqVO, patientId);
        payload.put("patient_id", patientId);
        return createAndRunJob(RehabAiConstants.JOB_TYPE_FOLLOWUP_MESSAGE_GENERATION, payload, reqVO.getAsyncMode(), operatorUserId);
    }

    @Override
    public RehabAiGenerateRespVO generateProgressSummary(RehabAiGenerateProgressSummaryReqVO reqVO, Long operatorUserId) {
        RehabProgressRecordDO progress = validateProgressReadable(reqVO.getProgressId(), operatorUserId);
        Map<String, Object> payload = buildProgressPayload(progress);
        payload.put("progress_id", progress.getId());
        payload.put("patient_id", progress.getPatientId());
        return createAndRunJob(RehabAiConstants.JOB_TYPE_PROGRESS_SUMMARY, payload, reqVO.getAsyncMode(), operatorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptOutput(RehabAiOutputAcceptReqVO reqVO, Long operatorUserId) {
        validateCanReview(operatorUserId);
        RehabAiOutputDO output = validateOutputReadable(reqVO.getOutputId(), operatorUserId);
        if (RehabAiConstants.REVIEW_STATUS_REJECTED.equals(output.getReviewStatus())) {
            throw exception(AI_OUTPUT_REVIEW_STATUS_INVALID);
        }
        RehabAiConfigDO config = getOrCreateGlobalConfig();
        boolean patientVisible = Boolean.TRUE.equals(reqVO.getPatientVisible())
                && Boolean.TRUE.equals(config.getVisibleToPatientAfterReviewOnly());

        RehabAiOutputDO after = new RehabAiOutputDO().setId(output.getId())
                .setReviewStatus(RehabAiConstants.REVIEW_STATUS_ACCEPTED)
                .setPatientVisible(patientVisible)
                .setReviewedBy(operatorUserId)
                .setReviewedTime(LocalDateTime.now());
        aiOutputMapper.updateById(after);
        createReviewLog(output.getId(), operatorUserId, RehabAiConstants.REVIEW_ACTION_ACCEPT,
                output.getRenderedText(), output.getRenderedText(), reqVO.getReviewNote());
        updateJobReviewStatus(output.getAiJobId(), RehabAiConstants.JOB_STATUS_ACCEPTED);

        RehabAiOutputDO latest = aiOutputMapper.selectById(output.getId());
        RehabAiJobDO job = aiJobMapper.selectById(output.getAiJobId());
        auditLogService.createAuditLog("ai_output", output.getId(), RehabOperationTypeConstants.AI_ACCEPT,
                operatorUserId, resolveRole(operatorUserId), output, latest, "success", reqVO.getReviewNote());
        if (patientVisible && job != null) {
            notificationService.createSystemNotification(RehabNotificationConstants.TARGET_PATIENT, null,
                    job.getPatientId(), job.getEpisodeId(), RehabNotificationConstants.RELATED_SYSTEM, output.getId(),
                    RehabNotificationConstants.TYPE_SYSTEM_NOTICE, RehabNotificationConstants.SEVERITY_INFO,
                    "AI 摘要已更新", "您的康复摘要已更新，可在患者端查看。", RehabNotificationConstants.DELIVERY_APP_PATIENT,
                    "/pages/index/index", "查看摘要");
            auditLogService.createAuditLog("ai_output", output.getId(), RehabOperationTypeConstants.AI_PUBLISH_PATIENT,
                    operatorUserId, resolveRole(operatorUserId), null, latest, "success", "发布患者可见 AI 内容");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editOutput(RehabAiOutputEditReqVO reqVO, Long operatorUserId) {
        validateCanReview(operatorUserId);
        RehabAiOutputDO output = validateOutputReadable(reqVO.getOutputId(), operatorUserId);
        RehabAiConfigDO config = getOrCreateGlobalConfig();
        boolean patientVisible = Boolean.TRUE.equals(reqVO.getPatientVisible())
                && Boolean.TRUE.equals(config.getVisibleToPatientAfterReviewOnly());

        RehabAiOutputDO after = new RehabAiOutputDO().setId(output.getId())
                .setReviewStatus(RehabAiConstants.REVIEW_STATUS_EDITED)
                .setRenderedText(reqVO.getEditedText())
                .setPatientVisible(patientVisible)
                .setReviewedBy(operatorUserId)
                .setReviewedTime(LocalDateTime.now());
        aiOutputMapper.updateById(after);
        createReviewLog(output.getId(), operatorUserId, RehabAiConstants.REVIEW_ACTION_EDIT,
                output.getRenderedText(), reqVO.getEditedText(), reqVO.getReviewNote());
        updateJobReviewStatus(output.getAiJobId(), RehabAiConstants.JOB_STATUS_REVIEWED);

        auditLogService.createAuditLog("ai_output", output.getId(), RehabOperationTypeConstants.AI_EDIT,
                operatorUserId, resolveRole(operatorUserId), output, aiOutputMapper.selectById(output.getId()),
                "success", reqVO.getReviewNote());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectOutput(RehabAiOutputRejectReqVO reqVO, Long operatorUserId) {
        validateCanReview(operatorUserId);
        RehabAiOutputDO output = validateOutputReadable(reqVO.getOutputId(), operatorUserId);
        RehabAiOutputDO after = new RehabAiOutputDO().setId(output.getId())
                .setReviewStatus(RehabAiConstants.REVIEW_STATUS_REJECTED)
                .setPatientVisible(false)
                .setReviewedBy(operatorUserId)
                .setReviewedTime(LocalDateTime.now());
        aiOutputMapper.updateById(after);
        createReviewLog(output.getId(), operatorUserId, RehabAiConstants.REVIEW_ACTION_REJECT,
                output.getRenderedText(), null, reqVO.getReviewNote());
        updateJobReviewStatus(output.getAiJobId(), RehabAiConstants.JOB_STATUS_REJECTED);

        auditLogService.createAuditLog("ai_output", output.getId(), RehabOperationTypeConstants.AI_REJECT,
                operatorUserId, resolveRole(operatorUserId), output, aiOutputMapper.selectById(output.getId()),
                "success", reqVO.getReviewNote());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RehabAiGenerateRespVO regenerateOutput(RehabAiOutputRegenerateReqVO reqVO, Long operatorUserId) {
        validateCanReview(operatorUserId);
        RehabAiOutputDO output = validateOutputReadable(reqVO.getOutputId(), operatorUserId);
        RehabAiJobDO originJob = validateJobReadable(output.getAiJobId(), operatorUserId);
        Map<String, Object> payload = parseJsonMap(originJob.getRequestPayloadJson());
        payload.put("regenerate_from_output_id", output.getId());
        RehabAiGenerateRespVO respVO = createAndRunJob(originJob.getJobType(), payload, reqVO.getAsyncMode(), operatorUserId);
        createReviewLog(output.getId(), operatorUserId, RehabAiConstants.REVIEW_ACTION_REGENERATE,
                output.getRenderedText(), null, "重生成 jobId=" + respVO.getJobId());
        auditLogService.createAuditLog("ai_output", output.getId(), RehabOperationTypeConstants.AI_REGENERATE,
                operatorUserId, resolveRole(operatorUserId), output, respVO, "success", "重生成");
        return respVO;
    }

    @Override
    public RehabAiConfigRespVO getConfig(Long operatorUserId) {
        validateAiConfigPermission(operatorUserId, false);
        return BeanUtils.toBean(getOrCreateGlobalConfig(), RehabAiConfigRespVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(RehabAiConfigUpdateReqVO reqVO, Long operatorUserId) {
        validateAiConfigPermission(operatorUserId, true);
        RehabAiConfigDO config = getOrCreateGlobalConfig();
        RehabAiConfigDO before = BeanUtils.toBean(config, RehabAiConfigDO.class);

        RehabAiConfigDO updateObj = new RehabAiConfigDO().setId(config.getId())
                .setAiEnabled(defaultIfNull(reqVO.getAiEnabled(), config.getAiEnabled()))
                .setEnableAssessmentInterpretation(defaultIfNull(reqVO.getEnableAssessmentInterpretation(), config.getEnableAssessmentInterpretation()))
                .setEnableReportSummary(defaultIfNull(reqVO.getEnableReportSummary(), config.getEnableReportSummary()))
                .setEnablePatientSummary(defaultIfNull(reqVO.getEnablePatientSummary(), config.getEnablePatientSummary()))
                .setEnablePlanDraft(defaultIfNull(reqVO.getEnablePlanDraft(), config.getEnablePlanDraft()))
                .setEnableFollowupWriter(defaultIfNull(reqVO.getEnableFollowupWriter(), config.getEnableFollowupWriter()))
                .setRequireHumanReviewBeforeVisible(defaultIfNull(reqVO.getRequireHumanReviewBeforeVisible(), config.getRequireHumanReviewBeforeVisible()))
                .setVisibleToPatientAfterReviewOnly(defaultIfNull(reqVO.getVisibleToPatientAfterReviewOnly(), config.getVisibleToPatientAfterReviewOnly()))
                .setPreferredModelName(StrUtil.blankToDefault(reqVO.getPreferredModelName(), config.getPreferredModelName()))
                .setPromptStyle(StrUtil.blankToDefault(reqVO.getPromptStyle(), config.getPromptStyle()))
                .setSafetyMode(StrUtil.blankToDefault(reqVO.getSafetyMode(), config.getSafetyMode()))
                .setNote(reqVO.getNote());
        aiConfigMapper.updateById(updateObj);
        RehabAiConfigDO after = aiConfigMapper.selectById(config.getId());
        auditLogService.createAuditLog("ai_config", config.getId(), RehabOperationTypeConstants.AI_CONFIG_UPDATE,
                operatorUserId, resolveRole(operatorUserId), before, after, "success", "更新 AI 配置");
    }

    @Override
    public PageResult<RehabAiPromptTemplateRespVO> getPromptTemplatePage(RehabAiPromptTemplatePageReqVO reqVO, Long operatorUserId) {
        validateAiConfigPermission(operatorUserId, false);
        PageResult<RehabAiPromptTemplateDO> pageResult = promptTemplateMapper.selectPage(reqVO);
        return new PageResult<RehabAiPromptTemplateRespVO>(BeanUtils.toBean(pageResult.getList(), RehabAiPromptTemplateRespVO.class), pageResult.getTotal());
    }

    @Override
    public RehabAiPromptTemplateRespVO getPromptTemplate(Long id, Long operatorUserId) {
        validateAiConfigPermission(operatorUserId, false);
        RehabAiPromptTemplateDO template = validateTemplateExists(id);
        return BeanUtils.toBean(template, RehabAiPromptTemplateRespVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPromptTemplate(RehabAiPromptTemplateCreateReqVO reqVO, Long operatorUserId) {
        validateAiConfigPermission(operatorUserId, true);
        RehabAiPromptTemplateDO template = BeanUtils.toBean(reqVO, RehabAiPromptTemplateDO.class);
        template.setEnabled(defaultIfNull(reqVO.getEnabled(), true));
        template.setIsDefault(defaultIfNull(reqVO.getIsDefault(), false));
        promptTemplateMapper.insert(template);
        if (Boolean.TRUE.equals(template.getIsDefault())) {
            promptTemplateMapper.clearDefaultByScope(template.getModuleScope(), template.getRoleScope());
            promptTemplateMapper.updateById(new RehabAiPromptTemplateDO().setId(template.getId()).setIsDefault(true));
        }
        auditLogService.createAuditLog("ai_prompt_template", template.getId(), RehabOperationTypeConstants.AI_TEMPLATE_ENABLE,
                operatorUserId, resolveRole(operatorUserId), null, template, "success", "创建模板");
        return template.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePromptTemplate(RehabAiPromptTemplateUpdateReqVO reqVO, Long operatorUserId) {
        validateAiConfigPermission(operatorUserId, true);
        RehabAiPromptTemplateDO exists = validateTemplateExists(reqVO.getId());
        RehabAiPromptTemplateDO updateObj = BeanUtils.toBean(reqVO, RehabAiPromptTemplateDO.class);
        if (updateObj.getEnabled() == null) {
            updateObj.setEnabled(exists.getEnabled());
        }
        if (updateObj.getIsDefault() == null) {
            updateObj.setIsDefault(exists.getIsDefault());
        }
        promptTemplateMapper.updateById(updateObj);
        if (Boolean.TRUE.equals(updateObj.getIsDefault())) {
            promptTemplateMapper.clearDefaultByScope(updateObj.getModuleScope(), updateObj.getRoleScope());
            promptTemplateMapper.updateById(new RehabAiPromptTemplateDO().setId(updateObj.getId()).setIsDefault(true));
        }
        auditLogService.createAuditLog("ai_prompt_template", reqVO.getId(), RehabOperationTypeConstants.AI_TEMPLATE_ENABLE,
                operatorUserId, resolveRole(operatorUserId), exists, promptTemplateMapper.selectById(reqVO.getId()),
                "success", "更新模板");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enablePromptTemplate(RehabAiPromptTemplateEnableReqVO reqVO, Long operatorUserId) {
        validateAiConfigPermission(operatorUserId, true);
        RehabAiPromptTemplateDO exists = validateTemplateExists(reqVO.getId());
        promptTemplateMapper.updateById(new RehabAiPromptTemplateDO().setId(reqVO.getId()).setEnabled(reqVO.getEnabled()));
        auditLogService.createAuditLog("ai_prompt_template", reqVO.getId(), RehabOperationTypeConstants.AI_TEMPLATE_ENABLE,
                operatorUserId, resolveRole(operatorUserId), exists, promptTemplateMapper.selectById(reqVO.getId()),
                "success", "启停模板");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultPromptTemplate(RehabAiPromptTemplateSetDefaultReqVO reqVO, Long operatorUserId) {
        validateAiConfigPermission(operatorUserId, true);
        RehabAiPromptTemplateDO template = validateTemplateExists(reqVO.getId());
        promptTemplateMapper.clearDefaultByScope(template.getModuleScope(), template.getRoleScope());
        promptTemplateMapper.updateById(new RehabAiPromptTemplateDO().setId(reqVO.getId()).setIsDefault(true).setEnabled(true));
        auditLogService.createAuditLog("ai_prompt_template", reqVO.getId(), RehabOperationTypeConstants.AI_TEMPLATE_SET_DEFAULT,
                operatorUserId, resolveRole(operatorUserId), null, promptTemplateMapper.selectById(reqVO.getId()),
                "success", "设为默认模板");
    }

    @Override
    public RehabAiOutputRespVO getLatestSummaryForAdminPatient(Long patientId, Long operatorUserId) {
        validatePatientReadable(patientId, operatorUserId);
        RehabAiOutputDO output = selectLatestOutputByPatient(patientId, Arrays.asList(
                RehabAiConstants.OUTPUT_TYPE_THERAPIST_SUMMARY,
                RehabAiConstants.OUTPUT_TYPE_ADMIN_SUMMARY,
                RehabAiConstants.OUTPUT_TYPE_PROGRESS_SUMMARY,
                RehabAiConstants.OUTPUT_TYPE_RISK_EXPLANATION
        ), false, false);
        return output == null ? null : toOutputRespList(Collections.singletonList(output)).get(0);
    }

    @Override
    public RehabAiOutputRespVO getLatestFollowupForAdminPatient(Long patientId, Long operatorUserId) {
        validatePatientReadable(patientId, operatorUserId);
        RehabAiOutputDO output = selectLatestOutputByPatient(patientId,
                Collections.singletonList(RehabAiConstants.OUTPUT_TYPE_FOLLOWUP_MESSAGE), false, false);
        return output == null ? null : toOutputRespList(Collections.singletonList(output)).get(0);
    }

    @Override
    public RehabAiOutputRespVO getLatestPatientVisibleSummary(Long patientId, Long appUserId) {
        if (!dataPermissionService.canReadPatient(patientId, appUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
        RehabAiOutputDO output = selectLatestOutputByPatient(patientId,
                Arrays.asList(RehabAiConstants.OUTPUT_TYPE_PATIENT_SUMMARY, RehabAiConstants.OUTPUT_TYPE_PROGRESS_SUMMARY),
                true, true);
        return output == null ? null : toOutputRespList(Collections.singletonList(output)).get(0);
    }

    @Override
    public RehabAiOutputRespVO getLatestPatientVisibleFollowup(Long patientId, Long appUserId) {
        if (!dataPermissionService.canReadPatient(patientId, appUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
        RehabAiOutputDO output = selectLatestOutputByPatient(patientId,
                Collections.singletonList(RehabAiConstants.OUTPUT_TYPE_FOLLOWUP_MESSAGE), true, true);
        return output == null ? null : toOutputRespList(Collections.singletonList(output)).get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    protected void executeJob(Long jobId) {
        RehabAiJobDO job = aiJobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        long begin = System.currentTimeMillis();
        Map<String, Object> payload = parseJsonMap(job.getRequestPayloadJson());
        if (payload == null) {
            payload = new LinkedHashMap<String, Object>();
        }
        RehabAiConfigDO config = getOrCreateGlobalConfig();
        List<OutputPlan> plans = resolveOutputPlans(job.getJobType(), payload);
        if (CollUtil.isEmpty(plans)) {
            aiJobMapper.updateById(new RehabAiJobDO().setId(jobId)
                    .setStatus(RehabAiConstants.JOB_STATUS_FAILED)
                    .setFallbackUsed(true)
                    .setLatencyMs(System.currentTimeMillis() - begin));
            return;
        }

        List<RehabAiOutputDO> generatedOutputs = new ArrayList<RehabAiOutputDO>();
        Map<String, Object> jobResponse = new LinkedHashMap<String, Object>();
        boolean fallbackUsed = false;
        Long totalLatency = 0L;
        for (OutputPlan plan : plans) {
            GeneratedOutput generated = generateOneOutput(job, payload, plan, config);
            if (generated == null) {
                continue;
            }
            fallbackUsed = fallbackUsed || Boolean.TRUE.equals(generated.getFallbackUsed());
            totalLatency += ObjUtil.defaultIfNull(generated.getLatencyMs(), 0L);

            RehabAiOutputDO output = RehabAiOutputDO.builder()
                    .aiJobId(job.getId())
                    .outputType(plan.getOutputType())
                    .targetObjectType(plan.getTargetObjectType())
                    .targetObjectId(plan.getTargetObjectId())
                    .schemaName(plan.getSchemaName())
                    .contentJson(generated.getContentJson())
                    .renderedText(generated.getRenderedText())
                    .evidenceRefsJson(generated.getEvidenceRefsJson())
                    .safetyStatus(generated.getSafetyStatus())
                    .reviewStatus(RehabAiConstants.REVIEW_STATUS_PENDING)
                    .patientVisible(false)
                    .build();
            aiOutputMapper.insert(output);
            generatedOutputs.add(output);

            jobResponse.put(plan.getOutputType(), parseJsonMap(generated.getContentJson()));
            if (RehabAiConstants.OUTPUT_TYPE_PLAN_DRAFT.equals(plan.getOutputType())) {
                upsertSuggestionBundle(job, RehabAiConstants.BUNDLE_TYPE_PLAN_BUNDLE, generated.getContentJson());
            } else if (RehabAiConstants.OUTPUT_TYPE_FOLLOWUP_MESSAGE.equals(plan.getOutputType())) {
                upsertSuggestionBundle(job, RehabAiConstants.BUNDLE_TYPE_FOLLOWUP_BUNDLE, generated.getContentJson());
            } else if (RehabAiConstants.OUTPUT_TYPE_THERAPIST_SUMMARY.equals(plan.getOutputType())) {
                upsertSuggestionBundle(job, RehabAiConstants.BUNDLE_TYPE_INTEGRATED_SUMMARY, generated.getContentJson());
            }
        }

        String responseJson = JsonUtils.toJsonString(jobResponse);
        String status = fallbackUsed ? RehabAiConstants.JOB_STATUS_FALLBACK_USED : RehabAiConstants.JOB_STATUS_SUCCESS;
        if (generatedOutputs.isEmpty()) {
            status = RehabAiConstants.JOB_STATUS_FAILED;
            fallbackUsed = true;
        }
        RehabAiJobDO updateObj = new RehabAiJobDO().setId(job.getId())
                .setStatus(status)
                .setFallbackUsed(fallbackUsed)
                .setResponsePayloadJson(responseJson)
                .setOutputHash(hash(responseJson))
                .setLatencyMs(System.currentTimeMillis() - begin)
                .setTokenUsageJson("{\"latency_sum_ms\":" + totalLatency + "}");
        aiJobMapper.updateById(updateObj);
        auditLogService.createAuditLog("ai_job", job.getId(), RehabOperationTypeConstants.AI_GENERATE,
                job.getTriggeredByUserId(), resolveRole(job.getTriggeredByUserId()), null, updateObj,
                "success", "AI 生成完成");
        if (fallbackUsed) {
            auditLogService.createAuditLog("ai_job", job.getId(), RehabOperationTypeConstants.AI_FALLBACK,
                    job.getTriggeredByUserId(), resolveRole(job.getTriggeredByUserId()), null, updateObj,
                    "success", "AI 生成触发降级");
        }
    }

    private GeneratedOutput generateOneOutput(RehabAiJobDO job, Map<String, Object> payload, OutputPlan plan, RehabAiConfigDO config) {
        String inputJson = JsonUtils.toJsonString(payload.get("input"));
        if (StrUtil.isBlank(inputJson)) {
            inputJson = "{}";
        }

        RehabAiPromptTemplateDO template = promptTemplateMapper.selectDefaultTemplate(plan.getModuleScope(), plan.getRoleScope());
        String promptName = buildPromptName(plan, template);
        String systemPrompt = template != null && StrUtil.isNotBlank(template.getSystemPrompt())
                ? template.getSystemPrompt() : RehabAiPromptRegistry.defaultSystemPrompt(plan.getModuleScope(), plan.getRoleScope());
        String userPromptTemplate = template != null && StrUtil.isNotBlank(template.getUserPromptTemplate())
                ? template.getUserPromptTemplate() : RehabAiPromptRegistry.defaultUserPromptTemplate(plan.getModuleScope());
        String userPrompt = userPromptTemplate.contains("{{input_json}}")
                ? userPromptTemplate.replace("{{input_json}}", inputJson)
                : userPromptTemplate + "\n输入 JSON:\n" + inputJson;
        if (StrUtil.isNotBlank(config.getPromptStyle())) {
            userPrompt = userPrompt + "\n输出风格：" + config.getPromptStyle();
        }
        String schemaName = template != null && StrUtil.isNotBlank(template.getOutputSchemaName())
                ? template.getOutputSchemaName() : plan.getSchemaName();
        Map<String, Object> schema = RehabAiPromptRegistry.schemaByName(schemaName);

        RehabAiClientOptions options = new RehabAiClientOptions();
        options.setModel(StrUtil.blankToDefault(config.getPreferredModelName(), defaultModel));
        options.setTemperature(defaultTemperature);
        options.setMaxOutputTokens(defaultMaxOutputTokens);
        options.setReasoningEffort(defaultReasoningEffort);
        options.setTimeoutSeconds(defaultTimeoutSeconds);
        options.setMaxRetries(defaultMaxRetries);
        options.setMockMode(Boolean.TRUE.equals(mockMode));

        boolean useAi = Boolean.TRUE.equals(aiAnalysisEnabled) && Boolean.TRUE.equals(config.getAiEnabled());
        if (!isModuleEnabled(config, job.getJobType())) {
            useAi = false;
        }
        if (!useAi) {
            RehabAiFallbackService.FallbackOutput fallback = fallbackService.build(plan.getOutputType(), "AI disabled");
            return GeneratedOutput.fromFallback(fallback, 0L);
        }

        RehabAiClient client = resolveAiClient();
        RehabAiClientResponse clientResp = client.generateStructured(systemPrompt, userPrompt, schemaName, schema, options);
        if (!Boolean.TRUE.equals(clientResp.getSuccess())) {
            RehabAiFallbackService.FallbackOutput fallback = fallbackService.build(plan.getOutputType(),
                    StrUtil.blankToDefault(clientResp.getErrorMessage(), "ai call failed"));
            return GeneratedOutput.fromFallback(fallback, clientResp.getLatencyMs());
        }

        RehabAiSchemaValidator.ValidationResult validateResult = schemaValidator.validate(clientResp.getOutputJson(), schema);
        if (!Boolean.TRUE.equals(validateResult.getValid())) {
            RehabAiFallbackService.FallbackOutput fallback = fallbackService.build(plan.getOutputType(),
                    "schema invalid: " + validateResult.getMessage());
            return GeneratedOutput.fromFallback(fallback, clientResp.getLatencyMs());
        }

        Map<String, Object> outputMap = validateResult.getOutputMap();
        String rendered = renderOutputText(plan.getOutputType(), outputMap);
        RehabAiSafetyGuard.SafetyResult safetyResult = safetyGuard.check(rendered, outputMap, config.getSafetyMode());
        if (RehabAiConstants.SAFETY_STATUS_BLOCKED.equals(safetyResult.getSafetyStatus())) {
            RehabAiFallbackService.FallbackOutput fallback = fallbackService.build(plan.getOutputType(),
                    "safety blocked: " + safetyResult.getReason());
            return GeneratedOutput.fromFallback(fallback, clientResp.getLatencyMs());
        }
        if (RehabAiConstants.SAFETY_STATUS_DOWNGRADED.equals(safetyResult.getSafetyStatus())) {
            outputMap.put("caveats", appendCaveats(outputMap.get("caveats"), Arrays.asList("证据不足", "仅为功能学推测", "需结合人工复核")));
        }

        GeneratedOutput output = new GeneratedOutput();
        output.setFallbackUsed(false);
        output.setSafetyStatus(safetyResult.getSafetyStatus());
        output.setRenderedText(safetyResult.getRenderedText());
        output.setContentJson(JsonUtils.toJsonString(outputMap));
        output.setEvidenceRefsJson(JsonUtils.toJsonString(extractEvidenceRefs(outputMap)));
        output.setLatencyMs(clientResp.getLatencyMs());
        output.setPromptName(promptName);
        return output;
    }

    private RehabAiGenerateRespVO createAndRunJob(String jobType, Map<String, Object> payload, Boolean asyncMode, Long operatorUserId) {
        validateCanGenerate(operatorUserId);
        RehabAiConfigDO config = getOrCreateGlobalConfig();
        String model = StrUtil.blankToDefault(config.getPreferredModelName(), defaultModel);

        Long patientId = toLong(payload.get("patient_id"));
        Long episodeId = toLong(payload.get("episode_id"));
        Long assessmentId = toLong(payload.get("assessment_id"));
        Long reportId = toLong(payload.get("report_id"));
        Long planId = toLong(payload.get("plan_id"));
        Long progressId = toLong(payload.get("progress_id"));
        Long alertId = toLong(payload.get("alert_id"));
        Long triggerId = toLong(payload.get("trigger_id"));

        RehabAiJobDO job = RehabAiJobDO.builder()
                .patientId(patientId)
                .episodeId(episodeId)
                .assessmentId(assessmentId)
                .reportId(reportId)
                .planId(planId)
                .progressId(progressId)
                .alertId(alertId)
                .triggerId(triggerId)
                .jobType(jobType)
                .modelName(model)
                .promptName("default")
                .requestPayloadJson(JsonUtils.toJsonString(payload))
                .inputHash(hash(JsonUtils.toJsonString(payload)))
                .status(RehabAiConstants.JOB_STATUS_PENDING)
                .fallbackUsed(false)
                .triggeredByUserId(operatorUserId)
                .build();
        aiJobMapper.insert(job);
        aiJobMapper.updateById(new RehabAiJobDO().setId(job.getId()).setJobNo(generateJobNo(job.getId())));

        RehabAiGenerateRespVO respVO = new RehabAiGenerateRespVO();
        respVO.setJobId(job.getId());
        respVO.setJobStatus(RehabAiConstants.JOB_STATUS_PENDING);
        respVO.setAsyncMode(Boolean.TRUE.equals(asyncMode));

        if (Boolean.TRUE.equals(asyncMode)) {
            runAsync(job.getId());
            return respVO;
        }
        executeJob(job.getId());
        RehabAiJobDO latestJob = aiJobMapper.selectById(job.getId());
        RehabAiOutputDO latestOutput = aiOutputMapper.selectLatestByJobId(job.getId());
        respVO.setJobStatus(latestJob == null ? RehabAiConstants.JOB_STATUS_FAILED : latestJob.getStatus());
        if (latestOutput != null) {
            respVO.setOutputId(latestOutput.getId());
            respVO.setReviewStatus(latestOutput.getReviewStatus());
            respVO.setRenderedText(latestOutput.getRenderedText());
            respVO.setContentJson(latestOutput.getContentJson());
            respVO.setFallbackUsed(Boolean.TRUE.equals(latestJob.getFallbackUsed()));
        } else {
            respVO.setFallbackUsed(true);
        }
        return respVO;
    }

    private void runAsync(final Long jobId) {
        if (taskExecutor != null) {
            taskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    safeExecute(jobId);
                }
            });
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                safeExecute(jobId);
            }
        }, "rehab-ai-job-" + jobId).start();
    }

    private void safeExecute(Long jobId) {
        try {
            executeJob(jobId);
        } catch (Exception ex) {
            log.error("[rehab-ai] async execute failed, jobId={}", jobId, ex);
            aiJobMapper.updateById(new RehabAiJobDO().setId(jobId)
                    .setStatus(RehabAiConstants.JOB_STATUS_FAILED)
                    .setFallbackUsed(true));
        }
    }

    private RehabAiClient resolveAiClient() {
        if (Boolean.TRUE.equals(usePlatformBridge) && platformAiBridgeClient != null) {
            return platformAiBridgeClient;
        }
        return openAiClient;
    }

    private Map<String, Object> buildAssessmentPayload(RehabAssessmentRecordDO assessment) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("patient_id", assessment.getPatientId());
        payload.put("episode_id", assessment.getEpisodeId());
        payload.put("assessment_id", assessment.getId());
        payload.put("assessment", assessment);
        List<RehabAssessmentModuleDataDO> modules = assessmentModuleDataMapper.selectListByAssessmentId(assessment.getId());
        List<Map<String, Object>> moduleList = new ArrayList<Map<String, Object>>();
        for (RehabAssessmentModuleDataDO module : modules) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("module_type", module.getModuleType());
            item.put("module_status", module.getModuleStatus());
            item.put("source_type", module.getSourceType());
            item.put("version", module.getVersion());
            item.put("data", parseJsonOrRaw(module.getDataJson()));
            moduleList.add(item);
        }
        payload.put("input", new LinkedHashMap<String, Object>());
        ((Map<String, Object>) payload.get("input")).put("assessment", assessment);
        ((Map<String, Object>) payload.get("input")).put("modules", moduleList);
        return payload;
    }

    private Map<String, Object> buildReportPayload(RehabReportDO report) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("patient_id", report.getPatientId());
        payload.put("episode_id", report.getEpisodeId());
        payload.put("report_id", report.getId());
        payload.put("assessment_id", report.getAssessmentId());
        payload.put("report", report);
        payload.put("input", new LinkedHashMap<String, Object>());
        ((Map<String, Object>) payload.get("input")).put("report", parseJsonOrRaw(report.getReportJson()));
        ((Map<String, Object>) payload.get("input")).put("meta", report);
        return payload;
    }

    private Map<String, Object> buildRiskPayload(RehabAiGenerateRiskExplanationReqVO reqVO, Long patientId) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("patient_id", patientId);
        payload.put("alert_id", reqVO.getAlertId());
        payload.put("trigger_id", reqVO.getTriggerId());
        payload.put("progress_id", reqVO.getProgressId());
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        if (reqVO.getAlertId() != null) {
            RehabAlertEventDO alert = alertEventMapper.selectById(reqVO.getAlertId());
            if (alert != null) {
                payload.put("episode_id", alert.getEpisodeId());
                payload.put("plan_id", alert.getPlanId());
                input.put("alert", alert);
            }
        }
        if (reqVO.getTriggerId() != null) {
            RehabReassessmentTriggerDO trigger = triggerMapper.selectById(reqVO.getTriggerId());
            if (trigger != null) {
                payload.put("episode_id", trigger.getEpisodeId());
                payload.put("plan_id", trigger.getPlanId());
                input.put("trigger", trigger);
            }
        }
        if (reqVO.getProgressId() != null) {
            RehabProgressRecordDO progress = progressRecordMapper.selectById(reqVO.getProgressId());
            if (progress != null) {
                payload.put("episode_id", progress.getEpisodeId());
                payload.put("plan_id", progress.getPlanId());
                input.put("progress", progress);
            }
        }
        input.put("active_alerts", alertEventMapper.selectActiveListByPatientId(patientId));
        payload.put("input", input);
        return payload;
    }

    private Map<String, Object> buildPlanDraftPayload(RehabAiGeneratePlanDraftReqVO reqVO, Long patientId) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("patient_id", patientId);
        payload.put("episode_id", reqVO.getEpisodeId());
        payload.put("assessment_id", reqVO.getAssessmentId());
        payload.put("report_id", reqVO.getReportId());
        payload.put("progress_id", reqVO.getProgressId());
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        if (reqVO.getAssessmentId() != null) {
            RehabAssessmentRecordDO assessment = assessmentRecordMapper.selectById(reqVO.getAssessmentId());
            if (assessment != null) {
                input.put("assessment", assessment);
                input.put("assessment_modules", assessmentModuleDataMapper.selectListByAssessmentId(assessment.getId()));
                payload.put("episode_id", ObjUtil.defaultIfNull(toLong(payload.get("episode_id")), assessment.getEpisodeId()));
            }
        }
        if (reqVO.getReportId() != null) {
            RehabReportDO report = reportMapper.selectById(reqVO.getReportId());
            if (report != null) {
                input.put("report", parseJsonOrRaw(report.getReportJson()));
                input.put("report_meta", report);
                payload.put("episode_id", ObjUtil.defaultIfNull(toLong(payload.get("episode_id")), report.getEpisodeId()));
            }
        }
        if (reqVO.getProgressId() != null) {
            RehabProgressRecordDO progress = progressRecordMapper.selectById(reqVO.getProgressId());
            if (progress != null) {
                input.put("progress", progress);
                payload.put("plan_id", progress.getPlanId());
                payload.put("episode_id", ObjUtil.defaultIfNull(toLong(payload.get("episode_id")), progress.getEpisodeId()));
            }
        }
        input.put("active_alerts", alertEventMapper.selectActiveListByPatientId(patientId));
        input.put("plan_history", carePlanMapper.selectListByPatientId(patientId));
        payload.put("input", input);
        return payload;
    }

    private Map<String, Object> buildFollowupPayload(RehabAiGenerateFollowupMessageReqVO reqVO, Long patientId) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("patient_id", patientId);
        payload.put("episode_id", reqVO.getEpisodeId());
        payload.put("progress_id", reqVO.getProgressId());
        payload.put("trigger_id", reqVO.getTriggerId());
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        RehabPatientDO patient = patientMapper.selectById(patientId);
        if (patient != null) {
            input.put("patient_stage", patient.getCurrentStage());
        }
        if (reqVO.getProgressId() != null) {
            RehabProgressRecordDO progress = progressRecordMapper.selectById(reqVO.getProgressId());
            if (progress != null) {
                input.put("progress", progress);
                payload.put("episode_id", ObjUtil.defaultIfNull(toLong(payload.get("episode_id")), progress.getEpisodeId()));
                payload.put("plan_id", progress.getPlanId());
            }
        }
        if (reqVO.getTriggerId() != null) {
            RehabReassessmentTriggerDO trigger = triggerMapper.selectById(reqVO.getTriggerId());
            if (trigger != null) {
                input.put("trigger", trigger);
                payload.put("episode_id", ObjUtil.defaultIfNull(toLong(payload.get("episode_id")), trigger.getEpisodeId()));
                payload.put("plan_id", ObjUtil.defaultIfNull(toLong(payload.get("plan_id")), trigger.getPlanId()));
            }
        }
        input.put("active_alerts", alertEventMapper.selectActiveListByPatientId(patientId));
        payload.put("input", input);
        return payload;
    }

    private Map<String, Object> buildProgressPayload(RehabProgressRecordDO progress) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("patient_id", progress.getPatientId());
        payload.put("episode_id", progress.getEpisodeId());
        payload.put("plan_id", progress.getPlanId());
        payload.put("progress_id", progress.getId());
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        input.put("progress", progress);
        input.put("recent_checkins", checkinMapper.selectListByPlanId(progress.getPlanId()).stream().limit(14).collect(Collectors.toList()));
        input.put("active_alerts", alertEventMapper.selectActiveListByPatientId(progress.getPatientId()));
        payload.put("input", input);
        return payload;
    }

    private RehabAssessmentRecordDO validateAssessmentReady(Long assessmentId, Long operatorUserId) {
        RehabAssessmentRecordDO assessment = assessmentRecordMapper.selectById(assessmentId);
        if (assessment == null) {
            throw exception(ASSESSMENT_NOT_EXISTS);
        }
        validatePatientReadable(assessment.getPatientId(), operatorUserId);
        if (!RehabAssessmentConstants.STATUS_COMPLETED.equals(assessment.getStatus())
                && !RehabAssessmentConstants.STATUS_REVIEWED.equals(assessment.getStatus())
                && !RehabAssessmentConstants.STATUS_ARCHIVED.equals(assessment.getStatus())) {
            throw exception(AI_PRECONDITION_FAILED);
        }
        return assessment;
    }

    private RehabReportDO validateReportReadable(Long reportId, Long operatorUserId) {
        RehabReportDO report = reportMapper.selectById(reportId);
        if (report == null) {
            throw exception(REPORT_NOT_EXISTS);
        }
        validatePatientReadable(report.getPatientId(), operatorUserId);
        return report;
    }

    private RehabProgressRecordDO validateProgressReadable(Long progressId, Long operatorUserId) {
        RehabProgressRecordDO progress = progressRecordMapper.selectById(progressId);
        if (progress == null) {
            throw exception(PROGRESS_NOT_EXISTS);
        }
        validatePatientReadable(progress.getPatientId(), operatorUserId);
        return progress;
    }

    private Long resolveRiskPatientId(RehabAiGenerateRiskExplanationReqVO reqVO) {
        if (reqVO.getPatientId() != null) {
            return reqVO.getPatientId();
        }
        if (reqVO.getAlertId() != null) {
            RehabAlertEventDO alert = alertEventMapper.selectById(reqVO.getAlertId());
            if (alert != null) {
                return alert.getPatientId();
            }
        }
        if (reqVO.getTriggerId() != null) {
            RehabReassessmentTriggerDO trigger = triggerMapper.selectById(reqVO.getTriggerId());
            if (trigger != null) {
                return trigger.getPatientId();
            }
        }
        if (reqVO.getProgressId() != null) {
            RehabProgressRecordDO progress = progressRecordMapper.selectById(reqVO.getProgressId());
            if (progress != null) {
                return progress.getPatientId();
            }
        }
        throw exception(AI_PRECONDITION_FAILED);
    }

    private Long resolvePlanDraftPatientId(RehabAiGeneratePlanDraftReqVO reqVO) {
        if (reqVO.getPatientId() != null) {
            return reqVO.getPatientId();
        }
        if (reqVO.getAssessmentId() != null) {
            RehabAssessmentRecordDO assessment = assessmentRecordMapper.selectById(reqVO.getAssessmentId());
            if (assessment != null) {
                return assessment.getPatientId();
            }
        }
        if (reqVO.getReportId() != null) {
            RehabReportDO report = reportMapper.selectById(reqVO.getReportId());
            if (report != null) {
                return report.getPatientId();
            }
        }
        if (reqVO.getProgressId() != null) {
            RehabProgressRecordDO progress = progressRecordMapper.selectById(reqVO.getProgressId());
            if (progress != null) {
                return progress.getPatientId();
            }
        }
        throw exception(AI_PRECONDITION_FAILED);
    }

    private Long resolveFollowupPatientId(RehabAiGenerateFollowupMessageReqVO reqVO) {
        if (reqVO.getPatientId() != null) {
            return reqVO.getPatientId();
        }
        if (reqVO.getProgressId() != null) {
            RehabProgressRecordDO progress = progressRecordMapper.selectById(reqVO.getProgressId());
            if (progress != null) {
                return progress.getPatientId();
            }
        }
        if (reqVO.getTriggerId() != null) {
            RehabReassessmentTriggerDO trigger = triggerMapper.selectById(reqVO.getTriggerId());
            if (trigger != null) {
                return trigger.getPatientId();
            }
        }
        throw exception(AI_PRECONDITION_FAILED);
    }

    private List<OutputPlan> resolveOutputPlans(String jobType, Map<String, Object> payload) {
        Long assessmentId = toLong(payload.get("assessment_id"));
        Long reportId = toLong(payload.get("report_id"));
        Long planId = toLong(payload.get("plan_id"));
        Long progressId = toLong(payload.get("progress_id"));
        Long alertId = toLong(payload.get("alert_id"));
        Long triggerId = toLong(payload.get("trigger_id"));
        if (RehabAiConstants.JOB_TYPE_ASSESSMENT_INTERPRETATION.equals(jobType)) {
            return Collections.singletonList(new OutputPlan(RehabAiConstants.OUTPUT_TYPE_THERAPIST_SUMMARY,
                    RehabAiConstants.TEMPLATE_SCOPE_ASSESSMENT, RehabAiConstants.ROLE_SCOPE_THERAPIST,
                    RehabAiPromptRegistry.resolveSchemaNameByOutputType(RehabAiConstants.OUTPUT_TYPE_THERAPIST_SUMMARY),
                    RehabAiConstants.TARGET_OBJECT_ASSESSMENT, assessmentId));
        }
        if (RehabAiConstants.JOB_TYPE_REPORT_SUMMARY.equals(jobType)) {
            List<OutputPlan> list = new ArrayList<OutputPlan>();
            list.add(new OutputPlan(RehabAiConstants.OUTPUT_TYPE_THERAPIST_SUMMARY,
                    RehabAiConstants.TEMPLATE_SCOPE_REPORT, RehabAiConstants.ROLE_SCOPE_THERAPIST,
                    RehabAiPromptRegistry.resolveSchemaNameByOutputType(RehabAiConstants.OUTPUT_TYPE_THERAPIST_SUMMARY),
                    RehabAiConstants.TARGET_OBJECT_REPORT, reportId));
            list.add(new OutputPlan(RehabAiConstants.OUTPUT_TYPE_ADMIN_SUMMARY,
                    RehabAiConstants.TEMPLATE_SCOPE_REPORT, RehabAiConstants.ROLE_SCOPE_ADMIN,
                    RehabAiPromptRegistry.resolveSchemaNameByOutputType(RehabAiConstants.OUTPUT_TYPE_ADMIN_SUMMARY),
                    RehabAiConstants.TARGET_OBJECT_REPORT, reportId));
            list.add(new OutputPlan(RehabAiConstants.OUTPUT_TYPE_PATIENT_SUMMARY,
                    RehabAiConstants.TEMPLATE_SCOPE_PATIENT_SUMMARY, RehabAiConstants.ROLE_SCOPE_PATIENT,
                    RehabAiPromptRegistry.resolveSchemaNameByOutputType(RehabAiConstants.OUTPUT_TYPE_PATIENT_SUMMARY),
                    RehabAiConstants.TARGET_OBJECT_REPORT, reportId));
            return list;
        }
        if (RehabAiConstants.JOB_TYPE_RISK_EXPLANATION.equals(jobType)) {
            Long targetId = alertId != null ? alertId : (triggerId != null ? triggerId : progressId);
            String targetType = alertId != null ? RehabAiConstants.TARGET_OBJECT_ALERT
                    : (triggerId != null ? RehabAiConstants.TARGET_OBJECT_TRIGGER : RehabAiConstants.TARGET_OBJECT_PROGRESS);
            return Collections.singletonList(new OutputPlan(RehabAiConstants.OUTPUT_TYPE_RISK_EXPLANATION,
                    RehabAiConstants.TEMPLATE_SCOPE_RISK, RehabAiConstants.ROLE_SCOPE_THERAPIST,
                    RehabAiPromptRegistry.resolveSchemaNameByOutputType(RehabAiConstants.OUTPUT_TYPE_RISK_EXPLANATION),
                    targetType, targetId));
        }
        if (RehabAiConstants.JOB_TYPE_PLAN_DRAFT_GENERATION.equals(jobType)) {
            Long targetId = planId != null ? planId : (assessmentId != null ? assessmentId : reportId);
            String targetType = planId != null ? RehabAiConstants.TARGET_OBJECT_PLAN
                    : (assessmentId != null ? RehabAiConstants.TARGET_OBJECT_ASSESSMENT : RehabAiConstants.TARGET_OBJECT_REPORT);
            return Collections.singletonList(new OutputPlan(RehabAiConstants.OUTPUT_TYPE_PLAN_DRAFT,
                    RehabAiConstants.TEMPLATE_SCOPE_PLAN, RehabAiConstants.ROLE_SCOPE_THERAPIST,
                    RehabAiPromptRegistry.resolveSchemaNameByOutputType(RehabAiConstants.OUTPUT_TYPE_PLAN_DRAFT),
                    targetType, targetId));
        }
        if (RehabAiConstants.JOB_TYPE_FOLLOWUP_MESSAGE_GENERATION.equals(jobType)) {
            Long targetId = triggerId != null ? triggerId : progressId;
            String targetType = triggerId != null ? RehabAiConstants.TARGET_OBJECT_TRIGGER : RehabAiConstants.TARGET_OBJECT_PROGRESS;
            return Collections.singletonList(new OutputPlan(RehabAiConstants.OUTPUT_TYPE_FOLLOWUP_MESSAGE,
                    RehabAiConstants.TEMPLATE_SCOPE_FOLLOWUP, RehabAiConstants.ROLE_SCOPE_THERAPIST,
                    RehabAiPromptRegistry.resolveSchemaNameByOutputType(RehabAiConstants.OUTPUT_TYPE_FOLLOWUP_MESSAGE),
                    targetType, targetId));
        }
        if (RehabAiConstants.JOB_TYPE_PROGRESS_SUMMARY.equals(jobType)) {
            return Collections.singletonList(new OutputPlan(RehabAiConstants.OUTPUT_TYPE_PROGRESS_SUMMARY,
                    RehabAiConstants.TEMPLATE_SCOPE_PROGRESS, RehabAiConstants.ROLE_SCOPE_THERAPIST,
                    RehabAiPromptRegistry.resolveSchemaNameByOutputType(RehabAiConstants.OUTPUT_TYPE_PROGRESS_SUMMARY),
                    RehabAiConstants.TARGET_OBJECT_PROGRESS, progressId));
        }
        return Collections.emptyList();
    }

    private String renderOutputText(String outputType, Map<String, Object> outputMap) {
        if (RehabAiConstants.OUTPUT_TYPE_PATIENT_SUMMARY.equals(outputType)) {
            return buildText(Arrays.asList(
                    outputMap.get("headline"),
                    joinList(outputMap.get("top_3_findings")),
                    joinList(outputMap.get("top_3_goals")),
                    outputMap.get("current_focus"),
                    joinList(outputMap.get("what_to_avoid")),
                    outputMap.get("when_to_recheck"),
                    outputMap.get("supportive_message")
            ));
        }
        if (RehabAiConstants.OUTPUT_TYPE_RISK_EXPLANATION.equals(outputType)) {
            return buildText(Arrays.asList(
                    "风险等级：" + outputMap.get("overall_risk_level"),
                    outputMap.get("explanation"),
                    "可能因素：" + joinList(outputMap.get("likely_contributors")),
                    "建议动作：" + joinList(outputMap.get("suggested_next_step")),
                    outputMap.get("patient_visible_text")
            ));
        }
        if (RehabAiConstants.OUTPUT_TYPE_PLAN_DRAFT.equals(outputType)) {
            return buildText(Arrays.asList(
                    "计划：" + outputMap.get("plan_name"),
                    "类型：" + outputMap.get("plan_type"),
                    "短期目标：" + joinList(outputMap.get("short_term_goals")),
                    "中期目标：" + joinList(outputMap.get("mid_term_goals")),
                    "长期目标：" + joinList(outputMap.get("long_term_goals")),
                    "注意事项：" + joinList(outputMap.get("precautions")),
                    "进阶策略：" + outputMap.get("progression_strategy"),
                    "退阶策略：" + outputMap.get("regression_strategy")
            ));
        }
        if (RehabAiConstants.OUTPUT_TYPE_FOLLOWUP_MESSAGE.equals(outputType)) {
            return buildText(Arrays.asList(
                    outputMap.get("patient_message"),
                    outputMap.get("therapist_internal_note"),
                    "建议随访间隔（天）：" + outputMap.get("recommended_followup_interval_days")
            ));
        }
        if (RehabAiConstants.OUTPUT_TYPE_PROGRESS_SUMMARY.equals(outputType)) {
            return buildText(Arrays.asList(
                    "进展状态：" + outputMap.get("progress_status"),
                    outputMap.get("summary"),
                    "积极变化：" + joinList(outputMap.get("positive_changes")),
                    "关注点：" + joinList(outputMap.get("concerning_changes")),
                    outputMap.get("adherence_comment"),
                    "下一步：" + joinList(outputMap.get("next_action"))
            ));
        }
        return buildText(Arrays.asList(
                outputMap.get("title"),
                outputMap.get("executive_summary"),
                "主要问题：" + joinList(outputMap.get("top_issues")),
                "优先动作：" + joinList(outputMap.get("priority_actions")),
                "风险备注：" + joinList(outputMap.get("risk_notes")),
                "管理重点：" + joinList(outputMap.get("management_focus")),
                "风险总览：" + joinList(outputMap.get("risk_overview"))
        ));
    }

    private Collection<Long> resolveVisibleJobIds(Long reqPatientId, Long operatorUserId) {
        Set<Long> visiblePatientIds = dataPermissionService.getVisiblePatientIds(operatorUserId);
        if (reqPatientId != null) {
            validatePatientReadable(reqPatientId, operatorUserId);
            visiblePatientIds = new HashSet<Long>(Collections.singletonList(reqPatientId));
        }
        if (visiblePatientIds != null && visiblePatientIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<RehabAiJobDO> jobs = aiJobMapper.selectList(new LambdaQueryWrapperX<RehabAiJobDO>()
                .inIfPresent(RehabAiJobDO::getPatientId, visiblePatientIds)
                .orderByDesc(RehabAiJobDO::getCreateTime)
                .orderByDesc(RehabAiJobDO::getId));
        return jobs.stream().map(RehabAiJobDO::getId).collect(Collectors.toSet());
    }

    private RehabAiJobDO validateJobReadable(Long id, Long operatorUserId) {
        RehabAiJobDO job = aiJobMapper.selectById(id);
        if (job == null) {
            throw exception(AI_JOB_NOT_EXISTS);
        }
        if (job.getPatientId() != null && !dataPermissionService.canReadPatient(job.getPatientId(), operatorUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
        return job;
    }

    private RehabAiOutputDO validateOutputReadable(Long id, Long operatorUserId) {
        RehabAiOutputDO output = aiOutputMapper.selectById(id);
        if (output == null) {
            throw exception(AI_OUTPUT_NOT_EXISTS);
        }
        RehabAiJobDO job = validateJobReadable(output.getAiJobId(), operatorUserId);
        if (job == null) {
            throw exception(AI_JOB_NOT_EXISTS);
        }
        return output;
    }

    private RehabAiPromptTemplateDO validateTemplateExists(Long id) {
        RehabAiPromptTemplateDO template = promptTemplateMapper.selectById(id);
        if (template == null) {
            throw exception(AI_PROMPT_TEMPLATE_NOT_EXISTS);
        }
        return template;
    }

    private RehabAiConfigDO getOrCreateGlobalConfig() {
        RehabAiConfigDO config = aiConfigMapper.selectGlobalConfig();
        if (config != null) {
            return config;
        }
        RehabAiConfigDO insert = RehabAiConfigDO.builder()
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
                .preferredModelName(defaultModel)
                .promptStyle(RehabAiConstants.PROMPT_STYLE_STANDARD)
                .safetyMode(RehabAiConstants.SAFETY_MODE_STRICT)
                .note("默认配置")
                .build();
        aiConfigMapper.insert(insert);
        return aiConfigMapper.selectById(insert.getId());
    }

    private boolean isModuleEnabled(RehabAiConfigDO config, String jobType) {
        if (RehabAiConstants.JOB_TYPE_ASSESSMENT_INTERPRETATION.equals(jobType)) {
            return Boolean.TRUE.equals(config.getEnableAssessmentInterpretation());
        }
        if (RehabAiConstants.JOB_TYPE_REPORT_SUMMARY.equals(jobType)) {
            return Boolean.TRUE.equals(config.getEnableReportSummary());
        }
        if (RehabAiConstants.JOB_TYPE_PLAN_DRAFT_GENERATION.equals(jobType)) {
            return Boolean.TRUE.equals(config.getEnablePlanDraft());
        }
        if (RehabAiConstants.JOB_TYPE_FOLLOWUP_MESSAGE_GENERATION.equals(jobType)) {
            return Boolean.TRUE.equals(config.getEnableFollowupWriter());
        }
        return true;
    }

    private void upsertSuggestionBundle(RehabAiJobDO job, String bundleType, String summaryJson) {
        if (job.getPatientId() == null) {
            return;
        }
        RehabAiSuggestionBundleDO existing = suggestionBundleMapper.selectLatestByTypeAndPatient(bundleType, job.getPatientId());
        if (existing == null) {
            RehabAiSuggestionBundleDO bundle = RehabAiSuggestionBundleDO.builder()
                    .patientId(job.getPatientId())
                    .episodeId(job.getEpisodeId())
                    .sourceAssessmentId(job.getAssessmentId())
                    .sourceProgressId(job.getProgressId())
                    .bundleType(bundleType)
                    .summaryJson(summaryJson)
                    .status(RehabAiConstants.BUNDLE_STATUS_DRAFT)
                    .build();
            suggestionBundleMapper.insert(bundle);
            return;
        }
        suggestionBundleMapper.updateById(new RehabAiSuggestionBundleDO().setId(existing.getId())
                .setEpisodeId(job.getEpisodeId())
                .setSourceAssessmentId(job.getAssessmentId())
                .setSourceProgressId(job.getProgressId())
                .setSummaryJson(summaryJson)
                .setStatus(RehabAiConstants.BUNDLE_STATUS_DRAFT));
    }

    private void createReviewLog(Long outputId, Long userId, String action, String beforeText, String afterText, String note) {
        RehabAiReviewLogDO reviewLog = RehabAiReviewLogDO.builder()
                .aiOutputId(outputId)
                .reviewerUserId(userId)
                .reviewAction(action)
                .beforeText(beforeText)
                .afterText(afterText)
                .reviewNote(note)
                .build();
        aiReviewLogMapper.insert(reviewLog);
    }

    private void updateJobReviewStatus(Long jobId, String status) {
        aiJobMapper.updateById(new RehabAiJobDO().setId(jobId).setStatus(status));
    }

    private RehabAiOutputDO selectLatestOutputByPatient(Long patientId, List<String> outputTypes,
                                                         boolean acceptedOnly, boolean patientVisibleOnly) {
        List<RehabAiJobDO> jobs = aiJobMapper.selectListByPatientId(patientId);
        if (CollUtil.isEmpty(jobs)) {
            return null;
        }
        Set<Long> jobIds = jobs.stream().map(RehabAiJobDO::getId).collect(Collectors.toSet());
        if (CollUtil.isEmpty(jobIds)) {
            return null;
        }
        List<RehabAiOutputDO> outputs = aiOutputMapper.selectList(new LambdaQueryWrapperX<RehabAiOutputDO>()
                .in(RehabAiOutputDO::getAiJobId, jobIds)
                .inIfPresent(RehabAiOutputDO::getOutputType, outputTypes)
                .eqIfPresent(RehabAiOutputDO::getReviewStatus, acceptedOnly ? RehabAiConstants.REVIEW_STATUS_ACCEPTED : null)
                .eqIfPresent(RehabAiOutputDO::getPatientVisible, patientVisibleOnly ? true : null)
                .orderByDesc(RehabAiOutputDO::getReviewedTime)
                .orderByDesc(RehabAiOutputDO::getCreateTime)
                .orderByDesc(RehabAiOutputDO::getId));
        return CollUtil.isEmpty(outputs) ? null : outputs.get(0);
    }

    private List<RehabAiJobRespVO> toJobRespList(List<RehabAiJobDO> list) {
        Set<Long> patientIds = list.stream().map(RehabAiJobDO::getPatientId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, RehabPatientDO> patientMap = patientIds.isEmpty() ? Collections.<Long, RehabPatientDO>emptyMap() : patientMapper.selectBatchIds(patientIds)
                .stream().collect(Collectors.toMap(RehabPatientDO::getId, item -> item, (a, b) -> a));
        Set<Long> userIds = list.stream().map(RehabAiJobDO::getTriggeredByUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> userMap = userIds.isEmpty() ? Collections.<Long, AdminUserRespDTO>emptyMap() : adminUserApi.getUserMap(userIds);
        return list.stream().map(item -> {
            RehabAiJobRespVO vo = BeanUtils.toBean(item, RehabAiJobRespVO.class);
            RehabPatientDO patient = patientMap.get(item.getPatientId());
            if (patient != null) {
                vo.setPatientName(patient.getName());
            }
            if (item.getTriggeredByUserId() != null && userMap.get(item.getTriggeredByUserId()) != null) {
                vo.setTriggeredByName(userMap.get(item.getTriggeredByUserId()).getNickname());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private List<RehabAiOutputRespVO> toOutputRespList(List<RehabAiOutputDO> list) {
        Set<Long> jobIds = list.stream().map(RehabAiOutputDO::getAiJobId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, RehabAiJobDO> jobMap = CollUtil.isEmpty(jobIds) ? Collections.<Long, RehabAiJobDO>emptyMap() : aiJobMapper.selectListByIds(jobIds)
                .stream().collect(Collectors.toMap(RehabAiJobDO::getId, item -> item, (a, b) -> a));
        Set<Long> patientIds = jobMap.values().stream().map(RehabAiJobDO::getPatientId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, RehabPatientDO> patientMap = CollUtil.isEmpty(patientIds) ? Collections.<Long, RehabPatientDO>emptyMap() : patientMapper.selectBatchIds(patientIds)
                .stream().collect(Collectors.toMap(RehabPatientDO::getId, item -> item, (a, b) -> a));
        Set<Long> userIds = list.stream().map(RehabAiOutputDO::getReviewedBy).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> userMap = CollUtil.isEmpty(userIds) ? Collections.<Long, AdminUserRespDTO>emptyMap() : adminUserApi.getUserMap(userIds);

        return list.stream().map(item -> {
            RehabAiOutputRespVO vo = BeanUtils.toBean(item, RehabAiOutputRespVO.class);
            RehabAiJobDO job = jobMap.get(item.getAiJobId());
            if (job != null) {
                vo.setJobNo(job.getJobNo());
                vo.setPatientId(job.getPatientId());
                RehabPatientDO patient = patientMap.get(job.getPatientId());
                if (patient != null) {
                    vo.setPatientName(patient.getName());
                }
            }
            if (item.getReviewedBy() != null && userMap.get(item.getReviewedBy()) != null) {
                vo.setReviewedByName(userMap.get(item.getReviewedBy()).getNickname());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private void validateCanGenerate(Long operatorUserId) {
        if (dataPermissionService.isClerk(operatorUserId)) {
            throw exception(AI_GENERATE_FORBIDDEN);
        }
    }

    private void validateCanReview(Long operatorUserId) {
        if (dataPermissionService.isClerk(operatorUserId)) {
            throw exception(AI_REVIEW_FORBIDDEN);
        }
    }

    private void validateAiConfigPermission(Long operatorUserId, boolean write) {
        if (!dataPermissionService.isSuperAdmin(operatorUserId)) {
            throw exception(write ? AI_GENERATE_FORBIDDEN : PATIENT_NO_PERMISSION);
        }
    }

    private void validatePatientReadable(Long patientId, Long operatorUserId) {
        if (!dataPermissionService.canReadPatient(patientId, operatorUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
    }

    private String resolveRole(Long userId) {
        if (userId == null) {
            return "unknown";
        }
        if (dataPermissionService.isSuperAdmin(userId)) {
            return "admin";
        }
        if (dataPermissionService.isTherapist(userId)) {
            return "therapist";
        }
        if (dataPermissionService.isClerk(userId)) {
            return "clerk";
        }
        return "unknown";
    }

    private String generateJobNo(Long id) {
        return "AIJ" + JOB_NO_DATE_FORMATTER.format(LocalDateTime.now()) + String.format("%04d", id % 10000);
    }

    private String hash(String text) {
        if (text == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String text) {
        if (StrUtil.isBlank(text) || !JsonUtils.isJsonObject(text)) {
            return new LinkedHashMap<String, Object>();
        }
        Map<String, Object> map = JsonUtils.parseObject(text, Map.class);
        return map == null ? new LinkedHashMap<String, Object>() : map;
    }

    private Object parseJsonOrRaw(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        if (JsonUtils.isJson(text)) {
            try {
                return JsonUtils.parseObject(text, Object.class);
            } catch (Exception ignored) {
            }
        }
        return text;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractEvidenceRefs(Map<String, Object> outputMap) {
        if (outputMap == null) {
            return Collections.emptyList();
        }
        Object refs = outputMap.get("evidence_refs");
        if (refs instanceof List) {
            return ((List<Object>) refs).stream().map(String::valueOf).collect(Collectors.toList());
        }
        if (refs instanceof String && StrUtil.isNotBlank((String) refs)) {
            return Collections.singletonList((String) refs);
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Object appendCaveats(Object oldCaveatObj, List<String> appendItems) {
        Set<String> set = new LinkedHashSet<String>();
        if (oldCaveatObj instanceof List) {
            for (Object item : (List<Object>) oldCaveatObj) {
                set.add(String.valueOf(item));
            }
        } else if (oldCaveatObj instanceof String && StrUtil.isNotBlank((String) oldCaveatObj)) {
            set.add(String.valueOf(oldCaveatObj));
        }
        set.addAll(appendItems);
        return new ArrayList<String>(set);
    }

    private String buildPromptName(OutputPlan plan, RehabAiPromptTemplateDO template) {
        if (template == null) {
            return "builtin:" + plan.getModuleScope() + ":" + plan.getRoleScope();
        }
        return template.getTemplateCode() + ":v" + template.getVersionNo();
    }

    private String buildText(List<Object> sections) {
        return sections.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("\n"));
    }

    @SuppressWarnings("unchecked")
    private String joinList(Object obj) {
        if (obj instanceof List) {
            return ((List<Object>) obj).stream().map(String::valueOf).collect(Collectors.joining("；"));
        }
        return obj == null ? "" : String.valueOf(obj);
    }

    private Long toLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Long) {
            return (Long) obj;
        }
        if (obj instanceof Integer) {
            return ((Integer) obj).longValue();
        }
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        try {
            return Long.valueOf(String.valueOf(obj));
        } catch (Exception ex) {
            return null;
        }
    }

    private Boolean defaultIfNull(Boolean value, Boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    @Data
    @AllArgsConstructor
    private static class OutputPlan {
        private String outputType;
        private String moduleScope;
        private String roleScope;
        private String schemaName;
        private String targetObjectType;
        private Long targetObjectId;
    }

    @Data
    private static class GeneratedOutput {
        private Boolean fallbackUsed;
        private String contentJson;
        private String renderedText;
        private String evidenceRefsJson;
        private String safetyStatus;
        private Long latencyMs;
        private String promptName;

        static GeneratedOutput fromFallback(RehabAiFallbackService.FallbackOutput fallback, Long latencyMs) {
            GeneratedOutput output = new GeneratedOutput();
            output.setFallbackUsed(true);
            output.setContentJson(fallback.getContentJson());
            output.setRenderedText(fallback.getRenderedText());
            output.setEvidenceRefsJson(fallback.getEvidenceRefsJson());
            output.setSafetyStatus(fallback.getSafetyStatus());
            output.setLatencyMs(ObjUtil.defaultIfNull(latencyMs, 0L));
            output.setPromptName("fallback");
            return output;
        }
    }
}
