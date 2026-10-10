package cn.iocoder.yudao.module.rehab.service.motion.ai;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAiDraftDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionAiDraftMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionMetricMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionRuleResultMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionScoreMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionTrialMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;
import cn.iocoder.yudao.module.rehab.service.ai.RehabAiSafetyGuard;
import cn.iocoder.yudao.module.rehab.service.ai.client.OpenAiResponsesClient;
import cn.iocoder.yudao.module.rehab.service.ai.client.PlatformAiBridgeClient;
import cn.iocoder.yudao.module.rehab.service.ai.client.RehabAiClient;
import cn.iocoder.yudao.module.rehab.service.ai.client.RehabAiClientOptions;
import cn.iocoder.yudao.module.rehab.service.ai.client.RehabAiClientResponse;
import cn.iocoder.yudao.module.rehab.service.motion.RehabMotionResultWriter;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 动作评估 AI 草稿。
 * <ul>
 *   <li>三重开关：全局 yudao.rehab.ai.enable-analysis、模块 yudao.rehab.motion.ai-enabled、评估 ai_allowed；任一关闭即模板草稿。</li>
 *   <li>只发送去标识化载荷（见 {@link MotionAiPayloadBuilder}），从不发送视频、姓名、备注。</li>
 *   <li>输出经结构校验 → 证据/分数/措辞校验 → 平台安全守卫；任一失败降级为模板草稿，流程不中断。</li>
 *   <li>日志只记录 provider、model、requestId、耗时、token、提示词版本，不记录请求/响应内容。</li>
 * </ul>
 */
@Service
@Slf4j
public class RehabMotionAiService {

    @Value("${yudao.rehab.ai.enable-analysis:${OPENAI_ENABLE_AI_ANALYSIS:false}}")
    private Boolean globalEnabled = false;
    @Value("${yudao.rehab.motion.ai-enabled:${MOTION_AI_ENABLED:false}}")
    private Boolean motionEnabled = false;
    @Value("${yudao.rehab.ai.default-model:${OPENAI_MODEL:gpt-4.1-mini}}")
    private String defaultModel = "gpt-4.1-mini";
    @Value("${yudao.rehab.ai.default-timeout-seconds:${OPENAI_TIMEOUT_SECONDS:45}}")
    private Integer timeoutSeconds = 45;
    @Value("${yudao.rehab.ai.default-max-retries:${OPENAI_MAX_RETRIES:2}}")
    private Integer maxRetries = 2;
    @Value("${yudao.rehab.ai.default-reasoning-effort:${OPENAI_REASONING_EFFORT:medium}}")
    private String reasoningEffort = "medium";
    @Value("${yudao.rehab.motion.ai-max-output-tokens:2500}")
    private Integer maxOutputTokens = 2500;
    @Value("${yudao.rehab.ai.mock-mode:false}")
    private Boolean mockMode = false;
    @Value("${yudao.rehab.ai.use-platform-bridge:false}")
    private Boolean usePlatformBridge = false;

    @Resource
    private RehabMotionAiDraftMapper aiDraftMapper;
    @Resource
    private RehabMotionTrialMapper trialMapper;
    @Resource
    private RehabMotionScoreMapper scoreMapper;
    @Resource
    private RehabMotionRuleResultMapper ruleResultMapper;
    @Resource
    private RehabMotionMetricMapper metricMapper;
    @Resource
    private RehabMotionResultWriter resultWriter;
    @Autowired(required = false)
    private OpenAiResponsesClient openAiClient;
    @Autowired(required = false)
    private PlatformAiBridgeClient platformAiBridgeClient;
    @Autowired(required = false)
    private RehabAiSafetyGuard safetyGuard;

    public String disabledReason(RehabMotionAssessmentDO a) {
        if (!Boolean.TRUE.equals(globalEnabled)) {
            return "AI 全局未启用";
        }
        if (!Boolean.TRUE.equals(motionEnabled)) {
            return "动作评估 AI 未启用";
        }
        if (!Boolean.TRUE.equals(a.getAiAllowed())) {
            return "本次评估未授权使用 AI";
        }
        if (resolveClient() == null) {
            return "AI 客户端不可用";
        }
        return null;
    }

    /** 生成草稿（worker 调用）。永不抛出 AI 相关异常：失败即模板草稿。 */
    public RehabMotionAiDraftDO generate(RehabMotionAssessmentDO a, Long taskId) {
        MotionAiPayloadBuilder.Payload payload = MotionAiPayloadBuilder.build(
                trialMapper.selectListByAssessmentId(a.getId()),
                scoreMapper.selectListByAssessmentId(a.getId()),
                ruleResultMapper.selectListByAssessmentId(a.getId()),
                metricMapper.selectListByAssessmentId(a.getId()),
                RehabMotionResultWriter.limitations(resultWriter.readResult(a)));

        RehabMotionAiDraftDO draft = RehabMotionAiDraftDO.builder()
                .assessmentId(a.getId())
                .taskId(taskId)
                .promptVersion(RehabMotionConstants.PROMPT_VERSION)
                .inputHash(payload.inputHash)
                .analysisRevision(a.getAnalyzedRevision())
                .build();

        String reason = disabledReason(a);
        if (reason == null) {
            reason = callAi(payload, draft);
        }
        if (reason != null) {
            Map<String, Object> fallback = MotionAiFallback.build(payload.data, reason);
            draft.setStatus(RehabMotionConstants.AI_FALLBACK);
            draft.setFallbackReason(StrUtil.maxLength(reason, 250));
            draft.setContentJson(JsonUtils.toJsonString(fallback));
            draft.setRenderedText(MotionAiFallback.render(fallback));
            draft.setEvidenceRefsJson(JsonUtils.toJsonString(fallback.get("evidence_refs")));
            if (draft.getProvider() == null) {
                draft.setProvider("none");
            }
            if (draft.getSafetyStatus() == null) {
                draft.setSafetyStatus("not_applicable");
            }
        }
        aiDraftMapper.update(null, new LambdaUpdateWrapper<RehabMotionAiDraftDO>()
                .set(RehabMotionAiDraftDO::getStatus, RehabMotionConstants.AI_STALE)
                .eq(RehabMotionAiDraftDO::getAssessmentId, a.getId())
                .in(RehabMotionAiDraftDO::getStatus, RehabMotionConstants.AI_GENERATED, RehabMotionConstants.AI_FALLBACK));
        aiDraftMapper.insert(draft);
        log.info("[motion-ai] assessment={} status={} provider={} model={} requestId={} latencyMs={} tokens={} prompt={}",
                a.getId(), draft.getStatus(), draft.getProvider(), draft.getModel(), draft.getProviderRequestId(),
                draft.getLatencyMs(), draft.getTokenUsageJson(), draft.getPromptVersion());
        return draft;
    }

    /** @return null = 成功（draft 已填充）；否则为降级原因 */
    private String callAi(MotionAiPayloadBuilder.Payload payload, RehabMotionAiDraftDO draft) {
        RehabAiClient client = resolveClient();
        draft.setProvider(client == platformAiBridgeClient && platformAiBridgeClient != null ? "platform" : "openai");
        RehabAiClientOptions options = new RehabAiClientOptions();
        options.setModel(defaultModel);
        options.setTemperature(0.2);
        options.setMaxOutputTokens(maxOutputTokens);
        options.setReasoningEffort(reasoningEffort);
        options.setTimeoutSeconds(timeoutSeconds);
        options.setMaxRetries(maxRetries);
        options.setMockMode(Boolean.TRUE.equals(mockMode));
        draft.setModel(StrUtil.maxLength(defaultModel, 60));
        String userPrompt = "以下是去标识化的动作评估结果 JSON。请按系统规则起草报告，只引用其中的 id。\n"
                + JsonUtils.toJsonString(payload.data);
        RehabAiClientResponse resp;
        try {
            resp = client.generateStructured(MotionAiPrompts.SYSTEM_PROMPT, userPrompt, MotionAiPrompts.SCHEMA_NAME,
                    MotionAiPrompts.schema(), options);
        } catch (RuntimeException ex) {
            return "AI 调用异常: " + ex.getClass().getSimpleName();
        }
        if (resp == null) {
            return "AI 无响应";
        }
        draft.setLatencyMs(resp.getLatencyMs());
        draft.setModel(StrUtil.maxLength(StrUtil.blankToDefault(resp.getModel(), defaultModel), 60));
        draft.setTokenUsageJson(StrUtil.maxLength(resp.getTokenUsageJson(), 480));
        draft.setProviderRequestId(extractRequestId(resp.getRawResponseJson()));
        if (!Boolean.TRUE.equals(resp.getSuccess())) {
            return "AI 调用失败: " + StrUtil.maxLength(StrUtil.blankToDefault(resp.getErrorMessage(), "unknown"), 120);
        }
        Map<String, Object> output;
        try {
            output = JsonUtils.parseObject(resp.getOutputJson(), new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (RuntimeException ex) {
            return "AI 输出不是合法 JSON";
        }
        String structural = MotionAiOutputValidator.structural(output);
        if (structural != null) {
            draft.setValidationMessage(structural);
            return "AI 输出结构不合法: " + structural;
        }
        MotionAiOutputValidator.Result checked = MotionAiOutputValidator.validate(output, payload.evidenceIds,
                payload.scoreValues);
        draft.setValidationMessage(StrUtil.maxLength(String.join("; ", checked.droppedReasons), 480));
        if (checked.keptClaims == 0) {
            return "AI 输出无有效证据引用";
        }
        String rendered = MotionAiFallback.render(checked.cleaned);
        if (safetyGuard != null) {
            RehabAiSafetyGuard.SafetyResult safety = safetyGuard.check(rendered, checked.cleaned, "strict");
            draft.setSafetyStatus(safety.getSafetyStatus());
            if ("blocked".equals(safety.getSafetyStatus())) {
                return "安全守卫拦截: " + StrUtil.maxLength(safety.getReason(), 60);
            }
            rendered = StrUtil.blankToDefault(safety.getRenderedText(), rendered);
        } else {
            draft.setSafetyStatus("passed");
        }
        draft.setStatus(RehabMotionConstants.AI_GENERATED);
        draft.setContentJson(JsonUtils.toJsonString(checked.cleaned));
        draft.setRenderedText(rendered);
        draft.setEvidenceRefsJson(JsonUtils.toJsonString(checked.usedEvidence));
        return null;
    }

    private RehabAiClient resolveClient() {
        if (Boolean.TRUE.equals(usePlatformBridge) && platformAiBridgeClient != null) {
            return platformAiBridgeClient;
        }
        return openAiClient;
    }

    /** 只提取响应 ID（如 resp_xxx），不保存原始响应。 */
    static String extractRequestId(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            Map<String, Object> map = JsonUtils.parseObject(raw, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            Object id = map == null ? null : map.get("id");
            if (id instanceof String && ((String) id).matches("^[A-Za-z0-9_\\-]{1,100}$")) {
                return (String) id;
            }
        } catch (RuntimeException ignored) {
            // 非 JSON（mock 等）
        }
        return null;
    }

    public List<RehabMotionAiDraftDO> list(Long assessmentId) {
        return aiDraftMapper.selectListByAssessmentId(assessmentId);
    }

}
