package cn.iocoder.yudao.module.rehab.service.assessment;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SFMA 汇总构建器（Step 9）
 *
 * 说明：
 * 1. 仅做功能学/动作学汇总与保守分类，不输出医学诊断
 * 2. 输出写回 sfma module 的 data_json.summary / risk_precheck / report_mapping
 * 3. 支持部分录入，证据不足时降级并提示人工复核
 */
@Component
public class RehabSfmaSummaryBuilder {

    private static final String CLASS_FN = "FN";
    private static final String CLASS_FP = "FP";
    private static final String CLASS_DN = "DN";
    private static final String CLASS_DP = "DP";

    private static final String PRIMARY_T = "T";
    private static final String PRIMARY_JMD = "JMD";
    private static final String PRIMARY_SMCD = "SMCD";

    private static final String REVIEW_PRIORITY_LOW = "low";
    private static final String REVIEW_PRIORITY_NORMAL = "normal";
    private static final String REVIEW_PRIORITY_HIGH = "high";

    private static final String STATUS_SUGGESTED = "suggested";
    private static final String STATUS_ACCEPTED = "accepted";
    private static final String STATUS_SKIPPED = "skipped";
    private static final String STAGE_DN_FIRST = "dn_first";
    private static final String STAGE_FP_SECOND = "fp_second";
    private static final String STAGE_DP_LAST = "dp_last";

    private static final String BREAKOUT_STATUS_NOT_STARTED = "not_started";
    private static final String BREAKOUT_STATUS_PARTIAL = "in_progress";
    private static final String BREAKOUT_STATUS_COMPLETED = "completed";
    private static final String BREAKOUT_STATUS_SKIPPED = "skipped";
    private static final String BREAKOUT_STATUS_STOPPED_PAIN = "stopped_due_to_pain";
    private static final String CERVICAL_FLEXION_TEST_CODE = "cervical_flexion";
    private static final String CERVICAL_FLEXION_BREAKOUT_KEY = "cervical_flexion_breakout";
    private static final String CERVICAL_EXTENSION_TEST_CODE = "cervical_extension";
    private static final String CERVICAL_EXTENSION_BREAKOUT_KEY = "cervical_extension_breakout";
    private static final String CERVICAL_ROTATION_LEFT_TEST_CODE = "cervical_rotation_left";
    private static final String CERVICAL_ROTATION_RIGHT_TEST_CODE = "cervical_rotation_right";
    private static final String CERVICAL_ROTATION_BREAKOUT_KEY = "cervical_rotation_breakout";
    private static final String UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE = "upper_extremity_pattern1_left";
    private static final String UPPER_EXTREMITY_PATTERN1_RIGHT_TEST_CODE = "upper_extremity_pattern1_right";
    private static final String UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY = "upper_extremity_pattern1_breakout";
    private static final String UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE = "upper_extremity_pattern2_left";
    private static final String UPPER_EXTREMITY_PATTERN2_RIGHT_TEST_CODE = "upper_extremity_pattern2_right";
    private static final String UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY = "upper_extremity_pattern2_breakout";
    private static final String MSF_BREAKOUT_KEY = "msf_breakout";
    private static final String MSE_BREAKOUT_KEY = "mse_breakout";
    private static final String MSR_BREAKOUT_KEY = "msr_breakout";
    private static final String ARMS_DOWN_SQUAT_BREAKOUT_KEY = "arms_down_squat_breakout";

    private static final List<SfmaTopTierDefinition> TOP_TIER_DEFINITIONS = Arrays.asList(
            def(CERVICAL_FLEXION_TEST_CODE, "颈椎屈曲", "none", CERVICAL_FLEXION_BREAKOUT_KEY, "cervical"),
            def(CERVICAL_EXTENSION_TEST_CODE, "颈椎伸展", "none", CERVICAL_EXTENSION_BREAKOUT_KEY, "cervical"),
            def(CERVICAL_ROTATION_LEFT_TEST_CODE, "颈椎旋转（左）", "left", CERVICAL_ROTATION_BREAKOUT_KEY, "cervical"),
            def(CERVICAL_ROTATION_RIGHT_TEST_CODE, "颈椎旋转（右）", "right", CERVICAL_ROTATION_BREAKOUT_KEY, "cervical"),
            def(UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE, "上肢模式1（左）", "left", UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY, "upper_extremity"),
            def(UPPER_EXTREMITY_PATTERN1_RIGHT_TEST_CODE, "上肢模式1（右）", "right", UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY, "upper_extremity"),
            def(UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE, "上肢模式2（左）", "left", UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY, "upper_extremity"),
            def(UPPER_EXTREMITY_PATTERN2_RIGHT_TEST_CODE, "上肢模式2（右）", "right", UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY, "upper_extremity"),
            def("multi_segmental_flexion", "多节段屈曲（MSF）", "none", MSF_BREAKOUT_KEY, "multi_segmental"),
            def("multi_segmental_extension", "多节段伸展（MSE）", "none", MSE_BREAKOUT_KEY, "multi_segmental"),
            def("multi_segmental_rotation_left", "多节段旋转（左）", "left", MSR_BREAKOUT_KEY, "multi_segmental"),
            def("multi_segmental_rotation_right", "多节段旋转（右）", "right", MSR_BREAKOUT_KEY, "multi_segmental"),
            def("single_leg_stance_left", "单腿站立（左）", "left", "sls_left", "single_leg_stance"),
            def("single_leg_stance_right", "单腿站立（右）", "right", "sls_right", "single_leg_stance"),
            def("arms_down_deep_squat", "垂臂下蹲", "none", ARMS_DOWN_SQUAT_BREAKOUT_KEY, "deep_squat")
    );

    private static final List<String> BREAKOUT_KEYS = Arrays.asList(
            CERVICAL_FLEXION_BREAKOUT_KEY,
            CERVICAL_EXTENSION_BREAKOUT_KEY,
            CERVICAL_ROTATION_BREAKOUT_KEY,
            UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY,
            UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY,
            MSF_BREAKOUT_KEY,
            MSE_BREAKOUT_KEY,
            MSR_BREAKOUT_KEY,
            "cervical_pattern",
            "upper_extremity_pattern_left",
            "upper_extremity_pattern_right",
            "msf",
            "mse",
            "msr_left",
            "msr_right",
            "sls_left",
            "sls_right",
            ARMS_DOWN_SQUAT_BREAKOUT_KEY,
            "deep_squat"
    );

    private static final List<String> LEGACY_MIRROR_KEYS = Arrays.asList(
            "basic_info",
            "top_tier",
            "breakout_recommendations",
            "breakouts",
            "cervical_flexion_top_tier",
            "cervical_flexion_breakout",
            "cervical_extension_top_tier",
            "cervical_extension_breakout",
            "cervical_rotation_top_tier",
            "cervical_rotation_breakout",
            "upper_extremity_pattern1_top_tier",
            "upper_extremity_pattern1_breakout",
            "upper_extremity_pattern2_top_tier",
            "upper_extremity_pattern2_breakout",
            "msf_breakout",
            "mse_breakout",
            "msr_breakout",
            ARMS_DOWN_SQUAT_BREAKOUT_KEY,
            "book_protocol",
            "summary",
            "risk_precheck",
            "report_mapping"
    );

    private static final Map<String, String> BREAKOUT_NAME_MAP;
    private static final Map<String, Integer> BREAKOUT_HIERARCHY_ORDER_MAP;

    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(CERVICAL_FLEXION_BREAKOUT_KEY, "颈椎屈曲 Breakout");
        map.put(CERVICAL_EXTENSION_BREAKOUT_KEY, "颈椎伸展 Breakout");
        map.put(CERVICAL_ROTATION_BREAKOUT_KEY, "颈椎旋转 Breakout");
        map.put(UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY, "上肢模式1 Breakout（左/右）");
        map.put(UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY, "上肢模式2 Breakout（左/右）");
        map.put(MSF_BREAKOUT_KEY, "多节段屈曲 Breakout（MSF）");
        map.put(MSE_BREAKOUT_KEY, "多节段伸展 Breakout（MSE）");
        map.put(MSR_BREAKOUT_KEY, "多节段旋转 Breakout（MSR）");
        map.put("cervical_pattern", "颈椎模式 Breakout");
        map.put("upper_extremity_pattern_left", "上肢模式 Breakout（左）");
        map.put("upper_extremity_pattern_right", "上肢模式 Breakout（右）");
        map.put("msf", "MSF Breakout");
        map.put("mse", "MSE Breakout（Legacy）");
        map.put("msr_left", "MSR Breakout（左）");
        map.put("msr_right", "MSR Breakout（右）");
        map.put("sls_left", "SLS Breakout（左）");
        map.put("sls_right", "SLS Breakout（右）");
        map.put(ARMS_DOWN_SQUAT_BREAKOUT_KEY, "垂臂下蹲分解评估");
        map.put("deep_squat", "垂臂下蹲 Breakout");
        BREAKOUT_NAME_MAP = Collections.unmodifiableMap(map);

        Map<String, Integer> hierarchy = new LinkedHashMap<>();
        hierarchy.put(CERVICAL_FLEXION_BREAKOUT_KEY, 10);
        hierarchy.put(CERVICAL_EXTENSION_BREAKOUT_KEY, 11);
        hierarchy.put(CERVICAL_ROTATION_BREAKOUT_KEY, 12);
        hierarchy.put(UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY, 22);
        hierarchy.put(UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY, 23);
        hierarchy.put(MSF_BREAKOUT_KEY, 30);
        hierarchy.put(MSE_BREAKOUT_KEY, 31);
        hierarchy.put(MSR_BREAKOUT_KEY, 40);
        hierarchy.put("cervical_pattern", 12);
        hierarchy.put("upper_extremity_pattern_left", 20);
        hierarchy.put("upper_extremity_pattern_right", 21);
        hierarchy.put("msf", 30);
        hierarchy.put("mse", 31);
        hierarchy.put("msr_left", 41);
        hierarchy.put("msr_right", 42);
        hierarchy.put("sls_left", 50);
        hierarchy.put("sls_right", 51);
        hierarchy.put(ARMS_DOWN_SQUAT_BREAKOUT_KEY, 60);
        hierarchy.put("deep_squat", 60);
        BREAKOUT_HIERARCHY_ORDER_MAP = Collections.unmodifiableMap(hierarchy);
    }

    public Map<String, Object> enrichWithSummary(Object rawDataJson) {
        Map<String, Object> rawPayload = normalizeToMap(rawDataJson);
        Map<String, Object> payload = extractSfmaPayload(rawPayload);
        syncDedicatedCervicalStructure(payload);

        Map<String, Object> basicInfo = ensureMap(payload, "basic_info");
        if (basicInfo.isEmpty()) {
            basicInfo.put("assessment_date", "");
            basicInfo.put("assessor", "");
        }

        Map<String, Map<String, Object>> topTierMap = ensureTopTier(payload);
        List<Map<String, Object>> recommendations = buildRecommendations(payload, topTierMap);
        syncBookProtocolToLegacyBreakouts(payload);
        Map<String, Map<String, Object>> breakouts = ensureBreakouts(payload, recommendations);
        syncDedicatedCervicalFromNormalized(payload, topTierMap, breakouts, recommendations);
        Map<String, Object> summary = buildSummary(payload, topTierMap, recommendations, breakouts);
        Map<String, Object> riskPrecheck = buildRiskPrecheck(topTierMap, breakouts, summary);
        Map<String, Object> reportMapping = buildReportMapping(summary, recommendations, breakouts, riskPrecheck);

        payload.put("top_tier", topTierMap);
        payload.put("breakout_recommendations", recommendations);
        payload.put("breakouts", breakouts);
        payload.put("summary", summary);
        payload.put("risk_precheck", riskPrecheck);
        payload.put("report_mapping", reportMapping);
        return wrapSfmaPayload(rawPayload, payload);
    }

    private void syncBookProtocolToLegacyBreakouts(Map<String, Object> payload) {
        Map<String, Object> protocol = castToMap(payload.get("book_protocol"));
        if (protocol == null) {
            return;
        }
        Map<String, Object> protocolWorkflows = castToMap(protocol.get("workflows"));
        if (protocolWorkflows == null) {
            return;
        }
        Map<String, Object> legacyBreakouts = ensureMap(payload, "breakouts");
        Map<String, List<String>> keyMapping = new LinkedHashMap<>();
        keyMapping.put("cervical", Arrays.asList(
                CERVICAL_FLEXION_BREAKOUT_KEY, CERVICAL_EXTENSION_BREAKOUT_KEY,
                CERVICAL_ROTATION_BREAKOUT_KEY, "cervical_pattern"));
        keyMapping.put("upper_extremity_pattern_1", Collections.singletonList(UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY));
        keyMapping.put("upper_extremity_pattern_2", Arrays.asList(
                UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY, "upper_extremity_pattern_left", "upper_extremity_pattern_right"));
        keyMapping.put("msf", Arrays.asList(MSF_BREAKOUT_KEY, "msf"));
        keyMapping.put("mse", Arrays.asList(MSE_BREAKOUT_KEY, "mse"));
        keyMapping.put("msr", Arrays.asList(MSR_BREAKOUT_KEY, "msr_left", "msr_right"));
        keyMapping.put("sls", Arrays.asList("sls_left", "sls_right"));
        keyMapping.put("ods", Arrays.asList(ARMS_DOWN_SQUAT_BREAKOUT_KEY, "deep_squat"));

        for (Map.Entry<String, List<String>> mapping : keyMapping.entrySet()) {
            Map<String, Object> workflow = castToMap(protocolWorkflows.get(mapping.getKey()));
            if (workflow == null) {
                continue;
            }
            String status = toStringValue(workflow.get("status"));
            List<String> findings = new ArrayList<>();
            boolean painPresent = false;
            int completedSteps = 0;
            for (Map<String, Object> step : castToMapList(workflow.get("steps"))) {
                String classification = normalizeClassification(step.get("classification"));
                String leftClassification = normalizeClassification(step.get("left_classification"));
                String rightClassification = normalizeClassification(step.get("right_classification"));
                if (BREAKOUT_STATUS_COMPLETED.equals(toStringValue(step.get("status")))) {
                    completedSteps++;
                }
                if (CLASS_FP.equals(classification) || CLASS_DP.equals(classification)
                        || CLASS_FP.equals(leftClassification) || CLASS_DP.equals(leftClassification)
                        || CLASS_FP.equals(rightClassification) || CLASS_DP.equals(rightClassification)) {
                    painPresent = true;
                }
                List<String> results = Arrays.asList(classification, leftClassification, rightClassification).stream()
                        .filter(StrUtil::isNotBlank).collect(Collectors.toList());
                if (!results.isEmpty()) {
                    findings.add(toStringValue(step.get("test_code")) + "=" + String.join("/", results));
                }
            }
            Map<String, Object> legacy = new LinkedHashMap<>();
            legacy.put("status", status);
            legacy.put("findings", String.join("；", findings));
            legacy.put("rom_key_values", "原书版协议已记录 " + completedSteps + " 个步骤");
            legacy.put("pain_present", painPresent);
            legacy.put("pain_vas", null);
            legacy.put("mobility_restriction_signs", "");
            legacy.put("motor_control_signs", "");
            legacy.put("asymmetry_signs", "");
            legacy.put("stop_due_to_pain", BREAKOUT_STATUS_STOPPED_PAIN.equals(status));
            legacy.put("stop_reason", BREAKOUT_STATUS_STOPPED_PAIN.equals(status) ? "分解测试出现疼痛，已按原书规则终止后续步骤" : "");
            legacy.put("clinician_note", toStringValue(workflow.get("note")));
            legacy.put("method", "SFMA 原书版分解评估");
            legacy.put("scale", "FN/FP/DN/DP");
            legacy.put("source_id", RehabSfmaBookProtocol.PROTOCOL_ID + ":" + RehabSfmaBookProtocol.PROTOCOL_VERSION);
            legacy.put("date", "");
            for (String legacyKey : mapping.getValue()) {
                legacyBreakouts.put(legacyKey, new LinkedHashMap<>(legacy));
            }
        }
    }

    public Map<String, Object> enrichWithFallback(Object rawDataJson, String reason) {
        Map<String, Object> rawPayload = normalizeToMap(rawDataJson);
        Map<String, Object> payload = extractSfmaPayload(rawPayload);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("top_tier_table", Collections.emptyList());
        summary.put("breakout_table", Collections.emptyList());
        Map<String, Object> fallbackCervicalTopTierSummary = new LinkedHashMap<>();
        fallbackCervicalTopTierSummary.put("classification", "");
        fallbackCervicalTopTierSummary.put("pain_present", false);
        fallbackCervicalTopTierSummary.put("breakout_suggested", false);
        fallbackCervicalTopTierSummary.put("breakout_completed", false);
        fallbackCervicalTopTierSummary.put("review_priority", REVIEW_PRIORITY_LOW);
        fallbackCervicalTopTierSummary.put("summary_text", "颈椎屈曲暂未完成 Top Tier 评估。");
        Map<String, Object> fallbackCervicalExtensionTopTierSummary = new LinkedHashMap<>(fallbackCervicalTopTierSummary);
        fallbackCervicalExtensionTopTierSummary.put("summary_text", "颈椎伸展暂未完成 Top Tier 评估。");
        Map<String, Object> fallbackTopTierSummary = new LinkedHashMap<>();
        fallbackTopTierSummary.put(CERVICAL_FLEXION_TEST_CODE, fallbackCervicalTopTierSummary);
        fallbackTopTierSummary.put(CERVICAL_EXTENSION_TEST_CODE, fallbackCervicalExtensionTopTierSummary);
        fallbackTopTierSummary.put(CERVICAL_ROTATION_LEFT_TEST_CODE, new LinkedHashMap<>(fallbackCervicalTopTierSummary));
        fallbackTopTierSummary.put(CERVICAL_ROTATION_RIGHT_TEST_CODE, new LinkedHashMap<>(fallbackCervicalTopTierSummary));
        fallbackTopTierSummary.put(UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE, new LinkedHashMap<>(fallbackCervicalTopTierSummary));
        fallbackTopTierSummary.put(UPPER_EXTREMITY_PATTERN1_RIGHT_TEST_CODE, new LinkedHashMap<>(fallbackCervicalTopTierSummary));
        fallbackTopTierSummary.put(UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE, new LinkedHashMap<>(fallbackCervicalTopTierSummary));
        fallbackTopTierSummary.put(UPPER_EXTREMITY_PATTERN2_RIGHT_TEST_CODE, new LinkedHashMap<>(fallbackCervicalTopTierSummary));
        fallbackTopTierSummary.put("multi_segmental_flexion", new LinkedHashMap<>(fallbackCervicalTopTierSummary));
        summary.put("top_tier_summary_item", fallbackTopTierSummary);

        Map<String, Object> fallbackCervicalBreakoutSummary = new LinkedHashMap<>();
        fallbackCervicalBreakoutSummary.put("breakout_status", BREAKOUT_STATUS_NOT_STARTED);
        fallbackCervicalBreakoutSummary.put("primary_findings", Collections.emptyList());
        fallbackCervicalBreakoutSummary.put("preliminary_direction", Collections.emptyList());
        fallbackCervicalBreakoutSummary.put("needs_manual_review", true);
        fallbackCervicalBreakoutSummary.put("summary_text", "颈椎屈曲分解评估尚未开始。");
        Map<String, Object> fallbackCervicalExtensionBreakoutSummary = new LinkedHashMap<>(fallbackCervicalBreakoutSummary);
        fallbackCervicalExtensionBreakoutSummary.put("summary_text", "颈椎伸展分解评估尚未开始。");
        Map<String, Object> fallbackBreakoutSummary = new LinkedHashMap<>();
        fallbackBreakoutSummary.put(CERVICAL_FLEXION_TEST_CODE, fallbackCervicalBreakoutSummary);
        fallbackBreakoutSummary.put(CERVICAL_EXTENSION_TEST_CODE, fallbackCervicalExtensionBreakoutSummary);
        fallbackBreakoutSummary.put(CERVICAL_ROTATION_LEFT_TEST_CODE, new LinkedHashMap<>(fallbackCervicalBreakoutSummary));
        fallbackBreakoutSummary.put(CERVICAL_ROTATION_RIGHT_TEST_CODE, new LinkedHashMap<>(fallbackCervicalBreakoutSummary));
        fallbackBreakoutSummary.put(UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE, new LinkedHashMap<>(fallbackCervicalBreakoutSummary));
        fallbackBreakoutSummary.put(UPPER_EXTREMITY_PATTERN1_RIGHT_TEST_CODE, new LinkedHashMap<>(fallbackCervicalBreakoutSummary));
        fallbackBreakoutSummary.put(UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE, new LinkedHashMap<>(fallbackCervicalBreakoutSummary));
        fallbackBreakoutSummary.put(UPPER_EXTREMITY_PATTERN2_RIGHT_TEST_CODE, new LinkedHashMap<>(fallbackCervicalBreakoutSummary));
        fallbackBreakoutSummary.put("multi_segmental_flexion", new LinkedHashMap<>(fallbackCervicalBreakoutSummary));
        summary.put("breakout_summary_item", fallbackBreakoutSummary);
        summary.put("primary_classification", PRIMARY_T);
        summary.put("secondary_classification", Collections.emptyList());
        summary.put("classification_confidence", "low");
        summary.put("mixed_pattern_possible", true);
        summary.put("clinical_meaning", "SFMA 汇总生成失败，当前仅保留基础录入结果，需结合人工复核。");
        summary.put("training_direction", "建议人工复核后再制定训练取向。");
        summary.put("priority_1", "证据不足");
        summary.put("priority_2", "证据不足");
        summary.put("priority_3", "证据不足");
        summary.put("major_limitation_chains", Collections.emptyList());
        summary.put("major_control_deficit_chains", Collections.emptyList());
        summary.put("left_right_key_asymmetry", Collections.emptyList());
        summary.put("manual_review_or_referral_hint", "建议优先人工复核；需结合进一步评估确认。");
        summary.put("pain_related_patterns", Collections.emptyList());
        summary.put("needs_manual_review", true);
        summary.put("evidence_refs", Collections.emptyList());
        summary.put("caveats", Collections.singletonList("summary_builder_failed:" + StrUtil.blankToDefault(reason, "unknown")));

        Map<String, Object> risk = new LinkedHashMap<>();
        risk.put("overall_risk_level", "medium");
        risk.put("risk_tags", Collections.singletonList("reassessment_attention"));
        risk.put("reason_text", "SFMA 自动初筛失败，已降级为人工复核优先。");
        risk.put("needs_manual_review", true);
        risk.put("internal_rule_only", true);
        risk.put("provisional_v1", true);
        risk.put("evidence_refs", Collections.emptyList());

        Map<String, Object> reportMapping = new LinkedHashMap<>();
        Map<String, Object> sfma = new LinkedHashMap<>();
        sfma.put("sfma_interpretation", Collections.singletonMap("classification_judgement", "证据不足，需人工复核"));
        sfma.put("classification_and_priority", Collections.singletonMap("primary", PRIMARY_T));
        sfma.put("major_limitation_chains", Collections.emptyList());
        sfma.put("major_control_deficit_chains", Collections.emptyList());
        sfma.put("left_right_asymmetry_focus", Collections.emptyList());
        sfma.put("manual_review_hint", "建议优先人工复核");
        Map<String, Object> fallbackCervicalMapping = new LinkedHashMap<>();
        fallbackCervicalMapping.put("top_tier_result", "");
        fallbackCervicalMapping.put("breakout_status", BREAKOUT_STATUS_NOT_STARTED);
        fallbackCervicalMapping.put("preliminary_direction", Collections.emptyList());
        fallbackCervicalMapping.put("needs_manual_review", true);
        fallbackCervicalMapping.put("summary_text", "证据不足，需人工复核。");
        sfma.put("cervical_flexion", fallbackCervicalMapping);
        Map<String, Object> fallbackCervicalExtensionMapping = new LinkedHashMap<>(fallbackCervicalMapping);
        fallbackCervicalExtensionMapping.put("summary_text", "颈椎伸展证据不足，需人工复核。");
        sfma.put("cervical_extension", fallbackCervicalExtensionMapping);
        Map<String, Object> fallbackCervicalRotationMapping = new LinkedHashMap<>();
        fallbackCervicalRotationMapping.put("left", new LinkedHashMap<>(fallbackCervicalMapping));
        fallbackCervicalRotationMapping.put("right", new LinkedHashMap<>(fallbackCervicalMapping));
        fallbackCervicalRotationMapping.put("rotation_asymmetry_focus", Collections.emptyList());
        sfma.put("cervical_rotation", fallbackCervicalRotationMapping);
        Map<String, Object> fallbackUe1Mapping = new LinkedHashMap<>();
        fallbackUe1Mapping.put("left", new LinkedHashMap<>(fallbackCervicalMapping));
        fallbackUe1Mapping.put("right", new LinkedHashMap<>(fallbackCervicalMapping));
        fallbackUe1Mapping.put("asymmetry_focus", Collections.emptyList());
        sfma.put("upper_extremity_pattern1", fallbackUe1Mapping);
        Map<String, Object> fallbackUe2Mapping = new LinkedHashMap<>();
        fallbackUe2Mapping.put("left", new LinkedHashMap<>(fallbackCervicalMapping));
        fallbackUe2Mapping.put("right", new LinkedHashMap<>(fallbackCervicalMapping));
        fallbackUe2Mapping.put("asymmetry_focus", Collections.emptyList());
        sfma.put("upper_extremity_pattern2", fallbackUe2Mapping);
        Map<String, Object> fallbackMsfMapping = new LinkedHashMap<>();
        fallbackMsfMapping.put("top_tier_result", "");
        fallbackMsfMapping.put("breakout_status", BREAKOUT_STATUS_NOT_STARTED);
        fallbackMsfMapping.put("preliminary_direction", Collections.emptyList());
        fallbackMsfMapping.put("primary_restriction_chain", Collections.emptyList());
        fallbackMsfMapping.put("primary_control_deficit_chain", Collections.emptyList());
        fallbackMsfMapping.put("left_right_asymmetry_focus", "");
        fallbackMsfMapping.put("clinical_meaning_hint", "");
        fallbackMsfMapping.put("training_direction_hint", "");
        fallbackMsfMapping.put("pause_or_referral_hint", "");
        fallbackMsfMapping.put("needs_manual_review", true);
        fallbackMsfMapping.put("summary_text", "多部位屈曲分解评估证据不足，需人工复核。");
        sfma.put("msf_breakout", fallbackMsfMapping);
        Map<String, Object> fallbackMseMapping = new LinkedHashMap<>();
        fallbackMseMapping.put("top_tier_result", "");
        fallbackMseMapping.put("breakout_status", BREAKOUT_STATUS_NOT_STARTED);
        fallbackMseMapping.put("preliminary_direction", Collections.emptyList());
        fallbackMseMapping.put("primary_restriction_chain", Collections.emptyList());
        fallbackMseMapping.put("primary_control_deficit_chain", Collections.emptyList());
        fallbackMseMapping.put("left_right_asymmetry_focus", "");
        fallbackMseMapping.put("clinical_meaning_hint", "");
        fallbackMseMapping.put("training_direction_hint", "");
        fallbackMseMapping.put("pause_or_referral_hint", "");
        fallbackMseMapping.put("needs_manual_review", true);
        fallbackMseMapping.put("manual_review_required", true);
        fallbackMseMapping.put("primary_region", "");
        fallbackMseMapping.put("likely_pattern", Collections.emptyList());
        fallbackMseMapping.put("upper_body_extension_flow_needed", false);
        fallbackMseMapping.put("lower_body_extension_flow_needed", false);
        fallbackMseMapping.put("next_flow_targets", Collections.emptyList());
        fallbackMseMapping.put("stop_and_treat_pain", false);
        fallbackMseMapping.put("summary_text", "多部位伸展分解评估证据不足，需人工复核。");
        sfma.put("multi_segmental_extension", fallbackMseMapping);
        sfma.put("mse_breakout", new LinkedHashMap<>(fallbackMseMapping));
        Map<String, Object> fallbackArmsDownMapping = new LinkedHashMap<>();
        fallbackArmsDownMapping.put("top_tier_result", "");
        fallbackArmsDownMapping.put("breakout_status", BREAKOUT_STATUS_NOT_STARTED);
        fallbackArmsDownMapping.put("preliminary_direction", Collections.emptyList());
        fallbackArmsDownMapping.put("primary_restriction_chain", Collections.emptyList());
        fallbackArmsDownMapping.put("primary_control_deficit_chain", Collections.emptyList());
        fallbackArmsDownMapping.put("risk_precheck_level", "medium");
        fallbackArmsDownMapping.put("risk_tags", Collections.emptyList());
        fallbackArmsDownMapping.put("clinical_meaning_hint", "");
        fallbackArmsDownMapping.put("training_direction_hint", "");
        fallbackArmsDownMapping.put("pause_or_referral_hint", "");
        fallbackArmsDownMapping.put("needs_manual_review", true);
        fallbackArmsDownMapping.put("summary_text", "垂臂下蹲分解评估证据不足，需人工复核。");
        sfma.put("arms_down_squat_breakout", fallbackArmsDownMapping);
        Map<String, Object> fallbackMsrSideMapping = new LinkedHashMap<>();
        fallbackMsrSideMapping.put("top_tier_result", "");
        fallbackMsrSideMapping.put("breakout_status", BREAKOUT_STATUS_NOT_STARTED);
        fallbackMsrSideMapping.put("preliminary_direction", Collections.emptyList());
        fallbackMsrSideMapping.put("primary_restriction_chain", Collections.emptyList());
        fallbackMsrSideMapping.put("primary_control_deficit_chain", Collections.emptyList());
        fallbackMsrSideMapping.put("side_specific_priority", "");
        fallbackMsrSideMapping.put("clinical_meaning_hint", "");
        fallbackMsrSideMapping.put("training_direction_hint", "");
        fallbackMsrSideMapping.put("pause_or_referral_hint", "");
        fallbackMsrSideMapping.put("needs_manual_review", true);
        fallbackMsrSideMapping.put("summary_text", "多部位旋转分解评估证据不足，需人工复核。");
        Map<String, Object> fallbackMsrMapping = new LinkedHashMap<>();
        fallbackMsrMapping.put("left", new LinkedHashMap<>(fallbackMsrSideMapping));
        fallbackMsrMapping.put("right", new LinkedHashMap<>(fallbackMsrSideMapping));
        fallbackMsrMapping.put("rotation_asymmetry_focus", Collections.emptyList());
        sfma.put("multi_segmental_rotation", fallbackMsrMapping);
        sfma.put("msr_breakout", new LinkedHashMap<>(fallbackMsrMapping));
        reportMapping.put("sfma", sfma);

        payload.put("cervical_flexion_top_tier", normalizeDedicatedCervicalTopTier(Collections.emptyMap()));
        payload.put("cervical_flexion_breakout", normalizeDedicatedCervicalBreakout(Collections.emptyMap()));
        payload.put("cervical_extension_top_tier", normalizeDedicatedCervicalExtensionTopTier(Collections.emptyMap()));
        payload.put("cervical_extension_breakout", normalizeDedicatedCervicalExtensionBreakout(Collections.emptyMap()));
        payload.put("cervical_rotation_top_tier", normalizeDedicatedCervicalRotationTopTier(Collections.emptyMap()));
        payload.put("cervical_rotation_breakout", normalizeDedicatedCervicalRotationBreakout(Collections.emptyMap()));
        payload.put("upper_extremity_pattern1_top_tier", normalizeDedicatedUpperExtremityPattern1TopTier(Collections.emptyMap()));
        payload.put("upper_extremity_pattern1_breakout", normalizeDedicatedUpperExtremityPattern1Breakout(Collections.emptyMap()));
        payload.put("upper_extremity_pattern2_top_tier", normalizeDedicatedUpperExtremityPattern2TopTier(Collections.emptyMap()));
        payload.put("upper_extremity_pattern2_breakout", normalizeDedicatedUpperExtremityPattern2Breakout(Collections.emptyMap()));
        payload.put("msf_breakout", normalizeDedicatedMsfBreakout(Collections.emptyMap()));
        payload.put("mse_breakout", normalizeDedicatedMseBreakout(Collections.emptyMap()));
        payload.put("arms_down_squat_breakout", normalizeDedicatedArmsDownSquatBreakout(Collections.emptyMap()));
        payload.put("msr_breakout", normalizeDedicatedMsrBreakout(Collections.emptyMap()));
        payload.put("summary", summary);
        payload.put("risk_precheck", risk);
        payload.put("report_mapping", reportMapping);
        return wrapSfmaPayload(rawPayload, payload);
    }

    private Map<String, Map<String, Object>> ensureTopTier(Map<String, Object> payload) {
        Map<String, Object> rawTopTier = ensureMap(payload, "top_tier");
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        Map<String, Object> dedicatedCervicalTopTier = castToMap(payload.get("cervical_flexion_top_tier"));
        Map<String, Object> dedicatedCervicalExtensionTopTier = castToMap(payload.get("cervical_extension_top_tier"));
        Map<String, Object> dedicatedCervicalRotationTopTier = castToMap(payload.get("cervical_rotation_top_tier"));
        Map<String, Object> dedicatedUpperExtremityPattern1TopTier = castToMap(payload.get("upper_extremity_pattern1_top_tier"));
        Map<String, Object> dedicatedUpperExtremityPattern2TopTier = castToMap(payload.get("upper_extremity_pattern2_top_tier"));

        for (SfmaTopTierDefinition definition : TOP_TIER_DEFINITIONS) {
            Map<String, Object> row;
            if (StrUtil.equals(definition.getTestCode(), CERVICAL_FLEXION_TEST_CODE)
                    && dedicatedCervicalTopTier != null && !dedicatedCervicalTopTier.isEmpty()) {
                row = mapDedicatedCervicalTopTierToLegacy(dedicatedCervicalTopTier);
            } else if (StrUtil.equals(definition.getTestCode(), CERVICAL_EXTENSION_TEST_CODE)
                    && dedicatedCervicalExtensionTopTier != null && !dedicatedCervicalExtensionTopTier.isEmpty()) {
                row = mapDedicatedCervicalExtensionTopTierToLegacy(dedicatedCervicalExtensionTopTier);
            } else if (StrUtil.equalsAny(definition.getTestCode(), CERVICAL_ROTATION_LEFT_TEST_CODE, CERVICAL_ROTATION_RIGHT_TEST_CODE)
                    && dedicatedCervicalRotationTopTier != null && !dedicatedCervicalRotationTopTier.isEmpty()) {
                row = mapDedicatedCervicalRotationTopTierSideToLegacy(dedicatedCervicalRotationTopTier, definition.getTestCode());
            } else if (StrUtil.equalsAny(definition.getTestCode(), UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE, UPPER_EXTREMITY_PATTERN1_RIGHT_TEST_CODE)
                    && dedicatedUpperExtremityPattern1TopTier != null && !dedicatedUpperExtremityPattern1TopTier.isEmpty()) {
                row = mapDedicatedUpperExtremityPattern1TopTierSideToLegacy(dedicatedUpperExtremityPattern1TopTier, definition.getTestCode());
            } else if (StrUtil.equalsAny(definition.getTestCode(), UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE, UPPER_EXTREMITY_PATTERN2_RIGHT_TEST_CODE)
                    && dedicatedUpperExtremityPattern2TopTier != null && !dedicatedUpperExtremityPattern2TopTier.isEmpty()) {
                row = mapDedicatedUpperExtremityPattern2TopTierSideToLegacy(dedicatedUpperExtremityPattern2TopTier, definition.getTestCode());
            } else {
                row = castToMap(rawTopTier.get(definition.getTestCode()));
            }
            if (row == null) {
                row = new LinkedHashMap<>();
            } else {
                row = new LinkedHashMap<>(row);
            }

            row.put("test_code", definition.getTestCode());
            row.put("test_name_zh", definition.getTestNameZh());
            row.put("side", definition.getSide());

            String classification = normalizeClassification(row.get("classification"));
            row.put("classification", classification);
            boolean painPresent = Boolean.TRUE.equals(row.get("pain_present"));

            if (CLASS_FP.equals(classification) || CLASS_DP.equals(classification)) {
                painPresent = true;
                row.put("review_priority", REVIEW_PRIORITY_HIGH);
                row.put("needs_breakout_suggestion", true);
                row.put("breakout_reason_text", StrUtil.blankToDefault(
                        toStringValue(row.get("breakout_reason_text")),
                        "疼痛性功能模式（FP/DP），建议优先疼痛管理并谨慎继续 Breakout。"
                ));
                row.put("caution_text", StrUtil.blankToDefault(
                        toStringValue(row.get("caution_text")),
                        "优先疼痛管理/谨慎继续分解"
                ));
            } else if (CLASS_DN.equals(classification)) {
                row.put("review_priority", StrUtil.blankToDefault(toStringValue(row.get("review_priority")), REVIEW_PRIORITY_NORMAL));
                row.put("needs_breakout_suggestion", true);
                row.put("breakout_reason_text", StrUtil.blankToDefault(
                        toStringValue(row.get("breakout_reason_text")),
                        "存在非疼痛性功能障碍（DN），建议进入 Breakout 分解。"
                ));
                if (StrUtil.equals(toStringValue(row.get("caution_text")), "优先疼痛管理/谨慎继续分解")) {
                    row.put("caution_text", "");
                }
            } else if (CLASS_FN.equals(classification)) {
                row.put("review_priority", StrUtil.blankToDefault(toStringValue(row.get("review_priority")), REVIEW_PRIORITY_LOW));
                row.put("needs_breakout_suggestion", false);
                row.put("breakout_reason_text", "");
                if (StrUtil.equals(toStringValue(row.get("caution_text")), "优先疼痛管理/谨慎继续分解")) {
                    row.put("caution_text", "");
                }
            } else {
                row.put("review_priority", StrUtil.blankToDefault(toStringValue(row.get("review_priority")), REVIEW_PRIORITY_NORMAL));
                row.put("needs_breakout_suggestion", false);
                row.put("breakout_reason_text", "");
            }

            row.put("pain_present", painPresent);
            row.put("movement_quality_note", StrUtil.blankToDefault(toStringValue(row.get("movement_quality_note")), ""));
            row.put("key_observation_note", StrUtil.blankToDefault(toStringValue(row.get("key_observation_note")), ""));
            row.put("rom_key_value", StrUtil.blankToDefault(toStringValue(row.get("rom_key_value")), ""));
            row.put("pain_vas", normalizeNumber(row.get("pain_vas")));
            row.put("clinician_note", StrUtil.blankToDefault(toStringValue(row.get("clinician_note")), ""));
            result.put(definition.getTestCode(), row);
        }
        return result;
    }

    private List<Map<String, Object>> buildRecommendations(Map<String, Object> payload,
                                                           Map<String, Map<String, Object>> topTierMap) {
        List<Map<String, Object>> existing = castToMapList(payload.get("breakout_recommendations"));
        Map<String, Map<String, Object>> existingByTestCode = existing.stream()
                .filter(item -> StrUtil.isNotBlank(toStringValue(item.get("test_code"))))
                .collect(Collectors.toMap(item -> toStringValue(item.get("test_code")), item -> item, (a, b) -> a));
        boolean topTierComplete = TOP_TIER_DEFINITIONS.stream()
                .allMatch(definition -> StrUtil.isNotBlank(normalizeClassification(
                        topTierMap.get(definition.getTestCode()) == null ? null
                                : topTierMap.get(definition.getTestCode()).get("classification"))));

        if (!topTierComplete) {
            return existing.stream().map(item -> {
                Map<String, Object> copy = new LinkedHashMap<>(item);
                copy.put("recommendation_status", normalizeRecommendationStatus(copy.get("recommendation_status")));
                String stage = normalizeRecommendationStage(copy.get("recommendation_stage"));
                if (stage == null) {
                    stage = recommendationStageByClassification(normalizeClassification(copy.get("classification")));
                }
                int order = normalizeInt(copy.get("recommendation_order"),
                        buildRecommendationOrder(stage, toStringValue(copy.get("breakout_key"))));
                copy.put("recommendation_stage", stage);
                copy.put("recommendation_order", order);
                copy.put("recommendation_note", StrUtil.blankToDefault(toStringValue(copy.get("recommendation_note")), ""));
                return copy;
            }).sorted((a, b) -> Integer.compare(normalizeInt(a.get("recommendation_order"), Integer.MAX_VALUE),
                    normalizeInt(b.get("recommendation_order"), Integer.MAX_VALUE)))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> recommendations = new ArrayList<>();
        for (SfmaTopTierDefinition definition : TOP_TIER_DEFINITIONS) {
            Map<String, Object> topTier = topTierMap.get(definition.getTestCode());
            if (topTier == null) {
                continue;
            }
            String classification = normalizeClassification(topTier.get("classification"));
            boolean suggested = CLASS_DN.equals(classification) || CLASS_FP.equals(classification) || CLASS_DP.equals(classification);
            if (!suggested) {
                continue;
            }
            Map<String, Object> existed = existingByTestCode.get(definition.getTestCode());
            String status = existed == null ? STATUS_SUGGESTED : normalizeRecommendationStatus(existed.get("recommendation_status"));
            String stage = recommendationStageByClassification(classification);
            Map<String, Object> recommendation = new LinkedHashMap<>();
            recommendation.put("recommendation_id", existed == null
                    ? "rec_" + definition.getTestCode()
                    : StrUtil.blankToDefault(toStringValue(existed.get("recommendation_id")), "rec_" + definition.getTestCode()));
            recommendation.put("test_code", definition.getTestCode());
            recommendation.put("test_name_zh", definition.getTestNameZh());
            recommendation.put("classification", classification);
            recommendation.put("breakout_key", definition.getBreakoutKey());
            recommendation.put("recommendation_status", status);
            recommendation.put("recommendation_stage", stage);
            recommendation.put("recommendation_order", existed != null
                    ? normalizeInt(existed.get("recommendation_order"),
                    buildRecommendationOrder(stage, definition.getBreakoutKey()))
                    : buildRecommendationOrder(stage, definition.getBreakoutKey()));
            recommendation.put("recommendation_reason", StrUtil.blankToDefault(
                    toStringValue(topTier.get("breakout_reason_text")),
                    CLASS_DN.equals(classification)
                            ? "存在非疼痛性功能障碍（DN），建议优先进入 Breakout 分解。"
                            : CLASS_FP.equals(classification)
                            ? "功能存在疼痛（FP），建议在疼痛管理前提下进入 Breakout。"
                            : "功能障碍并伴疼痛（DP），建议疼痛管理优先，DP 分解建议放在最后阶段。"
            ));
            recommendation.put("recommendation_note", existed == null ? "" : StrUtil.blankToDefault(toStringValue(existed.get("recommendation_note")), ""));
            recommendation.put("review_priority", StrUtil.blankToDefault(toStringValue(topTier.get("review_priority")), REVIEW_PRIORITY_NORMAL));
            recommendation.put("caution_text", StrUtil.blankToDefault(toStringValue(topTier.get("caution_text")), ""));
            recommendations.add(recommendation);
        }
        recommendations.sort((a, b) -> Integer.compare(
                normalizeInt(a.get("recommendation_order"), Integer.MAX_VALUE),
                normalizeInt(b.get("recommendation_order"), Integer.MAX_VALUE)
        ));
        return recommendations;
    }

    private Map<String, Map<String, Object>> ensureBreakouts(Map<String, Object> payload,
                                                             List<Map<String, Object>> recommendations) {
        Map<String, Object> raw = ensureMap(payload, "breakouts");
        Map<String, Map<String, Object>> breakouts = new LinkedHashMap<>();
        Map<String, Object> dedicatedCervicalBreakout = castToMap(payload.get("cervical_flexion_breakout"));
        Map<String, Object> dedicatedCervicalExtensionBreakout = castToMap(payload.get("cervical_extension_breakout"));
        Map<String, Object> dedicatedCervicalRotationBreakout = castToMap(payload.get("cervical_rotation_breakout"));
        Map<String, Object> dedicatedUpperExtremityPattern1Breakout = castToMap(payload.get("upper_extremity_pattern1_breakout"));
        Map<String, Object> dedicatedUpperExtremityPattern2Breakout = castToMap(payload.get("upper_extremity_pattern2_breakout"));
        Map<String, Object> dedicatedArmsDownSquatBreakout = castToMap(payload.get(ARMS_DOWN_SQUAT_BREAKOUT_KEY));
        Map<String, Object> dedicatedMsrBreakout = castToMap(payload.get(MSR_BREAKOUT_KEY));
        for (String key : BREAKOUT_KEYS) {
            Map<String, Object> item;
            if (StrUtil.equals(key, CERVICAL_FLEXION_BREAKOUT_KEY)
                    && dedicatedCervicalBreakout != null && !dedicatedCervicalBreakout.isEmpty()) {
                item = mapDedicatedCervicalBreakoutToLegacy(dedicatedCervicalBreakout);
            } else if (StrUtil.equals(key, CERVICAL_EXTENSION_BREAKOUT_KEY)
                    && dedicatedCervicalExtensionBreakout != null && !dedicatedCervicalExtensionBreakout.isEmpty()) {
                item = mapDedicatedCervicalExtensionBreakoutToLegacy(dedicatedCervicalExtensionBreakout);
            } else if (StrUtil.equals(key, CERVICAL_ROTATION_BREAKOUT_KEY)
                    && dedicatedCervicalRotationBreakout != null && !dedicatedCervicalRotationBreakout.isEmpty()) {
                item = mapDedicatedCervicalRotationBreakoutToLegacy(dedicatedCervicalRotationBreakout);
            } else if (StrUtil.equals(key, UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY)
                    && dedicatedUpperExtremityPattern1Breakout != null && !dedicatedUpperExtremityPattern1Breakout.isEmpty()) {
                item = mapDedicatedUpperExtremityPattern1BreakoutToLegacy(dedicatedUpperExtremityPattern1Breakout);
            } else if (StrUtil.equals(key, UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY)
                    && dedicatedUpperExtremityPattern2Breakout != null && !dedicatedUpperExtremityPattern2Breakout.isEmpty()) {
                item = mapDedicatedUpperExtremityPattern2BreakoutToLegacy(dedicatedUpperExtremityPattern2Breakout);
            } else if (StrUtil.equals(key, ARMS_DOWN_SQUAT_BREAKOUT_KEY)
                    && dedicatedArmsDownSquatBreakout != null && !dedicatedArmsDownSquatBreakout.isEmpty()) {
                item = mapDedicatedArmsDownSquatBreakoutToLegacy(dedicatedArmsDownSquatBreakout);
            } else if (StrUtil.equals(key, "deep_squat")
                    && dedicatedArmsDownSquatBreakout != null && !dedicatedArmsDownSquatBreakout.isEmpty()) {
                item = mapDedicatedArmsDownSquatBreakoutToLegacy(dedicatedArmsDownSquatBreakout);
            } else if (StrUtil.equals(key, MSR_BREAKOUT_KEY)
                    && dedicatedMsrBreakout != null && !dedicatedMsrBreakout.isEmpty()) {
                item = mapDedicatedMsrBreakoutToLegacyMerged(dedicatedMsrBreakout);
            } else if (StrUtil.equals(key, "msr_left")
                    && dedicatedMsrBreakout != null && !dedicatedMsrBreakout.isEmpty()) {
                item = mapDedicatedMsrBreakoutSideToLegacy(castToMap(dedicatedMsrBreakout.get("left")));
            } else if (StrUtil.equals(key, "msr_right")
                    && dedicatedMsrBreakout != null && !dedicatedMsrBreakout.isEmpty()) {
                item = mapDedicatedMsrBreakoutSideToLegacy(castToMap(dedicatedMsrBreakout.get("right")));
            } else {
                item = castToMap(raw.get(key));
            }
            if (item == null) {
                item = new LinkedHashMap<>();
            } else {
                item = new LinkedHashMap<>(item);
            }
            String status = normalizeBreakoutStatus(item.get("status"));
            if (status == null) {
                status = BREAKOUT_STATUS_NOT_STARTED;
            }
            item.put("status", status);
            item.put("findings", StrUtil.blankToDefault(toStringValue(item.get("findings")), ""));
            item.put("rom_key_values", StrUtil.blankToDefault(toStringValue(item.get("rom_key_values")), ""));
            item.put("pain_present", Boolean.TRUE.equals(item.get("pain_present")));
            item.put("pain_vas", normalizeNumber(item.get("pain_vas")));
            item.put("mobility_restriction_signs", StrUtil.blankToDefault(toStringValue(item.get("mobility_restriction_signs")), ""));
            item.put("motor_control_signs", StrUtil.blankToDefault(toStringValue(item.get("motor_control_signs")), ""));
            item.put("asymmetry_signs", StrUtil.blankToDefault(toStringValue(item.get("asymmetry_signs")), ""));
            item.put("stop_due_to_pain", Boolean.TRUE.equals(item.get("stop_due_to_pain")));
            item.put("stop_reason", StrUtil.blankToDefault(toStringValue(item.get("stop_reason")), ""));
            item.put("clinician_note", StrUtil.blankToDefault(toStringValue(item.get("clinician_note")), ""));
            item.put("method", StrUtil.blankToDefault(toStringValue(item.get("method")), ""));
            item.put("scale", StrUtil.blankToDefault(toStringValue(item.get("scale")), ""));
            item.put("source_id", StrUtil.blankToDefault(toStringValue(item.get("source_id")), ""));
            item.put("date", StrUtil.blankToDefault(toStringValue(item.get("date")), ""));
            item.put("sls_time_sec", normalizeNumber(item.get("sls_time_sec")));
            breakouts.put(key, item);
        }

        Set<String> acceptedKeys = recommendations.stream()
                .filter(item -> STATUS_ACCEPTED.equals(item.get("recommendation_status")))
                .map(item -> toStringValue(item.get("breakout_key")))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        for (String key : acceptedKeys) {
            Map<String, Object> breakout = breakouts.get(key);
            if (breakout == null) {
                continue;
            }
            String status = toStringValue(breakout.get("status"));
            if (StrUtil.equals(status, BREAKOUT_STATUS_NOT_STARTED)) {
                breakout.put("status", BREAKOUT_STATUS_PARTIAL);
            }
        }
        for (Map<String, Object> recommendation : recommendations) {
            if (!STATUS_SKIPPED.equals(toStringValue(recommendation.get("recommendation_status")))) {
                continue;
            }
            String key = toStringValue(recommendation.get("breakout_key"));
            Map<String, Object> breakout = breakouts.get(key);
            if (breakout != null && StrUtil.equals(toStringValue(breakout.get("status")), BREAKOUT_STATUS_NOT_STARTED)) {
                breakout.put("status", BREAKOUT_STATUS_SKIPPED);
            }
        }
        return breakouts;
    }

    private Map<String, Object> buildSummary(Map<String, Object> payload,
                                             Map<String, Map<String, Object>> topTierMap,
                                             List<Map<String, Object>> recommendations,
                                             Map<String, Map<String, Object>> breakouts) {
        List<Map<String, Object>> topTierTable = new ArrayList<>();
        int abnormalCount = 0;
        int painRelatedCount = 0;
        int incompleteCount = 0;

        Set<String> evidenceRefs = new LinkedHashSet<>();
        List<String> painRelatedPatterns = new ArrayList<>();
        List<String> caveats = new ArrayList<>();

        for (SfmaTopTierDefinition definition : TOP_TIER_DEFINITIONS) {
            Map<String, Object> row = topTierMap.get(definition.getTestCode());
            if (row == null) {
                continue;
            }
            String classification = normalizeClassification(row.get("classification"));
            if (StrUtil.isBlank(classification)) {
                incompleteCount++;
            } else if (!StrUtil.equals(classification, CLASS_FN)) {
                abnormalCount++;
                evidenceRefs.add(definition.getTestCode());
            }
            if (CLASS_FP.equals(classification) || CLASS_DP.equals(classification)) {
                painRelatedCount++;
                painRelatedPatterns.add(definition.getTestNameZh());
            }

            String breakoutStatus = "not_recommended";
            Optional<Map<String, Object>> recommendationOpt = recommendations.stream()
                    .filter(item -> StrUtil.equals(toStringValue(item.get("test_code")), definition.getTestCode()))
                    .findFirst();
            if (recommendationOpt.isPresent()) {
                String recommendationStatus = toStringValue(recommendationOpt.get().get("recommendation_status"));
                String breakoutKey = toStringValue(recommendationOpt.get().get("breakout_key"));
                Map<String, Object> breakout = breakouts.get(breakoutKey);
                String breakoutDataStatus = breakout == null ? BREAKOUT_STATUS_NOT_STARTED : toStringValue(breakout.get("status"));
                breakoutStatus = recommendationStatus + ":" + mapBreakoutStatusZh(breakoutDataStatus);
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("test_code", definition.getTestCode());
            item.put("test_name_zh", definition.getTestNameZh());
            item.put("classification", classification);
            item.put("pain_present", Boolean.TRUE.equals(row.get("pain_present")));
            item.put("needs_breakout_suggestion", Boolean.TRUE.equals(row.get("needs_breakout_suggestion")));
            item.put("breakout_status", breakoutStatus);
            item.put("observation_note", toStringValue(row.get("key_observation_note")));
            topTierTable.add(item);
        }

        List<Map<String, Object>> breakoutTable = new ArrayList<>();
        int mobilityScore = 0;
        int controlScore = 0;
        int asymmetryScore = 0;
        for (Map.Entry<String, Map<String, Object>> entry : breakouts.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> row = entry.getValue();
            String status = normalizeBreakoutStatus(row.get("status"));
            String mobilitySigns = toStringValue(row.get("mobility_restriction_signs"));
            String controlSigns = toStringValue(row.get("motor_control_signs"));
            String asymmetrySigns = toStringValue(row.get("asymmetry_signs"));
            if (StrUtil.isNotBlank(mobilitySigns)) {
                mobilityScore++;
                evidenceRefs.add("breakout:" + key + ":mobility");
            }
            if (StrUtil.isNotBlank(controlSigns)) {
                controlScore++;
                evidenceRefs.add("breakout:" + key + ":control");
            }
            if (StrUtil.isNotBlank(asymmetrySigns)) {
                asymmetryScore++;
                evidenceRefs.add("breakout:" + key + ":asymmetry");
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("breakout_key", key);
            item.put("breakout_name_zh", BREAKOUT_NAME_MAP.getOrDefault(key, key));
            item.put("status", status);
            item.put("findings", StrUtil.blankToDefault(toStringValue(row.get("findings")), ""));
            item.put("mobility_restriction_signs", mobilitySigns);
            item.put("motor_control_signs", controlSigns);
            item.put("asymmetry_signs", asymmetrySigns);
            breakoutTable.add(item);
        }

        ClassificationResult classificationResult = classify(abnormalCount, incompleteCount, painRelatedCount,
                mobilityScore, controlScore, topTierMap, breakouts);
        if (classificationResult.getCaveat() != null) {
            caveats.add(classificationResult.getCaveat());
        }

        List<String> majorLimitationChains = buildMajorLimitationChains(mobilityScore, topTierMap, breakouts);
        List<String> majorControlChains = buildMajorControlChains(controlScore, topTierMap, breakouts);
        List<String> asymmetryFocus = buildAsymmetryFocus(topTierMap, breakouts);
        List<String> priorityRegions = buildPriorityRegions(topTierMap, breakouts);
        List<String> priorities = buildPriorityTexts(priorityRegions, classificationResult.getPrimaryClassification());

        String clinicalMeaning = buildClinicalMeaning(classificationResult.getPrimaryClassification(), abnormalCount, incompleteCount);
        String trainingDirection = buildTrainingDirection(classificationResult.getPrimaryClassification(), painRelatedCount, incompleteCount);
        String manualReviewHint = buildManualReviewHint(painRelatedCount, incompleteCount, classificationResult.getConfidence(), asymmetryFocus);
        Map<String, Object> topTierSummaryItem = buildTopTierSummaryItem(topTierMap, recommendations, breakouts);
        Map<String, Object> breakoutSummaryItem = buildBreakoutSummaryItem(payload, breakouts);
        Map<String, Object> bookProtocolSummary = buildBookProtocolSummary(payload);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("top_tier_table", topTierTable);
        summary.put("breakout_table", breakoutTable);
        summary.put("top_tier_summary_item", topTierSummaryItem);
        summary.put("breakout_summary_item", breakoutSummaryItem);
        summary.put("book_protocol_summary", bookProtocolSummary);
        summary.put("primary_classification", classificationResult.getPrimaryClassification());
        summary.put("secondary_classification", classificationResult.getSecondaryClassification());
        summary.put("classification_confidence", classificationResult.getConfidence());
        summary.put("mixed_pattern_possible", classificationResult.isMixedPatternPossible());
        summary.put("clinical_meaning", clinicalMeaning);
        summary.put("training_direction", trainingDirection);
        summary.put("priority_1", priorities.size() > 0 ? priorities.get(0) : "优先人工复核");
        summary.put("priority_2", priorities.size() > 1 ? priorities.get(1) : "完善 Breakout 证据");
        summary.put("priority_3", priorities.size() > 2 ? priorities.get(2) : "复测确认变化");
        summary.put("major_limitation_chains", majorLimitationChains);
        summary.put("major_control_deficit_chains", majorControlChains);
        summary.put("left_right_key_asymmetry", asymmetryFocus);
        summary.put("manual_review_or_referral_hint", manualReviewHint);
        summary.put("pain_related_patterns", painRelatedPatterns);
        summary.put("needs_manual_review", painRelatedCount > 0 || StrUtil.equals(classificationResult.getConfidence(), "low"));
        summary.put("evidence_refs", new ArrayList<>(evidenceRefs));
        summary.put("caveats", caveats);
        return summary;
    }

    private Map<String, Object> buildBookProtocolSummary(Map<String, Object> payload) {
        Map<String, Object> protocol = castToMap(payload.get("book_protocol"));
        if (protocol == null || protocol.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocol_id", protocol.get("protocol_id"));
        result.put("protocol_version", protocol.get("protocol_version"));
        result.put("source", protocol.get("source"));

        List<Map<String, Object>> workflowTable = new ArrayList<>();
        int completedCount = 0;
        int stoppedDueToPainCount = 0;
        int recordedStepCount = 0;
        Map<String, Object> workflows = castToMap(protocol.get("workflows"));
        if (workflows != null) {
            for (Map.Entry<String, Object> entry : workflows.entrySet()) {
                Map<String, Object> workflow = castToMap(entry.getValue());
                if (workflow == null) {
                    continue;
                }
                String status = toStringValue(workflow.get("status"));
                if (BREAKOUT_STATUS_COMPLETED.equals(status)) {
                    completedCount++;
                }
                if (BREAKOUT_STATUS_STOPPED_PAIN.equals(status)) {
                    stoppedDueToPainCount++;
                }
                int workflowRecordedSteps = 0;
                for (Map<String, Object> step : castToMapList(workflow.get("steps"))) {
                    String stepStatus = toStringValue(step.get("status"));
                    if (BREAKOUT_STATUS_COMPLETED.equals(stepStatus)) {
                        workflowRecordedSteps++;
                    }
                }
                recordedStepCount += workflowRecordedSteps;

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("workflow_code", entry.getKey());
                row.put("workflow_name_zh", workflow.get("workflow_name_zh"));
                row.put("status", status);
                row.put("trigger_classifications", workflow.get("trigger_classifications"));
                row.put("recorded_step_count", workflowRecordedSteps);
                workflowTable.add(row);
            }
        }
        result.put("workflow_table", workflowTable);
        result.put("completed_workflow_count", completedCount);
        result.put("stopped_due_to_pain_count", stoppedDueToPainCount);
        result.put("recorded_step_count", recordedStepCount);
        result.put("pain_stop_rule_applied", stoppedDueToPainCount > 0);
        return result;
    }

    private Map<String, Object> buildRiskPrecheck(Map<String, Map<String, Object>> topTierMap,
                                                  Map<String, Map<String, Object>> breakouts,
                                                  Map<String, Object> summary) {
        int abnormalCount = 0;
        int painCount = 0;
        int asymmetryCount = 0;
        Set<String> tags = new LinkedHashSet<>();
        Set<String> evidenceRefs = new LinkedHashSet<>();

        for (SfmaTopTierDefinition definition : TOP_TIER_DEFINITIONS) {
            Map<String, Object> row = topTierMap.get(definition.getTestCode());
            if (row == null) {
                continue;
            }
            String classification = normalizeClassification(row.get("classification"));
            if (StrUtil.isBlank(classification) || CLASS_FN.equals(classification)) {
                continue;
            }
            abnormalCount++;
            evidenceRefs.add(definition.getTestCode());
            if (CLASS_FP.equals(classification) || CLASS_DP.equals(classification)) {
                painCount++;
                tags.add("pain_attention");
            }
            if (definition.getTestCode().startsWith("single_leg_stance") || definition.getTestCode().startsWith("multi_segmental_rotation")) {
                tags.add("dynamic_balance_attention");
            }
            if (definition.getTestCode().contains("upper_extremity")) {
                tags.add("upper_extremity_stability_attention");
            }
            if (definition.getTestCode().contains("multi_segmental") || StrUtil.equals(definition.getTestCode(), "arms_down_deep_squat")) {
                tags.add("lphc_stability_attention");
            }
        }

        for (Map.Entry<String, Map<String, Object>> entry : breakouts.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> row = entry.getValue();
            String asymmetrySigns = toStringValue(row.get("asymmetry_signs"));
            if (StrUtil.isNotBlank(asymmetrySigns)) {
                asymmetryCount++;
                tags.add("asymmetry_attention");
                evidenceRefs.add("breakout:" + key + ":asymmetry");
            }
            if (Boolean.TRUE.equals(row.get("stop_due_to_pain"))) {
                painCount++;
                tags.add("pain_attention");
                evidenceRefs.add("breakout:" + key + ":pain_stop");
            }
        }

        if (abnormalCount > 0 && tags.isEmpty()) {
            tags.add("reassessment_attention");
        }
        if (abnormalCount >= 8 || painCount >= 3) {
            tags.add("reassessment_attention");
        }

        String overallRiskLevel;
        if (abnormalCount >= 8 || painCount >= 3) {
            overallRiskLevel = "high";
        } else if (abnormalCount >= 4 || painCount >= 1 || asymmetryCount >= 2) {
            overallRiskLevel = "medium";
        } else {
            overallRiskLevel = "low";
        }

        String reasonText = "从 SFMA 动作表现看，当前初筛风险等级为 " + overallRiskLevel
                + "，提示关注 " + (tags.isEmpty() ? "复测跟踪" : String.join("、", tags))
                + "。该结果基于 internal_rule_only / provisional_v1，需结合人工复核。";

        Map<String, Object> risk = new LinkedHashMap<>();
        risk.put("overall_risk_level", overallRiskLevel);
        risk.put("risk_tags", new ArrayList<>(tags));
        risk.put("reason_text", reasonText);
        risk.put("needs_manual_review", Boolean.TRUE.equals(summary.get("needs_manual_review")) || painCount > 0);
        risk.put("internal_rule_only", true);
        risk.put("provisional_v1", true);
        risk.put("evidence_refs", new ArrayList<>(evidenceRefs));
        return risk;
    }

    private Map<String, Object> buildReportMapping(Map<String, Object> summary,
                                                   List<Map<String, Object>> recommendations,
                                                   Map<String, Map<String, Object>> breakouts,
                                                   Map<String, Object> riskPrecheck) {
        Map<String, Object> sfmaInterpretation = new LinkedHashMap<>();
        sfmaInterpretation.put("top_tier_table", summary.get("top_tier_table"));
        sfmaInterpretation.put("breakout_table", summary.get("breakout_table"));
        sfmaInterpretation.put("classification_judgement",
                "主分类：" + summary.get("primary_classification")
                        + "；次分类：" + joinList(summary.get("secondary_classification"), "、"));
        sfmaInterpretation.put("clinical_meaning", summary.get("clinical_meaning"));
        sfmaInterpretation.put("training_direction", summary.get("training_direction"));
        sfmaInterpretation.put("recommendation_overview", recommendations.stream()
                .map(item -> item.get("test_name_zh") + "：" + item.get("recommendation_status"))
                .collect(Collectors.joining("；")));
        sfmaInterpretation.put("recommendation_sequence", recommendations.stream().map(item -> {
            Map<String, Object> seq = new LinkedHashMap<>();
            seq.put("test_code", item.get("test_code"));
            seq.put("test_name_zh", item.get("test_name_zh"));
            seq.put("recommendation_stage", item.get("recommendation_stage"));
            seq.put("recommendation_order", item.get("recommendation_order"));
            seq.put("recommendation_status", item.get("recommendation_status"));
            return seq;
        }).collect(Collectors.toList()));
        sfmaInterpretation.put("book_protocol_summary", summary.get("book_protocol_summary"));

        Map<String, Object> classificationAndPriority = new LinkedHashMap<>();
        classificationAndPriority.put("primary", summary.get("primary_classification"));
        classificationAndPriority.put("secondary", summary.get("secondary_classification"));
        classificationAndPriority.put("priority_1", summary.get("priority_1"));
        classificationAndPriority.put("priority_2", summary.get("priority_2"));
        classificationAndPriority.put("priority_3", summary.get("priority_3"));

        Map<String, Object> sfma = new LinkedHashMap<>();
        sfma.put("sfma_interpretation", sfmaInterpretation);
        sfma.put("classification_and_priority", classificationAndPriority);
        sfma.put("major_limitation_chains", summary.get("major_limitation_chains"));
        sfma.put("major_control_deficit_chains", summary.get("major_control_deficit_chains"));
        sfma.put("left_right_asymmetry_focus", summary.get("left_right_key_asymmetry"));
        sfma.put("manual_review_hint", summary.get("manual_review_or_referral_hint"));
        sfma.put("risk_precheck", riskPrecheck);
        sfma.put("book_protocol_summary", summary.get("book_protocol_summary"));
        sfma.put("cervical_flexion", buildCervicalReportMapping(summary, CERVICAL_FLEXION_TEST_CODE));
        sfma.put("cervical_extension", buildCervicalReportMapping(summary, CERVICAL_EXTENSION_TEST_CODE));
        sfma.put("cervical_rotation", buildCervicalRotationReportMapping(summary));
        sfma.put("upper_extremity_pattern1", buildUpperExtremityPattern1ReportMapping(summary));
        sfma.put("upper_extremity_pattern2", buildUpperExtremityPattern2ReportMapping(summary));
        sfma.put("msf_breakout", buildMsfReportMapping(summary));
        sfma.put("multi_segmental_extension", buildMseReportMapping(summary));
        sfma.put("mse_breakout", buildMseReportMapping(summary));
        sfma.put(ARMS_DOWN_SQUAT_BREAKOUT_KEY, buildArmsDownSquatReportMapping(summary));
        sfma.put("breakout_completion",
                breakouts.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get("status"),
                        (a, b) -> b,
                        LinkedHashMap::new
                )));

        Map<String, Object> reportMapping = new LinkedHashMap<>();
        reportMapping.put("sfma", sfma);
        return reportMapping;
    }

    private ClassificationResult classify(int abnormalCount,
                                          int incompleteCount,
                                          int painRelatedCount,
                                          int mobilityScore,
                                          int controlScore,
                                          Map<String, Map<String, Object>> topTierMap,
                                          Map<String, Map<String, Object>> breakouts) {
        String primary = PRIMARY_T;
        List<String> secondary = new ArrayList<>();
        String confidence = "high";
        boolean mixed = false;
        String caveat = null;

        if (abnormalCount == 0 && incompleteCount == 0) {
            primary = PRIMARY_T;
            secondary.add("near_normal_pattern");
            confidence = "high";
        } else if (mobilityScore == 0 && controlScore == 0) {
            primary = PRIMARY_T;
            confidence = incompleteCount > 0 ? "low" : "medium";
            secondary.add("evidence_limited");
            caveat = "Breakout 证据有限，分类为保守推断。";
        } else if (mobilityScore > controlScore + 1) {
            primary = PRIMARY_JMD;
            secondary.add("mobility_dominant_pattern");
            confidence = mobilityScore >= 3 ? "medium" : "low";
        } else if (controlScore > mobilityScore + 1) {
            primary = PRIMARY_SMCD;
            secondary.add("stability_motor_control_dominant_pattern");
            confidence = controlScore >= 3 ? "medium" : "low";
        } else {
            primary = PRIMARY_SMCD;
            secondary.add("mixed_mobility_control_pattern");
            mixed = true;
            confidence = "medium";
        }

        if (painRelatedCount > 0) {
            secondary.add("pain_related_pattern");
            if (!StrUtil.equals(confidence, "low")) {
                confidence = "medium";
            }
        }

        if (incompleteCount > 0) {
            confidence = "low";
            secondary.add("incomplete_top_tier");
            caveat = StrUtil.blankToDefault(caveat, "Top Tier 未全部完成，需结合人工复核。");
        }

        List<String> asymmetry = buildAsymmetryFocus(topTierMap, breakouts);
        if (!asymmetry.isEmpty()) {
            secondary.add("asymmetry_pattern");
            mixed = true;
        }

        return new ClassificationResult(primary, distinct(secondary), confidence, mixed, caveat);
    }

    private List<String> buildMajorLimitationChains(int mobilityScore,
                                                    Map<String, Map<String, Object>> topTierMap,
                                                    Map<String, Map<String, Object>> breakouts) {
        List<String> chains = new ArrayList<>();
        if (mobilityScore <= 0) {
            return chains;
        }
        if (hasAnyAbnormal(topTierMap, "multi_segmental_flexion", "multi_segmental_extension", "single_leg_stance_left",
                "single_leg_stance_right", "arms_down_deep_squat")
                || hasAnyBreakoutMobility(breakouts, MSF_BREAKOUT_KEY, MSE_BREAKOUT_KEY, "msf", "mse", "sls_left", "sls_right",
                ARMS_DOWN_SQUAT_BREAKOUT_KEY, "deep_squat")) {
            chains.add("踝-髋-脊柱链");
        }
        if (hasAnyAbnormal(topTierMap, "upper_extremity_pattern1_left", "upper_extremity_pattern1_right",
                "upper_extremity_pattern2_left", "upper_extremity_pattern2_right")
                || hasAnyBreakoutMobility(breakouts, UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY,
                UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY,
                "upper_extremity_pattern_left", "upper_extremity_pattern_right")) {
            chains.add("肩胛-胸椎-肩关节链");
        }
        if (hasAnyAbnormal(topTierMap, "cervical_flexion", "cervical_extension", "cervical_rotation_left", "cervical_rotation_right")
                || hasAnyBreakoutMobility(breakouts, CERVICAL_FLEXION_BREAKOUT_KEY, CERVICAL_EXTENSION_BREAKOUT_KEY,
                CERVICAL_ROTATION_BREAKOUT_KEY, "cervical_pattern")) {
            chains.add("颈椎-胸椎联动链");
        }
        return chains;
    }

    private List<String> buildMajorControlChains(int controlScore,
                                                 Map<String, Map<String, Object>> topTierMap,
                                                 Map<String, Map<String, Object>> breakouts) {
        List<String> chains = new ArrayList<>();
        if (controlScore <= 0) {
            return chains;
        }
        if (hasAnyAbnormal(topTierMap, "multi_segmental_flexion", "multi_segmental_extension", "arms_down_deep_squat")
                || hasAnyBreakoutControl(breakouts, MSF_BREAKOUT_KEY, MSE_BREAKOUT_KEY, "msf", "mse",
                ARMS_DOWN_SQUAT_BREAKOUT_KEY, "deep_squat")) {
            chains.add("LPHC 控制链");
        }
        if (hasAnyAbnormal(topTierMap, "single_leg_stance_left", "single_leg_stance_right", "multi_segmental_rotation_left", "multi_segmental_rotation_right")
                || hasAnyBreakoutControl(breakouts, "sls_left", "sls_right", "msr_left", "msr_right")) {
            chains.add("单腿稳定与旋转控制链");
        }
        if (hasAnyAbnormal(topTierMap, "upper_extremity_pattern1_left", "upper_extremity_pattern1_right",
                "upper_extremity_pattern2_left", "upper_extremity_pattern2_right")
                || hasAnyBreakoutControl(breakouts, UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY,
                UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY,
                "upper_extremity_pattern_left", "upper_extremity_pattern_right")) {
            chains.add("上肢闭链控制链");
        }
        return chains;
    }

    private List<String> buildAsymmetryFocus(Map<String, Map<String, Object>> topTierMap,
                                             Map<String, Map<String, Object>> breakouts) {
        List<String> asymmetry = new ArrayList<>();
        comparePair(topTierMap, "cervical_rotation_left", "cervical_rotation_right", "颈椎旋转左右差", asymmetry);
        comparePair(topTierMap, "upper_extremity_pattern1_left", "upper_extremity_pattern1_right", "上肢模式1左右差", asymmetry);
        comparePair(topTierMap, "upper_extremity_pattern2_left", "upper_extremity_pattern2_right", "上肢模式2左右差", asymmetry);
        comparePair(topTierMap, "multi_segmental_rotation_left", "multi_segmental_rotation_right", "MSR 左右差", asymmetry);
        comparePair(topTierMap, "single_leg_stance_left", "single_leg_stance_right", "SLS 左右差", asymmetry);

        appendBreakoutAsymmetry(asymmetry, breakouts, "upper_extremity_pattern_left", "上肢 Breakout（左）");
        appendBreakoutAsymmetry(asymmetry, breakouts, "upper_extremity_pattern_right", "上肢 Breakout（右）");
        appendBreakoutAsymmetry(asymmetry, breakouts, UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY, "上肢模式1 Breakout");
        appendBreakoutAsymmetry(asymmetry, breakouts, UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY, "上肢模式2 Breakout");
        appendBreakoutAsymmetry(asymmetry, breakouts, CERVICAL_ROTATION_BREAKOUT_KEY, "颈椎旋转 Breakout");
        appendBreakoutAsymmetry(asymmetry, breakouts, "msr_left", "MSR Breakout（左）");
        appendBreakoutAsymmetry(asymmetry, breakouts, "msr_right", "MSR Breakout（右）");
        appendBreakoutAsymmetry(asymmetry, breakouts, "sls_left", "SLS Breakout（左）");
        appendBreakoutAsymmetry(asymmetry, breakouts, "sls_right", "SLS Breakout（右）");
        return distinct(asymmetry);
    }

    private List<String> buildPriorityRegions(Map<String, Map<String, Object>> topTierMap,
                                              Map<String, Map<String, Object>> breakouts) {
        Map<String, Integer> score = new LinkedHashMap<>();
        score.put("头颈", 0);
        score.put("肩", 0);
        score.put("LPHC", 0);
        score.put("膝", 0);
        score.put("踝足", 0);
        score.put("动态控制", 0);
        score.put("上肢闭链稳定", 0);

        for (SfmaTopTierDefinition definition : TOP_TIER_DEFINITIONS) {
            Map<String, Object> row = topTierMap.get(definition.getTestCode());
            String classification = normalizeClassification(row == null ? null : row.get("classification"));
            if (StrUtil.isBlank(classification) || CLASS_FN.equals(classification)) {
                continue;
            }
            if (StrUtil.equals(definition.getGroup(), "cervical")) {
                inc(score, "头颈");
            } else if (StrUtil.equals(definition.getGroup(), "upper_extremity")) {
                inc(score, "肩");
                inc(score, "上肢闭链稳定");
            } else if (StrUtil.equals(definition.getTestCode(), "multi_segmental_rotation_left")
                    || StrUtil.equals(definition.getTestCode(), "multi_segmental_rotation_right")
                    || StrUtil.equals(definition.getGroup(), "single_leg_stance")) {
                inc(score, "动态控制");
            } else if (StrUtil.equals(definition.getTestCode(), "arms_down_deep_squat")) {
                inc(score, "膝");
                inc(score, "LPHC");
            } else {
                inc(score, "LPHC");
            }
        }

        for (Map.Entry<String, Map<String, Object>> entry : breakouts.entrySet()) {
            Map<String, Object> row = entry.getValue();
            if (StrUtil.isBlank(toStringValue(row.get("motor_control_signs")))
                    && StrUtil.isBlank(toStringValue(row.get("mobility_restriction_signs")))) {
                continue;
            }
            String key = entry.getKey();
            if (StrUtil.contains(key, "sls")) {
                inc(score, "踝足");
                inc(score, "动态控制");
            } else if (StrUtil.contains(key, "msr")) {
                inc(score, "动态控制");
            } else if (StrUtil.contains(key, "upper_extremity")) {
                inc(score, "肩");
                inc(score, "上肢闭链稳定");
            } else if (StrUtil.equalsAny(key, ARMS_DOWN_SQUAT_BREAKOUT_KEY, "deep_squat")) {
                inc(score, "膝");
                inc(score, "LPHC");
            }
        }

        return score.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(Map.Entry::getKey)
                .limit(4)
                .collect(Collectors.toList());
    }

    private List<String> buildPriorityTexts(List<String> priorityRegions, String primaryClassification) {
        List<String> result = new ArrayList<>();
        if (!priorityRegions.isEmpty()) {
            result.add("优先处理区域：" + priorityRegions.get(0));
        }
        if (priorityRegions.size() > 1) {
            result.add("次优先区域：" + priorityRegions.get(1));
        }
        if (priorityRegions.size() > 2) {
            result.add("第三级区域：" + priorityRegions.get(2));
        }
        // Pad to a fixed size to avoid IndexOutOfBounds when evidence is sparse.
        while (result.size() < 3) {
            if (result.size() == 0) {
                result.add("结合 " + primaryClassification + " 模式补充 Breakout 证据");
            } else if (result.size() == 1) {
                result.add("复测确认变化趋势");
            } else {
                result.add("结合人工复核补齐优先级判断");
            }
        }
        return result.subList(0, 3);
    }

    private String buildClinicalMeaning(String primaryClassification, int abnormalCount, int incompleteCount) {
        if (abnormalCount == 0 && incompleteCount == 0) {
            return "SFMA Top Tier 当前以功能正常模式为主，未见明显功能受限线索。";
        }
        if (StrUtil.equals(primaryClassification, PRIMARY_JMD)) {
            return "SFMA 表现提示活动度限制线索更突出，需结合分解评估进一步确认限制链条。";
        }
        if (StrUtil.equals(primaryClassification, PRIMARY_SMCD)) {
            return "SFMA 表现提示稳定性/运动控制障碍线索更突出，建议优先关注控制链条重建。";
        }
        return "SFMA 当前表现提示存在功能异常线索，但证据仍有限，需结合人工复核。";
    }

    private String buildTrainingDirection(String primaryClassification, int painRelatedCount, int incompleteCount) {
        if (painRelatedCount > 0) {
            return "倾向疼痛管理优先，在症状可控前提下逐步推进分解与训练。";
        }
        if (incompleteCount > 0) {
            return "倾向先补齐 Top Tier/Breakout 证据，再明确训练取向。";
        }
        if (StrUtil.equals(primaryClassification, PRIMARY_JMD)) {
            return "倾向活动度优先（关节活动、组织延展）并结合基础控制训练。";
        }
        if (StrUtil.equals(primaryClassification, PRIMARY_SMCD)) {
            return "倾向控制优先（稳定性与运动控制）并结合必要活动度补偿。";
        }
        return "倾向维持与复测优先，持续跟踪功能变化。";
    }

    private String buildManualReviewHint(int painRelatedCount, int incompleteCount, String confidence, List<String> asymmetryFocus) {
        if (painRelatedCount > 0) {
            return "建议优先人工复核；建议疼痛管理优先后再推进分解/训练；必要时建议结合进一步医学评估。";
        }
        if (incompleteCount > 0 || StrUtil.equals(confidence, "low")) {
            return "建议优先人工复核，补充关键 Breakout 后再确定推进策略。";
        }
        if (!asymmetryFocus.isEmpty()) {
            return "存在左右差线索，建议人工复核并在后续复测中重点追踪。";
        }
        return "当前为功能学汇总结果，建议结合人工复核持续确认。";
    }

    private boolean hasAnyAbnormal(Map<String, Map<String, Object>> topTierMap, String... testCodes) {
        for (String testCode : testCodes) {
            Map<String, Object> row = topTierMap.get(testCode);
            if (row == null) {
                continue;
            }
            String classification = normalizeClassification(row.get("classification"));
            if (StrUtil.isNotBlank(classification) && !StrUtil.equals(classification, CLASS_FN)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyBreakoutMobility(Map<String, Map<String, Object>> breakouts, String... keys) {
        for (String key : keys) {
            Map<String, Object> row = breakouts.get(key);
            if (row != null && StrUtil.isNotBlank(toStringValue(row.get("mobility_restriction_signs")))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyBreakoutControl(Map<String, Map<String, Object>> breakouts, String... keys) {
        for (String key : keys) {
            Map<String, Object> row = breakouts.get(key);
            if (row != null && StrUtil.isNotBlank(toStringValue(row.get("motor_control_signs")))) {
                return true;
            }
        }
        return false;
    }

    private void comparePair(Map<String, Map<String, Object>> topTierMap, String leftCode, String rightCode,
                             String label, List<String> collector) {
        String left = normalizeClassification(topTierMap.get(leftCode) == null ? null : topTierMap.get(leftCode).get("classification"));
        String right = normalizeClassification(topTierMap.get(rightCode) == null ? null : topTierMap.get(rightCode).get("classification"));
        if (StrUtil.isNotBlank(left) && StrUtil.isNotBlank(right) && !StrUtil.equals(left, right)) {
            collector.add(label + "（" + left + " / " + right + "）");
        }
    }

    private void appendBreakoutAsymmetry(List<String> collector,
                                         Map<String, Map<String, Object>> breakouts,
                                         String breakoutKey,
                                         String label) {
        Map<String, Object> row = breakouts.get(breakoutKey);
        if (row == null) {
            return;
        }
        String asymmetry = toStringValue(row.get("asymmetry_signs"));
        if (StrUtil.isNotBlank(asymmetry)) {
            collector.add(label + "：" + asymmetry);
        }
    }

    private String recommendationStageByClassification(String classification) {
        if (StrUtil.equals(classification, CLASS_DN)) {
            return STAGE_DN_FIRST;
        }
        if (StrUtil.equals(classification, CLASS_FP)) {
            return STAGE_FP_SECOND;
        }
        if (StrUtil.equals(classification, CLASS_DP)) {
            return STAGE_DP_LAST;
        }
        return STAGE_DN_FIRST;
    }

    private String normalizeRecommendationStage(Object value) {
        String text = StrUtil.trimToEmpty(toStringValue(value));
        if (StrUtil.equalsAny(text, STAGE_DN_FIRST, STAGE_FP_SECOND, STAGE_DP_LAST)) {
            return text;
        }
        return null;
    }

    private int recommendationStageWeight(String stage) {
        if (StrUtil.equals(stage, STAGE_DN_FIRST)) {
            return 1;
        }
        if (StrUtil.equals(stage, STAGE_FP_SECOND)) {
            return 2;
        }
        if (StrUtil.equals(stage, STAGE_DP_LAST)) {
            return 3;
        }
        return 9;
    }

    private int buildRecommendationOrder(String stage, String breakoutKey) {
        int stageWeight = recommendationStageWeight(stage);
        int hierarchyWeight = BREAKOUT_HIERARCHY_ORDER_MAP.getOrDefault(breakoutKey, 999);
        return stageWeight * 100 + hierarchyWeight;
    }

    private String normalizeClassification(Object value) {
        String text = StrUtil.upperFirst(StrUtil.trimToEmpty(toStringValue(value))).toUpperCase(Locale.ROOT);
        if (Arrays.asList(CLASS_FN, CLASS_FP, CLASS_DN, CLASS_DP).contains(text)) {
            return text;
        }
        return "";
    }

    private String normalizeRecommendationStatus(Object value) {
        String text = StrUtil.trimToEmpty(toStringValue(value));
        if (StrUtil.equalsAny(text, STATUS_SUGGESTED, STATUS_ACCEPTED, STATUS_SKIPPED)) {
            return text;
        }
        return STATUS_SUGGESTED;
    }

    private String normalizeBreakoutStatus(Object value) {
        String text = StrUtil.trimToEmpty(toStringValue(value));
        if (StrUtil.equalsAny(text, "in_progress", "partial")) {
            return BREAKOUT_STATUS_PARTIAL;
        }
        if (StrUtil.equals(text, BREAKOUT_STATUS_STOPPED_PAIN)) {
            return BREAKOUT_STATUS_COMPLETED;
        }
        if (StrUtil.equalsAny(text,
                BREAKOUT_STATUS_NOT_STARTED,
                BREAKOUT_STATUS_PARTIAL,
                BREAKOUT_STATUS_COMPLETED,
                BREAKOUT_STATUS_SKIPPED)) {
            return text;
        }
        return BREAKOUT_STATUS_NOT_STARTED;
    }

    private String mapBreakoutStatusZh(String status) {
        if (StrUtil.equals(status, BREAKOUT_STATUS_COMPLETED)) {
            return "已完成";
        }
        if (StrUtil.equals(status, BREAKOUT_STATUS_PARTIAL)) {
            return "进行中";
        }
        if (StrUtil.equals(status, BREAKOUT_STATUS_SKIPPED)) {
            return "暂不分解";
        }
        return "未开始";
    }

    private Map<String, Object> buildTopTierSummaryItem(Map<String, Map<String, Object>> topTierMap,
                                                        List<Map<String, Object>> recommendations,
                                                        Map<String, Map<String, Object>> breakouts) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (SfmaTopTierDefinition definition : TOP_TIER_DEFINITIONS) {
            Map<String, Object> topTier = topTierMap.get(definition.getTestCode());
            if (topTier == null) {
                continue;
            }
            Optional<Map<String, Object>> recommendationOpt = recommendations.stream()
                    .filter(item -> StrUtil.equals(toStringValue(item.get("test_code")), definition.getTestCode()))
                    .findFirst();
            Map<String, Object> recommendation = recommendationOpt.orElse(null);
            String breakoutStatus = BREAKOUT_STATUS_NOT_STARTED;
            if (recommendation != null) {
                String breakoutKey = toStringValue(recommendation.get("breakout_key"));
                Map<String, Object> breakout = breakouts.get(breakoutKey);
                if (breakout != null) {
                    breakoutStatus = normalizeBreakoutStatus(breakout.get("status"));
                }
            }
            String classification = normalizeClassification(topTier.get("classification"));
            boolean painPresent = Boolean.TRUE.equals(topTier.get("pain_present"));
            boolean breakoutSuggested = Boolean.TRUE.equals(topTier.get("needs_breakout_suggestion"));
            boolean breakoutCompleted = StrUtil.equalsAny(breakoutStatus, BREAKOUT_STATUS_COMPLETED, BREAKOUT_STATUS_PARTIAL,
                    BREAKOUT_STATUS_STOPPED_PAIN);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("classification", classification);
            item.put("pain_present", painPresent);
            item.put("breakout_suggested", breakoutSuggested);
            item.put("breakout_completed", breakoutCompleted);
            item.put("review_priority", StrUtil.blankToDefault(toStringValue(topTier.get("review_priority")), REVIEW_PRIORITY_NORMAL));
            item.put("summary_text", buildTopTierSummaryText(classification, definition.getTestCode(), definition.getTestNameZh()));
            result.put(definition.getTestCode(), item);
        }
        return result;
    }

    private Map<String, Object> buildBreakoutSummaryItem(Map<String, Object> payload,
                                                         Map<String, Map<String, Object>> breakouts) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> dedicatedCervical = castToMap(payload.get("cervical_flexion_breakout"));
        if (dedicatedCervical != null && !dedicatedCervical.isEmpty()) {
            Map<String, Object> cervical = new LinkedHashMap<>();
            String status = normalizeBreakoutStatus(dedicatedCervical.get("breakout_status"));
            List<String> primaryFindings = buildCervicalPrimaryFindings(dedicatedCervical, "flexion");
            List<String> preliminaryDirection = castStringList(dedicatedCervical.get("breakout_preliminary_direction"));
            boolean needsManualReview = Boolean.TRUE.equals(dedicatedCervical.get("needs_manual_review"));
            cervical.put("breakout_status", status);
            cervical.put("primary_findings", primaryFindings);
            cervical.put("preliminary_direction", preliminaryDirection);
            cervical.put("needs_manual_review", needsManualReview);
            cervical.put("summary_text", StrUtil.blankToDefault(
                    toStringValue(dedicatedCervical.get("breakout_summary_text")),
                    buildCervicalBreakoutSummaryText(status, primaryFindings, preliminaryDirection, needsManualReview, "flexion")
            ));
            result.put(CERVICAL_FLEXION_TEST_CODE, cervical);
        }

        Map<String, Object> dedicatedCervicalExtension = castToMap(payload.get("cervical_extension_breakout"));
        if (dedicatedCervicalExtension != null && !dedicatedCervicalExtension.isEmpty()) {
            Map<String, Object> cervical = new LinkedHashMap<>();
            String status = normalizeBreakoutStatus(dedicatedCervicalExtension.get("breakout_status"));
            List<String> primaryFindings = buildCervicalPrimaryFindings(dedicatedCervicalExtension, "extension");
            List<String> preliminaryDirection = castStringList(dedicatedCervicalExtension.get("breakout_preliminary_direction"));
            boolean needsManualReview = Boolean.TRUE.equals(dedicatedCervicalExtension.get("needs_manual_review"));
            cervical.put("breakout_status", status);
            cervical.put("primary_findings", primaryFindings);
            cervical.put("preliminary_direction", preliminaryDirection);
            cervical.put("needs_manual_review", needsManualReview);
            cervical.put("summary_text", StrUtil.blankToDefault(
                    toStringValue(dedicatedCervicalExtension.get("breakout_summary_text")),
                    buildCervicalBreakoutSummaryText(status, primaryFindings, preliminaryDirection, needsManualReview, "extension")
            ));
            result.put(CERVICAL_EXTENSION_TEST_CODE, cervical);
        }

        Map<String, Object> dedicatedCervicalRotation = castToMap(payload.get("cervical_rotation_breakout"));
        if (dedicatedCervicalRotation != null && !dedicatedCervicalRotation.isEmpty()) {
            Map<String, Object> left = castToMap(dedicatedCervicalRotation.get("left"));
            Map<String, Object> right = castToMap(dedicatedCervicalRotation.get("right"));
            if (left != null) {
                String status = normalizeBreakoutStatus(left.get("breakout_status"));
                List<String> primaryFindings = buildCervicalRotationPrimaryFindings(left, "左侧");
                List<String> preliminaryDirection = castStringList(left.get("breakout_preliminary_direction"));
                boolean needsManualReview = Boolean.TRUE.equals(left.get("needs_manual_review"));
                Map<String, Object> leftItem = new LinkedHashMap<>();
                leftItem.put("breakout_status", status);
                leftItem.put("primary_findings", primaryFindings);
                leftItem.put("preliminary_direction", preliminaryDirection);
                leftItem.put("needs_manual_review", needsManualReview);
                leftItem.put("summary_text", StrUtil.blankToDefault(
                        toStringValue(left.get("breakout_summary_text")),
                        buildCervicalBreakoutSummaryText(status, primaryFindings, preliminaryDirection, needsManualReview, "rotation_left")
                ));
                result.put(CERVICAL_ROTATION_LEFT_TEST_CODE, leftItem);
            }
            if (right != null) {
                String status = normalizeBreakoutStatus(right.get("breakout_status"));
                List<String> primaryFindings = buildCervicalRotationPrimaryFindings(right, "右侧");
                List<String> preliminaryDirection = castStringList(right.get("breakout_preliminary_direction"));
                boolean needsManualReview = Boolean.TRUE.equals(right.get("needs_manual_review"));
                Map<String, Object> rightItem = new LinkedHashMap<>();
                rightItem.put("breakout_status", status);
                rightItem.put("primary_findings", primaryFindings);
                rightItem.put("preliminary_direction", preliminaryDirection);
                rightItem.put("needs_manual_review", needsManualReview);
                rightItem.put("summary_text", StrUtil.blankToDefault(
                        toStringValue(right.get("breakout_summary_text")),
                        buildCervicalBreakoutSummaryText(status, primaryFindings, preliminaryDirection, needsManualReview, "rotation_right")
                ));
                result.put(CERVICAL_ROTATION_RIGHT_TEST_CODE, rightItem);
            }
        }

        Map<String, Object> dedicatedUpperExtremityPattern1 = castToMap(payload.get("upper_extremity_pattern1_breakout"));
        if (dedicatedUpperExtremityPattern1 != null && !dedicatedUpperExtremityPattern1.isEmpty()) {
            Map<String, Object> left = castToMap(dedicatedUpperExtremityPattern1.get("left"));
            Map<String, Object> right = castToMap(dedicatedUpperExtremityPattern1.get("right"));
            if (left != null) {
                String status = normalizeBreakoutStatus(left.get("breakout_status"));
                List<String> primaryFindings = buildUpperExtremityPattern1PrimaryFindings(left, "左侧");
                List<String> preliminaryDirection = castStringList(left.get("breakout_preliminary_direction"));
                boolean needsManualReview = Boolean.TRUE.equals(left.get("needs_manual_review"));
                Map<String, Object> leftItem = new LinkedHashMap<>();
                leftItem.put("breakout_status", status);
                leftItem.put("primary_findings", primaryFindings);
                leftItem.put("preliminary_direction", preliminaryDirection);
                leftItem.put("needs_manual_review", needsManualReview);
                leftItem.put("summary_text", StrUtil.blankToDefault(
                        toStringValue(left.get("breakout_summary_text")),
                        buildUpperExtremityPattern1BreakoutSummaryText(status, primaryFindings, preliminaryDirection, needsManualReview, "left")
                ));
                result.put(UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE, leftItem);
            }
            if (right != null) {
                String status = normalizeBreakoutStatus(right.get("breakout_status"));
                List<String> primaryFindings = buildUpperExtremityPattern1PrimaryFindings(right, "右侧");
                List<String> preliminaryDirection = castStringList(right.get("breakout_preliminary_direction"));
                boolean needsManualReview = Boolean.TRUE.equals(right.get("needs_manual_review"));
                Map<String, Object> rightItem = new LinkedHashMap<>();
                rightItem.put("breakout_status", status);
                rightItem.put("primary_findings", primaryFindings);
                rightItem.put("preliminary_direction", preliminaryDirection);
                rightItem.put("needs_manual_review", needsManualReview);
                rightItem.put("summary_text", StrUtil.blankToDefault(
                        toStringValue(right.get("breakout_summary_text")),
                        buildUpperExtremityPattern1BreakoutSummaryText(status, primaryFindings, preliminaryDirection, needsManualReview, "right")
                ));
                result.put(UPPER_EXTREMITY_PATTERN1_RIGHT_TEST_CODE, rightItem);
            }
        }

        Map<String, Object> dedicatedUpperExtremityPattern2 = castToMap(payload.get("upper_extremity_pattern2_breakout"));
        if (dedicatedUpperExtremityPattern2 != null && !dedicatedUpperExtremityPattern2.isEmpty()) {
            Map<String, Object> left = castToMap(dedicatedUpperExtremityPattern2.get("left"));
            Map<String, Object> right = castToMap(dedicatedUpperExtremityPattern2.get("right"));
            if (left != null) {
                String status = normalizeBreakoutStatus(left.get("breakout_status"));
                List<String> primaryFindings = buildUpperExtremityPattern2PrimaryFindings(left, "左侧");
                List<String> preliminaryDirection = castStringList(left.get("breakout_preliminary_direction"));
                boolean needsManualReview = Boolean.TRUE.equals(left.get("needs_manual_review"));
                Map<String, Object> leftItem = new LinkedHashMap<>();
                leftItem.put("breakout_status", status);
                leftItem.put("primary_findings", primaryFindings);
                leftItem.put("preliminary_direction", preliminaryDirection);
                leftItem.put("needs_manual_review", needsManualReview);
                leftItem.put("summary_text", StrUtil.blankToDefault(
                        toStringValue(left.get("breakout_summary_text")),
                        buildUpperExtremityPattern2BreakoutSummaryText(status, primaryFindings, preliminaryDirection,
                                needsManualReview, "left")
                ));
                result.put(UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE, leftItem);
            }
            if (right != null) {
                String status = normalizeBreakoutStatus(right.get("breakout_status"));
                List<String> primaryFindings = buildUpperExtremityPattern2PrimaryFindings(right, "右侧");
                List<String> preliminaryDirection = castStringList(right.get("breakout_preliminary_direction"));
                boolean needsManualReview = Boolean.TRUE.equals(right.get("needs_manual_review"));
                Map<String, Object> rightItem = new LinkedHashMap<>();
                rightItem.put("breakout_status", status);
                rightItem.put("primary_findings", primaryFindings);
                rightItem.put("preliminary_direction", preliminaryDirection);
                rightItem.put("needs_manual_review", needsManualReview);
                rightItem.put("summary_text", StrUtil.blankToDefault(
                        toStringValue(right.get("breakout_summary_text")),
                        buildUpperExtremityPattern2BreakoutSummaryText(status, primaryFindings, preliminaryDirection,
                                needsManualReview, "right")
                ));
                result.put(UPPER_EXTREMITY_PATTERN2_RIGHT_TEST_CODE, rightItem);
            }
        }

        Map<String, Object> dedicatedMsfBreakout = castToMap(payload.get(MSF_BREAKOUT_KEY));
        if (dedicatedMsfBreakout != null && !dedicatedMsfBreakout.isEmpty()) {
            String status = toStringValue(dedicatedMsfBreakout.get("breakout_status"));
            List<String> primaryFindings = buildMsfPrimaryFindings(dedicatedMsfBreakout);
            List<String> preliminaryDirection = castStringList(dedicatedMsfBreakout.get("breakout_preliminary_direction"));
            boolean needsManualReview = Boolean.TRUE.equals(dedicatedMsfBreakout.get("needs_manual_review"));
            Map<String, Object> msfAnalysis = castToMap(dedicatedMsfBreakout.get("msf_analysis"));
            Map<String, Object> msfAnalysisSummary = castToMap(msfAnalysis == null ? null : msfAnalysis.get("summary"));
            String analysisSummaryText = toStringValue(msfAnalysisSummary == null ? null : msfAnalysisSummary.get("summary_text"));
            List<String> likelyPattern = castStringList(msfAnalysisSummary == null ? null : msfAnalysisSummary.get("likely_pattern"));
            boolean rotationFlowNeeded = Boolean.TRUE.equals(msfAnalysisSummary == null ? null : msfAnalysisSummary.get("rotation_flow_needed"));
            boolean stopAndTreatPain = Boolean.TRUE.equals(msfAnalysisSummary == null ? null : msfAnalysisSummary.get("stop_and_treat_pain"));
            boolean manualReviewRequired = Boolean.TRUE.equals(msfAnalysisSummary == null ? null : msfAnalysisSummary.get("manual_review_required"));
            String primaryRegion = toStringValue(msfAnalysisSummary == null ? null : msfAnalysisSummary.get("primary_region"));
            if (primaryFindings.isEmpty() && !likelyPattern.isEmpty()) {
                primaryFindings = new ArrayList<>(likelyPattern);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("breakout_code", MSF_BREAKOUT_KEY);
            item.put("breakout_name_zh", "多部位屈曲分解评估");
            item.put("breakout_status", status);
            item.put("preliminary_direction", preliminaryDirection);
            item.put("primary_restriction_chain", castStringList(dedicatedMsfBreakout.get("primary_restriction_chain")));
            item.put("primary_control_deficit_chain", castStringList(dedicatedMsfBreakout.get("primary_control_deficit_chain")));
            item.put("left_right_asymmetry_focus", toStringValue(dedicatedMsfBreakout.get("left_right_asymmetry_focus")));
            item.put("needs_manual_review", needsManualReview);
            item.put("manual_review_required", manualReviewRequired || needsManualReview);
            item.put("primary_region", primaryRegion);
            item.put("likely_pattern", likelyPattern);
            item.put("rotation_flow_needed", rotationFlowNeeded);
            item.put("stop_and_treat_pain", stopAndTreatPain);
            item.put("primary_findings", primaryFindings);
            item.put("clinical_meaning_hint", toStringValue(dedicatedMsfBreakout.get("clinical_meaning_hint")));
            item.put("training_direction_hint", toStringValue(dedicatedMsfBreakout.get("training_direction_hint")));
            item.put("pause_or_referral_hint", toStringValue(dedicatedMsfBreakout.get("pause_or_referral_hint")));
            item.put("summary_text", StrUtil.blankToDefault(
                    toStringValue(dedicatedMsfBreakout.get("breakout_summary_text")),
                    StrUtil.blankToDefault(
                            analysisSummaryText,
                            buildMsfBreakoutSummaryText(status, primaryFindings, preliminaryDirection, needsManualReview)
                    )
            ));
            result.put("multi_segmental_flexion", item);
        }

        Map<String, Object> dedicatedMseBreakout = castToMap(payload.get(MSE_BREAKOUT_KEY));
        if (dedicatedMseBreakout != null && !dedicatedMseBreakout.isEmpty()) {
            String status = normalizeDedicatedMseBreakoutStatus(dedicatedMseBreakout.get("breakout_status"));
            List<String> primaryFindings = buildMsePrimaryFindings(dedicatedMseBreakout);
            List<String> preliminaryDirection = castStringList(dedicatedMseBreakout.get("breakout_preliminary_direction"));
            boolean needsManualReview = Boolean.TRUE.equals(dedicatedMseBreakout.get("needs_manual_review"));
            Map<String, Object> mseAnalysis = castToMap(dedicatedMseBreakout.get("mse_analysis"));
            Map<String, Object> mseAnalysisSummary = castToMap(mseAnalysis == null ? null : mseAnalysis.get("summary"));
            String analysisSummaryText = toStringValue(mseAnalysisSummary == null ? null : mseAnalysisSummary.get("summary_text"));
            List<String> likelyPattern = castStringList(mseAnalysisSummary == null ? null : mseAnalysisSummary.get("likely_pattern"));
            boolean upperBodyFlowNeeded = Boolean.TRUE.equals(mseAnalysisSummary == null ? null : mseAnalysisSummary.get("upper_body_extension_flow_needed"));
            boolean lowerBodyFlowNeeded = Boolean.TRUE.equals(mseAnalysisSummary == null ? null : mseAnalysisSummary.get("lower_body_extension_flow_needed"));
            List<String> nextFlowTargets = castStringList(mseAnalysisSummary == null ? null : mseAnalysisSummary.get("next_flow_targets"));
            boolean stopAndTreatPain = Boolean.TRUE.equals(mseAnalysisSummary == null ? null : mseAnalysisSummary.get("stop_and_treat_pain"));
            boolean manualReviewRequired = Boolean.TRUE.equals(mseAnalysisSummary == null ? null : mseAnalysisSummary.get("manual_review_required"));
            String primaryRegion = toStringValue(mseAnalysisSummary == null ? null : mseAnalysisSummary.get("primary_region"));
            if (primaryFindings.isEmpty() && !likelyPattern.isEmpty()) {
                primaryFindings = new ArrayList<>(likelyPattern);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("breakout_code", MSE_BREAKOUT_KEY);
            item.put("breakout_name_zh", "多部位伸展分解评估");
            item.put("breakout_status", status);
            item.put("preliminary_direction", preliminaryDirection);
            item.put("primary_restriction_chain", castStringList(dedicatedMseBreakout.get("primary_restriction_chain")));
            item.put("primary_control_deficit_chain", castStringList(dedicatedMseBreakout.get("primary_control_deficit_chain")));
            item.put("left_right_asymmetry_focus", toStringValue(dedicatedMseBreakout.get("left_right_asymmetry_focus")));
            item.put("needs_manual_review", needsManualReview);
            item.put("manual_review_required", manualReviewRequired || needsManualReview);
            item.put("primary_region", primaryRegion);
            item.put("likely_pattern", likelyPattern);
            item.put("upper_body_extension_flow_needed", upperBodyFlowNeeded);
            item.put("lower_body_extension_flow_needed", lowerBodyFlowNeeded);
            item.put("next_flow_targets", nextFlowTargets);
            item.put("stop_and_treat_pain", stopAndTreatPain);
            item.put("primary_findings", primaryFindings);
            item.put("clinical_meaning_hint", toStringValue(dedicatedMseBreakout.get("clinical_meaning_hint")));
            item.put("training_direction_hint", toStringValue(dedicatedMseBreakout.get("training_direction_hint")));
            item.put("pause_or_referral_hint", toStringValue(dedicatedMseBreakout.get("pause_or_referral_hint")));
            item.put("summary_text", StrUtil.blankToDefault(
                    toStringValue(dedicatedMseBreakout.get("breakout_summary_text")),
                    StrUtil.blankToDefault(
                            analysisSummaryText,
                            buildMseBreakoutSummaryText(status, primaryFindings, preliminaryDirection, needsManualReview)
                    )
            ));
            result.put("multi_segmental_extension", item);
        }

        Map<String, Object> dedicatedArmsDownBreakout = castToMap(payload.get(ARMS_DOWN_SQUAT_BREAKOUT_KEY));
        if (dedicatedArmsDownBreakout != null && !dedicatedArmsDownBreakout.isEmpty()) {
            String status = normalizeDedicatedArmsDownSquatBreakoutStatus(dedicatedArmsDownBreakout.get("breakout_status"));
            List<String> primaryFindings = buildArmsDownSquatPrimaryFindings(dedicatedArmsDownBreakout);
            List<String> preliminaryDirection = castStringList(dedicatedArmsDownBreakout.get("breakout_preliminary_direction"));
            boolean needsManualReview = Boolean.TRUE.equals(dedicatedArmsDownBreakout.get("needs_manual_review"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("breakout_code", ARMS_DOWN_SQUAT_BREAKOUT_KEY);
            item.put("breakout_name_zh", "垂臂下蹲分解评估");
            item.put("breakout_status", status);
            item.put("preliminary_direction", preliminaryDirection);
            item.put("primary_restriction_chain", castStringList(dedicatedArmsDownBreakout.get("primary_restriction_chain")));
            item.put("primary_control_deficit_chain", castStringList(dedicatedArmsDownBreakout.get("primary_control_deficit_chain")));
            item.put("left_right_asymmetry_focus", toStringValue(dedicatedArmsDownBreakout.get("left_right_asymmetry_global")));
            item.put("risk_precheck_level", StrUtil.blankToDefault(toStringValue(dedicatedArmsDownBreakout.get("risk_precheck_level")), ""));
            item.put("risk_tags", castStringList(dedicatedArmsDownBreakout.get("risk_tags")));
            item.put("needs_manual_review", needsManualReview);
            item.put("primary_findings", primaryFindings);
            item.put("clinical_meaning_hint", toStringValue(dedicatedArmsDownBreakout.get("clinical_meaning_hint")));
            item.put("training_direction_hint", toStringValue(dedicatedArmsDownBreakout.get("training_direction_hint")));
            item.put("pause_or_referral_hint", toStringValue(dedicatedArmsDownBreakout.get("pause_or_referral_hint")));
            item.put("summary_text", StrUtil.blankToDefault(
                    toStringValue(dedicatedArmsDownBreakout.get("breakout_summary_text")),
                    buildArmsDownSquatBreakoutSummaryText(status, primaryFindings, preliminaryDirection, needsManualReview)
            ));
            result.put("arms_down_deep_squat", item);
        }

        for (Map.Entry<String, Map<String, Object>> entry : breakouts.entrySet()) {
            String key = entry.getKey();
            if (StrUtil.equalsAny(key, CERVICAL_FLEXION_BREAKOUT_KEY, CERVICAL_EXTENSION_BREAKOUT_KEY,
                    CERVICAL_ROTATION_BREAKOUT_KEY, UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY,
                    UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY, MSF_BREAKOUT_KEY, MSE_BREAKOUT_KEY,
                    ARMS_DOWN_SQUAT_BREAKOUT_KEY, "msf", "mse", "deep_squat")) {
                continue;
            }
            Map<String, Object> row = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            String status = normalizeBreakoutStatus(row.get("status"));
            List<String> findings = distinct(Arrays.asList(
                    toStringValue(row.get("findings")),
                    toStringValue(row.get("mobility_restriction_signs")),
                    toStringValue(row.get("motor_control_signs")),
                    toStringValue(row.get("asymmetry_signs"))
            ));
            item.put("breakout_status", status);
            item.put("primary_findings", findings);
            item.put("preliminary_direction", inferBreakoutDirection(row));
            item.put("needs_manual_review", Boolean.TRUE.equals(row.get("stop_due_to_pain")) || Boolean.TRUE.equals(row.get("pain_present")));
            item.put("summary_text", buildGenericBreakoutSummaryText(key, status, findings));
            result.put(key, item);
        }
        return result;
    }

    private Map<String, Object> buildCervicalReportMapping(Map<String, Object> summary, String testCode) {
        Map<String, Object> topTierSummaryItem = castToMap(summary.get("top_tier_summary_item"));
        Map<String, Object> breakoutSummaryItem = castToMap(summary.get("breakout_summary_item"));
        Map<String, Object> top = topTierSummaryItem == null ? null : castToMap(topTierSummaryItem.get(testCode));
        Map<String, Object> breakout = breakoutSummaryItem == null ? null : castToMap(breakoutSummaryItem.get(testCode));

        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("top_tier_result", top == null ? "" : toStringValue(top.get("classification")));
        mapping.put("breakout_status", breakout == null ? BREAKOUT_STATUS_NOT_STARTED : toStringValue(breakout.get("breakout_status")));
        mapping.put("preliminary_direction", breakout == null ? Collections.emptyList() : breakout.get("preliminary_direction"));
        mapping.put("needs_manual_review", breakout != null && Boolean.TRUE.equals(breakout.get("needs_manual_review")));
        mapping.put("summary_text", breakout != null && StrUtil.isNotBlank(toStringValue(breakout.get("summary_text")))
                ? toStringValue(breakout.get("summary_text"))
                : top == null ? "" : toStringValue(top.get("summary_text")));
        return mapping;
    }

    private Map<String, Object> buildCervicalRotationReportMapping(Map<String, Object> summary) {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("left", buildCervicalReportMapping(summary, CERVICAL_ROTATION_LEFT_TEST_CODE));
        mapping.put("right", buildCervicalReportMapping(summary, CERVICAL_ROTATION_RIGHT_TEST_CODE));
        List<String> asymmetry = castStringList(summary.get("left_right_key_asymmetry")).stream()
                .filter(item -> StrUtil.contains(item, "颈椎旋转"))
                .collect(Collectors.toList());
        mapping.put("rotation_asymmetry_focus", asymmetry);
        return mapping;
    }

    private Map<String, Object> buildUpperExtremityPattern1ReportMapping(Map<String, Object> summary) {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("left", buildCervicalReportMapping(summary, UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE));
        mapping.put("right", buildCervicalReportMapping(summary, UPPER_EXTREMITY_PATTERN1_RIGHT_TEST_CODE));
        List<String> asymmetry = castStringList(summary.get("left_right_key_asymmetry")).stream()
                .filter(item -> StrUtil.containsAny(item, "上肢模式1", "上肢模式"))
                .collect(Collectors.toList());
        mapping.put("asymmetry_focus", asymmetry);
        return mapping;
    }

    private Map<String, Object> buildUpperExtremityPattern2ReportMapping(Map<String, Object> summary) {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("left", buildCervicalReportMapping(summary, UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE));
        mapping.put("right", buildCervicalReportMapping(summary, UPPER_EXTREMITY_PATTERN2_RIGHT_TEST_CODE));
        List<String> asymmetry = castStringList(summary.get("left_right_key_asymmetry")).stream()
                .filter(item -> StrUtil.containsAny(item, "上肢模式2", "上肢模式"))
                .collect(Collectors.toList());
        mapping.put("asymmetry_focus", asymmetry);
        return mapping;
    }

    private Map<String, Object> buildMsfReportMapping(Map<String, Object> summary) {
        Map<String, Object> topTierSummaryItem = castToMap(summary.get("top_tier_summary_item"));
        Map<String, Object> breakoutSummaryItem = castToMap(summary.get("breakout_summary_item"));
        Map<String, Object> top = topTierSummaryItem == null ? null : castToMap(topTierSummaryItem.get("multi_segmental_flexion"));
        Map<String, Object> breakout = breakoutSummaryItem == null ? null : castToMap(breakoutSummaryItem.get("multi_segmental_flexion"));

        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("top_tier_result", top == null ? "" : toStringValue(top.get("classification")));
        mapping.put("breakout_status", breakout == null ? BREAKOUT_STATUS_NOT_STARTED : toStringValue(breakout.get("breakout_status")));
        mapping.put("preliminary_direction", breakout == null ? Collections.emptyList() : breakout.get("preliminary_direction"));
        mapping.put("primary_restriction_chain", breakout == null ? Collections.emptyList() : breakout.get("primary_restriction_chain"));
        mapping.put("primary_control_deficit_chain", breakout == null ? Collections.emptyList() : breakout.get("primary_control_deficit_chain"));
        mapping.put("left_right_asymmetry_focus", breakout == null ? "" : toStringValue(breakout.get("left_right_asymmetry_focus")));
        mapping.put("clinical_meaning_hint", breakout == null ? "" : toStringValue(breakout.get("clinical_meaning_hint")));
        mapping.put("training_direction_hint", breakout == null ? "" : toStringValue(breakout.get("training_direction_hint")));
        mapping.put("pause_or_referral_hint", breakout == null ? "" : toStringValue(breakout.get("pause_or_referral_hint")));
        mapping.put("needs_manual_review", breakout != null && Boolean.TRUE.equals(breakout.get("needs_manual_review")));
        mapping.put("manual_review_required", breakout != null && Boolean.TRUE.equals(breakout.get("manual_review_required")));
        mapping.put("primary_region", breakout == null ? "" : toStringValue(breakout.get("primary_region")));
        mapping.put("likely_pattern", breakout == null ? Collections.emptyList() : castStringList(breakout.get("likely_pattern")));
        mapping.put("rotation_flow_needed", breakout != null && Boolean.TRUE.equals(breakout.get("rotation_flow_needed")));
        mapping.put("stop_and_treat_pain", breakout != null && Boolean.TRUE.equals(breakout.get("stop_and_treat_pain")));
        mapping.put("summary_text", breakout != null && StrUtil.isNotBlank(toStringValue(breakout.get("summary_text")))
                ? toStringValue(breakout.get("summary_text"))
                : top == null ? "" : toStringValue(top.get("summary_text")));
        return mapping;
    }

    private Map<String, Object> buildMseReportMapping(Map<String, Object> summary) {
        Map<String, Object> topTierSummaryItem = castToMap(summary.get("top_tier_summary_item"));
        Map<String, Object> breakoutSummaryItem = castToMap(summary.get("breakout_summary_item"));
        Map<String, Object> top = topTierSummaryItem == null ? null : castToMap(topTierSummaryItem.get("multi_segmental_extension"));
        Map<String, Object> breakout = breakoutSummaryItem == null ? null : castToMap(breakoutSummaryItem.get("multi_segmental_extension"));

        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("top_tier_result", top == null ? "" : toStringValue(top.get("classification")));
        mapping.put("breakout_status", breakout == null ? BREAKOUT_STATUS_NOT_STARTED : toStringValue(breakout.get("breakout_status")));
        mapping.put("preliminary_direction", breakout == null ? Collections.emptyList() : breakout.get("preliminary_direction"));
        mapping.put("primary_restriction_chain", breakout == null ? Collections.emptyList() : breakout.get("primary_restriction_chain"));
        mapping.put("primary_control_deficit_chain", breakout == null ? Collections.emptyList() : breakout.get("primary_control_deficit_chain"));
        mapping.put("left_right_asymmetry_focus", breakout == null ? "" : toStringValue(breakout.get("left_right_asymmetry_focus")));
        mapping.put("clinical_meaning_hint", breakout == null ? "" : toStringValue(breakout.get("clinical_meaning_hint")));
        mapping.put("training_direction_hint", breakout == null ? "" : toStringValue(breakout.get("training_direction_hint")));
        mapping.put("pause_or_referral_hint", breakout == null ? "" : toStringValue(breakout.get("pause_or_referral_hint")));
        mapping.put("needs_manual_review", breakout != null && Boolean.TRUE.equals(breakout.get("needs_manual_review")));
        mapping.put("manual_review_required", breakout != null && Boolean.TRUE.equals(breakout.get("manual_review_required")));
        mapping.put("primary_region", breakout == null ? "" : toStringValue(breakout.get("primary_region")));
        mapping.put("likely_pattern", breakout == null ? Collections.emptyList() : castStringList(breakout.get("likely_pattern")));
        mapping.put("upper_body_extension_flow_needed",
                breakout != null && Boolean.TRUE.equals(breakout.get("upper_body_extension_flow_needed")));
        mapping.put("lower_body_extension_flow_needed",
                breakout != null && Boolean.TRUE.equals(breakout.get("lower_body_extension_flow_needed")));
        mapping.put("next_flow_targets", breakout == null ? Collections.emptyList() : castStringList(breakout.get("next_flow_targets")));
        mapping.put("stop_and_treat_pain", breakout != null && Boolean.TRUE.equals(breakout.get("stop_and_treat_pain")));
        mapping.put("summary_text", breakout != null && StrUtil.isNotBlank(toStringValue(breakout.get("summary_text")))
                ? toStringValue(breakout.get("summary_text"))
                : top == null ? "" : toStringValue(top.get("summary_text")));
        return mapping;
    }

    private Map<String, Object> buildArmsDownSquatReportMapping(Map<String, Object> summary) {
        Map<String, Object> topTierSummaryItem = castToMap(summary.get("top_tier_summary_item"));
        Map<String, Object> breakoutSummaryItem = castToMap(summary.get("breakout_summary_item"));
        Map<String, Object> top = topTierSummaryItem == null ? null : castToMap(topTierSummaryItem.get("arms_down_deep_squat"));
        Map<String, Object> breakout = breakoutSummaryItem == null ? null : castToMap(breakoutSummaryItem.get("arms_down_deep_squat"));

        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("top_tier_result", top == null ? "" : toStringValue(top.get("classification")));
        mapping.put("breakout_status", breakout == null ? BREAKOUT_STATUS_NOT_STARTED : toStringValue(breakout.get("breakout_status")));
        mapping.put("preliminary_direction", breakout == null ? Collections.emptyList() : breakout.get("preliminary_direction"));
        mapping.put("primary_restriction_chain", breakout == null ? Collections.emptyList() : breakout.get("primary_restriction_chain"));
        mapping.put("primary_control_deficit_chain", breakout == null ? Collections.emptyList() : breakout.get("primary_control_deficit_chain"));
        mapping.put("risk_precheck_level", breakout == null ? "medium" : toStringValue(breakout.get("risk_precheck_level")));
        mapping.put("risk_tags", breakout == null ? Collections.emptyList() : breakout.get("risk_tags"));
        mapping.put("clinical_meaning_hint", breakout == null ? "" : toStringValue(breakout.get("clinical_meaning_hint")));
        mapping.put("training_direction_hint", breakout == null ? "" : toStringValue(breakout.get("training_direction_hint")));
        mapping.put("pause_or_referral_hint", breakout == null ? "" : toStringValue(breakout.get("pause_or_referral_hint")));
        mapping.put("needs_manual_review", breakout != null && Boolean.TRUE.equals(breakout.get("needs_manual_review")));
        mapping.put("summary_text", breakout != null && StrUtil.isNotBlank(toStringValue(breakout.get("summary_text")))
                ? toStringValue(breakout.get("summary_text"))
                : top == null ? "" : toStringValue(top.get("summary_text")));
        return mapping;
    }

    private String buildTopTierSummaryText(String classification, String testCode, String testNameZh) {
        if (!StrUtil.equalsAny(testCode, CERVICAL_FLEXION_TEST_CODE, CERVICAL_EXTENSION_TEST_CODE)) {
            if (StrUtil.equalsAny(testCode, UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE, UPPER_EXTREMITY_PATTERN1_RIGHT_TEST_CODE)) {
                if (StrUtil.equals(classification, CLASS_FN)) {
                    return testNameZh + "当前功能表现正常，暂无专项分解建议。";
                }
                if (StrUtil.equals(classification, CLASS_FP)) {
                    return testNameZh + "功能可完成但伴疼痛，建议谨慎进入上肢模式1分解评估。";
                }
                if (StrUtil.equals(classification, CLASS_DN)) {
                    return testNameZh + "存在功能异常，建议进入上肢模式1分解评估。";
                }
                if (StrUtil.equals(classification, CLASS_DP)) {
                    return testNameZh + "存在功能异常并伴疼痛，建议优先人工复核后再推进分解评估。";
                }
                return testNameZh + "暂未完成 Top Tier 评估。";
            }
            if (StrUtil.equalsAny(testCode, UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE, UPPER_EXTREMITY_PATTERN2_RIGHT_TEST_CODE)) {
                if (StrUtil.equals(classification, CLASS_FN)) {
                    return testNameZh + "当前功能表现正常，暂无专项分解建议。";
                }
                if (StrUtil.equals(classification, CLASS_FP)) {
                    return testNameZh + "功能可完成但伴疼痛，建议谨慎进入上肢模式2分解评估。";
                }
                if (StrUtil.equals(classification, CLASS_DN)) {
                    return testNameZh + "存在功能异常，建议进入上肢模式2分解评估。";
                }
                if (StrUtil.equals(classification, CLASS_DP)) {
                    return testNameZh + "存在功能异常并伴疼痛，建议优先人工复核后再推进分解评估。";
                }
                return testNameZh + "暂未完成 Top Tier 评估。";
            }
            if (StrUtil.equals(classification, CLASS_FN)) {
                return testNameZh + "当前功能表现正常。";
            }
            if (StrUtil.equals(classification, CLASS_FP)) {
                return testNameZh + "功能可完成但伴疼痛，建议谨慎推进并优先人工复核。";
            }
            if (StrUtil.equals(classification, CLASS_DN)) {
                return testNameZh + "存在功能异常，建议进入对应 Breakout 分解评估。";
            }
            if (StrUtil.equals(classification, CLASS_DP)) {
                return testNameZh + "存在功能异常并伴疼痛，建议优先人工复核后再推进分解评估。";
            }
            return testNameZh + "暂未完成 Top Tier 评估。";
        }
        String action = StrUtil.equals(testCode, CERVICAL_EXTENSION_TEST_CODE) ? "颈椎伸展" : "颈椎屈曲";
        if (StrUtil.equals(classification, CLASS_FN)) {
            return action + "功能正常，无痛，暂无进一步分解评估建议。";
        }
        if (StrUtil.equals(classification, CLASS_FP)) {
            return action + "功能可完成但伴疼痛，建议谨慎进入高级分解评估并优先人工复核。";
        }
        if (StrUtil.equals(classification, CLASS_DN)) {
            return action + "存在功能异常，建议进行" + action + "分解评估。";
        }
        if (StrUtil.equals(classification, CLASS_DP)) {
            return action + "存在功能异常并伴疼痛，建议优先人工复核并谨慎进行" + action + "分解评估。";
        }
        return action + "暂未完成 Top Tier 评估。";
    }

    private String buildCervicalBreakoutSummaryText(String status,
                                                    List<String> findings,
                                                    List<String> direction,
                                                    boolean needsManualReview,
                                                    String motion) {
        String action;
        if ("extension".equals(motion)) {
            action = "颈椎伸展";
        } else if ("rotation_left".equals(motion)) {
            action = "颈椎左旋转";
        } else if ("rotation_right".equals(motion)) {
            action = "颈椎右旋转";
        } else {
            action = "颈椎屈曲";
        }
        if (StrUtil.equals(status, BREAKOUT_STATUS_NOT_STARTED)) {
            return action + "分解评估尚未开始。";
        }
        if (StrUtil.equals(status, BREAKOUT_STATUS_SKIPPED)) {
            return action + "分解评估暂未执行，建议结合后续复测决定是否补充。";
        }
        if (StrUtil.equals(status, BREAKOUT_STATUS_STOPPED_PAIN)) {
            return action + "分解评估因疼痛中止，建议优先疼痛管理并进行人工复核。";
        }
        String findingText = findings.isEmpty() ? "暂未提取到明确异常线索" : String.join("、", findings);
        String directionText = direction.isEmpty() ? "方向待补充" : String.join("、", direction);
        return action + "分解评估" + mapBreakoutStatusZh(status) + "，主要表现为：" + findingText
                + "；初步方向：" + directionText + (needsManualReview ? "；建议人工复核。" : "。");
    }

    private String buildGenericBreakoutSummaryText(String breakoutKey, String status, List<String> findings) {
        String name = BREAKOUT_NAME_MAP.getOrDefault(breakoutKey, breakoutKey);
        if (StrUtil.equals(status, BREAKOUT_STATUS_NOT_STARTED)) {
            return name + "尚未开始。";
        }
        if (StrUtil.equals(status, BREAKOUT_STATUS_SKIPPED)) {
            return name + "暂未执行。";
        }
        if (StrUtil.equals(status, BREAKOUT_STATUS_STOPPED_PAIN)) {
            return name + "因疼痛中止，建议优先人工复核。";
        }
        String findingText = findings.isEmpty() ? "未提取到明确线索" : String.join("、", findings);
        return name + mapBreakoutStatusZh(status) + "，主要线索：" + findingText + "。";
    }

    private List<String> buildUpperExtremityPattern1PrimaryFindings(Map<String, Object> side, String sideZh) {
        List<String> findings = new ArrayList<>();
        String proneActive = normalizeClassification(side.get("prone_active_result"));
        if (StrUtil.isNotBlank(proneActive)) {
            findings.add(sideZh + "俯卧位主动：" + proneActive);
        }
        String pronePassive = normalizeClassification(side.get("prone_passive_result"));
        if (StrUtil.isNotBlank(pronePassive)) {
            findings.add(sideZh + "俯卧位被动：" + pronePassive);
        }
        String supineInteractive = normalizeClassification(side.get("supine_interactive_result"));
        if (StrUtil.isNotBlank(supineInteractive)) {
            findings.add(sideZh + "仰卧位交互：" + supineInteractive);
        }
        String activeQuality = toStringValue(side.get("active_ue_pattern1_quality"));
        if (StrUtil.isNotBlank(activeQuality)) {
            findings.add(sideZh + "主动模式质量：" + activeQuality);
        }
        String scapular = toStringValue(side.get("scapular_control_observation"));
        if (StrUtil.isNotBlank(scapular)) {
            findings.add(sideZh + "肩胛控制：" + scapular);
        }
        String thoracic = toStringValue(side.get("thoracic_influence_observation"));
        if (StrUtil.isNotBlank(thoracic)) {
            findings.add(sideZh + "胸椎影响：" + thoracic);
        }
        String gleno = toStringValue(side.get("glenohumeral_observation"));
        if (StrUtil.isNotBlank(gleno)) {
            findings.add(sideZh + "盂肱观察：" + gleno);
        }
        findings.addAll(castStringList(side.get("breakout_preliminary_direction")).stream()
                .map(item -> sideZh + "方向：" + item).collect(Collectors.toList()));
        return distinct(findings);
    }

    private String buildUpperExtremityPattern1BreakoutSummaryText(String status,
                                                                  List<String> findings,
                                                                  List<String> direction,
                                                                  boolean needsManualReview,
                                                                  String side) {
        String action = "left".equals(side) ? "上肢模式1（左）" : "上肢模式1（右）";
        if (StrUtil.equals(status, BREAKOUT_STATUS_NOT_STARTED)) {
            return action + "分解评估尚未开始。";
        }
        if (StrUtil.equals(status, BREAKOUT_STATUS_SKIPPED)) {
            return action + "分解评估暂未执行，建议结合后续复测决定是否补充。";
        }
        if (StrUtil.equals(status, BREAKOUT_STATUS_STOPPED_PAIN)) {
            return action + "分解评估因疼痛中止，建议优先疼痛管理并进行人工复核。";
        }
        String findingText = findings.isEmpty() ? "暂未提取到明确异常线索" : String.join("、", findings);
        String directionText = direction.isEmpty() ? "方向待补充" : String.join("、", direction);
        return action + "分解评估" + mapBreakoutStatusZh(status) + "，主要表现为：" + findingText
                + "；初步方向：" + directionText + (needsManualReview ? "；建议人工复核。" : "。");
    }

    private List<String> inferBreakoutDirection(Map<String, Object> row) {
        Set<String> result = new LinkedHashSet<>();
        if (StrUtil.isNotBlank(toStringValue(row.get("mobility_restriction_signs")))) {
            result.add("更偏活动度限制");
        }
        if (StrUtil.isNotBlank(toStringValue(row.get("motor_control_signs")))) {
            result.add("更偏运动控制问题");
        }
        if (Boolean.TRUE.equals(row.get("pain_present")) || Boolean.TRUE.equals(row.get("stop_due_to_pain"))) {
            result.add("更偏疼痛主导");
        }
        return new ArrayList<>(result);
    }

    private List<String> buildCervicalPrimaryFindings(Map<String, Object> dedicatedCervical, String motion) {
        List<String> findings = new ArrayList<>();
        String activeQuality = toStringValue(dedicatedCervical.get(
                "extension".equals(motion) ? "active_cervical_extension_quality" : "active_cervical_flexion_quality"));
        if (StrUtil.isNotBlank(activeQuality)) {
            findings.add(("extension".equals(motion) ? "主动伸展：" : "主动屈曲：") + activeQuality);
        }
        String passiveQuality = toStringValue(dedicatedCervical.get(
                "extension".equals(motion) ? "passive_cervical_extension_quality" : "passive_cervical_flexion_quality"));
        if (StrUtil.isNotBlank(passiveQuality)) {
            findings.add(("extension".equals(motion) ? "被动伸展：" : "被动屈曲：") + passiveQuality);
        }
        String upperObservation = toStringValue(dedicatedCervical.get(
                "extension".equals(motion) ? "upper_cervical_extension_observation" : "upper_cervical_flexion_observation"));
        if (StrUtil.isNotBlank(upperObservation)) {
            findings.add("上位颈观察：" + upperObservation);
        }
        List<String> compensationPatterns = castStringList(dedicatedCervical.get("compensation_patterns"));
        if (!compensationPatterns.isEmpty()) {
            findings.add("代偿模式：" + String.join("、", compensationPatterns));
        }
        return findings;
    }

    private List<String> buildCervicalRotationPrimaryFindings(Map<String, Object> side, String sideLabel) {
        List<String> findings = new ArrayList<>();
        String activeQuality = toStringValue(side.get("active_cervical_rotation_quality"));
        if (StrUtil.isNotBlank(activeQuality)) {
            findings.add(sideLabel + "主动旋转：" + activeQuality);
        }
        String passiveQuality = toStringValue(side.get("passive_cervical_rotation_quality"));
        if (StrUtil.isNotBlank(passiveQuality)) {
            findings.add(sideLabel + "被动旋转：" + passiveQuality);
        }
        String upperObservation = toStringValue(side.get("upper_cervical_rotation_observation"));
        if (StrUtil.isNotBlank(upperObservation)) {
            findings.add(sideLabel + "上位颈观察：" + upperObservation);
        }
        List<String> compensationPatterns = castStringList(side.get("compensation_patterns"));
        if (!compensationPatterns.isEmpty()) {
            findings.add(sideLabel + "代偿模式：" + String.join("、", compensationPatterns));
        }
        return findings;
    }

    private List<String> castStringList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<?> list = (List<?>) value;
        return list.stream().filter(Objects::nonNull).map(String::valueOf).filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }

    private void syncDedicatedCervicalStructure(Map<String, Object> payload) {
        Map<String, Object> topTier = ensureMap(payload, "top_tier");
        Map<String, Object> breakouts = ensureMap(payload, "breakouts");

        Map<String, Object> dedicatedTop = castToMap(payload.get("cervical_flexion_top_tier"));
        if (dedicatedTop == null || dedicatedTop.isEmpty()) {
            Map<String, Object> legacyTop = castToMap(topTier.get(CERVICAL_FLEXION_TEST_CODE));
            dedicatedTop = mapLegacyTopTierToDedicatedCervical(legacyTop);
        } else {
            dedicatedTop = normalizeDedicatedCervicalTopTier(dedicatedTop);
        }
        payload.put("cervical_flexion_top_tier", dedicatedTop);
        topTier.put(CERVICAL_FLEXION_TEST_CODE, mapDedicatedCervicalTopTierToLegacy(dedicatedTop));

        Map<String, Object> dedicatedBreakout = castToMap(payload.get("cervical_flexion_breakout"));
        if (dedicatedBreakout == null || dedicatedBreakout.isEmpty()) {
            Map<String, Object> legacyBreakout = castToMap(breakouts.get(CERVICAL_FLEXION_BREAKOUT_KEY));
            dedicatedBreakout = mapLegacyBreakoutToDedicatedCervical(legacyBreakout);
        } else {
            dedicatedBreakout = normalizeDedicatedCervicalBreakout(dedicatedBreakout);
        }
        payload.put("cervical_flexion_breakout", dedicatedBreakout);
        breakouts.put(CERVICAL_FLEXION_BREAKOUT_KEY, mapDedicatedCervicalBreakoutToLegacy(dedicatedBreakout));

        Map<String, Object> dedicatedExtensionTop = castToMap(payload.get("cervical_extension_top_tier"));
        if (dedicatedExtensionTop == null || dedicatedExtensionTop.isEmpty()) {
            Map<String, Object> legacyExtensionTop = castToMap(topTier.get(CERVICAL_EXTENSION_TEST_CODE));
            dedicatedExtensionTop = mapLegacyTopTierToDedicatedCervicalExtension(legacyExtensionTop);
        } else {
            dedicatedExtensionTop = normalizeDedicatedCervicalExtensionTopTier(dedicatedExtensionTop);
        }
        payload.put("cervical_extension_top_tier", dedicatedExtensionTop);
        topTier.put(CERVICAL_EXTENSION_TEST_CODE, mapDedicatedCervicalExtensionTopTierToLegacy(dedicatedExtensionTop));

        Map<String, Object> dedicatedExtensionBreakout = castToMap(payload.get("cervical_extension_breakout"));
        if (dedicatedExtensionBreakout == null || dedicatedExtensionBreakout.isEmpty()) {
            Map<String, Object> legacyExtensionBreakout = castToMap(breakouts.get(CERVICAL_EXTENSION_BREAKOUT_KEY));
            dedicatedExtensionBreakout = mapLegacyBreakoutToDedicatedCervicalExtension(legacyExtensionBreakout);
        } else {
            dedicatedExtensionBreakout = normalizeDedicatedCervicalExtensionBreakout(dedicatedExtensionBreakout);
        }
        payload.put("cervical_extension_breakout", dedicatedExtensionBreakout);
        breakouts.put(CERVICAL_EXTENSION_BREAKOUT_KEY,
                mapDedicatedCervicalExtensionBreakoutToLegacy(dedicatedExtensionBreakout));

        Map<String, Object> dedicatedRotationTop = castToMap(payload.get("cervical_rotation_top_tier"));
        if (dedicatedRotationTop == null || dedicatedRotationTop.isEmpty()) {
            Map<String, Object> legacyLeft = castToMap(topTier.get(CERVICAL_ROTATION_LEFT_TEST_CODE));
            Map<String, Object> legacyRight = castToMap(topTier.get(CERVICAL_ROTATION_RIGHT_TEST_CODE));
            dedicatedRotationTop = mapLegacyTopTierToDedicatedCervicalRotation(legacyLeft, legacyRight);
        } else {
            dedicatedRotationTop = normalizeDedicatedCervicalRotationTopTier(dedicatedRotationTop);
        }
        payload.put("cervical_rotation_top_tier", dedicatedRotationTop);
        topTier.put(CERVICAL_ROTATION_LEFT_TEST_CODE,
                mapDedicatedCervicalRotationTopTierSideToLegacy(dedicatedRotationTop, CERVICAL_ROTATION_LEFT_TEST_CODE));
        topTier.put(CERVICAL_ROTATION_RIGHT_TEST_CODE,
                mapDedicatedCervicalRotationTopTierSideToLegacy(dedicatedRotationTop, CERVICAL_ROTATION_RIGHT_TEST_CODE));

        Map<String, Object> dedicatedRotationBreakout = castToMap(payload.get("cervical_rotation_breakout"));
        if (dedicatedRotationBreakout == null || dedicatedRotationBreakout.isEmpty()) {
            Map<String, Object> legacyRotationBreakout = castToMap(breakouts.get(CERVICAL_ROTATION_BREAKOUT_KEY));
            if (legacyRotationBreakout == null || legacyRotationBreakout.isEmpty()) {
                legacyRotationBreakout = castToMap(breakouts.get("cervical_pattern"));
            }
            dedicatedRotationBreakout = mapLegacyBreakoutToDedicatedCervicalRotation(legacyRotationBreakout);
        } else {
            dedicatedRotationBreakout = normalizeDedicatedCervicalRotationBreakout(dedicatedRotationBreakout);
        }
        payload.put("cervical_rotation_breakout", dedicatedRotationBreakout);
        Map<String, Object> rotationLegacy = mapDedicatedCervicalRotationBreakoutToLegacy(dedicatedRotationBreakout);
        breakouts.put(CERVICAL_ROTATION_BREAKOUT_KEY, rotationLegacy);
        // 兼容历史 cervical_pattern 聚合镜像
        breakouts.put("cervical_pattern", new LinkedHashMap<>(rotationLegacy));

        Map<String, Object> dedicatedUpperExtremityPattern1Top = castToMap(payload.get("upper_extremity_pattern1_top_tier"));
        if (dedicatedUpperExtremityPattern1Top == null || dedicatedUpperExtremityPattern1Top.isEmpty()) {
            Map<String, Object> legacyLeft = castToMap(topTier.get(UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE));
            Map<String, Object> legacyRight = castToMap(topTier.get(UPPER_EXTREMITY_PATTERN1_RIGHT_TEST_CODE));
            dedicatedUpperExtremityPattern1Top = mapLegacyTopTierToDedicatedUpperExtremityPattern1(legacyLeft, legacyRight);
        } else {
            dedicatedUpperExtremityPattern1Top = normalizeDedicatedUpperExtremityPattern1TopTier(dedicatedUpperExtremityPattern1Top);
        }
        payload.put("upper_extremity_pattern1_top_tier", dedicatedUpperExtremityPattern1Top);
        topTier.put(UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE,
                mapDedicatedUpperExtremityPattern1TopTierSideToLegacy(dedicatedUpperExtremityPattern1Top, UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE));
        topTier.put(UPPER_EXTREMITY_PATTERN1_RIGHT_TEST_CODE,
                mapDedicatedUpperExtremityPattern1TopTierSideToLegacy(dedicatedUpperExtremityPattern1Top, UPPER_EXTREMITY_PATTERN1_RIGHT_TEST_CODE));

        Map<String, Object> dedicatedUpperExtremityPattern1Breakout = castToMap(payload.get("upper_extremity_pattern1_breakout"));
        if (dedicatedUpperExtremityPattern1Breakout == null || dedicatedUpperExtremityPattern1Breakout.isEmpty()) {
            Map<String, Object> legacyLeft = castToMap(breakouts.get("upper_extremity_pattern_left"));
            Map<String, Object> legacyRight = castToMap(breakouts.get("upper_extremity_pattern_right"));
            Map<String, Object> legacyCombined = castToMap(breakouts.get(UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY));
            if ((legacyLeft == null || legacyLeft.isEmpty()) && legacyCombined != null && !legacyCombined.isEmpty()) {
                legacyLeft = legacyCombined;
            }
            if ((legacyRight == null || legacyRight.isEmpty()) && legacyCombined != null && !legacyCombined.isEmpty()) {
                legacyRight = legacyCombined;
            }
            dedicatedUpperExtremityPattern1Breakout = mapLegacyBreakoutToDedicatedUpperExtremityPattern1(legacyLeft, legacyRight);
        } else {
            dedicatedUpperExtremityPattern1Breakout = normalizeDedicatedUpperExtremityPattern1Breakout(dedicatedUpperExtremityPattern1Breakout);
        }
        payload.put("upper_extremity_pattern1_breakout", dedicatedUpperExtremityPattern1Breakout);
        Map<String, Object> upperExtremityPattern1Legacy = mapDedicatedUpperExtremityPattern1BreakoutToLegacy(dedicatedUpperExtremityPattern1Breakout);
        breakouts.put(UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY, upperExtremityPattern1Legacy);

        Map<String, Object> dedicatedUpperExtremityPattern2Top = castToMap(payload.get("upper_extremity_pattern2_top_tier"));
        if (dedicatedUpperExtremityPattern2Top == null || dedicatedUpperExtremityPattern2Top.isEmpty()) {
            Map<String, Object> legacyLeft = castToMap(topTier.get(UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE));
            Map<String, Object> legacyRight = castToMap(topTier.get(UPPER_EXTREMITY_PATTERN2_RIGHT_TEST_CODE));
            dedicatedUpperExtremityPattern2Top = mapLegacyTopTierToDedicatedUpperExtremityPattern2(legacyLeft, legacyRight);
        } else {
            dedicatedUpperExtremityPattern2Top = normalizeDedicatedUpperExtremityPattern2TopTier(dedicatedUpperExtremityPattern2Top);
        }
        payload.put("upper_extremity_pattern2_top_tier", dedicatedUpperExtremityPattern2Top);
        topTier.put(UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE,
                mapDedicatedUpperExtremityPattern2TopTierSideToLegacy(dedicatedUpperExtremityPattern2Top, UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE));
        topTier.put(UPPER_EXTREMITY_PATTERN2_RIGHT_TEST_CODE,
                mapDedicatedUpperExtremityPattern2TopTierSideToLegacy(dedicatedUpperExtremityPattern2Top, UPPER_EXTREMITY_PATTERN2_RIGHT_TEST_CODE));

        Map<String, Object> dedicatedUpperExtremityPattern2Breakout = castToMap(payload.get("upper_extremity_pattern2_breakout"));
        if (dedicatedUpperExtremityPattern2Breakout == null || dedicatedUpperExtremityPattern2Breakout.isEmpty()) {
            Map<String, Object> legacyLeft = castToMap(breakouts.get("upper_extremity_pattern_left"));
            Map<String, Object> legacyRight = castToMap(breakouts.get("upper_extremity_pattern_right"));
            Map<String, Object> legacyCombined = castToMap(breakouts.get(UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY));
            if ((legacyLeft == null || legacyLeft.isEmpty()) && legacyCombined != null && !legacyCombined.isEmpty()) {
                legacyLeft = legacyCombined;
            }
            if ((legacyRight == null || legacyRight.isEmpty()) && legacyCombined != null && !legacyCombined.isEmpty()) {
                legacyRight = legacyCombined;
            }
            dedicatedUpperExtremityPattern2Breakout = mapLegacyBreakoutToDedicatedUpperExtremityPattern2(legacyLeft, legacyRight);
        } else {
            dedicatedUpperExtremityPattern2Breakout = normalizeDedicatedUpperExtremityPattern2Breakout(dedicatedUpperExtremityPattern2Breakout);
        }
        payload.put("upper_extremity_pattern2_breakout", dedicatedUpperExtremityPattern2Breakout);
        Map<String, Object> upperExtremityPattern2Legacy = mapDedicatedUpperExtremityPattern2BreakoutToLegacy(dedicatedUpperExtremityPattern2Breakout);
        breakouts.put(UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY, upperExtremityPattern2Legacy);
        breakouts.put("upper_extremity_pattern_left",
                mapDedicatedUpperExtremityPattern2BreakoutSideToLegacy(
                        castToMap(dedicatedUpperExtremityPattern2Breakout.get("left"))));
        breakouts.put("upper_extremity_pattern_right",
                mapDedicatedUpperExtremityPattern2BreakoutSideToLegacy(
                        castToMap(dedicatedUpperExtremityPattern2Breakout.get("right"))));

        Map<String, Object> dedicatedMsfBreakout = castToMap(payload.get(MSF_BREAKOUT_KEY));
        if (dedicatedMsfBreakout == null || dedicatedMsfBreakout.isEmpty()) {
            Map<String, Object> legacyMsfBreakout = castToMap(breakouts.get(MSF_BREAKOUT_KEY));
            if (legacyMsfBreakout == null || legacyMsfBreakout.isEmpty()) {
                legacyMsfBreakout = castToMap(breakouts.get("msf"));
            }
            dedicatedMsfBreakout = mapLegacyBreakoutToDedicatedMsf(legacyMsfBreakout);
        } else {
            dedicatedMsfBreakout = normalizeDedicatedMsfBreakout(dedicatedMsfBreakout);
        }
        payload.put(MSF_BREAKOUT_KEY, dedicatedMsfBreakout);
        Map<String, Object> msfLegacy = mapDedicatedMsfBreakoutToLegacy(dedicatedMsfBreakout);
        breakouts.put(MSF_BREAKOUT_KEY, msfLegacy);
        breakouts.put("msf", new LinkedHashMap<>(msfLegacy));

        Map<String, Object> dedicatedMseBreakout = castToMap(payload.get(MSE_BREAKOUT_KEY));
        if (dedicatedMseBreakout == null || dedicatedMseBreakout.isEmpty()) {
            Map<String, Object> legacyMseBreakout = castToMap(breakouts.get(MSE_BREAKOUT_KEY));
            if (legacyMseBreakout == null || legacyMseBreakout.isEmpty()) {
                legacyMseBreakout = castToMap(breakouts.get("mse"));
            }
            dedicatedMseBreakout = mapLegacyBreakoutToDedicatedMse(legacyMseBreakout);
        } else {
            dedicatedMseBreakout = normalizeDedicatedMseBreakout(dedicatedMseBreakout);
        }
        payload.put(MSE_BREAKOUT_KEY, dedicatedMseBreakout);
        Map<String, Object> mseLegacy = mapDedicatedMseBreakoutToLegacy(dedicatedMseBreakout);
        breakouts.put(MSE_BREAKOUT_KEY, mseLegacy);
        breakouts.put("mse", new LinkedHashMap<>(mseLegacy));

        Map<String, Object> dedicatedMsrBreakout = castToMap(payload.get(MSR_BREAKOUT_KEY));
        if (dedicatedMsrBreakout == null || dedicatedMsrBreakout.isEmpty()) {
            Map<String, Object> legacyMsrLeft = castToMap(breakouts.get("msr_left"));
            Map<String, Object> legacyMsrRight = castToMap(breakouts.get("msr_right"));
            Map<String, Object> legacyMsrMerged = castToMap(breakouts.get(MSR_BREAKOUT_KEY));
            dedicatedMsrBreakout = mapLegacyBreakoutToDedicatedMsr(legacyMsrLeft, legacyMsrRight, legacyMsrMerged);
        } else {
            dedicatedMsrBreakout = normalizeDedicatedMsrBreakout(dedicatedMsrBreakout);
        }
        payload.put(MSR_BREAKOUT_KEY, dedicatedMsrBreakout);
        Map<String, Object> msrLeftLegacy = mapDedicatedMsrBreakoutSideToLegacy(castToMap(dedicatedMsrBreakout.get("left")));
        Map<String, Object> msrRightLegacy = mapDedicatedMsrBreakoutSideToLegacy(castToMap(dedicatedMsrBreakout.get("right")));
        Map<String, Object> msrMergedLegacy = mapDedicatedMsrBreakoutToLegacyMerged(dedicatedMsrBreakout);
        breakouts.put("msr_left", msrLeftLegacy);
        breakouts.put("msr_right", msrRightLegacy);
        breakouts.put(MSR_BREAKOUT_KEY, msrMergedLegacy);
    }

    private void syncDedicatedCervicalFromNormalized(Map<String, Object> payload,
                                                     Map<String, Map<String, Object>> topTierMap,
                                                     Map<String, Map<String, Object>> breakouts,
                                                     List<Map<String, Object>> recommendations) {
        Map<String, Object> legacyTop = topTierMap.get(CERVICAL_FLEXION_TEST_CODE);
        Map<String, Object> dedicatedTop = mapLegacyTopTierToDedicatedCervical(legacyTop);
        boolean accepted = recommendations.stream()
                .anyMatch(item -> StrUtil.equals(toStringValue(item.get("test_code")), CERVICAL_FLEXION_TEST_CODE)
                        && StrUtil.equals(toStringValue(item.get("recommendation_status")), STATUS_ACCEPTED));
        if (accepted && StrUtil.equals(toStringValue(dedicatedTop.get("breakout_target")), CERVICAL_FLEXION_BREAKOUT_KEY)) {
            Map<String, Object> dedicatedBreakout = castToMap(payload.get("cervical_flexion_breakout"));
            if (dedicatedBreakout != null
                    && StrUtil.equals(toStringValue(dedicatedBreakout.get("breakout_status")), BREAKOUT_STATUS_NOT_STARTED)) {
                dedicatedBreakout.put("breakout_status", BREAKOUT_STATUS_PARTIAL);
            }
        }
        payload.put("cervical_flexion_top_tier", dedicatedTop);

        Map<String, Object> dedicatedBreakout = castToMap(payload.get("cervical_flexion_breakout"));
        dedicatedBreakout = normalizeDedicatedCervicalBreakout(dedicatedBreakout);
        payload.put("cervical_flexion_breakout", dedicatedBreakout);
        breakouts.put(CERVICAL_FLEXION_BREAKOUT_KEY, mapDedicatedCervicalBreakoutToLegacy(dedicatedBreakout));

        Map<String, Object> legacyExtensionTop = topTierMap.get(CERVICAL_EXTENSION_TEST_CODE);
        Map<String, Object> dedicatedExtensionTop = mapLegacyTopTierToDedicatedCervicalExtension(legacyExtensionTop);
        boolean extensionAccepted = recommendations.stream()
                .anyMatch(item -> StrUtil.equals(toStringValue(item.get("test_code")), CERVICAL_EXTENSION_TEST_CODE)
                        && StrUtil.equals(toStringValue(item.get("recommendation_status")), STATUS_ACCEPTED));
        if (extensionAccepted
                && StrUtil.equals(toStringValue(dedicatedExtensionTop.get("breakout_target")), CERVICAL_EXTENSION_BREAKOUT_KEY)) {
            Map<String, Object> dedicatedExtensionBreakout = castToMap(payload.get("cervical_extension_breakout"));
            if (dedicatedExtensionBreakout != null
                    && StrUtil.equals(toStringValue(dedicatedExtensionBreakout.get("breakout_status")),
                    BREAKOUT_STATUS_NOT_STARTED)) {
                dedicatedExtensionBreakout.put("breakout_status", BREAKOUT_STATUS_PARTIAL);
            }
        }
        payload.put("cervical_extension_top_tier", dedicatedExtensionTop);

        Map<String, Object> dedicatedExtensionBreakout = castToMap(payload.get("cervical_extension_breakout"));
        dedicatedExtensionBreakout = normalizeDedicatedCervicalExtensionBreakout(dedicatedExtensionBreakout);
        payload.put("cervical_extension_breakout", dedicatedExtensionBreakout);
        breakouts.put(CERVICAL_EXTENSION_BREAKOUT_KEY,
                mapDedicatedCervicalExtensionBreakoutToLegacy(dedicatedExtensionBreakout));

        Map<String, Object> legacyRotationLeftTop = topTierMap.get(CERVICAL_ROTATION_LEFT_TEST_CODE);
        Map<String, Object> legacyRotationRightTop = topTierMap.get(CERVICAL_ROTATION_RIGHT_TEST_CODE);
        Map<String, Object> dedicatedRotationTop = mapLegacyTopTierToDedicatedCervicalRotation(legacyRotationLeftTop, legacyRotationRightTop);
        boolean rotationLeftAccepted = recommendations.stream()
                .anyMatch(item -> StrUtil.equals(toStringValue(item.get("test_code")), CERVICAL_ROTATION_LEFT_TEST_CODE)
                        && StrUtil.equals(toStringValue(item.get("recommendation_status")), STATUS_ACCEPTED));
        boolean rotationRightAccepted = recommendations.stream()
                .anyMatch(item -> StrUtil.equals(toStringValue(item.get("test_code")), CERVICAL_ROTATION_RIGHT_TEST_CODE)
                        && StrUtil.equals(toStringValue(item.get("recommendation_status")), STATUS_ACCEPTED));
        Map<String, Object> dedicatedRotationBreakout = castToMap(payload.get("cervical_rotation_breakout"));
        dedicatedRotationBreakout = normalizeDedicatedCervicalRotationBreakout(dedicatedRotationBreakout);
        Map<String, Object> rotationLeftTop = castToMap(dedicatedRotationTop.get("left"));
        Map<String, Object> rotationRightTop = castToMap(dedicatedRotationTop.get("right"));
        if (rotationLeftAccepted && rotationLeftTop != null && StrUtil.equals(
                toStringValue(rotationLeftTop.get("breakout_target")), CERVICAL_ROTATION_BREAKOUT_KEY)) {
            Map<String, Object> leftBreakout = castToMap(dedicatedRotationBreakout.get("left"));
            if (leftBreakout != null && StrUtil.equals(toStringValue(leftBreakout.get("breakout_status")), BREAKOUT_STATUS_NOT_STARTED)) {
                leftBreakout.put("breakout_status", BREAKOUT_STATUS_PARTIAL);
            }
        }
        if (rotationRightAccepted && rotationRightTop != null && StrUtil.equals(
                toStringValue(rotationRightTop.get("breakout_target")), CERVICAL_ROTATION_BREAKOUT_KEY)) {
            Map<String, Object> rightBreakout = castToMap(dedicatedRotationBreakout.get("right"));
            if (rightBreakout != null && StrUtil.equals(toStringValue(rightBreakout.get("breakout_status")), BREAKOUT_STATUS_NOT_STARTED)) {
                rightBreakout.put("breakout_status", BREAKOUT_STATUS_PARTIAL);
            }
        }
        payload.put("cervical_rotation_top_tier", dedicatedRotationTop);
        payload.put("cervical_rotation_breakout", dedicatedRotationBreakout);
        topTierMap.put(CERVICAL_ROTATION_LEFT_TEST_CODE,
                mapDedicatedCervicalRotationTopTierSideToLegacy(dedicatedRotationTop, CERVICAL_ROTATION_LEFT_TEST_CODE));
        topTierMap.put(CERVICAL_ROTATION_RIGHT_TEST_CODE,
                mapDedicatedCervicalRotationTopTierSideToLegacy(dedicatedRotationTop, CERVICAL_ROTATION_RIGHT_TEST_CODE));
        Map<String, Object> rotationLegacy = mapDedicatedCervicalRotationBreakoutToLegacy(dedicatedRotationBreakout);
        breakouts.put(CERVICAL_ROTATION_BREAKOUT_KEY, rotationLegacy);
        // 兼容旧结构聚合
        breakouts.put("cervical_pattern", new LinkedHashMap<>(rotationLegacy));

        Map<String, Object> legacyUe1LeftTop = topTierMap.get(UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE);
        Map<String, Object> legacyUe1RightTop = topTierMap.get(UPPER_EXTREMITY_PATTERN1_RIGHT_TEST_CODE);
        Map<String, Object> dedicatedUe1Top = mapLegacyTopTierToDedicatedUpperExtremityPattern1(legacyUe1LeftTop, legacyUe1RightTop);
        boolean ue1LeftAccepted = recommendations.stream()
                .anyMatch(item -> StrUtil.equals(toStringValue(item.get("test_code")), UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE)
                        && StrUtil.equals(toStringValue(item.get("recommendation_status")), STATUS_ACCEPTED));
        boolean ue1RightAccepted = recommendations.stream()
                .anyMatch(item -> StrUtil.equals(toStringValue(item.get("test_code")), UPPER_EXTREMITY_PATTERN1_RIGHT_TEST_CODE)
                        && StrUtil.equals(toStringValue(item.get("recommendation_status")), STATUS_ACCEPTED));
        Map<String, Object> dedicatedUe1Breakout = castToMap(payload.get("upper_extremity_pattern1_breakout"));
        dedicatedUe1Breakout = normalizeDedicatedUpperExtremityPattern1Breakout(dedicatedUe1Breakout);
        Map<String, Object> ue1LeftTop = castToMap(dedicatedUe1Top.get("left"));
        Map<String, Object> ue1RightTop = castToMap(dedicatedUe1Top.get("right"));
        if (ue1LeftAccepted && ue1LeftTop != null
                && StrUtil.equals(toStringValue(ue1LeftTop.get("breakout_target")), UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY)) {
            Map<String, Object> leftBreakout = castToMap(dedicatedUe1Breakout.get("left"));
            if (leftBreakout != null && StrUtil.equals(toStringValue(leftBreakout.get("breakout_status")), BREAKOUT_STATUS_NOT_STARTED)) {
                leftBreakout.put("breakout_status", BREAKOUT_STATUS_PARTIAL);
            }
        }
        if (ue1RightAccepted && ue1RightTop != null
                && StrUtil.equals(toStringValue(ue1RightTop.get("breakout_target")), UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY)) {
            Map<String, Object> rightBreakout = castToMap(dedicatedUe1Breakout.get("right"));
            if (rightBreakout != null && StrUtil.equals(toStringValue(rightBreakout.get("breakout_status")), BREAKOUT_STATUS_NOT_STARTED)) {
                rightBreakout.put("breakout_status", BREAKOUT_STATUS_PARTIAL);
            }
        }
        payload.put("upper_extremity_pattern1_top_tier", dedicatedUe1Top);
        payload.put("upper_extremity_pattern1_breakout", dedicatedUe1Breakout);
        topTierMap.put(UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE,
                mapDedicatedUpperExtremityPattern1TopTierSideToLegacy(dedicatedUe1Top, UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE));
        topTierMap.put(UPPER_EXTREMITY_PATTERN1_RIGHT_TEST_CODE,
                mapDedicatedUpperExtremityPattern1TopTierSideToLegacy(dedicatedUe1Top, UPPER_EXTREMITY_PATTERN1_RIGHT_TEST_CODE));
        Map<String, Object> ue1Legacy = mapDedicatedUpperExtremityPattern1BreakoutToLegacy(dedicatedUe1Breakout);
        breakouts.put(UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY, ue1Legacy);

        Map<String, Object> legacyUe2LeftTop = topTierMap.get(UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE);
        Map<String, Object> legacyUe2RightTop = topTierMap.get(UPPER_EXTREMITY_PATTERN2_RIGHT_TEST_CODE);
        Map<String, Object> dedicatedUe2Top = mapLegacyTopTierToDedicatedUpperExtremityPattern2(legacyUe2LeftTop, legacyUe2RightTop);
        boolean ue2LeftAccepted = recommendations.stream()
                .anyMatch(item -> StrUtil.equals(toStringValue(item.get("test_code")), UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE)
                        && StrUtil.equals(toStringValue(item.get("recommendation_status")), STATUS_ACCEPTED));
        boolean ue2RightAccepted = recommendations.stream()
                .anyMatch(item -> StrUtil.equals(toStringValue(item.get("test_code")), UPPER_EXTREMITY_PATTERN2_RIGHT_TEST_CODE)
                        && StrUtil.equals(toStringValue(item.get("recommendation_status")), STATUS_ACCEPTED));
        Map<String, Object> dedicatedUe2Breakout = castToMap(payload.get("upper_extremity_pattern2_breakout"));
        dedicatedUe2Breakout = normalizeDedicatedUpperExtremityPattern2Breakout(dedicatedUe2Breakout);
        Map<String, Object> ue2LeftTop = castToMap(dedicatedUe2Top.get("left"));
        Map<String, Object> ue2RightTop = castToMap(dedicatedUe2Top.get("right"));
        if (ue2LeftAccepted && ue2LeftTop != null
                && StrUtil.equals(toStringValue(ue2LeftTop.get("breakout_target")), UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY)) {
            Map<String, Object> leftBreakout = castToMap(dedicatedUe2Breakout.get("left"));
            if (leftBreakout != null && StrUtil.equals(toStringValue(leftBreakout.get("breakout_status")), BREAKOUT_STATUS_NOT_STARTED)) {
                leftBreakout.put("breakout_status", BREAKOUT_STATUS_PARTIAL);
            }
        }
        if (ue2RightAccepted && ue2RightTop != null
                && StrUtil.equals(toStringValue(ue2RightTop.get("breakout_target")), UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY)) {
            Map<String, Object> rightBreakout = castToMap(dedicatedUe2Breakout.get("right"));
            if (rightBreakout != null && StrUtil.equals(toStringValue(rightBreakout.get("breakout_status")), BREAKOUT_STATUS_NOT_STARTED)) {
                rightBreakout.put("breakout_status", BREAKOUT_STATUS_PARTIAL);
            }
        }
        payload.put("upper_extremity_pattern2_top_tier", dedicatedUe2Top);
        payload.put("upper_extremity_pattern2_breakout", dedicatedUe2Breakout);
        topTierMap.put(UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE,
                mapDedicatedUpperExtremityPattern2TopTierSideToLegacy(dedicatedUe2Top, UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE));
        topTierMap.put(UPPER_EXTREMITY_PATTERN2_RIGHT_TEST_CODE,
                mapDedicatedUpperExtremityPattern2TopTierSideToLegacy(dedicatedUe2Top, UPPER_EXTREMITY_PATTERN2_RIGHT_TEST_CODE));
        Map<String, Object> ue2Legacy = mapDedicatedUpperExtremityPattern2BreakoutToLegacy(dedicatedUe2Breakout);
        breakouts.put(UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY, ue2Legacy);
        breakouts.put("upper_extremity_pattern_left",
                mapDedicatedUpperExtremityPattern2BreakoutSideToLegacy(castToMap(dedicatedUe2Breakout.get("left"))));
        breakouts.put("upper_extremity_pattern_right",
                mapDedicatedUpperExtremityPattern2BreakoutSideToLegacy(castToMap(dedicatedUe2Breakout.get("right"))));

        Map<String, Object> legacyMsfTop = topTierMap.get("multi_segmental_flexion");
        Map<String, Object> dedicatedMsfBreakout = castToMap(payload.get(MSF_BREAKOUT_KEY));
        dedicatedMsfBreakout = normalizeDedicatedMsfBreakout(dedicatedMsfBreakout);
        boolean msfAccepted = recommendations.stream()
                .anyMatch(item -> StrUtil.equals(toStringValue(item.get("test_code")), "multi_segmental_flexion")
                        && StrUtil.equals(toStringValue(item.get("recommendation_status")), STATUS_ACCEPTED));
        boolean msfSkipped = recommendations.stream()
                .anyMatch(item -> StrUtil.equals(toStringValue(item.get("test_code")), "multi_segmental_flexion")
                        && StrUtil.equals(toStringValue(item.get("recommendation_status")), STATUS_SKIPPED));
        if (legacyMsfTop != null && StrUtil.isNotBlank(toStringValue(legacyMsfTop.get("breakout_reason_text")))) {
            dedicatedMsfBreakout.put("breakout_reason_from_top_tier", toStringValue(legacyMsfTop.get("breakout_reason_text")));
        }
        String msfStatus = normalizeDedicatedMsfBreakoutStatus(dedicatedMsfBreakout.get("breakout_status"));
        if (msfAccepted && StrUtil.equals(msfStatus, "not_started")) {
            dedicatedMsfBreakout.put("breakout_status", "in_progress");
        } else if (msfSkipped && StrUtil.equals(msfStatus, "not_started")) {
            dedicatedMsfBreakout.put("breakout_status", "skipped");
        }
        payload.put(MSF_BREAKOUT_KEY, dedicatedMsfBreakout);
        Map<String, Object> msfLegacy = mapDedicatedMsfBreakoutToLegacy(dedicatedMsfBreakout);
        breakouts.put(MSF_BREAKOUT_KEY, msfLegacy);
        breakouts.put("msf", new LinkedHashMap<>(msfLegacy));

        Map<String, Object> legacyMseTop = topTierMap.get("multi_segmental_extension");
        Map<String, Object> dedicatedMseBreakout = castToMap(payload.get(MSE_BREAKOUT_KEY));
        dedicatedMseBreakout = normalizeDedicatedMseBreakout(dedicatedMseBreakout);
        boolean mseAccepted = recommendations.stream()
                .anyMatch(item -> StrUtil.equals(toStringValue(item.get("test_code")), "multi_segmental_extension")
                        && StrUtil.equals(toStringValue(item.get("recommendation_status")), STATUS_ACCEPTED));
        boolean mseSkipped = recommendations.stream()
                .anyMatch(item -> StrUtil.equals(toStringValue(item.get("test_code")), "multi_segmental_extension")
                        && StrUtil.equals(toStringValue(item.get("recommendation_status")), STATUS_SKIPPED));
        if (legacyMseTop != null && StrUtil.isNotBlank(toStringValue(legacyMseTop.get("breakout_reason_text")))) {
            dedicatedMseBreakout.put("breakout_reason_from_top_tier", toStringValue(legacyMseTop.get("breakout_reason_text")));
        }
        String mseStatus = normalizeDedicatedMseBreakoutStatus(dedicatedMseBreakout.get("breakout_status"));
        if (mseAccepted && StrUtil.equals(mseStatus, BREAKOUT_STATUS_NOT_STARTED)) {
            dedicatedMseBreakout.put("breakout_status", "in_progress");
        } else if (mseSkipped && StrUtil.equals(mseStatus, BREAKOUT_STATUS_NOT_STARTED)) {
            dedicatedMseBreakout.put("breakout_status", BREAKOUT_STATUS_SKIPPED);
        }
        payload.put(MSE_BREAKOUT_KEY, dedicatedMseBreakout);
        Map<String, Object> mseLegacy = mapDedicatedMseBreakoutToLegacy(dedicatedMseBreakout);
        breakouts.put(MSE_BREAKOUT_KEY, mseLegacy);
        breakouts.put("mse", new LinkedHashMap<>(mseLegacy));

        Map<String, Object> dedicatedMsrBreakout = castToMap(payload.get(MSR_BREAKOUT_KEY));
        dedicatedMsrBreakout = normalizeDedicatedMsrBreakout(dedicatedMsrBreakout);
        boolean msrLeftAccepted = recommendations.stream()
                .anyMatch(item -> StrUtil.equals(toStringValue(item.get("test_code")), "multi_segmental_rotation_left")
                        && StrUtil.equals(toStringValue(item.get("recommendation_status")), STATUS_ACCEPTED));
        boolean msrRightAccepted = recommendations.stream()
                .anyMatch(item -> StrUtil.equals(toStringValue(item.get("test_code")), "multi_segmental_rotation_right")
                        && StrUtil.equals(toStringValue(item.get("recommendation_status")), STATUS_ACCEPTED));
        boolean msrLeftSkipped = recommendations.stream()
                .anyMatch(item -> StrUtil.equals(toStringValue(item.get("test_code")), "multi_segmental_rotation_left")
                        && StrUtil.equals(toStringValue(item.get("recommendation_status")), STATUS_SKIPPED));
        boolean msrRightSkipped = recommendations.stream()
                .anyMatch(item -> StrUtil.equals(toStringValue(item.get("test_code")), "multi_segmental_rotation_right")
                        && StrUtil.equals(toStringValue(item.get("recommendation_status")), STATUS_SKIPPED));
        Map<String, Object> msrLeft = castToMap(dedicatedMsrBreakout.get("left"));
        Map<String, Object> msrRight = castToMap(dedicatedMsrBreakout.get("right"));
        Map<String, Object> msrLeftTop = topTierMap.get("multi_segmental_rotation_left");
        Map<String, Object> msrRightTop = topTierMap.get("multi_segmental_rotation_right");
        if (msrLeftTop != null && StrUtil.isNotBlank(toStringValue(msrLeftTop.get("breakout_reason_text")))) {
            msrLeft.put("breakout_reason_from_top_tier", toStringValue(msrLeftTop.get("breakout_reason_text")));
        }
        if (msrRightTop != null && StrUtil.isNotBlank(toStringValue(msrRightTop.get("breakout_reason_text")))) {
            msrRight.put("breakout_reason_from_top_tier", toStringValue(msrRightTop.get("breakout_reason_text")));
        }
        String msrLeftStatus = normalizeDedicatedMsrBreakoutStatus(msrLeft.get("breakout_status"));
        String msrRightStatus = normalizeDedicatedMsrBreakoutStatus(msrRight.get("breakout_status"));
        if (msrLeftAccepted && StrUtil.equals(msrLeftStatus, BREAKOUT_STATUS_NOT_STARTED)) {
            msrLeft.put("breakout_status", "in_progress");
        } else if (msrLeftSkipped && StrUtil.equals(msrLeftStatus, BREAKOUT_STATUS_NOT_STARTED)) {
            msrLeft.put("breakout_status", BREAKOUT_STATUS_SKIPPED);
        }
        if (msrRightAccepted && StrUtil.equals(msrRightStatus, BREAKOUT_STATUS_NOT_STARTED)) {
            msrRight.put("breakout_status", "in_progress");
        } else if (msrRightSkipped && StrUtil.equals(msrRightStatus, BREAKOUT_STATUS_NOT_STARTED)) {
            msrRight.put("breakout_status", BREAKOUT_STATUS_SKIPPED);
        }
        dedicatedMsrBreakout.put("left", msrLeft);
        dedicatedMsrBreakout.put("right", msrRight);
        payload.put(MSR_BREAKOUT_KEY, dedicatedMsrBreakout);
        breakouts.put("msr_left", mapDedicatedMsrBreakoutSideToLegacy(msrLeft));
        breakouts.put("msr_right", mapDedicatedMsrBreakoutSideToLegacy(msrRight));
        breakouts.put(MSR_BREAKOUT_KEY, mapDedicatedMsrBreakoutToLegacyMerged(dedicatedMsrBreakout));
    }

    private Map<String, Object> normalizeDedicatedCervicalTopTier(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        String classification = normalizeClassification(input == null ? null : input.get("classification"));
        boolean painPresent = Boolean.TRUE.equals(input == null ? null : input.get("pain_present"));
        String reviewPriority = "medium";
        boolean needsBreakout = false;
        String breakoutReason = "";
        String breakoutTarget = "";
        if (StrUtil.equals(classification, CLASS_FN)) {
            reviewPriority = REVIEW_PRIORITY_LOW;
        } else if (StrUtil.equals(classification, CLASS_FP)) {
            painPresent = true;
            reviewPriority = REVIEW_PRIORITY_HIGH;
            needsBreakout = true;
            breakoutTarget = CERVICAL_FLEXION_BREAKOUT_KEY;
            breakoutReason = "颈椎屈曲为疼痛性功能模式，建议谨慎进入颈椎屈曲分解评估。";
        } else if (StrUtil.equals(classification, CLASS_DN)) {
            reviewPriority = "medium";
            needsBreakout = true;
            breakoutTarget = CERVICAL_FLEXION_BREAKOUT_KEY;
            breakoutReason = "颈椎屈曲存在功能异常，建议进入颈椎屈曲分解评估。";
        } else if (StrUtil.equals(classification, CLASS_DP)) {
            painPresent = true;
            reviewPriority = REVIEW_PRIORITY_HIGH;
            needsBreakout = true;
            breakoutTarget = CERVICAL_FLEXION_BREAKOUT_KEY;
            breakoutReason = "颈椎屈曲存在功能异常并伴疼痛，建议优先人工复核并谨慎进入颈椎屈曲分解评估。";
        } else {
            reviewPriority = "medium";
        }
        if (StrUtil.isNotBlank(toStringValue(input == null ? null : input.get("breakout_reason_text")))) {
            breakoutReason = toStringValue(input.get("breakout_reason_text"));
        }

        result.put("test_code", CERVICAL_FLEXION_TEST_CODE);
        result.put("test_name_zh", "颈椎屈曲");
        result.put("classification", classification);
        result.put("pain_present", painPresent);
        result.put("pain_vas", normalizeNumber(input == null ? null : input.get("pain_vas")));
        result.put("top_tier_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("top_tier_note")), ""));
        result.put("needs_breakout_suggestion", needsBreakout);
        result.put("breakout_target", breakoutTarget);
        result.put("breakout_reason_text", breakoutReason);
        result.put("review_priority", reviewPriority);
        return result;
    }

    private Map<String, Object> mapDedicatedCervicalTopTierToLegacy(Map<String, Object> dedicated) {
        Map<String, Object> normalized = normalizeDedicatedCervicalTopTier(dedicated);
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("test_code", CERVICAL_FLEXION_TEST_CODE);
        legacy.put("test_name_zh", "颈椎屈曲");
        legacy.put("side", "none");
        legacy.put("classification", normalized.get("classification"));
        legacy.put("pain_present", normalized.get("pain_present"));
        legacy.put("movement_quality_note", "");
        legacy.put("key_observation_note", "");
        legacy.put("rom_key_value", "");
        legacy.put("pain_vas", normalized.get("pain_vas"));
        legacy.put("needs_breakout_suggestion", normalized.get("needs_breakout_suggestion"));
        legacy.put("breakout_reason_text", normalized.get("breakout_reason_text"));
        legacy.put("clinician_note", normalized.get("top_tier_note"));
        String reviewPriority = toStringValue(normalized.get("review_priority"));
        legacy.put("review_priority", StrUtil.equals(reviewPriority, "medium") ? REVIEW_PRIORITY_NORMAL : reviewPriority);
        legacy.put("caution_text",
                StrUtil.equalsAny(toStringValue(normalized.get("classification")), CLASS_FP, CLASS_DP)
                        ? "优先疼痛管理/谨慎继续分解"
                        : "");
        return legacy;
    }

    private Map<String, Object> mapLegacyTopTierToDedicatedCervical(Map<String, Object> legacy) {
        Map<String, Object> converted = new LinkedHashMap<>();
        converted.put("classification", normalizeClassification(legacy == null ? null : legacy.get("classification")));
        converted.put("pain_present", Boolean.TRUE.equals(legacy == null ? null : legacy.get("pain_present")));
        converted.put("pain_vas", normalizeNumber(legacy == null ? null : legacy.get("pain_vas")));
        converted.put("top_tier_note", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("clinician_note")), ""));
        converted.put("breakout_reason_text", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("breakout_reason_text")), ""));
        Object reviewPriority = legacy == null ? null : legacy.get("review_priority");
        if (StrUtil.equals(toStringValue(reviewPriority), REVIEW_PRIORITY_NORMAL)) {
            converted.put("review_priority", "medium");
        } else {
            converted.put("review_priority", toStringValue(reviewPriority));
        }
        return normalizeDedicatedCervicalTopTier(converted);
    }

    private Map<String, Object> normalizeDedicatedCervicalExtensionTopTier(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        String classification = normalizeClassification(input == null ? null : input.get("classification"));
        boolean painPresent = Boolean.TRUE.equals(input == null ? null : input.get("pain_present"));
        String reviewPriority = "medium";
        boolean needsBreakout = false;
        String breakoutReason = "";
        String breakoutTarget = "";
        if (StrUtil.equals(classification, CLASS_FN)) {
            reviewPriority = REVIEW_PRIORITY_LOW;
        } else if (StrUtil.equals(classification, CLASS_FP)) {
            painPresent = true;
            reviewPriority = REVIEW_PRIORITY_HIGH;
            needsBreakout = true;
            breakoutTarget = CERVICAL_EXTENSION_BREAKOUT_KEY;
            breakoutReason = "颈椎伸展为疼痛性功能模式，建议谨慎进入颈椎伸展分解评估。";
        } else if (StrUtil.equals(classification, CLASS_DN)) {
            reviewPriority = "medium";
            needsBreakout = true;
            breakoutTarget = CERVICAL_EXTENSION_BREAKOUT_KEY;
            breakoutReason = "颈椎伸展存在功能异常，建议进入颈椎伸展分解评估。";
        } else if (StrUtil.equals(classification, CLASS_DP)) {
            painPresent = true;
            reviewPriority = REVIEW_PRIORITY_HIGH;
            needsBreakout = true;
            breakoutTarget = CERVICAL_EXTENSION_BREAKOUT_KEY;
            breakoutReason = "颈椎伸展存在功能异常并伴疼痛，建议优先人工复核并谨慎进入颈椎伸展分解评估。";
        } else {
            reviewPriority = "medium";
        }
        if (StrUtil.isNotBlank(toStringValue(input == null ? null : input.get("breakout_reason_text")))) {
            breakoutReason = toStringValue(input.get("breakout_reason_text"));
        }

        result.put("test_code", CERVICAL_EXTENSION_TEST_CODE);
        result.put("test_name_zh", "颈椎伸展");
        result.put("classification", classification);
        result.put("pain_present", painPresent);
        result.put("pain_vas", normalizeNumber(input == null ? null : input.get("pain_vas")));
        result.put("top_tier_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("top_tier_note")), ""));
        result.put("needs_breakout_suggestion", needsBreakout);
        result.put("breakout_target", breakoutTarget);
        result.put("breakout_reason_text", breakoutReason);
        result.put("review_priority", reviewPriority);
        return result;
    }

    private Map<String, Object> mapDedicatedCervicalExtensionTopTierToLegacy(Map<String, Object> dedicated) {
        Map<String, Object> normalized = normalizeDedicatedCervicalExtensionTopTier(dedicated);
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("test_code", CERVICAL_EXTENSION_TEST_CODE);
        legacy.put("test_name_zh", "颈椎伸展");
        legacy.put("side", "none");
        legacy.put("classification", normalized.get("classification"));
        legacy.put("pain_present", normalized.get("pain_present"));
        legacy.put("movement_quality_note", "");
        legacy.put("key_observation_note", "");
        legacy.put("rom_key_value", "");
        legacy.put("pain_vas", normalized.get("pain_vas"));
        legacy.put("needs_breakout_suggestion", normalized.get("needs_breakout_suggestion"));
        legacy.put("breakout_reason_text", normalized.get("breakout_reason_text"));
        legacy.put("clinician_note", normalized.get("top_tier_note"));
        String reviewPriority = toStringValue(normalized.get("review_priority"));
        legacy.put("review_priority", StrUtil.equals(reviewPriority, "medium") ? REVIEW_PRIORITY_NORMAL : reviewPriority);
        legacy.put("caution_text",
                StrUtil.equalsAny(toStringValue(normalized.get("classification")), CLASS_FP, CLASS_DP)
                        ? "优先疼痛管理/谨慎继续分解"
                        : "");
        return legacy;
    }

    private Map<String, Object> mapLegacyTopTierToDedicatedCervicalExtension(Map<String, Object> legacy) {
        Map<String, Object> converted = new LinkedHashMap<>();
        converted.put("classification", normalizeClassification(legacy == null ? null : legacy.get("classification")));
        converted.put("pain_present", Boolean.TRUE.equals(legacy == null ? null : legacy.get("pain_present")));
        converted.put("pain_vas", normalizeNumber(legacy == null ? null : legacy.get("pain_vas")));
        converted.put("top_tier_note", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("clinician_note")), ""));
        converted.put("breakout_reason_text",
                StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("breakout_reason_text")), ""));
        Object reviewPriority = legacy == null ? null : legacy.get("review_priority");
        if (StrUtil.equals(toStringValue(reviewPriority), REVIEW_PRIORITY_NORMAL)) {
            converted.put("review_priority", "medium");
        } else {
            converted.put("review_priority", toStringValue(reviewPriority));
        }
        return normalizeDedicatedCervicalExtensionTopTier(converted);
    }

    private Map<String, Object> normalizeDedicatedCervicalRotationTopTier(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("left", normalizeDedicatedCervicalRotationTopTierSide(
                castToMap(input == null ? null : input.get("left")), CERVICAL_ROTATION_LEFT_TEST_CODE));
        result.put("right", normalizeDedicatedCervicalRotationTopTierSide(
                castToMap(input == null ? null : input.get("right")), CERVICAL_ROTATION_RIGHT_TEST_CODE));
        return result;
    }

    private Map<String, Object> normalizeDedicatedCervicalRotationTopTierSide(Map<String, Object> input, String testCode) {
        String classification = normalizeClassification(input == null ? null : input.get("classification"));
        boolean painPresent = Boolean.TRUE.equals(input == null ? null : input.get("pain_present"));
        String reviewPriority = "medium";
        boolean needsBreakout = false;
        String breakoutReason = "";
        String breakoutTarget = "";
        String action = StrUtil.equals(testCode, CERVICAL_ROTATION_LEFT_TEST_CODE) ? "颈椎左旋转" : "颈椎右旋转";
        if (StrUtil.equals(classification, CLASS_FN)) {
            reviewPriority = REVIEW_PRIORITY_LOW;
        } else if (StrUtil.equals(classification, CLASS_FP)) {
            painPresent = true;
            reviewPriority = REVIEW_PRIORITY_HIGH;
            needsBreakout = true;
            breakoutTarget = CERVICAL_ROTATION_BREAKOUT_KEY;
            breakoutReason = action + "为疼痛性功能模式，建议谨慎进入颈椎旋转分解评估。";
        } else if (StrUtil.equals(classification, CLASS_DN)) {
            reviewPriority = "medium";
            needsBreakout = true;
            breakoutTarget = CERVICAL_ROTATION_BREAKOUT_KEY;
            breakoutReason = action + "存在功能异常，建议进入颈椎旋转分解评估。";
        } else if (StrUtil.equals(classification, CLASS_DP)) {
            painPresent = true;
            reviewPriority = REVIEW_PRIORITY_HIGH;
            needsBreakout = true;
            breakoutTarget = CERVICAL_ROTATION_BREAKOUT_KEY;
            breakoutReason = action + "存在功能异常并伴疼痛，建议优先人工复核并谨慎进入颈椎旋转分解评估。";
        }
        if (StrUtil.isNotBlank(toStringValue(input == null ? null : input.get("breakout_reason_text")))) {
            breakoutReason = toStringValue(input.get("breakout_reason_text"));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("test_code", testCode);
        result.put("test_name_zh", StrUtil.equals(testCode, CERVICAL_ROTATION_LEFT_TEST_CODE) ? "颈椎旋转（左）" : "颈椎旋转（右）");
        result.put("side", StrUtil.equals(testCode, CERVICAL_ROTATION_LEFT_TEST_CODE) ? "left" : "right");
        result.put("classification", classification);
        result.put("pain_present", painPresent);
        result.put("pain_vas", normalizeNumber(input == null ? null : input.get("pain_vas")));
        result.put("top_tier_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("top_tier_note")), ""));
        result.put("needs_breakout_suggestion", needsBreakout);
        result.put("breakout_target", breakoutTarget);
        result.put("breakout_reason_text", breakoutReason);
        result.put("review_priority", reviewPriority);
        return result;
    }

    private Map<String, Object> mapLegacyTopTierToDedicatedCervicalRotation(Map<String, Object> leftLegacy, Map<String, Object> rightLegacy) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> left = new LinkedHashMap<>();
        left.put("classification", normalizeClassification(leftLegacy == null ? null : leftLegacy.get("classification")));
        left.put("pain_present", Boolean.TRUE.equals(leftLegacy == null ? null : leftLegacy.get("pain_present")));
        left.put("pain_vas", normalizeNumber(leftLegacy == null ? null : leftLegacy.get("pain_vas")));
        left.put("top_tier_note", StrUtil.blankToDefault(toStringValue(leftLegacy == null ? null : leftLegacy.get("clinician_note")), ""));
        left.put("breakout_reason_text", StrUtil.blankToDefault(toStringValue(leftLegacy == null ? null : leftLegacy.get("breakout_reason_text")), ""));
        Object leftReviewPriority = leftLegacy == null ? null : leftLegacy.get("review_priority");
        left.put("review_priority", StrUtil.equals(toStringValue(leftReviewPriority), REVIEW_PRIORITY_NORMAL) ? "medium" : toStringValue(leftReviewPriority));

        Map<String, Object> right = new LinkedHashMap<>();
        right.put("classification", normalizeClassification(rightLegacy == null ? null : rightLegacy.get("classification")));
        right.put("pain_present", Boolean.TRUE.equals(rightLegacy == null ? null : rightLegacy.get("pain_present")));
        right.put("pain_vas", normalizeNumber(rightLegacy == null ? null : rightLegacy.get("pain_vas")));
        right.put("top_tier_note", StrUtil.blankToDefault(toStringValue(rightLegacy == null ? null : rightLegacy.get("clinician_note")), ""));
        right.put("breakout_reason_text", StrUtil.blankToDefault(toStringValue(rightLegacy == null ? null : rightLegacy.get("breakout_reason_text")), ""));
        Object rightReviewPriority = rightLegacy == null ? null : rightLegacy.get("review_priority");
        right.put("review_priority", StrUtil.equals(toStringValue(rightReviewPriority), REVIEW_PRIORITY_NORMAL) ? "medium" : toStringValue(rightReviewPriority));

        result.put("left", normalizeDedicatedCervicalRotationTopTierSide(left, CERVICAL_ROTATION_LEFT_TEST_CODE));
        result.put("right", normalizeDedicatedCervicalRotationTopTierSide(right, CERVICAL_ROTATION_RIGHT_TEST_CODE));
        return result;
    }

    private Map<String, Object> mapDedicatedCervicalRotationTopTierSideToLegacy(Map<String, Object> dedicatedTop, String testCode) {
        Map<String, Object> side = castToMap(dedicatedTop == null ? null : dedicatedTop.get(
                StrUtil.equals(testCode, CERVICAL_ROTATION_LEFT_TEST_CODE) ? "left" : "right"));
        Map<String, Object> normalized = normalizeDedicatedCervicalRotationTopTierSide(side, testCode);
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("test_code", testCode);
        legacy.put("test_name_zh", normalized.get("test_name_zh"));
        legacy.put("side", normalized.get("side"));
        legacy.put("classification", normalized.get("classification"));
        legacy.put("pain_present", normalized.get("pain_present"));
        legacy.put("movement_quality_note", "");
        legacy.put("key_observation_note", "");
        legacy.put("rom_key_value", "");
        legacy.put("pain_vas", normalized.get("pain_vas"));
        legacy.put("needs_breakout_suggestion", normalized.get("needs_breakout_suggestion"));
        legacy.put("breakout_reason_text", normalized.get("breakout_reason_text"));
        legacy.put("clinician_note", normalized.get("top_tier_note"));
        String reviewPriority = toStringValue(normalized.get("review_priority"));
        legacy.put("review_priority", StrUtil.equals(reviewPriority, "medium") ? REVIEW_PRIORITY_NORMAL : reviewPriority);
        legacy.put("caution_text",
                StrUtil.equalsAny(toStringValue(normalized.get("classification")), CLASS_FP, CLASS_DP)
                        ? "优先疼痛管理/谨慎继续分解"
                        : "");
        return legacy;
    }

    private Map<String, Object> normalizeDedicatedCervicalRotationBreakout(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("left", normalizeDedicatedCervicalRotationBreakoutSide(castToMap(input == null ? null : input.get("left"))));
        result.put("right", normalizeDedicatedCervicalRotationBreakoutSide(castToMap(input == null ? null : input.get("right"))));
        result.put("asymmetry_focus", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("asymmetry_focus")), ""));
        result.put("overall_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("overall_note")), ""));
        return result;
    }

    private Map<String, Object> normalizeDedicatedCervicalRotationBreakoutSide(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("breakout_status", normalizeBreakoutStatus(input == null ? null : input.get("breakout_status")));
        result.put("breakout_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_note")), ""));
        result.put("active_cervical_rotation_quality", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("active_cervical_rotation_quality")), ""));
        result.put("active_cervical_rotation_pain", Boolean.TRUE.equals(input == null ? null : input.get("active_cervical_rotation_pain")));
        result.put("active_cervical_rotation_rom_key", normalizeNumber(input == null ? null : input.get("active_cervical_rotation_rom_key")));
        result.put("passive_cervical_rotation_quality", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("passive_cervical_rotation_quality")), ""));
        result.put("passive_cervical_rotation_pain", Boolean.TRUE.equals(input == null ? null : input.get("passive_cervical_rotation_pain")));
        result.put("passive_cervical_rotation_rom_key", normalizeNumber(input == null ? null : input.get("passive_cervical_rotation_rom_key")));
        result.put("passive_vs_active_difference", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("passive_vs_active_difference")), ""));
        result.put("upper_cervical_rotation_observation", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("upper_cervical_rotation_observation")), ""));
        result.put("upper_cervical_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("upper_cervical_note")), ""));
        result.put("compensation_patterns", castStringList(input == null ? null : input.get("compensation_patterns")));
        result.put("compensation_other_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("compensation_other_note")), ""));
        result.put("related_region_influence", castStringList(input == null ? null : input.get("related_region_influence")));
        result.put("breakout_preliminary_direction", castStringList(input == null ? null : input.get("breakout_preliminary_direction")));
        result.put("breakout_summary_text", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_summary_text")), ""));
        boolean needsManualReview = Boolean.TRUE.equals(input == null ? null : input.get("needs_manual_review"))
                || Boolean.TRUE.equals(input == null ? null : input.get("active_cervical_rotation_pain"))
                || Boolean.TRUE.equals(input == null ? null : input.get("passive_cervical_rotation_pain"))
                || StrUtil.equals(toStringValue(input == null ? null : input.get("breakout_status")), BREAKOUT_STATUS_STOPPED_PAIN);
        result.put("needs_manual_review", needsManualReview);
        return result;
    }

    private Map<String, Object> mapLegacyBreakoutToDedicatedCervicalRotation(Map<String, Object> legacy) {
        Map<String, Object> side = new LinkedHashMap<>();
        side.put("breakout_status", normalizeBreakoutStatus(legacy == null ? null : legacy.get("status")));
        side.put("breakout_note", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("clinician_note")), ""));
        side.put("active_cervical_rotation_quality", "");
        side.put("active_cervical_rotation_pain", Boolean.TRUE.equals(legacy == null ? null : legacy.get("pain_present")));
        side.put("active_cervical_rotation_rom_key", null);
        side.put("passive_cervical_rotation_quality", "");
        side.put("passive_cervical_rotation_pain", false);
        side.put("passive_cervical_rotation_rom_key", null);
        side.put("passive_vs_active_difference", "");
        side.put("upper_cervical_rotation_observation", "");
        side.put("upper_cervical_note", "");
        side.put("compensation_patterns", Collections.emptyList());
        side.put("compensation_other_note", "");
        side.put("related_region_influence", Collections.emptyList());
        side.put("breakout_preliminary_direction", inferBreakoutDirection(legacy == null ? Collections.emptyMap() : legacy));
        side.put("breakout_summary_text", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("findings")), ""));
        side.put("needs_manual_review", Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("left", normalizeDedicatedCervicalRotationBreakoutSide(side));
        result.put("right", normalizeDedicatedCervicalRotationBreakoutSide(side));
        result.put("asymmetry_focus", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("asymmetry_signs")), ""));
        result.put("overall_note", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("clinician_note")), ""));
        return normalizeDedicatedCervicalRotationBreakout(result);
    }

    private Map<String, Object> mapDedicatedCervicalRotationBreakoutToLegacy(Map<String, Object> dedicated) {
        Map<String, Object> normalized = normalizeDedicatedCervicalRotationBreakout(dedicated);
        Map<String, Object> left = castToMap(normalized.get("left"));
        Map<String, Object> right = castToMap(normalized.get("right"));
        String status = mergeRotationStatus(
                normalizeBreakoutStatus(left == null ? null : left.get("breakout_status")),
                normalizeBreakoutStatus(right == null ? null : right.get("breakout_status")));
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("status", status);
        legacy.put("findings", distinct(Arrays.asList(
                toStringValue(left == null ? null : left.get("breakout_summary_text")),
                toStringValue(right == null ? null : right.get("breakout_summary_text")),
                toStringValue(normalized.get("overall_note"))
        )).stream().filter(StrUtil::isNotBlank).collect(Collectors.joining("；")));
        legacy.put("rom_key_values", buildCervicalRotationRomText(left, right));
        legacy.put("pain_present",
                Boolean.TRUE.equals(left == null ? null : left.get("active_cervical_rotation_pain"))
                        || Boolean.TRUE.equals(left == null ? null : left.get("passive_cervical_rotation_pain"))
                        || Boolean.TRUE.equals(right == null ? null : right.get("active_cervical_rotation_pain"))
                        || Boolean.TRUE.equals(right == null ? null : right.get("passive_cervical_rotation_pain")));
        legacy.put("pain_vas", null);
        legacy.put("mobility_restriction_signs",
                castStringList(left == null ? null : left.get("breakout_preliminary_direction")).contains("更偏活动度限制")
                        || castStringList(right == null ? null : right.get("breakout_preliminary_direction")).contains("更偏活动度限制")
                        ? "更偏活动度限制" : "");
        legacy.put("motor_control_signs",
                castStringList(left == null ? null : left.get("breakout_preliminary_direction")).contains("更偏运动控制问题")
                        || castStringList(right == null ? null : right.get("breakout_preliminary_direction")).contains("更偏运动控制问题")
                        ? "更偏运动控制问题" : "");
        legacy.put("asymmetry_signs", toStringValue(normalized.get("asymmetry_focus")));
        legacy.put("stop_due_to_pain",
                StrUtil.equals(status, BREAKOUT_STATUS_STOPPED_PAIN)
                        || Boolean.TRUE.equals(left == null ? null : left.get("needs_manual_review"))
                        || Boolean.TRUE.equals(right == null ? null : right.get("needs_manual_review")));
        legacy.put("stop_reason", distinct(Arrays.asList(
                toStringValue(left == null ? null : left.get("breakout_note")),
                toStringValue(right == null ? null : right.get("breakout_note"))
        )).stream().filter(StrUtil::isNotBlank).collect(Collectors.joining("；")));
        legacy.put("clinician_note", toStringValue(normalized.get("overall_note")));
        legacy.put("method", "");
        legacy.put("scale", "");
        legacy.put("source_id", "");
        legacy.put("date", "");
        legacy.put("sls_time_sec", null);
        return legacy;
    }

    private String mergeRotationStatus(String leftStatus, String rightStatus) {
        if (StrUtil.equalsAny(leftStatus, BREAKOUT_STATUS_STOPPED_PAIN)
                || StrUtil.equalsAny(rightStatus, BREAKOUT_STATUS_STOPPED_PAIN)) {
            return BREAKOUT_STATUS_STOPPED_PAIN;
        }
        if (StrUtil.equals(leftStatus, BREAKOUT_STATUS_COMPLETED) && StrUtil.equals(rightStatus, BREAKOUT_STATUS_COMPLETED)) {
            return BREAKOUT_STATUS_COMPLETED;
        }
        if (StrUtil.equals(leftStatus, BREAKOUT_STATUS_SKIPPED) && StrUtil.equals(rightStatus, BREAKOUT_STATUS_SKIPPED)) {
            return BREAKOUT_STATUS_SKIPPED;
        }
        if (StrUtil.equalsAny(leftStatus, BREAKOUT_STATUS_PARTIAL, BREAKOUT_STATUS_COMPLETED)
                || StrUtil.equalsAny(rightStatus, BREAKOUT_STATUS_PARTIAL, BREAKOUT_STATUS_COMPLETED)) {
            return BREAKOUT_STATUS_PARTIAL;
        }
        if (StrUtil.equalsAny(leftStatus, BREAKOUT_STATUS_SKIPPED) || StrUtil.equalsAny(rightStatus, BREAKOUT_STATUS_SKIPPED)) {
            return BREAKOUT_STATUS_SKIPPED;
        }
        return BREAKOUT_STATUS_NOT_STARTED;
    }

    private String buildCervicalRotationRomText(Map<String, Object> left, Map<String, Object> right) {
        List<String> tokens = new ArrayList<>();
        Number leftActive = normalizeNumber(left == null ? null : left.get("active_cervical_rotation_rom_key"));
        Number leftPassive = normalizeNumber(left == null ? null : left.get("passive_cervical_rotation_rom_key"));
        Number rightActive = normalizeNumber(right == null ? null : right.get("active_cervical_rotation_rom_key"));
        Number rightPassive = normalizeNumber(right == null ? null : right.get("passive_cervical_rotation_rom_key"));
        if (leftActive != null) {
            tokens.add("左主动ROM:" + leftActive);
        }
        if (leftPassive != null) {
            tokens.add("左被动ROM:" + leftPassive);
        }
        if (rightActive != null) {
            tokens.add("右主动ROM:" + rightActive);
        }
        if (rightPassive != null) {
            tokens.add("右被动ROM:" + rightPassive);
        }
        return String.join(" | ", tokens);
    }

    private Map<String, Object> normalizeDedicatedUpperExtremityPattern1TopTier(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("left", normalizeDedicatedUpperExtremityPattern1TopTierSide(
                castToMap(input == null ? null : input.get("left")), UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE));
        result.put("right", normalizeDedicatedUpperExtremityPattern1TopTierSide(
                castToMap(input == null ? null : input.get("right")), UPPER_EXTREMITY_PATTERN1_RIGHT_TEST_CODE));
        return result;
    }

    private Map<String, Object> normalizeDedicatedUpperExtremityPattern1TopTierSide(Map<String, Object> input, String testCode) {
        String classification = normalizeClassification(input == null ? null : input.get("classification"));
        boolean painPresent = Boolean.TRUE.equals(input == null ? null : input.get("pain_present"));
        String reviewPriority = "medium";
        boolean needsBreakout = false;
        String breakoutReason = "";
        String breakoutTarget = "";
        String action = StrUtil.equals(testCode, UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE) ? "上肢模式1（左）" : "上肢模式1（右）";
        if (StrUtil.equals(classification, CLASS_FN)) {
            reviewPriority = REVIEW_PRIORITY_LOW;
        } else if (StrUtil.equals(classification, CLASS_FP)) {
            painPresent = true;
            reviewPriority = REVIEW_PRIORITY_HIGH;
            needsBreakout = true;
            breakoutTarget = UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY;
            breakoutReason = action + "为疼痛性功能模式，建议谨慎进入上肢模式1分解评估。";
        } else if (StrUtil.equals(classification, CLASS_DN)) {
            reviewPriority = "medium";
            needsBreakout = true;
            breakoutTarget = UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY;
            breakoutReason = action + "存在功能异常，建议进入上肢模式1分解评估。";
        } else if (StrUtil.equals(classification, CLASS_DP)) {
            painPresent = true;
            reviewPriority = REVIEW_PRIORITY_HIGH;
            needsBreakout = true;
            breakoutTarget = UPPER_EXTREMITY_PATTERN1_BREAKOUT_KEY;
            breakoutReason = action + "存在功能异常并伴疼痛，建议优先人工复核并谨慎进入上肢模式1分解评估。";
        }
        if (StrUtil.isNotBlank(toStringValue(input == null ? null : input.get("breakout_reason_text")))) {
            breakoutReason = toStringValue(input.get("breakout_reason_text"));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("test_code", testCode);
        result.put("test_name_zh", StrUtil.equals(testCode, UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE) ? "上肢模式1（左）" : "上肢模式1（右）");
        result.put("side", StrUtil.equals(testCode, UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE) ? "left" : "right");
        result.put("classification", classification);
        result.put("pain_present", painPresent);
        result.put("pain_vas", normalizeNumber(input == null ? null : input.get("pain_vas")));
        result.put("top_tier_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("top_tier_note")), ""));
        result.put("needs_breakout_suggestion", needsBreakout);
        result.put("breakout_target", breakoutTarget);
        result.put("breakout_reason_text", breakoutReason);
        result.put("review_priority", reviewPriority);
        return result;
    }

    private Map<String, Object> mapLegacyTopTierToDedicatedUpperExtremityPattern1(Map<String, Object> leftLegacy,
                                                                                    Map<String, Object> rightLegacy) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> left = new LinkedHashMap<>();
        left.put("classification", normalizeClassification(leftLegacy == null ? null : leftLegacy.get("classification")));
        left.put("pain_present", Boolean.TRUE.equals(leftLegacy == null ? null : leftLegacy.get("pain_present")));
        left.put("pain_vas", normalizeNumber(leftLegacy == null ? null : leftLegacy.get("pain_vas")));
        left.put("top_tier_note", StrUtil.blankToDefault(toStringValue(leftLegacy == null ? null : leftLegacy.get("clinician_note")), ""));
        left.put("breakout_reason_text", StrUtil.blankToDefault(toStringValue(leftLegacy == null ? null : leftLegacy.get("breakout_reason_text")), ""));
        Object leftReviewPriority = leftLegacy == null ? null : leftLegacy.get("review_priority");
        left.put("review_priority", StrUtil.equals(toStringValue(leftReviewPriority), REVIEW_PRIORITY_NORMAL) ? "medium" : toStringValue(leftReviewPriority));

        Map<String, Object> right = new LinkedHashMap<>();
        right.put("classification", normalizeClassification(rightLegacy == null ? null : rightLegacy.get("classification")));
        right.put("pain_present", Boolean.TRUE.equals(rightLegacy == null ? null : rightLegacy.get("pain_present")));
        right.put("pain_vas", normalizeNumber(rightLegacy == null ? null : rightLegacy.get("pain_vas")));
        right.put("top_tier_note", StrUtil.blankToDefault(toStringValue(rightLegacy == null ? null : rightLegacy.get("clinician_note")), ""));
        right.put("breakout_reason_text", StrUtil.blankToDefault(toStringValue(rightLegacy == null ? null : rightLegacy.get("breakout_reason_text")), ""));
        Object rightReviewPriority = rightLegacy == null ? null : rightLegacy.get("review_priority");
        right.put("review_priority", StrUtil.equals(toStringValue(rightReviewPriority), REVIEW_PRIORITY_NORMAL) ? "medium" : toStringValue(rightReviewPriority));

        result.put("left", normalizeDedicatedUpperExtremityPattern1TopTierSide(left, UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE));
        result.put("right", normalizeDedicatedUpperExtremityPattern1TopTierSide(right, UPPER_EXTREMITY_PATTERN1_RIGHT_TEST_CODE));
        return result;
    }

    private Map<String, Object> mapDedicatedUpperExtremityPattern1TopTierSideToLegacy(Map<String, Object> dedicatedTop, String testCode) {
        Map<String, Object> side = castToMap(dedicatedTop == null ? null : dedicatedTop.get(
                StrUtil.equals(testCode, UPPER_EXTREMITY_PATTERN1_LEFT_TEST_CODE) ? "left" : "right"));
        Map<String, Object> normalized = normalizeDedicatedUpperExtremityPattern1TopTierSide(side, testCode);
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("test_code", testCode);
        legacy.put("test_name_zh", normalized.get("test_name_zh"));
        legacy.put("side", normalized.get("side"));
        legacy.put("classification", normalized.get("classification"));
        legacy.put("pain_present", normalized.get("pain_present"));
        legacy.put("movement_quality_note", "");
        legacy.put("key_observation_note", "");
        legacy.put("rom_key_value", "");
        legacy.put("pain_vas", normalized.get("pain_vas"));
        legacy.put("needs_breakout_suggestion", normalized.get("needs_breakout_suggestion"));
        legacy.put("breakout_reason_text", normalized.get("breakout_reason_text"));
        legacy.put("clinician_note", normalized.get("top_tier_note"));
        String reviewPriority = toStringValue(normalized.get("review_priority"));
        legacy.put("review_priority", StrUtil.equals(reviewPriority, "medium") ? REVIEW_PRIORITY_NORMAL : reviewPriority);
        legacy.put("caution_text",
                StrUtil.equalsAny(toStringValue(normalized.get("classification")), CLASS_FP, CLASS_DP)
                        ? "优先疼痛管理/谨慎继续分解"
                        : "");
        return legacy;
    }

    private Map<String, Object> normalizeDedicatedUpperExtremityPattern1Breakout(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("left", normalizeDedicatedUpperExtremityPattern1BreakoutSide(castToMap(input == null ? null : input.get("left"))));
        result.put("right", normalizeDedicatedUpperExtremityPattern1BreakoutSide(castToMap(input == null ? null : input.get("right"))));
        result.put("asymmetry_focus", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("asymmetry_focus")), ""));
        result.put("overall_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("overall_note")), ""));
        return result;
    }

    private Map<String, Object> normalizeDedicatedUpperExtremityPattern1BreakoutSide(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("breakout_status", normalizeBreakoutStatus(input == null ? null : input.get("breakout_status")));
        result.put("breakout_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_note")), ""));
        result.put("prone_active_result", normalizeClassification(input == null ? null : input.get("prone_active_result")));
        result.put("prone_active_pain_vas", normalizeNumber(input == null ? null : input.get("prone_active_pain_vas")));
        result.put("prone_active_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("prone_active_note")), ""));
        result.put("prone_passive_result", normalizeClassification(input == null ? null : input.get("prone_passive_result")));
        result.put("prone_passive_pain_vas", normalizeNumber(input == null ? null : input.get("prone_passive_pain_vas")));
        result.put("prone_passive_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("prone_passive_note")), ""));
        result.put("supine_interactive_result", normalizeClassification(input == null ? null : input.get("supine_interactive_result")));
        result.put("supine_interactive_pain_vas", normalizeNumber(input == null ? null : input.get("supine_interactive_pain_vas")));
        result.put("supine_interactive_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("supine_interactive_note")), ""));
        result.put("flow_recommendation_text", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("flow_recommendation_text")), ""));
        result.put("local_biomechanics_needed", Boolean.TRUE.equals(input == null ? null : input.get("local_biomechanics_needed")));
        result.put("stop_and_treat", Boolean.TRUE.equals(input == null ? null : input.get("stop_and_treat")));
        result.put("active_ue_pattern1_quality", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("active_ue_pattern1_quality")), ""));
        result.put("active_ue_pattern1_pain", Boolean.TRUE.equals(input == null ? null : input.get("active_ue_pattern1_pain")));
        result.put("active_ue_pattern1_rom_key", normalizeNumber(input == null ? null : input.get("active_ue_pattern1_rom_key")));
        result.put("scapular_control_observation", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("scapular_control_observation")), ""));
        result.put("thoracic_influence_observation", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("thoracic_influence_observation")), ""));
        result.put("glenohumeral_observation", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("glenohumeral_observation")), ""));
        result.put("compensation_patterns", castStringList(input == null ? null : input.get("compensation_patterns")));
        result.put("compensation_other_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("compensation_other_note")), ""));
        result.put("related_region_influence", castStringList(input == null ? null : input.get("related_region_influence")));
        result.put("breakout_preliminary_direction", castStringList(input == null ? null : input.get("breakout_preliminary_direction")));
        result.put("breakout_summary_text", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_summary_text")), ""));
        boolean needsManualReview = Boolean.TRUE.equals(input == null ? null : input.get("needs_manual_review"))
                || Boolean.TRUE.equals(input == null ? null : input.get("active_ue_pattern1_pain"))
                || StrUtil.equals(toStringValue(input == null ? null : input.get("breakout_status")), BREAKOUT_STATUS_STOPPED_PAIN);
        result.put("needs_manual_review", needsManualReview);
        return result;
    }

    private Map<String, Object> mapLegacyBreakoutToDedicatedUpperExtremityPattern1(Map<String, Object> leftLegacy,
                                                                                    Map<String, Object> rightLegacy) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("left", mapLegacyBreakoutToDedicatedUpperExtremityPattern1Side(leftLegacy));
        result.put("right", mapLegacyBreakoutToDedicatedUpperExtremityPattern1Side(rightLegacy));
        result.put("asymmetry_focus", StrUtil.blankToDefault(
                toStringValue(leftLegacy == null ? null : leftLegacy.get("asymmetry_signs")),
                toStringValue(rightLegacy == null ? null : rightLegacy.get("asymmetry_signs"))));
        result.put("overall_note", StrUtil.blankToDefault(
                toStringValue(leftLegacy == null ? null : leftLegacy.get("clinician_note")),
                toStringValue(rightLegacy == null ? null : rightLegacy.get("clinician_note"))));
        return normalizeDedicatedUpperExtremityPattern1Breakout(result);
    }

    private Map<String, Object> mapLegacyBreakoutToDedicatedUpperExtremityPattern1Side(Map<String, Object> legacy) {
        Map<String, Object> side = new LinkedHashMap<>();
        side.put("breakout_status", normalizeBreakoutStatus(legacy == null ? null : legacy.get("status")));
        side.put("breakout_note", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("clinician_note")), ""));
        side.put("prone_active_result", "");
        side.put("prone_active_pain_vas", normalizeNumber(legacy == null ? null : legacy.get("pain_vas")));
        side.put("prone_active_note", "");
        side.put("prone_passive_result", "");
        side.put("prone_passive_pain_vas", null);
        side.put("prone_passive_note", "");
        side.put("supine_interactive_result", "");
        side.put("supine_interactive_pain_vas", null);
        side.put("supine_interactive_note", "");
        side.put("flow_recommendation_text", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("findings")), ""));
        side.put("local_biomechanics_needed", false);
        side.put("stop_and_treat", Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain")));
        side.put("active_ue_pattern1_quality", "");
        side.put("active_ue_pattern1_pain", Boolean.TRUE.equals(legacy == null ? null : legacy.get("pain_present")));
        side.put("active_ue_pattern1_rom_key", null);
        side.put("scapular_control_observation", "");
        side.put("thoracic_influence_observation", "");
        side.put("glenohumeral_observation", "");
        side.put("compensation_patterns", Collections.emptyList());
        side.put("compensation_other_note", "");
        side.put("related_region_influence", Collections.emptyList());
        side.put("breakout_preliminary_direction", inferBreakoutDirection(legacy == null ? Collections.emptyMap() : legacy));
        side.put("breakout_summary_text", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("findings")), ""));
        side.put("needs_manual_review", Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain")));
        return normalizeDedicatedUpperExtremityPattern1BreakoutSide(side);
    }

    private Map<String, Object> mapDedicatedUpperExtremityPattern1BreakoutToLegacy(Map<String, Object> dedicated) {
        Map<String, Object> normalized = normalizeDedicatedUpperExtremityPattern1Breakout(dedicated);
        Map<String, Object> left = castToMap(normalized.get("left"));
        Map<String, Object> right = castToMap(normalized.get("right"));
        Map<String, Object> legacy = new LinkedHashMap<>();
        String status = mergeRotationStatus(
                normalizeBreakoutStatus(left == null ? null : left.get("breakout_status")),
                normalizeBreakoutStatus(right == null ? null : right.get("breakout_status")));
        legacy.put("status", status);
        legacy.put("findings", distinct(Arrays.asList(
                toStringValue(left == null ? null : left.get("breakout_summary_text")),
                toStringValue(right == null ? null : right.get("breakout_summary_text")),
                toStringValue(left == null ? null : left.get("flow_recommendation_text")),
                toStringValue(right == null ? null : right.get("flow_recommendation_text")),
                toStringValue(normalized.get("overall_note"))
        )).stream().filter(StrUtil::isNotBlank).collect(Collectors.joining("；")));
        legacy.put("rom_key_values", buildUpperExtremityPattern1RomText(left, right));
        legacy.put("pain_present",
                Boolean.TRUE.equals(left == null ? null : left.get("active_ue_pattern1_pain"))
                        || Boolean.TRUE.equals(right == null ? null : right.get("active_ue_pattern1_pain"))
                        || StrUtil.equalsAny(normalizeClassification(left == null ? null : left.get("prone_active_result")), CLASS_FP, CLASS_DP)
                        || StrUtil.equalsAny(normalizeClassification(right == null ? null : right.get("prone_active_result")), CLASS_FP, CLASS_DP)
                        || StrUtil.equalsAny(normalizeClassification(left == null ? null : left.get("prone_passive_result")), CLASS_FP, CLASS_DP)
                        || StrUtil.equalsAny(normalizeClassification(right == null ? null : right.get("prone_passive_result")), CLASS_FP, CLASS_DP)
                        || StrUtil.equalsAny(normalizeClassification(left == null ? null : left.get("supine_interactive_result")), CLASS_FP, CLASS_DP)
                        || StrUtil.equalsAny(normalizeClassification(right == null ? null : right.get("supine_interactive_result")), CLASS_FP, CLASS_DP));
        legacy.put("pain_vas", null);
        legacy.put("mobility_restriction_signs",
                castStringList(left == null ? null : left.get("breakout_preliminary_direction")).contains("更偏活动度限制")
                        || castStringList(right == null ? null : right.get("breakout_preliminary_direction")).contains("更偏活动度限制")
                        || StrUtil.equals(normalizeClassification(left == null ? null : left.get("prone_passive_result")), CLASS_DN)
                        || StrUtil.equals(normalizeClassification(right == null ? null : right.get("prone_passive_result")), CLASS_DN)
                        ? "更偏活动度限制" : "");
        legacy.put("motor_control_signs",
                castStringList(left == null ? null : left.get("breakout_preliminary_direction")).contains("更偏运动控制问题")
                        || castStringList(right == null ? null : right.get("breakout_preliminary_direction")).contains("更偏运动控制问题")
                        || StrUtil.equalsAny(normalizeClassification(left == null ? null : left.get("supine_interactive_result")), CLASS_FN, CLASS_DN)
                        || StrUtil.equalsAny(normalizeClassification(right == null ? null : right.get("supine_interactive_result")), CLASS_FN, CLASS_DN)
                        ? "更偏运动控制问题" : "");
        legacy.put("asymmetry_signs", toStringValue(normalized.get("asymmetry_focus")));
        legacy.put("stop_due_to_pain",
                StrUtil.equals(status, BREAKOUT_STATUS_STOPPED_PAIN)
                        || Boolean.TRUE.equals(left == null ? null : left.get("stop_and_treat"))
                        || Boolean.TRUE.equals(right == null ? null : right.get("stop_and_treat"))
                        || Boolean.TRUE.equals(left == null ? null : left.get("needs_manual_review"))
                        || Boolean.TRUE.equals(right == null ? null : right.get("needs_manual_review")));
        legacy.put("stop_reason", distinct(Arrays.asList(
                toStringValue(left == null ? null : left.get("breakout_note")),
                toStringValue(right == null ? null : right.get("breakout_note"))
        )).stream().filter(StrUtil::isNotBlank).collect(Collectors.joining("；")));
        legacy.put("clinician_note", toStringValue(normalized.get("overall_note")));
        legacy.put("method", "");
        legacy.put("scale", "");
        legacy.put("source_id", "");
        legacy.put("date", "");
        legacy.put("sls_time_sec", null);
        return legacy;
    }

    private Map<String, Object> mapDedicatedUpperExtremityPattern1BreakoutSideToLegacy(Map<String, Object> side) {
        Map<String, Object> normalized = normalizeDedicatedUpperExtremityPattern1BreakoutSide(side);
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("status", normalized.get("breakout_status"));
        legacy.put("findings", StrUtil.blankToDefault(
                toStringValue(normalized.get("breakout_summary_text")),
                toStringValue(normalized.get("breakout_note"))
        ));
        Number rom = normalizeNumber(normalized.get("active_ue_pattern1_rom_key"));
        legacy.put("rom_key_values", rom == null ? "" : "关键ROM:" + rom);
        legacy.put("pain_present", Boolean.TRUE.equals(normalized.get("active_ue_pattern1_pain")));
        if (!Boolean.TRUE.equals(legacy.get("pain_present"))) {
            legacy.put("pain_present",
                    StrUtil.equalsAny(normalizeClassification(normalized.get("prone_active_result")), CLASS_FP, CLASS_DP)
                            || StrUtil.equalsAny(normalizeClassification(normalized.get("prone_passive_result")), CLASS_FP, CLASS_DP)
                            || StrUtil.equalsAny(normalizeClassification(normalized.get("supine_interactive_result")), CLASS_FP, CLASS_DP));
        }
        legacy.put("pain_vas", null);
        legacy.put("mobility_restriction_signs",
                castStringList(normalized.get("breakout_preliminary_direction")).contains("更偏活动度限制")
                        || StrUtil.equals(normalizeClassification(normalized.get("prone_passive_result")), CLASS_DN)
                        ? "更偏活动度限制" : "");
        legacy.put("motor_control_signs",
                castStringList(normalized.get("breakout_preliminary_direction")).contains("更偏运动控制问题")
                        || StrUtil.equalsAny(normalizeClassification(normalized.get("supine_interactive_result")), CLASS_FN, CLASS_DN)
                        ? "更偏运动控制问题" : "");
        legacy.put("asymmetry_signs", "");
        legacy.put("stop_due_to_pain",
                StrUtil.equals(toStringValue(normalized.get("breakout_status")), BREAKOUT_STATUS_STOPPED_PAIN)
                        || Boolean.TRUE.equals(normalized.get("stop_and_treat"))
                        || Boolean.TRUE.equals(normalized.get("needs_manual_review")));
        legacy.put("stop_reason", toStringValue(normalized.get("breakout_note")));
        legacy.put("clinician_note", toStringValue(normalized.get("breakout_note")));
        legacy.put("method", "");
        legacy.put("scale", "");
        legacy.put("source_id", "");
        legacy.put("date", "");
        legacy.put("sls_time_sec", null);
        return legacy;
    }

    private String buildUpperExtremityPattern1RomText(Map<String, Object> left, Map<String, Object> right) {
        List<String> tokens = new ArrayList<>();
        Number leftActive = normalizeNumber(left == null ? null : left.get("active_ue_pattern1_rom_key"));
        Number rightActive = normalizeNumber(right == null ? null : right.get("active_ue_pattern1_rom_key"));
        if (leftActive != null) {
            tokens.add("左关键ROM:" + leftActive);
        }
        if (rightActive != null) {
            tokens.add("右关键ROM:" + rightActive);
        }
        return String.join(" | ", tokens);
    }

    private Map<String, Object> normalizeDedicatedUpperExtremityPattern2TopTier(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("left", normalizeDedicatedUpperExtremityPattern2TopTierSide(
                castToMap(input == null ? null : input.get("left")), UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE));
        result.put("right", normalizeDedicatedUpperExtremityPattern2TopTierSide(
                castToMap(input == null ? null : input.get("right")), UPPER_EXTREMITY_PATTERN2_RIGHT_TEST_CODE));
        return result;
    }

    private Map<String, Object> normalizeDedicatedUpperExtremityPattern2TopTierSide(Map<String, Object> input, String testCode) {
        String classification = normalizeClassification(input == null ? null : input.get("classification"));
        boolean painPresent = Boolean.TRUE.equals(input == null ? null : input.get("pain_present"));
        String reviewPriority = REVIEW_PRIORITY_NORMAL;
        boolean needsBreakout = false;
        String breakoutReason = "";
        String breakoutTarget = "";
        String action = StrUtil.equals(testCode, UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE) ? "上肢模式2（左）" : "上肢模式2（右）";
        if (StrUtil.equals(classification, CLASS_FN)) {
            reviewPriority = REVIEW_PRIORITY_LOW;
        } else if (StrUtil.equals(classification, CLASS_FP)) {
            painPresent = true;
            reviewPriority = REVIEW_PRIORITY_HIGH;
            needsBreakout = true;
            breakoutTarget = UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY;
            breakoutReason = action + "为疼痛性功能模式，建议谨慎进入上肢模式2分解评估。";
        } else if (StrUtil.equals(classification, CLASS_DN)) {
            reviewPriority = REVIEW_PRIORITY_NORMAL;
            needsBreakout = true;
            breakoutTarget = UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY;
            breakoutReason = action + "存在功能异常，建议进入上肢模式2分解评估。";
        } else if (StrUtil.equals(classification, CLASS_DP)) {
            painPresent = true;
            reviewPriority = REVIEW_PRIORITY_HIGH;
            needsBreakout = true;
            breakoutTarget = UPPER_EXTREMITY_PATTERN2_BREAKOUT_KEY;
            breakoutReason = action + "存在功能异常并伴疼痛，建议优先人工复核并谨慎进入上肢模式2分解评估。";
        }
        if (StrUtil.isNotBlank(toStringValue(input == null ? null : input.get("breakout_reason_text")))) {
            breakoutReason = toStringValue(input.get("breakout_reason_text"));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("test_code", testCode);
        result.put("test_name_zh",
                StrUtil.equals(testCode, UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE) ? "上肢模式2（左）" : "上肢模式2（右）");
        result.put("side", StrUtil.equals(testCode, UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE) ? "left" : "right");
        result.put("classification", classification);
        result.put("pain_present", painPresent);
        result.put("pain_vas", normalizeNumber(input == null ? null : input.get("pain_vas")));
        result.put("top_tier_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("top_tier_note")), ""));
        result.put("needs_breakout_suggestion", needsBreakout);
        result.put("breakout_target", breakoutTarget);
        result.put("breakout_reason_text", breakoutReason);
        result.put("review_priority", reviewPriority);
        return result;
    }

    private Map<String, Object> mapLegacyTopTierToDedicatedUpperExtremityPattern2(Map<String, Object> leftLegacy,
                                                                                    Map<String, Object> rightLegacy) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> left = new LinkedHashMap<>();
        left.put("classification", normalizeClassification(leftLegacy == null ? null : leftLegacy.get("classification")));
        left.put("pain_present", Boolean.TRUE.equals(leftLegacy == null ? null : leftLegacy.get("pain_present")));
        left.put("pain_vas", normalizeNumber(leftLegacy == null ? null : leftLegacy.get("pain_vas")));
        left.put("top_tier_note", StrUtil.blankToDefault(toStringValue(leftLegacy == null ? null : leftLegacy.get("clinician_note")), ""));
        left.put("breakout_reason_text", StrUtil.blankToDefault(toStringValue(leftLegacy == null ? null : leftLegacy.get("breakout_reason_text")), ""));
        Object leftReviewPriority = leftLegacy == null ? null : leftLegacy.get("review_priority");
        left.put("review_priority", StrUtil.equals(toStringValue(leftReviewPriority), REVIEW_PRIORITY_NORMAL) ? "medium" : toStringValue(leftReviewPriority));

        Map<String, Object> right = new LinkedHashMap<>();
        right.put("classification", normalizeClassification(rightLegacy == null ? null : rightLegacy.get("classification")));
        right.put("pain_present", Boolean.TRUE.equals(rightLegacy == null ? null : rightLegacy.get("pain_present")));
        right.put("pain_vas", normalizeNumber(rightLegacy == null ? null : rightLegacy.get("pain_vas")));
        right.put("top_tier_note", StrUtil.blankToDefault(toStringValue(rightLegacy == null ? null : rightLegacy.get("clinician_note")), ""));
        right.put("breakout_reason_text", StrUtil.blankToDefault(toStringValue(rightLegacy == null ? null : rightLegacy.get("breakout_reason_text")), ""));
        Object rightReviewPriority = rightLegacy == null ? null : rightLegacy.get("review_priority");
        right.put("review_priority", StrUtil.equals(toStringValue(rightReviewPriority), REVIEW_PRIORITY_NORMAL) ? "medium" : toStringValue(rightReviewPriority));

        result.put("left", normalizeDedicatedUpperExtremityPattern2TopTierSide(left, UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE));
        result.put("right", normalizeDedicatedUpperExtremityPattern2TopTierSide(right, UPPER_EXTREMITY_PATTERN2_RIGHT_TEST_CODE));
        return result;
    }

    private Map<String, Object> mapDedicatedUpperExtremityPattern2TopTierSideToLegacy(Map<String, Object> dedicatedTop,
                                                                                        String testCode) {
        Map<String, Object> side = castToMap(dedicatedTop == null ? null : dedicatedTop.get(
                StrUtil.equals(testCode, UPPER_EXTREMITY_PATTERN2_LEFT_TEST_CODE) ? "left" : "right"));
        Map<String, Object> normalized = normalizeDedicatedUpperExtremityPattern2TopTierSide(side, testCode);
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("test_code", testCode);
        legacy.put("test_name_zh", normalized.get("test_name_zh"));
        legacy.put("side", normalized.get("side"));
        legacy.put("classification", normalized.get("classification"));
        legacy.put("pain_present", normalized.get("pain_present"));
        legacy.put("movement_quality_note", "");
        legacy.put("key_observation_note", "");
        legacy.put("rom_key_value", "");
        legacy.put("pain_vas", normalized.get("pain_vas"));
        legacy.put("needs_breakout_suggestion", normalized.get("needs_breakout_suggestion"));
        legacy.put("breakout_reason_text", normalized.get("breakout_reason_text"));
        legacy.put("clinician_note", normalized.get("top_tier_note"));
        String reviewPriority = toStringValue(normalized.get("review_priority"));
        legacy.put("review_priority", StrUtil.equals(reviewPriority, "medium") ? REVIEW_PRIORITY_NORMAL : reviewPriority);
        legacy.put("caution_text",
                StrUtil.equalsAny(toStringValue(normalized.get("classification")), CLASS_FP, CLASS_DP)
                        ? "优先疼痛管理/谨慎继续分解"
                        : "");
        return legacy;
    }

    private Map<String, Object> normalizeDedicatedUpperExtremityPattern2Breakout(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("left", normalizeDedicatedUpperExtremityPattern2BreakoutSide(castToMap(input == null ? null : input.get("left"))));
        result.put("right", normalizeDedicatedUpperExtremityPattern2BreakoutSide(castToMap(input == null ? null : input.get("right"))));
        result.put("asymmetry_focus", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("asymmetry_focus")), ""));
        result.put("overall_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("overall_note")), ""));
        return result;
    }

    private Map<String, Object> normalizeDedicatedUpperExtremityPattern2BreakoutSide(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("breakout_status", normalizeBreakoutStatus(input == null ? null : input.get("breakout_status")));
        result.put("breakout_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_note")), ""));
        result.put("prone_active_result", normalizeClassification(input == null ? null : input.get("prone_active_result")));
        result.put("prone_active_pain_vas", normalizeNumber(input == null ? null : input.get("prone_active_pain_vas")));
        result.put("prone_active_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("prone_active_note")), ""));
        result.put("prone_passive_result", normalizeClassification(input == null ? null : input.get("prone_passive_result")));
        result.put("prone_passive_pain_vas", normalizeNumber(input == null ? null : input.get("prone_passive_pain_vas")));
        result.put("prone_passive_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("prone_passive_note")), ""));
        result.put("supine_interactive_result", normalizeClassification(input == null ? null : input.get("supine_interactive_result")));
        result.put("supine_interactive_pain_vas", normalizeNumber(input == null ? null : input.get("supine_interactive_pain_vas")));
        result.put("supine_interactive_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("supine_interactive_note")), ""));
        result.put("flow_recommendation_text", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("flow_recommendation_text")), ""));
        result.put("local_biomechanics_needed", Boolean.TRUE.equals(input == null ? null : input.get("local_biomechanics_needed")));
        result.put("stop_and_treat", Boolean.TRUE.equals(input == null ? null : input.get("stop_and_treat")));
        result.put("active_ue_pattern2_quality", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("active_ue_pattern2_quality")), ""));
        result.put("active_ue_pattern2_pain", Boolean.TRUE.equals(input == null ? null : input.get("active_ue_pattern2_pain")));
        result.put("active_ue_pattern2_rom_key", normalizeNumber(input == null ? null : input.get("active_ue_pattern2_rom_key")));
        result.put("scapular_control_observation", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("scapular_control_observation")), ""));
        result.put("thoracic_influence_observation", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("thoracic_influence_observation")), ""));
        result.put("glenohumeral_observation", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("glenohumeral_observation")), ""));
        result.put("compensation_patterns", castStringList(input == null ? null : input.get("compensation_patterns")));
        result.put("compensation_other_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("compensation_other_note")), ""));
        result.put("related_region_influence", castStringList(input == null ? null : input.get("related_region_influence")));
        result.put("breakout_preliminary_direction", castStringList(input == null ? null : input.get("breakout_preliminary_direction")));
        result.put("breakout_summary_text", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_summary_text")), ""));
        boolean needsManualReview = Boolean.TRUE.equals(input == null ? null : input.get("needs_manual_review"))
                || Boolean.TRUE.equals(input == null ? null : input.get("active_ue_pattern2_pain"))
                || StrUtil.equals(toStringValue(input == null ? null : input.get("breakout_status")), BREAKOUT_STATUS_STOPPED_PAIN);
        result.put("needs_manual_review", needsManualReview);
        return result;
    }

    private Map<String, Object> mapLegacyBreakoutToDedicatedUpperExtremityPattern2(Map<String, Object> leftLegacy,
                                                                                    Map<String, Object> rightLegacy) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("left", mapLegacyBreakoutToDedicatedUpperExtremityPattern2Side(leftLegacy));
        result.put("right", mapLegacyBreakoutToDedicatedUpperExtremityPattern2Side(rightLegacy));
        result.put("asymmetry_focus", StrUtil.blankToDefault(
                toStringValue(leftLegacy == null ? null : leftLegacy.get("asymmetry_signs")),
                toStringValue(rightLegacy == null ? null : rightLegacy.get("asymmetry_signs"))));
        result.put("overall_note", StrUtil.blankToDefault(
                toStringValue(leftLegacy == null ? null : leftLegacy.get("clinician_note")),
                toStringValue(rightLegacy == null ? null : rightLegacy.get("clinician_note"))));
        return normalizeDedicatedUpperExtremityPattern2Breakout(result);
    }

    private Map<String, Object> mapLegacyBreakoutToDedicatedUpperExtremityPattern2Side(Map<String, Object> legacy) {
        Map<String, Object> side = new LinkedHashMap<>();
        side.put("breakout_status", normalizeBreakoutStatus(legacy == null ? null : legacy.get("status")));
        side.put("breakout_note", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("clinician_note")), ""));
        side.put("prone_active_result", "");
        side.put("prone_active_pain_vas", normalizeNumber(legacy == null ? null : legacy.get("pain_vas")));
        side.put("prone_active_note", "");
        side.put("prone_passive_result", "");
        side.put("prone_passive_pain_vas", null);
        side.put("prone_passive_note", "");
        side.put("supine_interactive_result", "");
        side.put("supine_interactive_pain_vas", null);
        side.put("supine_interactive_note", "");
        side.put("flow_recommendation_text", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("findings")), ""));
        side.put("local_biomechanics_needed", false);
        side.put("stop_and_treat", Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain")));
        side.put("active_ue_pattern2_quality", "");
        side.put("active_ue_pattern2_pain", Boolean.TRUE.equals(legacy == null ? null : legacy.get("pain_present")));
        side.put("active_ue_pattern2_rom_key", null);
        side.put("scapular_control_observation", "");
        side.put("thoracic_influence_observation", "");
        side.put("glenohumeral_observation", "");
        side.put("compensation_patterns", Collections.emptyList());
        side.put("compensation_other_note", "");
        side.put("related_region_influence", Collections.emptyList());
        side.put("breakout_preliminary_direction", inferBreakoutDirection(legacy == null ? Collections.emptyMap() : legacy));
        side.put("breakout_summary_text", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("findings")), ""));
        side.put("needs_manual_review", Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain")));
        return normalizeDedicatedUpperExtremityPattern2BreakoutSide(side);
    }

    private Map<String, Object> mapDedicatedUpperExtremityPattern2BreakoutToLegacy(Map<String, Object> dedicated) {
        Map<String, Object> normalized = normalizeDedicatedUpperExtremityPattern2Breakout(dedicated);
        Map<String, Object> left = castToMap(normalized.get("left"));
        Map<String, Object> right = castToMap(normalized.get("right"));
        Map<String, Object> legacy = new LinkedHashMap<>();
        String status = mergeRotationStatus(
                normalizeBreakoutStatus(left == null ? null : left.get("breakout_status")),
                normalizeBreakoutStatus(right == null ? null : right.get("breakout_status")));
        legacy.put("status", status);
        legacy.put("findings", distinct(Arrays.asList(
                toStringValue(left == null ? null : left.get("breakout_summary_text")),
                toStringValue(right == null ? null : right.get("breakout_summary_text")),
                toStringValue(left == null ? null : left.get("flow_recommendation_text")),
                toStringValue(right == null ? null : right.get("flow_recommendation_text")),
                toStringValue(normalized.get("overall_note"))
        )).stream().filter(StrUtil::isNotBlank).collect(Collectors.joining("；")));
        legacy.put("rom_key_values", buildUpperExtremityPattern2RomText(left, right));
        legacy.put("pain_present",
                Boolean.TRUE.equals(left == null ? null : left.get("active_ue_pattern2_pain"))
                        || Boolean.TRUE.equals(right == null ? null : right.get("active_ue_pattern2_pain"))
                        || StrUtil.equalsAny(normalizeClassification(left == null ? null : left.get("prone_active_result")), CLASS_FP, CLASS_DP)
                        || StrUtil.equalsAny(normalizeClassification(right == null ? null : right.get("prone_active_result")), CLASS_FP, CLASS_DP)
                        || StrUtil.equalsAny(normalizeClassification(left == null ? null : left.get("prone_passive_result")), CLASS_FP, CLASS_DP)
                        || StrUtil.equalsAny(normalizeClassification(right == null ? null : right.get("prone_passive_result")), CLASS_FP, CLASS_DP)
                        || StrUtil.equalsAny(normalizeClassification(left == null ? null : left.get("supine_interactive_result")), CLASS_FP, CLASS_DP)
                        || StrUtil.equalsAny(normalizeClassification(right == null ? null : right.get("supine_interactive_result")), CLASS_FP, CLASS_DP));
        legacy.put("pain_vas", null);
        legacy.put("mobility_restriction_signs",
                castStringList(left == null ? null : left.get("breakout_preliminary_direction")).contains("更偏活动度限制")
                        || castStringList(right == null ? null : right.get("breakout_preliminary_direction")).contains("更偏活动度限制")
                        || StrUtil.equals(normalizeClassification(left == null ? null : left.get("prone_passive_result")), CLASS_DN)
                        || StrUtil.equals(normalizeClassification(right == null ? null : right.get("prone_passive_result")), CLASS_DN)
                        ? "更偏活动度限制" : "");
        legacy.put("motor_control_signs",
                castStringList(left == null ? null : left.get("breakout_preliminary_direction")).contains("更偏运动控制问题")
                        || castStringList(right == null ? null : right.get("breakout_preliminary_direction")).contains("更偏运动控制问题")
                        || StrUtil.equalsAny(normalizeClassification(left == null ? null : left.get("supine_interactive_result")), CLASS_FN, CLASS_DN)
                        || StrUtil.equalsAny(normalizeClassification(right == null ? null : right.get("supine_interactive_result")), CLASS_FN, CLASS_DN)
                        ? "更偏运动控制问题" : "");
        legacy.put("asymmetry_signs", toStringValue(normalized.get("asymmetry_focus")));
        legacy.put("stop_due_to_pain",
                StrUtil.equals(status, BREAKOUT_STATUS_STOPPED_PAIN)
                        || Boolean.TRUE.equals(left == null ? null : left.get("stop_and_treat"))
                        || Boolean.TRUE.equals(right == null ? null : right.get("stop_and_treat"))
                        || Boolean.TRUE.equals(left == null ? null : left.get("needs_manual_review"))
                        || Boolean.TRUE.equals(right == null ? null : right.get("needs_manual_review")));
        legacy.put("stop_reason", distinct(Arrays.asList(
                toStringValue(left == null ? null : left.get("breakout_note")),
                toStringValue(right == null ? null : right.get("breakout_note"))
        )).stream().filter(StrUtil::isNotBlank).collect(Collectors.joining("；")));
        legacy.put("clinician_note", toStringValue(normalized.get("overall_note")));
        legacy.put("method", "");
        legacy.put("scale", "");
        legacy.put("source_id", "");
        legacy.put("date", "");
        legacy.put("sls_time_sec", null);
        return legacy;
    }

    private Map<String, Object> mapDedicatedUpperExtremityPattern2BreakoutSideToLegacy(Map<String, Object> side) {
        Map<String, Object> normalized = normalizeDedicatedUpperExtremityPattern2BreakoutSide(side);
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("status", normalized.get("breakout_status"));
        legacy.put("findings", StrUtil.blankToDefault(
                toStringValue(normalized.get("breakout_summary_text")),
                toStringValue(normalized.get("breakout_note"))
        ));
        Number rom = normalizeNumber(normalized.get("active_ue_pattern2_rom_key"));
        legacy.put("rom_key_values", rom == null ? "" : "关键ROM:" + rom);
        legacy.put("pain_present", Boolean.TRUE.equals(normalized.get("active_ue_pattern2_pain")));
        if (!Boolean.TRUE.equals(legacy.get("pain_present"))) {
            legacy.put("pain_present",
                    StrUtil.equalsAny(normalizeClassification(normalized.get("prone_active_result")), CLASS_FP, CLASS_DP)
                            || StrUtil.equalsAny(normalizeClassification(normalized.get("prone_passive_result")), CLASS_FP, CLASS_DP)
                            || StrUtil.equalsAny(normalizeClassification(normalized.get("supine_interactive_result")), CLASS_FP, CLASS_DP));
        }
        legacy.put("pain_vas", null);
        legacy.put("mobility_restriction_signs",
                castStringList(normalized.get("breakout_preliminary_direction")).contains("更偏活动度限制")
                        || StrUtil.equals(normalizeClassification(normalized.get("prone_passive_result")), CLASS_DN)
                        ? "更偏活动度限制" : "");
        legacy.put("motor_control_signs",
                castStringList(normalized.get("breakout_preliminary_direction")).contains("更偏运动控制问题")
                        || StrUtil.equalsAny(normalizeClassification(normalized.get("supine_interactive_result")), CLASS_FN, CLASS_DN)
                        ? "更偏运动控制问题" : "");
        legacy.put("asymmetry_signs", "");
        legacy.put("stop_due_to_pain",
                StrUtil.equals(toStringValue(normalized.get("breakout_status")), BREAKOUT_STATUS_STOPPED_PAIN)
                        || Boolean.TRUE.equals(normalized.get("stop_and_treat"))
                        || Boolean.TRUE.equals(normalized.get("needs_manual_review")));
        legacy.put("stop_reason", toStringValue(normalized.get("breakout_note")));
        legacy.put("clinician_note", toStringValue(normalized.get("breakout_note")));
        legacy.put("method", "");
        legacy.put("scale", "");
        legacy.put("source_id", "");
        legacy.put("date", "");
        legacy.put("sls_time_sec", null);
        return legacy;
    }

    private List<String> buildUpperExtremityPattern2PrimaryFindings(Map<String, Object> side, String sideZh) {
        List<String> findings = new ArrayList<>();
        String proneActive = normalizeClassification(side.get("prone_active_result"));
        if (StrUtil.isNotBlank(proneActive)) {
            findings.add(sideZh + "俯卧位主动：" + proneActive);
        }
        String pronePassive = normalizeClassification(side.get("prone_passive_result"));
        if (StrUtil.isNotBlank(pronePassive)) {
            findings.add(sideZh + "俯卧位被动：" + pronePassive);
        }
        String supineInteractive = normalizeClassification(side.get("supine_interactive_result"));
        if (StrUtil.isNotBlank(supineInteractive)) {
            findings.add(sideZh + "仰卧位交互：" + supineInteractive);
        }
        String activeQuality = toStringValue(side.get("active_ue_pattern2_quality"));
        if (StrUtil.isNotBlank(activeQuality)) {
            findings.add(sideZh + "主动模式质量：" + activeQuality);
        }
        String scapular = toStringValue(side.get("scapular_control_observation"));
        if (StrUtil.isNotBlank(scapular)) {
            findings.add(sideZh + "肩胛控制：" + scapular);
        }
        String thoracic = toStringValue(side.get("thoracic_influence_observation"));
        if (StrUtil.isNotBlank(thoracic)) {
            findings.add(sideZh + "胸椎影响：" + thoracic);
        }
        String gleno = toStringValue(side.get("glenohumeral_observation"));
        if (StrUtil.isNotBlank(gleno)) {
            findings.add(sideZh + "盂肱观察：" + gleno);
        }
        findings.addAll(castStringList(side.get("breakout_preliminary_direction")).stream()
                .map(item -> sideZh + "方向：" + item).collect(Collectors.toList()));
        return distinct(findings);
    }

    private String buildUpperExtremityPattern2BreakoutSummaryText(String status,
                                                                  List<String> findings,
                                                                  List<String> direction,
                                                                  boolean needsManualReview,
                                                                  String side) {
        String action = "left".equals(side) ? "上肢模式2（左）" : "上肢模式2（右）";
        if (StrUtil.equals(status, BREAKOUT_STATUS_NOT_STARTED)) {
            return action + "分解评估尚未开始。";
        }
        if (StrUtil.equals(status, BREAKOUT_STATUS_SKIPPED)) {
            return action + "分解评估暂未执行，建议结合后续复测决定是否补充。";
        }
        if (StrUtil.equals(status, BREAKOUT_STATUS_STOPPED_PAIN)) {
            return action + "分解评估因疼痛中止，建议优先疼痛管理并进行人工复核。";
        }
        String findingText = findings.isEmpty() ? "暂未提取到明确异常线索" : String.join("、", findings);
        String directionText = direction.isEmpty() ? "方向待补充" : String.join("、", direction);
        return action + "分解评估" + mapBreakoutStatusZh(status) + "，主要表现为：" + findingText
                + "；初步方向：" + directionText + (needsManualReview ? "；建议人工复核。" : "。");
    }

    private String buildUpperExtremityPattern2RomText(Map<String, Object> left, Map<String, Object> right) {
        List<String> tokens = new ArrayList<>();
        Number leftActive = normalizeNumber(left == null ? null : left.get("active_ue_pattern2_rom_key"));
        Number rightActive = normalizeNumber(right == null ? null : right.get("active_ue_pattern2_rom_key"));
        if (leftActive != null) {
            tokens.add("左关键ROM:" + leftActive);
        }
        if (rightActive != null) {
            tokens.add("右关键ROM:" + rightActive);
        }
        return String.join(" | ", tokens);
    }

    private String normalizeDedicatedMsrBreakoutStatus(Object value) {
        String status = StrUtil.trimToEmpty(toStringValue(value));
        if (StrUtil.equals(status, "partial") || StrUtil.equals(status, BREAKOUT_STATUS_STOPPED_PAIN)) {
            return "in_progress";
        }
        if (StrUtil.equalsAny(status, "not_started", "in_progress", "completed", "skipped")) {
            return status;
        }
        return "not_started";
    }

    private String mapDedicatedMsrStatusToLegacy(String dedicatedStatus) {
        if (StrUtil.equals(dedicatedStatus, "in_progress")) {
            return BREAKOUT_STATUS_PARTIAL;
        }
        if (StrUtil.equalsAny(dedicatedStatus, BREAKOUT_STATUS_NOT_STARTED, BREAKOUT_STATUS_COMPLETED, BREAKOUT_STATUS_SKIPPED)) {
            return dedicatedStatus;
        }
        return BREAKOUT_STATUS_NOT_STARTED;
    }

    private Map<String, Object> normalizeDedicatedMsrBreakoutSide(Map<String, Object> input, String side) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("breakout_status", normalizeDedicatedMsrBreakoutStatus(input == null ? null : input.get("breakout_status")));
        result.put("rotation_side", side);
        result.put("breakout_reason_from_top_tier",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_reason_from_top_tier")), ""));
        result.put("breakout_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_note")), ""));
        result.put("active_rotation_global_quality",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("active_rotation_global_quality")), ""));
        result.put("active_rotation_pain", Boolean.TRUE.equals(input == null ? null : input.get("active_rotation_pain")));
        result.put("active_rotation_pain_area", castStringList(input == null ? null : input.get("active_rotation_pain_area")));
        result.put("active_rotation_pain_other_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("active_rotation_pain_other_note")), ""));
        result.put("global_rotation_quality_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("global_rotation_quality_note")), ""));
        result.put("rotation_range_key", normalizeNumber(input == null ? null : input.get("rotation_range_key")));
        result.put("stance_stability_observation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("stance_stability_observation")), ""));
        result.put("ankle_foot_support_influence",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("ankle_foot_support_influence")), ""));
        result.put("lower_extremity_loading_asymmetry",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("lower_extremity_loading_asymmetry")), ""));
        result.put("knee_control_influence",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("knee_control_influence")), ""));
        result.put("lower_extremity_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("lower_extremity_note")), ""));
        result.put("hip_rotation_contribution",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("hip_rotation_contribution")), ""));
        result.put("pelvis_rotation_control",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pelvis_rotation_control")), ""));
        result.put("hip_pelvis_dissociation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("hip_pelvis_dissociation")), ""));
        result.put("left_right_hip_rotation_asymmetry",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("left_right_hip_rotation_asymmetry")), ""));
        result.put("hip_pelvis_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("hip_pelvis_note")), ""));
        result.put("thoracic_rotation_participation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("thoracic_rotation_participation")), ""));
        result.put("lumbar_rotation_participation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("lumbar_rotation_participation")), ""));
        result.put("rotation_distribution_observation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("rotation_distribution_observation")), ""));
        result.put("thorax_pelvis_coupling_observation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("thorax_pelvis_coupling_observation")), ""));
        result.put("spine_thorax_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("spine_thorax_note")), ""));
        result.put("shoulder_girdle_participation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("shoulder_girdle_participation")), ""));
        result.put("upper_extremity_assist_pattern",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("upper_extremity_assist_pattern")), ""));
        result.put("shoulder_thorax_link_observation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("shoulder_thorax_link_observation")), ""));
        result.put("shoulder_upper_extremity_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("shoulder_upper_extremity_note")), ""));
        result.put("compensation_patterns", castStringList(input == null ? null : input.get("compensation_patterns")));
        result.put("compensation_other_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("compensation_other_note")), ""));
        result.put("pain_dominant_pattern",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pain_dominant_pattern")), ""));
        result.put("symptom_irritability",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("symptom_irritability")), ""));
        result.put("pain_control_priority_hint",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pain_control_priority_hint")), ""));
        result.put("breakout_preliminary_direction",
                castStringList(input == null ? null : input.get("breakout_preliminary_direction")));
        result.put("primary_restriction_chain",
                castStringList(input == null ? null : input.get("primary_restriction_chain")));
        result.put("primary_control_deficit_chain",
                castStringList(input == null ? null : input.get("primary_control_deficit_chain")));
        result.put("side_specific_priority",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("side_specific_priority")), ""));
        result.put("compare_with_other_side_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("compare_with_other_side_note")), ""));
        result.put("breakout_summary_text",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_summary_text")), ""));
        result.put("clinical_meaning_hint",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("clinical_meaning_hint")), ""));
        result.put("training_direction_hint",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("training_direction_hint")), ""));
        result.put("reassessment_priority", normalizeReassessmentPriority(input == null ? null : input.get("reassessment_priority")));
        result.put("pause_or_referral_hint",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pause_or_referral_hint")), ""));

        boolean painDominant = StrUtil.equalsAny(toStringValue(result.get("pain_dominant_pattern")), "疑似是", "明显是");
        boolean priorityManualReview = StrUtil.contains(toStringValue(result.get("pain_control_priority_hint")), "人工复核");
        boolean manualReview = Boolean.TRUE.equals(input == null ? null : input.get("needs_manual_review"))
                || Boolean.TRUE.equals(result.get("active_rotation_pain"))
                || painDominant
                || priorityManualReview;
        result.put("needs_manual_review", manualReview);
        return result;
    }

    private Map<String, Object> normalizeDedicatedMsrBreakout(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("left", normalizeDedicatedMsrBreakoutSide(castToMap(input == null ? null : input.get("left")), "left"));
        result.put("right", normalizeDedicatedMsrBreakoutSide(castToMap(input == null ? null : input.get("right")), "right"));
        result.put("asymmetry_focus",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("asymmetry_focus")), ""));
        result.put("overall_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("overall_note")), ""));
        return result;
    }

    private Map<String, Object> mapLegacyBreakoutToDedicatedMsrSide(Map<String, Object> legacy, String side) {
        Map<String, Object> result = new LinkedHashMap<>();
        String legacyStatus = normalizeBreakoutStatus(legacy == null ? null : legacy.get("status"));
        result.put("breakout_status", StrUtil.equals(legacyStatus, BREAKOUT_STATUS_PARTIAL) ? "in_progress" : legacyStatus);
        result.put("rotation_side", side);
        result.put("breakout_reason_from_top_tier", "");
        result.put("breakout_note", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("clinician_note")), ""));
        result.put("needs_manual_review", Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain")));
        result.put("active_rotation_global_quality", "");
        result.put("active_rotation_pain", Boolean.TRUE.equals(legacy == null ? null : legacy.get("pain_present")));
        result.put("active_rotation_pain_area", Collections.emptyList());
        result.put("active_rotation_pain_other_note", "");
        result.put("global_rotation_quality_note", "");
        result.put("rotation_range_key", null);
        result.put("stance_stability_observation", "");
        result.put("ankle_foot_support_influence", "");
        result.put("lower_extremity_loading_asymmetry", "");
        result.put("knee_control_influence", "");
        result.put("lower_extremity_note", "");
        result.put("hip_rotation_contribution", "");
        result.put("pelvis_rotation_control", "");
        result.put("hip_pelvis_dissociation", "");
        result.put("left_right_hip_rotation_asymmetry", "");
        result.put("hip_pelvis_note", "");
        result.put("thoracic_rotation_participation", "");
        result.put("lumbar_rotation_participation", "");
        result.put("rotation_distribution_observation", "");
        result.put("thorax_pelvis_coupling_observation", "");
        result.put("spine_thorax_note", "");
        result.put("shoulder_girdle_participation", "");
        result.put("upper_extremity_assist_pattern", "");
        result.put("shoulder_thorax_link_observation", "");
        result.put("shoulder_upper_extremity_note", "");
        result.put("compensation_patterns", Collections.emptyList());
        result.put("compensation_other_note", "");
        result.put("pain_dominant_pattern", Boolean.TRUE.equals(legacy == null ? null : legacy.get("pain_present")) ? "疑似是" : "");
        result.put("symptom_irritability", "");
        result.put("pain_control_priority_hint",
                Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain")) ? "是，建议优先人工复核" : "");
        result.put("breakout_preliminary_direction", inferBreakoutDirection(legacy == null ? Collections.emptyMap() : legacy));
        result.put("primary_restriction_chain", Collections.emptyList());
        result.put("primary_control_deficit_chain", Collections.emptyList());
        result.put("side_specific_priority", "");
        result.put("compare_with_other_side_note", "");
        result.put("breakout_summary_text", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("findings")), ""));
        result.put("clinical_meaning_hint", "");
        result.put("training_direction_hint", "");
        result.put("reassessment_priority", "medium");
        result.put("pause_or_referral_hint",
                Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain")) ? "建议优先人工复核" : "");
        return normalizeDedicatedMsrBreakoutSide(result, side);
    }

    private Map<String, Object> mapLegacyBreakoutToDedicatedMsr(Map<String, Object> legacyLeft,
                                                                Map<String, Object> legacyRight,
                                                                Map<String, Object> legacyMerged) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("left", mapLegacyBreakoutToDedicatedMsrSide(legacyLeft, "left"));
        result.put("right", mapLegacyBreakoutToDedicatedMsrSide(legacyRight, "right"));
        result.put("asymmetry_focus",
                StrUtil.blankToDefault(toStringValue(legacyMerged == null ? null : legacyMerged.get("asymmetry_signs")), ""));
        result.put("overall_note",
                StrUtil.blankToDefault(toStringValue(legacyMerged == null ? null : legacyMerged.get("clinician_note")), ""));
        return normalizeDedicatedMsrBreakout(result);
    }

    private Map<String, Object> mapDedicatedMsrBreakoutSideToLegacy(Map<String, Object> dedicatedSide) {
        Map<String, Object> normalized = normalizeDedicatedMsrBreakoutSide(dedicatedSide, toStringValue(
                dedicatedSide == null ? null : dedicatedSide.get("rotation_side")));
        String status = mapDedicatedMsrStatusToLegacy(toStringValue(normalized.get("breakout_status")));
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("status", status);
        legacy.put("findings", distinct(Arrays.asList(
                toStringValue(normalized.get("breakout_summary_text")),
                toStringValue(normalized.get("clinical_meaning_hint")),
                toStringValue(normalized.get("training_direction_hint")),
                toStringValue(normalized.get("breakout_note"))
        )).stream().filter(StrUtil::isNotBlank).collect(Collectors.joining("；")));
        Number range = normalizeNumber(normalized.get("rotation_range_key"));
        legacy.put("rom_key_values", range == null ? "" : "关键旋转范围:" + range);
        boolean painPresent = Boolean.TRUE.equals(normalized.get("active_rotation_pain"))
                || StrUtil.equalsAny(toStringValue(normalized.get("pain_dominant_pattern")), "疑似是", "明显是");
        legacy.put("pain_present", painPresent);
        legacy.put("pain_vas", null);
        List<String> direction = castStringList(normalized.get("breakout_preliminary_direction"));
        legacy.put("mobility_restriction_signs",
                direction.stream()
                        .filter(item -> StrUtil.equalsAny(item, "更偏活动度限制", "更偏髋旋转参与不足", "更偏胸椎旋转不足"))
                        .collect(Collectors.joining("、")));
        legacy.put("motor_control_signs",
                direction.stream()
                        .filter(item -> StrUtil.equalsAny(item, "更偏骨盆旋转控制差", "更偏腰椎代偿", "更偏运动控制问题"))
                        .collect(Collectors.joining("、")));
        legacy.put("asymmetry_signs", StrUtil.blankToDefault(toStringValue(normalized.get("side_specific_priority")), ""));
        boolean stopDueToPain = StrUtil.equals(toStringValue(normalized.get("pause_or_referral_hint")), "建议优先人工复核")
                && painPresent;
        legacy.put("stop_due_to_pain", stopDueToPain);
        legacy.put("stop_reason", stopDueToPain ? toStringValue(normalized.get("breakout_note")) : "");
        legacy.put("clinician_note", toStringValue(normalized.get("breakout_note")));
        legacy.put("method", "");
        legacy.put("scale", "");
        legacy.put("source_id", "");
        legacy.put("date", "");
        legacy.put("sls_time_sec", null);
        return legacy;
    }

    private Map<String, Object> mapDedicatedMsrBreakoutToLegacyMerged(Map<String, Object> dedicated) {
        Map<String, Object> normalized = normalizeDedicatedMsrBreakout(dedicated);
        Map<String, Object> left = castToMap(normalized.get("left"));
        Map<String, Object> right = castToMap(normalized.get("right"));
        Map<String, Object> leftLegacy = mapDedicatedMsrBreakoutSideToLegacy(left);
        Map<String, Object> rightLegacy = mapDedicatedMsrBreakoutSideToLegacy(right);
        Map<String, Object> legacy = new LinkedHashMap<>();
        String leftStatus = toStringValue(left == null ? null : left.get("breakout_status"));
        String rightStatus = toStringValue(right == null ? null : right.get("breakout_status"));
        if (StrUtil.equals(leftStatus, "completed") && StrUtil.equals(rightStatus, "completed")) {
            legacy.put("status", BREAKOUT_STATUS_COMPLETED);
        } else if (StrUtil.equals(leftStatus, "skipped") && StrUtil.equals(rightStatus, "skipped")) {
            legacy.put("status", BREAKOUT_STATUS_SKIPPED);
        } else if (StrUtil.equalsAny(leftStatus, "in_progress", "completed")
                || StrUtil.equalsAny(rightStatus, "in_progress", "completed")) {
            legacy.put("status", BREAKOUT_STATUS_PARTIAL);
        } else if (StrUtil.equalsAny(leftStatus, "skipped", rightStatus, "skipped")) {
            legacy.put("status", BREAKOUT_STATUS_SKIPPED);
        } else {
            legacy.put("status", BREAKOUT_STATUS_NOT_STARTED);
        }
        legacy.put("findings", distinct(Arrays.asList(
                toStringValue(left == null ? null : left.get("breakout_summary_text")),
                toStringValue(right == null ? null : right.get("breakout_summary_text")),
                toStringValue(normalized.get("overall_note"))
        )).stream().filter(StrUtil::isNotBlank).collect(Collectors.joining("；")));
        Number leftRange = normalizeNumber(left == null ? null : left.get("rotation_range_key"));
        Number rightRange = normalizeNumber(right == null ? null : right.get("rotation_range_key"));
        List<String> romTokens = new ArrayList<>();
        if (leftRange != null) {
            romTokens.add("左关键旋转:" + leftRange);
        }
        if (rightRange != null) {
            romTokens.add("右关键旋转:" + rightRange);
        }
        legacy.put("rom_key_values", String.join(" | ", romTokens));
        legacy.put("pain_present", Boolean.TRUE.equals(leftLegacy.get("pain_present")) || Boolean.TRUE.equals(rightLegacy.get("pain_present")));
        legacy.put("pain_vas", null);
        legacy.put("mobility_restriction_signs", distinct(Arrays.asList(
                toStringValue(leftLegacy.get("mobility_restriction_signs")),
                toStringValue(rightLegacy.get("mobility_restriction_signs"))
        )).stream().collect(Collectors.joining("、")));
        legacy.put("motor_control_signs", distinct(Arrays.asList(
                toStringValue(leftLegacy.get("motor_control_signs")),
                toStringValue(rightLegacy.get("motor_control_signs"))
        )).stream().collect(Collectors.joining("、")));
        legacy.put("asymmetry_signs", StrUtil.blankToDefault(toStringValue(normalized.get("asymmetry_focus")), ""));
        legacy.put("stop_due_to_pain",
                Boolean.TRUE.equals(leftLegacy.get("stop_due_to_pain")) || Boolean.TRUE.equals(rightLegacy.get("stop_due_to_pain")));
        legacy.put("stop_reason", distinct(Arrays.asList(
                toStringValue(leftLegacy.get("stop_reason")),
                toStringValue(rightLegacy.get("stop_reason"))
        )).stream().collect(Collectors.joining("；")));
        legacy.put("clinician_note", toStringValue(normalized.get("overall_note")));
        legacy.put("method", "");
        legacy.put("scale", "");
        legacy.put("source_id", "");
        legacy.put("date", "");
        legacy.put("sls_time_sec", null);
        return legacy;
    }

    private String normalizeDedicatedMsfBreakoutStatus(Object value) {
        String status = StrUtil.trimToEmpty(toStringValue(value));
        if (StrUtil.equals(status, "partial")) {
            return "in_progress";
        }
        if (StrUtil.equals(status, BREAKOUT_STATUS_STOPPED_PAIN)) {
            return "in_progress";
        }
        if (StrUtil.equalsAny(status, "not_started", "in_progress", "completed", "skipped")) {
            return status;
        }
        return "not_started";
    }

    private String mapDedicatedMsfStatusToLegacy(String dedicatedStatus) {
        if (StrUtil.equals(dedicatedStatus, "in_progress")) {
            return BREAKOUT_STATUS_PARTIAL;
        }
        if (StrUtil.equalsAny(dedicatedStatus, BREAKOUT_STATUS_NOT_STARTED, BREAKOUT_STATUS_COMPLETED, BREAKOUT_STATUS_SKIPPED)) {
            return dedicatedStatus;
        }
        return BREAKOUT_STATUS_NOT_STARTED;
    }

    private String normalizeReassessmentPriority(Object value) {
        String priority = StrUtil.trimToEmpty(toStringValue(value));
        if (StrUtil.equalsAny(priority, REVIEW_PRIORITY_LOW, "medium", REVIEW_PRIORITY_HIGH)) {
            return priority;
        }
        return "medium";
    }

    private boolean isSfmaPainResult(String result) {
        return StrUtil.equalsAny(result, "FP", "DP");
    }

    private Double avgNullable(Number left, Number right) {
        List<Double> values = new ArrayList<>();
        if (left != null) {
            values.add(left.doubleValue());
        }
        if (right != null) {
            values.add(right.doubleValue());
        }
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().reduce(0.0, Double::sum) / values.size();
    }

    private String deriveMsfLongSitResultType(Boolean canTouchToes, Number sacralAngleDeg, String sacralStatus, boolean painPresent) {
        if (StrUtil.isBlank(sacralStatus) && sacralAngleDeg == null) {
            return "";
        }
        String normalizedSacralStatus = sacralStatus;
        if (StrUtil.isBlank(normalizedSacralStatus) && sacralAngleDeg != null) {
            normalizedSacralStatus = sacralAngleDeg.doubleValue() >= 80D ? "正常(≥80°)" : "受限(<80°)";
        }
        if (Boolean.TRUE.equals(canTouchToes) && StrUtil.equals(normalizedSacralStatus, "正常(≥80°)") && !painPresent) {
            return "fn_and_sacrum_normal";
        }
        if (StrUtil.equals(normalizedSacralStatus, "正常(≥80°)") && (painPresent || !Boolean.TRUE.equals(canTouchToes))) {
            return "abnormal_with_sacrum_normal";
        }
        if (StrUtil.equals(normalizedSacralStatus, "受限(<80°)")) {
            return "abnormal_with_sacrum_limited";
        }
        return "";
    }

    private String deriveMsfPslrResultType(String pslrResult, Number pslrLeft, Number pslrRight, Number aslrLeft, Number aslrRight, int pslrThreshold) {
        if (StrUtil.isBlank(pslrResult)) {
            return "";
        }
        if (isSfmaPainResult(pslrResult)) {
            return "fp_or_dp";
        }
        Double avgPslr = avgNullable(pslrLeft, pslrRight);
        Double avgAslr = avgNullable(aslrLeft, aslrRight);
        if (avgPslr != null && avgPslr > pslrThreshold) {
            return "fn_gt_80";
        }
        if (avgPslr != null && avgAslr != null && avgPslr < pslrThreshold && avgPslr - avgAslr >= 10D) {
            return "fn_gap_gt_10_and_lt_80";
        }
        if (StrUtil.equals(pslrResult, "DN") || (avgPslr != null && avgAslr != null && avgPslr <= avgAslr)) {
            return "dn_pslr_lte_aslr";
        }
        if (StrUtil.equals(pslrResult, "FN")) {
            return "fn_gt_80";
        }
        return "";
    }

    private Map<String, Object> buildDefaultMsfAnalysisMap() {
        Map<String, Object> flow = new LinkedHashMap<>();

        flow.put("single_leg_stance_forward_bend", new LinkedHashMap<String, Object>() {{
            put("node_code", "single_leg_stance_forward_bend");
            put("node_name_zh", "单腿站立体前屈");
            put("purpose", "判断体前屈是对称性还是不对称性功能障碍，或作为疼痛诱发策略。");
            put("instructions", "一侧脚蹬台阶、对侧膝伸直，双手相叠前屈触碰支撑腿同侧足趾，双侧重复比较。");
            put("clinical_notes", "该节点用于暴露单侧前屈问题；无论结果如何均继续进入长坐位触摸足趾。");
            put("result_type", "");
            put("result_code", "");
            put("input_fields", Arrays.asList("left_result", "right_result", "pain_present", "note"));
            put("next_step_rules", "所有结果都进入 long_sit_toe_touch");
            put("output_meaning", "记录是双侧问题还是单侧问题；该步不终止流程。");
            put("stop_if_pain", false);
            put("result", "");
            put("left_result", "");
            put("right_result", "");
            put("bilateral_summary", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        flow.put("long_sit_toe_touch", new LinkedHashMap<String, Object>() {{
            put("node_code", "long_sit_toe_touch");
            put("node_name_zh", "长坐位触摸足趾");
            put("purpose", "在不负重姿势下鉴别屈曲受限更像后链紧张、髋屈曲受限还是脊柱屈曲障碍。");
            put("instructions", "长坐位双下肢伸直前屈触趾，记录是否触趾、骶骨角度（80°阈值）与疼痛。");
            put("clinical_notes", "FN且骶骨角正常提示负重髋稳定/协调问题；异常+骶骨角正常偏向负重脊柱稳定或灵活性问题；异常+骶骨角受限偏向髋屈曲或脊柱屈曲受限。");
            put("result_type", "");
            put("result_code", "");
            put("input_fields", Arrays.asList("can_touch_toes", "sacral_angle_deg", "pain_present", "note"));
            put("next_step_rules", "fn_and_sacrum_normal→rotation_analysis（进入旋转动作解析）；abnormal_with_sacrum_normal→prone_backward_rocking；abnormal_with_sacrum_limited→active_straight_leg_raise");
            put("output_meaning", "根据触趾/骶骨角/疼痛分流到 rolling / prone / ASLR。");
            put("stop_if_pain", false);
            put("result", "");
            put("can_touch_toes", false);
            put("sacral_angle_deg", null);
            put("sacral_angle_status", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        flow.put("active_straight_leg_raise", new LinkedHashMap<String, Object>() {{
            put("node_code", "active_straight_leg_raise");
            put("node_name_zh", "主动直腿抬高（ASLR）");
            put("purpose", "测试膝伸直状态下髋关节主动屈曲能力。");
            put("instructions", "仰卧位记录左右抬高角度，正常参考>70°，并记录疼痛。");
            put("clinical_notes", "ASLR 与 PSLR 联合用于区分后链TED/髋JMD 与核心稳定或主动屈髋力量问题。");
            put("result_type", "");
            put("result_code", "");
            put("input_fields", Arrays.asList("left_aslr_deg", "right_aslr_deg", "pain_present", "note"));
            put("next_step_rules", "FN→prone_backward_rocking；DN/FP/DP→passive_straight_leg_raise");
            put("output_meaning", "FN 进入俯卧位向后摆动；DN/FP/DP 进入 PSLR。");
            put("stop_if_pain", false);
            put("result", "");
            put("left_aslr_deg", null);
            put("right_aslr_deg", null);
            put("bilateral_summary", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        flow.put("passive_straight_leg_raise", new LinkedHashMap<String, Object>() {{
            put("node_code", "passive_straight_leg_raise");
            put("node_name_zh", "被动直腿抬高（PSLR）");
            put("purpose", "鉴别后链 TED / 髋关节 JMD 与主动控制不足。");
            put("instructions", "记录左右被动角度，并与 ASLR 比较。");
            put("clinical_notes", "PSLR>80°或明显优于ASLR支持核心稳定/主动屈髋控制不足；PSLR<=ASLR更支持后链紧张或髋灵活性不足。");
            put("result_type", "");
            put("result_code", "");
            put("input_fields", Arrays.asList("left_pslr_deg", "right_pslr_deg", "left_aslr_deg", "right_aslr_deg", "pain_present", "note"));
            put("next_step_rules", "fn_gt_80→rolling；fn_gap_gt_10_and_lt_80→supine_double_knees_to_chest；fp_or_dp→停止；dn_pslr_lte_aslr→supine_double_knees_to_chest");
            put("output_meaning", "按 PSLR/ASLR 关系分流到 rolling / 双膝触胸；疼痛则停止。");
            put("stop_if_pain", true);
            put("result", "");
            put("left_pslr_deg", null);
            put("right_pslr_deg", null);
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        flow.put("prone_backward_rocking", new LinkedHashMap<String, Object>() {{
            put("node_code", "prone_backward_rocking");
            put("node_name_zh", "俯卧位向后摆动");
            put("purpose", "判断不负重姿势下脊柱屈曲能力。");
            put("instructions", "胸膝位后摆，观察臀部贴近足跟与胸廓触腿情况。");
            put("clinical_notes", "若膝关节不适可改用仰卧位双膝触胸；FN更支持负重下脊柱稳定/控制问题，DN更支持脊柱JMD/TED。");
            put("result_type", "");
            put("result_code", "");
            put("input_fields", Arrays.asList("result", "pain_present", "note"));
            put("next_step_rules", "FN/DN 输出结果；FP/DP 停止并优先处理疼痛");
            put("output_meaning", "FN 输出负重脊柱屈曲SMCD；DN 输出脊柱JMD/TED；FP/DP 停止。");
            put("stop_if_pain", true);
            put("result", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        flow.put("supine_double_knees_to_chest", new LinkedHashMap<String, Object>() {{
            put("node_code", "supine_double_knees_to_chest");
            put("node_name_zh", "仰卧位双膝触胸");
            put("purpose", "评估不负重姿势下髋关节灵活性。");
            put("instructions", "双膝抱胸，观察大腿是否可压近胸部及疼痛反应。");
            put("clinical_notes", "FN更支持后链TED和/或主动屈髋SMCD，DN更支持髋JMD和/或后链TED。");
            put("result_type", "");
            put("result_code", "");
            put("input_fields", Arrays.asList("result", "pain_present", "note"));
            put("next_step_rules", "FN/DN 输出结果；FP/DP 停止并优先处理疼痛");
            put("output_meaning", "FN 输出后链TED/主动屈髋SMCD；DN 输出髋JMD/后链TED；FP/DP 停止。");
            put("stop_if_pain", true);
            put("result", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        flow.put("rolling_analysis_result", new LinkedHashMap<String, Object>() {{
            put("node_code", "rolling_analysis_result");
            put("node_name_zh", "滚动解析结果");
            put("purpose", "区分基础屈曲模式 SMCD 与负重屈曲模式 SMCD。");
            put("instructions", "可接入滚动模块结果，暂未接入时可手工记录占位结果。");
            put("clinical_notes", "该节点通常出现在长坐触趾FN或PSLR>80°之后，用于细化SMCD方向。");
            put("result_type", "");
            put("result_code", "");
            put("input_fields", Arrays.asList("result", "pain_present", "note"));
            put("next_step_rules", "FN/DN 输出结果；FP/DP 停止并优先处理疼痛");
            put("output_meaning", "FN 输出负重髋屈曲SMCD；DN 输出基础屈曲模式SMCD；FP/DP 停止。");
            put("stop_if_pain", true);
            put("result", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("primary_region", "");
        summary.put("likely_pattern", new ArrayList<>());
        summary.put("single_vs_bilateral_pattern", "");
        summary.put("hip_flexion_mobility_issue", false);
        summary.put("posterior_chain_ted_issue", false);
        summary.put("spinal_flexion_mobility_issue", false);
        summary.put("loaded_flexion_smcd_issue", false);
        summary.put("base_flexion_pattern_smcd_issue", false);
        summary.put("core_or_active_hip_flexion_smcd_issue", false);
        summary.put("rotation_flow_needed", false);
        summary.put("stop_and_treat_pain", false);
        summary.put("manual_review_required", false);
        summary.put("summary_text", "");

        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("flow_nodes", flow);
        analysis.put("flexion_flow", flow);
        analysis.put("summary", summary);
        return analysis;
    }

    private Map<String, Object> normalizeMsfAnalysis(Map<String, Object> input, Map<String, Object> normalizedFields) {
        Map<String, Object> analysis = buildDefaultMsfAnalysisMap();
        Map<String, Object> flow = castToMap(analysis.get("flow_nodes"));
        Map<String, Object> summary = castToMap(analysis.get("summary"));

        String singleLeg = toStringValue(normalizedFields.get("single_leg_standing_forward_flexion_result"));
        String singleLegAsym = toStringValue(normalizedFields.get("single_leg_standing_forward_flexion_asymmetry"));
        Map<String, Object> singleNode = castToMap(flow.get("single_leg_stance_forward_bend"));
        singleNode.put("left_result", StrUtil.equals(singleLegAsym, "左侧更差") ? "DN" : "");
        singleNode.put("right_result", StrUtil.equals(singleLegAsym, "右侧更差") ? "DN" : "");
        singleNode.put("bilateral_summary", singleLeg);
        singleNode.put("result", singleLeg);
        singleNode.put("pain_present", StrUtil.contains(singleLeg, "疼痛"));
        singleNode.put("note", toStringValue(normalizedFields.get("single_leg_standing_forward_flexion_note")));
        if (StrUtil.equals(singleLeg, "双侧功能正常且无痛")) {
            singleNode.put("result_type", "bilateral_FN");
        } else if (StrUtil.equals(singleLeg, "双侧功能障碍或疼痛")) {
            singleNode.put("result_type", "bilateral_abnormal_or_pain");
        } else if (StrUtil.equals(singleLeg, "单侧功能障碍或疼痛")) {
            singleNode.put("result_type", "unilateral_abnormal_or_pain");
        }
        singleNode.put("result_code", toStringValue(singleNode.get("result_type")));
        singleNode.put("summary_text", StrUtil.isBlank(singleLeg) ? "单腿站立体前屈尚未录入。" :
                "单腿站立体前屈结果：" + singleLeg + (StrUtil.isBlank(singleLegAsym) ? "" : "（" + singleLegAsym + "）") + "。");

        String longSitResult = toStringValue(normalizedFields.get("long_sit_toe_touch_result"));
        String longSitSacralStatus = toStringValue(normalizedFields.get("long_sit_sacral_angle_status"));
        boolean longSitCanTouch = StrUtil.equals(toStringValue(normalizedFields.get("long_sit_toe_touch_reach_status")), "可触及足趾");
        Number longSitSacralDeg = normalizeNumber(normalizedFields.get("long_sit_sacral_angle_deg"));
        boolean longSitPainPresent = isSfmaPainResult(longSitResult);
        String longSitResultType = deriveMsfLongSitResultType(longSitCanTouch, longSitSacralDeg, longSitSacralStatus, longSitPainPresent);
        Map<String, Object> longSitNode = castToMap(flow.get("long_sit_toe_touch"));
        longSitNode.put("can_touch_toes", longSitCanTouch);
        longSitNode.put("sacral_angle_deg", longSitSacralDeg);
        longSitNode.put("sacral_angle_status", longSitSacralStatus);
        longSitNode.put("result", longSitResult);
        longSitNode.put("result_type", longSitResultType);
        longSitNode.put("result_code", longSitResultType);
        longSitNode.put("pain_present", longSitPainPresent);
        longSitNode.put("note", toStringValue(normalizedFields.get("long_sit_toe_touch_note")));
        longSitNode.put("summary_text", StrUtil.isBlank(longSitResult) ? "长坐位触趾尚未录入。" :
                "长坐位触趾：" + longSitResult + (StrUtil.isBlank(longSitSacralStatus) ? "" : "，骶骨角" + longSitSacralStatus) + "。");

        String aslrResult = toStringValue(normalizedFields.get("aslr_result"));
        Map<String, Object> aslrNode = castToMap(flow.get("active_straight_leg_raise"));
        aslrNode.put("left_aslr_deg", normalizeNumber(normalizedFields.get("aslr_left_deg")));
        aslrNode.put("right_aslr_deg", normalizeNumber(normalizedFields.get("aslr_right_deg")));
        aslrNode.put("result", aslrResult);
        String aslrResultType = StrUtil.equals(aslrResult, CLASS_FN) ? CLASS_FN : (StrUtil.isNotBlank(aslrResult) ? "DN_or_FP_or_DP" : "");
        aslrNode.put("result_type", aslrResultType);
        aslrNode.put("result_code", aslrResultType);
        aslrNode.put("bilateral_summary", aslrResult);
        aslrNode.put("pain_present", isSfmaPainResult(aslrResult));
        aslrNode.put("note", toStringValue(normalizedFields.get("aslr_note")));
        aslrNode.put("summary_text", StrUtil.isBlank(aslrResult) ? "ASLR 尚未录入。" :
                "ASLR 结果：" + aslrResult + "。");

        String pslrResult = toStringValue(normalizedFields.get("pslr_result"));
        Number pslrThresholdNum = normalizeNumber(normalizedFields.get("pslr_threshold_ref"));
        String pslrResultType = deriveMsfPslrResultType(
                pslrResult,
                normalizeNumber(normalizedFields.get("pslr_left_deg")),
                normalizeNumber(normalizedFields.get("pslr_right_deg")),
                normalizeNumber(normalizedFields.get("aslr_left_deg")),
                normalizeNumber(normalizedFields.get("aslr_right_deg")),
                pslrThresholdNum == null ? 80 : pslrThresholdNum.intValue()
        );
        Map<String, Object> pslrNode = castToMap(flow.get("passive_straight_leg_raise"));
        pslrNode.put("left_pslr_deg", normalizeNumber(normalizedFields.get("pslr_left_deg")));
        pslrNode.put("right_pslr_deg", normalizeNumber(normalizedFields.get("pslr_right_deg")));
        pslrNode.put("result", pslrResult);
        pslrNode.put("result_type", pslrResultType);
        pslrNode.put("result_code", pslrResultType);
        pslrNode.put("pain_present", StrUtil.equals(pslrResultType, "fp_or_dp"));
        pslrNode.put("note", toStringValue(normalizedFields.get("pslr_note")));
        pslrNode.put("summary_text", StrUtil.isBlank(pslrResult) ? "PSLR 尚未录入。" :
                "PSLR 结果：" + pslrResult + (StrUtil.isBlank(toStringValue(normalizedFields.get("pslr_vs_aslr_interpretation"))) ? "" :
                        "（" + toStringValue(normalizedFields.get("pslr_vs_aslr_interpretation")) + "）") + "。");

        String proneResult = toStringValue(normalizedFields.get("prone_rock_back_result"));
        Map<String, Object> proneNode = castToMap(flow.get("prone_backward_rocking"));
        proneNode.put("result", proneResult);
        String proneResultType = isSfmaPainResult(proneResult) ? "fp_or_dp" : proneResult;
        proneNode.put("result_type", proneResultType);
        proneNode.put("result_code", proneResultType);
        proneNode.put("pain_present", isSfmaPainResult(proneResult));
        proneNode.put("note", toStringValue(normalizedFields.get("prone_rock_back_note")));
        proneNode.put("summary_text", StrUtil.isBlank(proneResult) ? "俯卧位向后摆动尚未录入。" : "俯卧位向后摆动：" + proneResult + "。");

        String supineResult = toStringValue(normalizedFields.get("supine_knees_to_chest_result"));
        Map<String, Object> supineNode = castToMap(flow.get("supine_double_knees_to_chest"));
        supineNode.put("result", supineResult);
        String supineResultType = isSfmaPainResult(supineResult) ? "fp_or_dp" : supineResult;
        supineNode.put("result_type", supineResultType);
        supineNode.put("result_code", supineResultType);
        supineNode.put("pain_present", isSfmaPainResult(supineResult));
        supineNode.put("note", toStringValue(normalizedFields.get("supine_knees_to_chest_note")));
        supineNode.put("summary_text", StrUtil.isBlank(supineResult) ? "仰卧位双膝触胸尚未录入。" : "仰卧位双膝触胸：" + supineResult + "。");

        String rollingResult = toStringValue(normalizedFields.get("rolling_result"));
        Map<String, Object> rollingNode = castToMap(flow.get("rolling_analysis_result"));
        rollingNode.put("result", rollingResult);
        String rollingResultType = isSfmaPainResult(rollingResult) ? "fp_or_dp" : rollingResult;
        rollingNode.put("result_type", rollingResultType);
        rollingNode.put("result_code", rollingResultType);
        rollingNode.put("pain_present", isSfmaPainResult(rollingResult));
        rollingNode.put("note", toStringValue(normalizedFields.get("rolling_note")));
        rollingNode.put("summary_text", StrUtil.isBlank(rollingResult) ? "滚动解析结果尚未录入。" : "滚动解析结果：" + rollingResult + "。");

        boolean stopAndTreatPain = StrUtil.equals(toStringValue(normalizedFields.get("flow_next_step")), "停止并优先处理疼痛")
                || StrUtil.equals(pslrResultType, "fp_or_dp")
                || isSfmaPainResult(proneResult)
                || isSfmaPainResult(supineResult)
                || isSfmaPainResult(rollingResult);
        summary.put("single_vs_bilateral_pattern", toStringValue(singleNode.get("result_type")));
        summary.put("stop_and_treat_pain", stopAndTreatPain);
        summary.put("rotation_flow_needed", StrUtil.equals(longSitResultType, "fn_and_sacrum_normal"));
        summary.put("hip_flexion_mobility_issue",
                StrUtil.equals(longSitResultType, "abnormal_with_sacrum_limited")
                        || StrUtil.equals(supineResultType, "DN")
                        || StrUtil.equals(pslrResultType, "dn_pslr_lte_aslr"));
        summary.put("posterior_chain_ted_issue",
                StrUtil.equalsAny(supineResultType, "FN", "DN")
                        || StrUtil.equals(pslrResultType, "dn_pslr_lte_aslr"));
        summary.put("spinal_flexion_mobility_issue",
                StrUtil.equals(longSitResultType, "abnormal_with_sacrum_normal")
                        || StrUtil.equals(longSitResultType, "abnormal_with_sacrum_limited")
                        || StrUtil.equals(proneResultType, "DN"));
        summary.put("loaded_flexion_smcd_issue",
                (StrUtil.equals(longSitResultType, "fn_and_sacrum_normal") && StrUtil.equals(rollingResultType, CLASS_FN))
                        || StrUtil.equals(proneResultType, CLASS_FN));
        summary.put("base_flexion_pattern_smcd_issue",
                StrUtil.equals(longSitResultType, "fn_and_sacrum_normal") && StrUtil.equals(rollingResultType, CLASS_DN));
        summary.put("core_or_active_hip_flexion_smcd_issue",
                StrUtil.equalsAny(pslrResultType, "fn_gt_80", "fn_gap_gt_10_and_lt_80", "fn_gt_aslr_by_10_but_lt_80")
                        || StrUtil.equals(supineResultType, "FN"));
        boolean manualReviewRequired = stopAndTreatPain || Boolean.TRUE.equals(normalizedFields.get("needs_manual_review"))
                || StrUtil.equalsAny(toStringValue(normalizedFields.get("pain_dominant_pattern")), "疑似是", "明显是");
        summary.put("manual_review_required", manualReviewRequired);

        List<String> likelyPattern = new ArrayList<>();
        if (Boolean.TRUE.equals(summary.get("loaded_flexion_smcd_issue"))) {
            likelyPattern.add("负重下屈曲模式稳定性/运动控制问题倾向");
        }
        if (Boolean.TRUE.equals(summary.get("hip_flexion_mobility_issue"))) {
            likelyPattern.add("髋关节屈曲灵活性问题倾向");
        }
        if (Boolean.TRUE.equals(summary.get("posterior_chain_ted_issue"))) {
            likelyPattern.add("后链组织延展性问题倾向");
        }
        if (Boolean.TRUE.equals(summary.get("spinal_flexion_mobility_issue"))) {
            likelyPattern.add("脊柱屈曲灵活性问题倾向");
        }
        if (Boolean.TRUE.equals(summary.get("core_or_active_hip_flexion_smcd_issue"))) {
            likelyPattern.add("核心稳定/主动屈髋控制问题倾向");
        }
        if (Boolean.TRUE.equals(summary.get("base_flexion_pattern_smcd_issue"))) {
            likelyPattern.add("基础屈曲动作模式SMCD倾向");
        }
        if (Boolean.TRUE.equals(summary.get("rotation_flow_needed"))) {
            likelyPattern.add("建议继续进入旋转动作解析");
        }
        if (Boolean.TRUE.equals(summary.get("stop_and_treat_pain"))) {
            likelyPattern.add("当前应停止解析并优先处理疼痛");
        }
        summary.put("likely_pattern", likelyPattern);

        String primaryRegion = "";
        if (Boolean.TRUE.equals(summary.get("hip_flexion_mobility_issue"))) {
            primaryRegion = "髋-骨盆";
        } else if (Boolean.TRUE.equals(summary.get("spinal_flexion_mobility_issue"))) {
            primaryRegion = "脊柱";
        } else if (Boolean.TRUE.equals(summary.get("posterior_chain_ted_issue"))) {
            primaryRegion = "后链";
        } else if (Boolean.TRUE.equals(summary.get("loaded_flexion_smcd_issue"))) {
            primaryRegion = "负重屈曲控制";
        } else if (Boolean.TRUE.equals(summary.get("base_flexion_pattern_smcd_issue"))) {
            primaryRegion = "基础屈曲控制";
        }
        summary.put("primary_region", primaryRegion);
        if (Boolean.TRUE.equals(summary.get("stop_and_treat_pain"))) {
            summary.put("summary_text", "当前解析在疼痛性结果处停止，建议优先处理疼痛后再继续。");
        } else if (!likelyPattern.isEmpty()) {
            summary.put("summary_text", "多节段屈曲解析提示：" + String.join("；", likelyPattern) + "。");
        } else {
            summary.put("summary_text", "当前数据不足以形成明确流程结论，建议继续补充分解节点并结合人工复核。");
        }

        if (input != null && !input.isEmpty()) {
            Map<String, Object> inputSummary = castToMap(input.get("summary"));
            if (inputSummary != null && !inputSummary.isEmpty()) {
                String manualSummary = toStringValue(inputSummary.get("summary_text"));
                if (StrUtil.isNotBlank(manualSummary)) {
                    summary.put("summary_text", manualSummary);
                }
            }
        }
        analysis.put("flow_nodes", flow);
        analysis.put("flexion_flow", flow);
        return analysis;
    }

    private Map<String, Object> buildDefaultMseAnalysisMap() {
        Map<String, Object> flow = new LinkedHashMap<>();
        flow.put("trunk_extension_without_upper_extremity", new LinkedHashMap<String, Object>() {{
            put("node_code", "trunk_extension_without_upper_extremity");
            put("node_name_zh", "无上肢参与的躯体后伸");
            put("purpose", "排除肩关节和肩部肌群参与，观察无上肢参与情况下的脊柱/躯体后伸。");
            put("instructions", "患者站立双手叉腰后伸，观察肩关节垂线与髂前上棘垂线，并记录疼痛。");
            put("clinical_notes", "若 FN，优先进入上半身伸展流程；否则继续单腿站立后伸。");
            put("result_type", "");
            put("next_step_rules", "FN -> 上半身伸展流程；FP/DP/DN -> 单腿站立躯体后伸");
            put("stop_if_pain", false);
            put("result", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        flow.put("single_leg_stance_trunk_extension", new LinkedHashMap<String, Object>() {{
            put("node_code", "single_leg_stance_trunk_extension");
            put("node_name_zh", "单腿站立躯体后伸");
            put("purpose", "区分对称/不对称问题，聚焦单侧负重下伸展能力。");
            put("instructions", "左右分别执行单腿站立后伸，记录左右结果与双侧汇总。");
            put("clinical_notes", "双侧均 FN 提示对称性核心控制问题；任一侧异常继续俯卧撑。");
            put("result_type", "");
            put("next_step_rules", "双侧FN -> 上半身伸展流程；任一侧FP/DP/DN -> 俯卧撑");
            put("stop_if_pain", false);
            put("left_result", "");
            put("right_result", "");
            put("bilateral_summary", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        flow.put("prone_press_up", new LinkedHashMap<String, Object>() {{
            put("node_code", "prone_press_up");
            put("node_name_zh", "俯卧撑");
            put("purpose", "观察非负重姿势下的躯体后伸，区分负重与非负重伸展问题。");
            put("instructions", "俯卧位支撑后伸，必要时骨盆下垫约6cm后复测。");
            put("clinical_notes", "FN 往往提示负重下控制问题，仍需进入上半身与下半身伸展流程。");
            put("result_type", "");
            put("next_step_rules", "FN -> 上半身+下半身流程；FP/DP/DN -> 腰部固定（内旋）主动旋转/伸展");
            put("stop_if_pain", false);
            put("result", "");
            put("used_pad", false);
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        flow.put("lumbar_fixed_internal_rotation_active_extension_rotation", new LinkedHashMap<String, Object>() {{
            put("node_code", "lumbar_fixed_internal_rotation_active_extension_rotation");
            put("node_name_zh", "腰部固定（内旋）主动旋转/伸展");
            put("purpose", "在非负重且肩内旋状态下，观察胸椎伸展和旋转主动能力。");
            put("instructions", "俯卧位向后摆姿势下，左右分别执行主动旋转/伸展。");
            put("clinical_notes", "FN 进入俯卧位肘支撑；FP/DP/DN 进入被动旋转/伸展。");
            put("result_type", "");
            put("next_step_rules", "FN -> 俯卧位肘支撑旋转/伸展；FP/DP/DN -> 腰部固定（内旋）被动旋转/伸展");
            put("stop_if_pain", false);
            put("left_result", "");
            put("right_result", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        flow.put("lumbar_fixed_internal_rotation_passive_extension_rotation", new LinkedHashMap<String, Object>() {{
            put("node_code", "lumbar_fixed_internal_rotation_passive_extension_rotation");
            put("node_name_zh", "腰部固定（内旋）被动旋转/伸展");
            put("purpose", "观察胸椎在非负重、肩内旋状态下的被动伸展与旋转能力。");
            put("instructions", "俯卧位向后摆姿势下由治疗师被动测试，比较左右。");
            put("clinical_notes", "FP/DP 停止流程；FN/单侧DN/双侧DN进入上半身与下半身伸展流程。");
            put("result_type", "");
            put("next_step_rules", "FP/DP -> 停止并优先疼痛处理；FN/单侧DN/双侧DN -> 上半身+下半身流程");
            put("stop_if_pain", true);
            put("left_result", "");
            put("right_result", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        flow.put("prone_elbow_supported_extension_rotation", new LinkedHashMap<String, Object>() {{
            put("node_code", "prone_elbow_supported_extension_rotation");
            put("node_name_zh", "俯卧位肘支撑旋转/伸展");
            put("purpose", "作为腰椎通过性测试并观察疼痛诱发。");
            put("instructions", "俯卧位肘支撑完成旋转/伸展，左右分别记录。");
            put("clinical_notes", "FP/DP 停止流程；双侧FN或DN结果进入上半身与下半身伸展流程。");
            put("result_type", "");
            put("next_step_rules", "FP/DP -> 停止并优先疼痛处理；双侧FN/单侧DN/双侧DN -> 上半身+下半身流程");
            put("stop_if_pain", true);
            put("left_result", "");
            put("right_result", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});

        Map<String, Object> lowerFlow = new LinkedHashMap<>();
        lowerFlow.put("standing_hip_extension", new LinkedHashMap<String, Object>() {{
            put("node_code", "standing_hip_extension");
            put("node_name_zh", "站立位髋关节后伸");
            put("purpose", "评估负重位髋伸展与下肢支撑策略。");
            put("instructions", "双侧站立位髋后伸并比较。");
            put("clinical_notes", "FN（双侧>10°）可进入滚动；异常进入俯卧位髋关节主动后伸。");
            put("result_type", "");
            put("next_step_rules", "FN -> 滚动解析（下半身）；DN/FP/DP -> 俯卧位髋关节主动后伸");
            put("stop_if_pain", false);
            put("result", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        lowerFlow.put("prone_active_hip_extension", new LinkedHashMap<String, Object>() {{
            put("node_code", "prone_active_hip_extension");
            put("node_name_zh", "俯卧位髋关节主动后伸");
            put("purpose", "在非负重位评估主动髋伸展。");
            put("instructions", "俯卧位左右主动后伸并记录。");
            put("clinical_notes", "FN进入滚动；DN/FP/DP进入俯卧位髋关节被动后伸。");
            put("result_type", "");
            put("next_step_rules", "FN -> 滚动解析（下半身）；DN/FP/DP -> 俯卧位髋关节被动后伸");
            put("stop_if_pain", false);
            put("result", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        lowerFlow.put("prone_passive_hip_extension", new LinkedHashMap<String, Object>() {{
            put("node_code", "prone_passive_hip_extension");
            put("node_name_zh", "俯卧位髋关节被动后伸");
            put("purpose", "比较主动/被动差异，区分控制与灵活性。");
            put("instructions", "俯卧位被动髋后伸，与主动结果比较。");
            put("clinical_notes", "FN可进入改良托马斯；DN/FP/DP进入法伯尔；被动显著优于主动可进入滚动。");
            put("result_type", "");
            put("next_step_rules", "fn_gap_gt_25 -> 滚动解析（下半身）；FN -> 改良托马斯；DN/FP/DP -> 法伯尔");
            put("stop_if_pain", true);
            put("result", "");
            put("gap_percent", null);
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        lowerFlow.put("rolling_analysis_result_lower", new LinkedHashMap<String, Object>() {{
            put("node_code", "rolling_analysis_result_lower");
            put("node_name_zh", "滚动解析结果（下半身）");
            put("purpose", "评估基础伸展模式与负重髋伸展控制问题。");
            put("instructions", "记录滚动结果。");
            put("clinical_notes", "FN提示负重髋伸展SMCD；DN提示基础伸展模式SMCD；FP/DP疼痛终止。");
            put("result_type", "");
            put("next_step_rules", "FN/DN -> END；FP/DP -> STOP_PAIN");
            put("stop_if_pain", true);
            put("result", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        lowerFlow.put("faber_test", new LinkedHashMap<String, Object>() {{
            put("node_code", "faber_test");
            put("node_name_zh", "法伯尔试验");
            put("purpose", "筛查髋/骶髂灵活性与疼痛诱发。");
            put("instructions", "仰卧位执行 FABER 双侧比较。");
            put("clinical_notes", "FN或DN继续改良托马斯；FP/DP疼痛终止。");
            put("result_type", "");
            put("next_step_rules", "FN/DN -> 改良托马斯；FP/DP -> STOP_PAIN");
            put("stop_if_pain", true);
            put("result", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        lowerFlow.put("modified_thomas_test", new LinkedHashMap<String, Object>() {{
            put("node_code", "modified_thomas_test");
            put("node_name_zh", "改良托马斯试验");
            put("purpose", "区分前链/侧链TED、髋JMD与核心控制问题。");
            put("instructions", "按改良托马斯流程记录结果。");
            put("clinical_notes", "FN或分型FN提示不同组织链受限；DN提示髋JMD/TED；FP/DP疼痛终止。");
            put("result_type", "");
            put("next_step_rules", "FN/分型FN/DN -> END；FP/DP -> STOP_PAIN");
            put("stop_if_pain", true);
            put("result", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});

        Map<String, Object> upperFlow = new LinkedHashMap<>();
        upperFlow.put("single_shoulder_extension", new LinkedHashMap<String, Object>() {{
            put("node_code", "single_shoulder_extension");
            put("node_name_zh", "单肩后伸");
            put("purpose", "识别单侧上半身伸展障碍与疼痛。");
            put("instructions", "单臂上举过头并后伸，双侧比较。");
            put("clinical_notes", "DN/FP/DP进入仰卧位双髋屈曲背阔肌拉伸；FN可提示复查脊柱/颈椎。");
            put("result_type", "");
            put("next_step_rules", "FN -> 可结束上半身流程（建议复查脊柱/颈椎）；DN/FP/DP -> 仰卧位双髋屈曲背阔肌拉伸");
            put("stop_if_pain", false);
            put("result", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        upperFlow.put("supine_double_hip_flexion_lat_stretch", new LinkedHashMap<String, Object>() {{
            put("node_code", "supine_double_hip_flexion_lat_stretch");
            put("node_name_zh", "仰卧位双髋屈曲背阔肌拉伸");
            put("purpose", "评估不负重位背阔肌长度与肩屈曲模式。");
            put("instructions", "仰卧双髋屈曲位，双臂上举接近床面。");
            put("clinical_notes", "FN常提示负重上肢伸展SMCD；异常进入双髋伸展背阔肌拉伸。");
            put("result_type", "");
            put("next_step_rules", "FN -> END（可提示上肢稳定/控制）；DN/FP/DP -> 仰卧位双髋伸展背阔肌拉伸");
            put("stop_if_pain", false);
            put("result", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        upperFlow.put("supine_double_hip_extension_lat_stretch", new LinkedHashMap<String, Object>() {{
            put("node_code", "supine_double_hip_extension_lat_stretch");
            put("node_name_zh", "仰卧位双髋伸展背阔肌拉伸");
            put("purpose", "区分背阔肌后链问题与胸廓/肩带问题。");
            put("instructions", "仰卧双髋伸展位，记录手臂接近床面的变化。");
            put("clinical_notes", "轻微改善或异常均建议继续腰部固定（外旋）旋转/伸展。");
            put("result_type", "");
            put("next_step_rules", "任一结果 -> 腰部固定（外旋）旋转/伸展（FP/DP同时提示疼痛复核）");
            put("stop_if_pain", false);
            put("result", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        upperFlow.put("lumbar_fixed_external_rotation_extension", new LinkedHashMap<String, Object>() {{
            put("node_code", "lumbar_fixed_external_rotation_extension");
            put("node_name_zh", "腰部固定（外旋）旋转/伸展");
            put("purpose", "降低肩胛稳定要求，观察胸椎伸展旋转。");
            put("instructions", "俯卧跪位手外旋头后，左右旋转/伸展。");
            put("clinical_notes", "FN多提示肩胛/盂肱稳定控制问题；异常进入内旋主动旋转/伸展。");
            put("result_type", "");
            put("next_step_rules", "FN -> END（肩胛/盂肱控制方向）；DN/FP/DP -> 腰部固定（内旋）主动旋转/伸展（上半身）");
            put("stop_if_pain", false);
            put("result", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        upperFlow.put("lumbar_fixed_internal_rotation_active_extension_rotation_upper", new LinkedHashMap<String, Object>() {{
            put("node_code", "lumbar_fixed_internal_rotation_active_extension_rotation_upper");
            put("node_name_zh", "腰部固定（内旋）主动旋转/伸展（上半身）");
            put("purpose", "在上半身流程中进一步确认胸椎主动表现。");
            put("instructions", "同内旋主动旋转/伸展方法记录。");
            put("clinical_notes", "FN可提示肩带JMD/TED方向；异常进入内旋被动旋转/伸展。");
            put("result_type", "");
            put("next_step_rules", "FN -> END（肩带JMD/TED方向）；DN/FP/DP -> 腰部固定（内旋）被动旋转/伸展（上半身）");
            put("stop_if_pain", false);
            put("result", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});
        upperFlow.put("lumbar_fixed_internal_rotation_passive_extension_rotation_upper", new LinkedHashMap<String, Object>() {{
            put("node_code", "lumbar_fixed_internal_rotation_passive_extension_rotation_upper");
            put("node_name_zh", "腰部固定（内旋）被动旋转/伸展（上半身）");
            put("purpose", "区分胸椎双侧/单侧结构限制与控制问题。");
            put("instructions", "同内旋被动旋转/伸展方法记录。");
            put("clinical_notes", "FP/DP疼痛终止；FN提示胸椎双侧SMCD；DN提示胸椎JMD/TED。");
            put("result_type", "");
            put("next_step_rules", "FP/DP -> STOP_PAIN；FN/单侧DN/双侧DN -> END");
            put("stop_if_pain", true);
            put("result", "");
            put("pain_present", false);
            put("note", "");
            put("summary_text", "");
        }});

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("primary_region", "");
        summary.put("likely_pattern", new ArrayList<>());
        summary.put("thoracic_extension_issue", false);
        summary.put("lumbar_extension_issue", false);
        summary.put("weight_bearing_stability_issue", false);
        summary.put("pain_dominant", false);
        summary.put("upper_body_extension_flow_needed", false);
        summary.put("lower_body_extension_flow_needed", false);
        summary.put("next_flow_targets", new ArrayList<>());
        summary.put("stop_and_treat_pain", false);
        summary.put("manual_review_required", false);
        summary.put("summary_text", "");

        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("spinal_extension_flow", flow);
        analysis.put("lower_body_extension_flow", lowerFlow);
        analysis.put("upper_body_extension_flow", upperFlow);
        analysis.put("summary", summary);
        return analysis;
    }

    private Map<String, Object> normalizeMseAnalysis(Map<String, Object> input, Map<String, Object> normalizedFields) {
        Map<String, Object> analysis = buildDefaultMseAnalysisMap();
        Map<String, Object> flow = castToMap(analysis.get("spinal_extension_flow"));
        Map<String, Object> lowerFlow = castToMap(analysis.get("lower_body_extension_flow"));
        Map<String, Object> upperFlow = castToMap(analysis.get("upper_body_extension_flow"));
        Map<String, Object> summary = castToMap(analysis.get("summary"));

        Map<String, Object> inputFlow = castToMap(input == null ? null : input.get("spinal_extension_flow"));
        mergeFlowNodes(flow, inputFlow);
        Map<String, Object> inputLowerFlow = castToMap(input == null ? null : input.get("lower_body_extension_flow"));
        mergeFlowNodes(lowerFlow, inputLowerFlow);
        Map<String, Object> inputUpperFlow = castToMap(input == null ? null : input.get("upper_body_extension_flow"));
        mergeFlowNodes(upperFlow, inputUpperFlow);

        Set<String> enabledNodes = new LinkedHashSet<>();
        enabledNodes.add("trunk_extension_without_upper_extremity");
        boolean stopAndTreatPain = false;
        boolean upperFlowNeeded = false;
        boolean lowerFlowNeeded = false;
        boolean weightBearingIssue = false;
        boolean thoracicIssue = false;
        boolean lumbarIssue = false;

        Map<String, Object> node1 = castToMap(flow.get("trunk_extension_without_upper_extremity"));
        String node1Result = normalizeClassification(node1.get("result_type"));
        node1.put("result_type", node1Result);
        node1.put("pain_present", Boolean.TRUE.equals(node1.get("pain_present")) || isSfmaPainResult(node1Result));
        node1.put("result", node1Result);
        node1.put("summary_text", StrUtil.isBlank(node1Result)
                ? "无上肢参与的躯体后伸尚未录入。"
                : "无上肢参与的躯体后伸：" + node1Result + "。");
        if (StrUtil.isNotBlank(node1Result)) {
            if (StrUtil.equals(node1Result, CLASS_FN)) {
                upperFlowNeeded = true;
                weightBearingIssue = true;
            } else {
                enabledNodes.add("single_leg_stance_trunk_extension");
            }
        }

        Map<String, Object> node2 = castToMap(flow.get("single_leg_stance_trunk_extension"));
        String node2Left = normalizeClassification(node2.get("left_result"));
        String node2Right = normalizeClassification(node2.get("right_result"));
        node2.put("left_result", node2Left);
        node2.put("right_result", node2Right);
        String bilateralSummary = toStringValue(node2.get("bilateral_summary"));
        if (StrUtil.isBlank(bilateralSummary)) {
            bilateralSummary = deriveMseSingleLegBilateralSummary(node2Left, node2Right);
            node2.put("bilateral_summary", bilateralSummary);
        }
        node2.put("pain_present", Boolean.TRUE.equals(node2.get("pain_present"))
                || isSfmaPainResult(node2Left) || isSfmaPainResult(node2Right));
        node2.put("summary_text", StrUtil.isBlank(bilateralSummary)
                ? "单腿站立躯体后伸尚未录入。"
                : "单腿站立躯体后伸：" + bilateralSummary + "（左:" + StrUtil.blankToDefault(node2Left, "-")
                + " / 右:" + StrUtil.blankToDefault(node2Right, "-") + "）。");
        if (enabledNodes.contains("single_leg_stance_trunk_extension") && StrUtil.isNotBlank(bilateralSummary)) {
            if (StrUtil.equals(bilateralSummary, "bilateral_FN")) {
                upperFlowNeeded = true;
                weightBearingIssue = true;
            } else {
                enabledNodes.add("prone_press_up");
            }
        }

        Map<String, Object> node3 = castToMap(flow.get("prone_press_up"));
        String node3Result = normalizeClassification(node3.get("result_type"));
        node3.put("result_type", node3Result);
        node3.put("pain_present", Boolean.TRUE.equals(node3.get("pain_present")) || isSfmaPainResult(node3Result));
        node3.put("result", node3Result);
        node3.put("summary_text", StrUtil.isBlank(node3Result)
                ? "俯卧撑尚未录入。"
                : "俯卧撑：" + node3Result + (Boolean.TRUE.equals(node3.get("used_pad")) ? "（使用垫高）" : "") + "。");
        if (enabledNodes.contains("prone_press_up") && StrUtil.isNotBlank(node3Result)) {
            if (StrUtil.equals(node3Result, CLASS_FN)) {
                upperFlowNeeded = true;
                lowerFlowNeeded = true;
                weightBearingIssue = true;
            } else {
                enabledNodes.add("lumbar_fixed_internal_rotation_active_extension_rotation");
            }
        }

        Map<String, Object> node4 = castToMap(flow.get("lumbar_fixed_internal_rotation_active_extension_rotation"));
        String node4Left = normalizeClassification(node4.get("left_result"));
        String node4Right = normalizeClassification(node4.get("right_result"));
        String node4Result = normalizeClassification(node4.get("result_type"));
        if (StrUtil.isBlank(node4Result)) {
            node4Result = deriveMseNode4ResultType(node4Left, node4Right);
        }
        node4.put("left_result", node4Left);
        node4.put("right_result", node4Right);
        node4.put("result_type", node4Result);
        node4.put("pain_present", Boolean.TRUE.equals(node4.get("pain_present")) || isSfmaPainResult(node4Result));
        node4.put("summary_text", StrUtil.isBlank(node4Result)
                ? "腰部固定（内旋）主动旋转/伸展尚未录入。"
                : "腰部固定（内旋）主动旋转/伸展：" + node4Result + "（左:" + StrUtil.blankToDefault(node4Left, "-")
                + " / 右:" + StrUtil.blankToDefault(node4Right, "-") + "）。");
        if (enabledNodes.contains("lumbar_fixed_internal_rotation_active_extension_rotation") && StrUtil.isNotBlank(node4Result)) {
            if (StrUtil.equals(node4Result, CLASS_FN)) {
                enabledNodes.add("prone_elbow_supported_extension_rotation");
            } else {
                enabledNodes.add("lumbar_fixed_internal_rotation_passive_extension_rotation");
            }
        }

        Map<String, Object> node5 = castToMap(flow.get("lumbar_fixed_internal_rotation_passive_extension_rotation"));
        String node5Left = normalizeClassification(node5.get("left_result"));
        String node5Right = normalizeClassification(node5.get("right_result"));
        String node5Result = toStringValue(node5.get("result_type"));
        if (!StrUtil.equalsAny(node5Result, CLASS_FN, CLASS_FP, CLASS_DP, "unilateral_DN", "bilateral_DN")) {
            node5Result = deriveMseNode5ResultType(node5Left, node5Right);
        }
        node5.put("left_result", node5Left);
        node5.put("right_result", node5Right);
        node5.put("result_type", node5Result);
        node5.put("pain_present", Boolean.TRUE.equals(node5.get("pain_present"))
                || StrUtil.equalsAny(node5Result, CLASS_FP, CLASS_DP));
        node5.put("summary_text", StrUtil.isBlank(node5Result)
                ? "腰部固定（内旋）被动旋转/伸展尚未录入。"
                : "腰部固定（内旋）被动旋转/伸展：" + node5Result + "（左:" + StrUtil.blankToDefault(node5Left, "-")
                + " / 右:" + StrUtil.blankToDefault(node5Right, "-") + "）。");
        if (enabledNodes.contains("lumbar_fixed_internal_rotation_passive_extension_rotation") && StrUtil.isNotBlank(node5Result)) {
            if (StrUtil.equalsAny(node5Result, CLASS_FP, CLASS_DP)) {
                stopAndTreatPain = true;
            } else {
                upperFlowNeeded = true;
                lowerFlowNeeded = true;
                thoracicIssue = true;
            }
        }

        Map<String, Object> node6 = castToMap(flow.get("prone_elbow_supported_extension_rotation"));
        String node6Left = normalizeClassification(node6.get("left_result"));
        String node6Right = normalizeClassification(node6.get("right_result"));
        String node6Result = toStringValue(node6.get("result_type"));
        if (!StrUtil.equalsAny(node6Result, "bilateral_FN", "unilateral_DN", "bilateral_DN", CLASS_FP, CLASS_DP)) {
            node6Result = deriveMseNode6ResultType(node6Left, node6Right);
        }
        node6.put("left_result", node6Left);
        node6.put("right_result", node6Right);
        node6.put("result_type", node6Result);
        node6.put("pain_present", Boolean.TRUE.equals(node6.get("pain_present"))
                || StrUtil.equalsAny(node6Result, CLASS_FP, CLASS_DP));
        node6.put("summary_text", StrUtil.isBlank(node6Result)
                ? "俯卧位肘支撑旋转/伸展尚未录入。"
                : "俯卧位肘支撑旋转/伸展：" + node6Result + "（左:" + StrUtil.blankToDefault(node6Left, "-")
                + " / 右:" + StrUtil.blankToDefault(node6Right, "-") + "）。");
        if (enabledNodes.contains("prone_elbow_supported_extension_rotation") && StrUtil.isNotBlank(node6Result)) {
            if (StrUtil.equalsAny(node6Result, CLASS_FP, CLASS_DP)) {
                stopAndTreatPain = true;
            } else {
                upperFlowNeeded = true;
                lowerFlowNeeded = true;
                lumbarIssue = true;
                if (StrUtil.equals(node6Result, "bilateral_FN")) {
                    weightBearingIssue = true;
                }
            }
        }

        if (upperFlowNeeded) {
            enabledNodes.add("single_shoulder_extension");
        }
        if (lowerFlowNeeded) {
            enabledNodes.add("standing_hip_extension");
        }

        // 下半身伸展流程
        Map<String, Object> lowerNode1 = castToMap(lowerFlow.get("standing_hip_extension"));
        String lowerNode1Result = normalizeClassification(lowerNode1.get("result_type"));
        lowerNode1.put("result_type", lowerNode1Result);
        lowerNode1.put("result", lowerNode1Result);
        lowerNode1.put("pain_present", Boolean.TRUE.equals(lowerNode1.get("pain_present")) || isSfmaPainResult(lowerNode1Result));
        lowerNode1.put("summary_text", StrUtil.isBlank(lowerNode1Result)
                ? "站立位髋关节后伸尚未录入。"
                : "站立位髋关节后伸：" + lowerNode1Result + "。");
        if (!stopAndTreatPain && enabledNodes.contains("standing_hip_extension") && StrUtil.isNotBlank(lowerNode1Result)) {
            if (StrUtil.equals(lowerNode1Result, CLASS_FN)) {
                enabledNodes.add("rolling_analysis_result_lower");
                weightBearingIssue = true;
            } else {
                enabledNodes.add("prone_active_hip_extension");
            }
        }

        Map<String, Object> lowerNode2 = castToMap(lowerFlow.get("prone_active_hip_extension"));
        String lowerNode2Result = normalizeClassification(lowerNode2.get("result_type"));
        lowerNode2.put("result_type", lowerNode2Result);
        lowerNode2.put("result", lowerNode2Result);
        lowerNode2.put("pain_present", Boolean.TRUE.equals(lowerNode2.get("pain_present")) || isSfmaPainResult(lowerNode2Result));
        lowerNode2.put("summary_text", StrUtil.isBlank(lowerNode2Result)
                ? "俯卧位髋关节主动后伸尚未录入。"
                : "俯卧位髋关节主动后伸：" + lowerNode2Result + "。");
        if (!stopAndTreatPain && enabledNodes.contains("prone_active_hip_extension") && StrUtil.isNotBlank(lowerNode2Result)) {
            if (StrUtil.equals(lowerNode2Result, CLASS_FN)) {
                enabledNodes.add("rolling_analysis_result_lower");
            } else {
                enabledNodes.add("prone_passive_hip_extension");
            }
        }

        Map<String, Object> lowerNode3 = castToMap(lowerFlow.get("prone_passive_hip_extension"));
        String lowerNode3Result = toStringValue(lowerNode3.get("result_type"));
        if (!StrUtil.equalsAny(lowerNode3Result, CLASS_FN, CLASS_FP, CLASS_DP, CLASS_DN, "fn_gap_gt_25")) {
            lowerNode3Result = "";
        }
        lowerNode3.put("result_type", lowerNode3Result);
        lowerNode3.put("result", lowerNode3Result);
        lowerNode3.put("pain_present", Boolean.TRUE.equals(lowerNode3.get("pain_present"))
                || StrUtil.equalsAny(lowerNode3Result, CLASS_FP, CLASS_DP));
        lowerNode3.put("summary_text", StrUtil.isBlank(lowerNode3Result)
                ? "俯卧位髋关节被动后伸尚未录入。"
                : "俯卧位髋关节被动后伸：" + lowerNode3Result + "。");
        if (!stopAndTreatPain && enabledNodes.contains("prone_passive_hip_extension") && StrUtil.isNotBlank(lowerNode3Result)) {
            if (StrUtil.equalsAny(lowerNode3Result, CLASS_FP, CLASS_DP)) {
                stopAndTreatPain = true;
            } else if (StrUtil.equals(lowerNode3Result, "fn_gap_gt_25")) {
                enabledNodes.add("rolling_analysis_result_lower");
            } else if (StrUtil.equals(lowerNode3Result, CLASS_FN)) {
                enabledNodes.add("modified_thomas_test");
            } else {
                enabledNodes.add("faber_test");
            }
        }

        Map<String, Object> lowerNode4 = castToMap(lowerFlow.get("rolling_analysis_result_lower"));
        String lowerNode4Result = normalizeClassification(lowerNode4.get("result_type"));
        lowerNode4.put("result_type", lowerNode4Result);
        lowerNode4.put("result", lowerNode4Result);
        lowerNode4.put("pain_present", Boolean.TRUE.equals(lowerNode4.get("pain_present")) || isSfmaPainResult(lowerNode4Result));
        lowerNode4.put("summary_text", StrUtil.isBlank(lowerNode4Result)
                ? "滚动解析（下半身）尚未录入。"
                : "滚动解析（下半身）：" + lowerNode4Result + "。");
        if (!stopAndTreatPain && enabledNodes.contains("rolling_analysis_result_lower")
                && StrUtil.equalsAny(lowerNode4Result, CLASS_FP, CLASS_DP)) {
            stopAndTreatPain = true;
        }

        Map<String, Object> lowerNode5 = castToMap(lowerFlow.get("faber_test"));
        String lowerNode5Result = normalizeClassification(lowerNode5.get("result_type"));
        lowerNode5.put("result_type", lowerNode5Result);
        lowerNode5.put("result", lowerNode5Result);
        lowerNode5.put("pain_present", Boolean.TRUE.equals(lowerNode5.get("pain_present")) || isSfmaPainResult(lowerNode5Result));
        lowerNode5.put("summary_text", StrUtil.isBlank(lowerNode5Result)
                ? "法伯尔试验尚未录入。"
                : "法伯尔试验：" + lowerNode5Result + "。");
        if (!stopAndTreatPain && enabledNodes.contains("faber_test") && StrUtil.isNotBlank(lowerNode5Result)) {
            if (StrUtil.equalsAny(lowerNode5Result, CLASS_FP, CLASS_DP)) {
                stopAndTreatPain = true;
            } else {
                enabledNodes.add("modified_thomas_test");
            }
        }

        Map<String, Object> lowerNode6 = castToMap(lowerFlow.get("modified_thomas_test"));
        String lowerNode6Result = toStringValue(lowerNode6.get("result_type"));
        if (!StrUtil.equalsAny(lowerNode6Result, CLASS_FN, CLASS_FP, CLASS_DP, CLASS_DN, "fn_with_knee_extension",
                "fn_with_hip_abduction", "fn_with_both")) {
            lowerNode6Result = "";
        }
        lowerNode6.put("result_type", lowerNode6Result);
        lowerNode6.put("result", lowerNode6Result);
        lowerNode6.put("pain_present", Boolean.TRUE.equals(lowerNode6.get("pain_present"))
                || StrUtil.equalsAny(lowerNode6Result, CLASS_FP, CLASS_DP));
        lowerNode6.put("summary_text", StrUtil.isBlank(lowerNode6Result)
                ? "改良托马斯试验尚未录入。"
                : "改良托马斯试验：" + lowerNode6Result + "。");
        if (!stopAndTreatPain && enabledNodes.contains("modified_thomas_test")
                && StrUtil.equalsAny(lowerNode6Result, CLASS_FP, CLASS_DP)) {
            stopAndTreatPain = true;
        }

        // 上半身伸展流程
        Map<String, Object> upperNode1 = castToMap(upperFlow.get("single_shoulder_extension"));
        String upperNode1Result = normalizeClassification(upperNode1.get("result_type"));
        upperNode1.put("result_type", upperNode1Result);
        upperNode1.put("result", upperNode1Result);
        upperNode1.put("pain_present", Boolean.TRUE.equals(upperNode1.get("pain_present")) || isSfmaPainResult(upperNode1Result));
        upperNode1.put("summary_text", StrUtil.isBlank(upperNode1Result)
                ? "单肩后伸尚未录入。"
                : "单肩后伸：" + upperNode1Result + "。");
        if (!stopAndTreatPain && enabledNodes.contains("single_shoulder_extension") && StrUtil.isNotBlank(upperNode1Result)
                && !StrUtil.equals(upperNode1Result, CLASS_FN)) {
            enabledNodes.add("supine_double_hip_flexion_lat_stretch");
        }

        Map<String, Object> upperNode2 = castToMap(upperFlow.get("supine_double_hip_flexion_lat_stretch"));
        String upperNode2Result = normalizeClassification(upperNode2.get("result_type"));
        upperNode2.put("result_type", upperNode2Result);
        upperNode2.put("result", upperNode2Result);
        upperNode2.put("pain_present", Boolean.TRUE.equals(upperNode2.get("pain_present")) || isSfmaPainResult(upperNode2Result));
        upperNode2.put("summary_text", StrUtil.isBlank(upperNode2Result)
                ? "仰卧位双髋屈曲背阔肌拉伸尚未录入。"
                : "仰卧位双髋屈曲背阔肌拉伸：" + upperNode2Result + "。");
        if (!stopAndTreatPain && enabledNodes.contains("supine_double_hip_flexion_lat_stretch") && StrUtil.isNotBlank(upperNode2Result)
                && !StrUtil.equals(upperNode2Result, CLASS_FN)) {
            enabledNodes.add("supine_double_hip_extension_lat_stretch");
        }

        Map<String, Object> upperNode3 = castToMap(upperFlow.get("supine_double_hip_extension_lat_stretch"));
        String upperNode3Result = toStringValue(upperNode3.get("result_type"));
        if (!StrUtil.equalsAny(upperNode3Result, CLASS_FN, CLASS_FP, CLASS_DP, CLASS_DN, "partial_improvement")) {
            upperNode3Result = "";
        }
        upperNode3.put("result_type", upperNode3Result);
        upperNode3.put("result", upperNode3Result);
        upperNode3.put("pain_present", Boolean.TRUE.equals(upperNode3.get("pain_present"))
                || StrUtil.equalsAny(upperNode3Result, CLASS_FP, CLASS_DP));
        upperNode3.put("summary_text", StrUtil.isBlank(upperNode3Result)
                ? "仰卧位双髋伸展背阔肌拉伸尚未录入。"
                : "仰卧位双髋伸展背阔肌拉伸：" + upperNode3Result + "。");
        if (!stopAndTreatPain && enabledNodes.contains("supine_double_hip_extension_lat_stretch") && StrUtil.isNotBlank(upperNode3Result)) {
            enabledNodes.add("lumbar_fixed_external_rotation_extension");
        }

        Map<String, Object> upperNode4 = castToMap(upperFlow.get("lumbar_fixed_external_rotation_extension"));
        String upperNode4Result = normalizeClassification(upperNode4.get("result_type"));
        upperNode4.put("result_type", upperNode4Result);
        upperNode4.put("result", upperNode4Result);
        upperNode4.put("pain_present", Boolean.TRUE.equals(upperNode4.get("pain_present")) || isSfmaPainResult(upperNode4Result));
        upperNode4.put("summary_text", StrUtil.isBlank(upperNode4Result)
                ? "腰部固定（外旋）旋转/伸展尚未录入。"
                : "腰部固定（外旋）旋转/伸展：" + upperNode4Result + "。");
        if (!stopAndTreatPain && enabledNodes.contains("lumbar_fixed_external_rotation_extension") && StrUtil.isNotBlank(upperNode4Result)
                && !StrUtil.equals(upperNode4Result, CLASS_FN)) {
            enabledNodes.add("lumbar_fixed_internal_rotation_active_extension_rotation_upper");
        }

        Map<String, Object> upperNode5 = castToMap(upperFlow.get("lumbar_fixed_internal_rotation_active_extension_rotation_upper"));
        String upperNode5Result = normalizeClassification(upperNode5.get("result_type"));
        upperNode5.put("result_type", upperNode5Result);
        upperNode5.put("result", upperNode5Result);
        upperNode5.put("pain_present", Boolean.TRUE.equals(upperNode5.get("pain_present")) || isSfmaPainResult(upperNode5Result));
        upperNode5.put("summary_text", StrUtil.isBlank(upperNode5Result)
                ? "腰部固定（内旋）主动旋转/伸展（上半身）尚未录入。"
                : "腰部固定（内旋）主动旋转/伸展（上半身）：" + upperNode5Result + "。");
        if (!stopAndTreatPain && enabledNodes.contains("lumbar_fixed_internal_rotation_active_extension_rotation_upper")
                && StrUtil.isNotBlank(upperNode5Result) && !StrUtil.equals(upperNode5Result, CLASS_FN)) {
            enabledNodes.add("lumbar_fixed_internal_rotation_passive_extension_rotation_upper");
        }

        Map<String, Object> upperNode6 = castToMap(upperFlow.get("lumbar_fixed_internal_rotation_passive_extension_rotation_upper"));
        String upperNode6Result = toStringValue(upperNode6.get("result_type"));
        if (!StrUtil.equalsAny(upperNode6Result, CLASS_FN, CLASS_FP, CLASS_DP, "unilateral_DN", "bilateral_DN")) {
            upperNode6Result = "";
        }
        upperNode6.put("result_type", upperNode6Result);
        upperNode6.put("result", upperNode6Result);
        upperNode6.put("pain_present", Boolean.TRUE.equals(upperNode6.get("pain_present"))
                || StrUtil.equalsAny(upperNode6Result, CLASS_FP, CLASS_DP));
        upperNode6.put("summary_text", StrUtil.isBlank(upperNode6Result)
                ? "腰部固定（内旋）被动旋转/伸展（上半身）尚未录入。"
                : "腰部固定（内旋）被动旋转/伸展（上半身）：" + upperNode6Result + "。");
        if (!stopAndTreatPain && enabledNodes.contains("lumbar_fixed_internal_rotation_passive_extension_rotation_upper")
                && StrUtil.equalsAny(upperNode6Result, CLASS_FP, CLASS_DP)) {
            stopAndTreatPain = true;
        }

        if (StrUtil.equalsAny(upperNode6Result, "unilateral_DN", "bilateral_DN")) {
            thoracicIssue = true;
        }

        boolean painDominant = stopAndTreatPain
                || mseNodeHasPain(node1)
                || mseNodeHasPain(node2)
                || mseNodeHasPain(node3)
                || mseNodeHasPain(node4)
                || mseNodeHasPain(node5)
                || mseNodeHasPain(node6)
                || mseNodeHasPain(lowerNode1)
                || mseNodeHasPain(lowerNode2)
                || mseNodeHasPain(lowerNode3)
                || mseNodeHasPain(lowerNode4)
                || mseNodeHasPain(lowerNode5)
                || mseNodeHasPain(lowerNode6)
                || mseNodeHasPain(upperNode1)
                || mseNodeHasPain(upperNode2)
                || mseNodeHasPain(upperNode3)
                || mseNodeHasPain(upperNode4)
                || mseNodeHasPain(upperNode5)
                || mseNodeHasPain(upperNode6);
        summary.put("thoracic_extension_issue", thoracicIssue);
        summary.put("lumbar_extension_issue", lumbarIssue);
        summary.put("weight_bearing_stability_issue", weightBearingIssue);
        summary.put("pain_dominant", painDominant);
        summary.put("upper_body_extension_flow_needed", upperFlowNeeded);
        summary.put("lower_body_extension_flow_needed", lowerFlowNeeded);
        List<String> nextFlowTargets = new ArrayList<>();
        if (upperFlowNeeded) {
            nextFlowTargets.add("upper_body_extension_flow");
        }
        if (lowerFlowNeeded) {
            nextFlowTargets.add("lower_body_extension_flow");
        }
        summary.put("next_flow_targets", nextFlowTargets);
        summary.put("stop_and_treat_pain", stopAndTreatPain);

        List<String> likelyPattern = new ArrayList<>();
        if (thoracicIssue) {
            likelyPattern.add("当前更像胸椎伸展问题");
        }
        if (lumbarIssue) {
            likelyPattern.add("当前更像腰椎伸展问题");
        }
        if (weightBearingIssue) {
            likelyPattern.add("当前更像负重下脊柱伸展稳定/运动控制问题");
        }
        if (upperFlowNeeded) {
            likelyPattern.add("当前应继续进入上半身伸展流程");
        }
        if (lowerFlowNeeded) {
            likelyPattern.add("当前应继续进入下半身伸展流程");
        }
        if (stopAndTreatPain) {
            likelyPattern.add("当前应停止解析并优先处理疼痛");
        }
        summary.put("likely_pattern", likelyPattern);

        String primaryRegion = "";
        if (thoracicIssue) {
            primaryRegion = "胸椎伸展链";
        } else if (lumbarIssue) {
            primaryRegion = "腰椎伸展链";
        } else if (weightBearingIssue) {
            primaryRegion = "负重伸展控制链";
        }
        summary.put("primary_region", primaryRegion);

        boolean manualReviewRequired = stopAndTreatPain
                || Boolean.TRUE.equals(normalizedFields.get("needs_manual_review"))
                || StrUtil.equalsAny(toStringValue(normalizedFields.get("pain_dominant_pattern")), "疑似是", "明显是")
                || StrUtil.contains(toStringValue(normalizedFields.get("pain_control_priority_hint")), "人工复核");
        summary.put("manual_review_required", manualReviewRequired);
        if (stopAndTreatPain) {
            summary.put("summary_text", "当前解析在疼痛性结果处停止，建议优先处理疼痛后再继续。");
        } else if (!likelyPattern.isEmpty()) {
            summary.put("summary_text", "多节段伸展解析提示：" + String.join("；", likelyPattern) + "。");
        } else {
            summary.put("summary_text", "当前数据不足以形成明确伸展流程结论，建议继续补充分解节点并结合人工复核。");
        }

        if (input != null && !input.isEmpty()) {
            Map<String, Object> inputSummary = castToMap(input.get("summary"));
            if (inputSummary != null && !inputSummary.isEmpty()) {
                String manualSummary = toStringValue(inputSummary.get("summary_text"));
                if (StrUtil.isNotBlank(manualSummary)) {
                    summary.put("summary_text", manualSummary);
                }
            }
        }
        return analysis;
    }

    private void mergeFlowNodes(Map<String, Object> targetFlow, Map<String, Object> sourceFlow) {
        if (targetFlow == null || sourceFlow == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : sourceFlow.entrySet()) {
            Map<String, Object> defaultNode = castToMap(targetFlow.get(entry.getKey()));
            Map<String, Object> incomingNode = castToMap(entry.getValue());
            if (defaultNode != null && incomingNode != null) {
                defaultNode.putAll(incomingNode);
            }
        }
    }

    private boolean mseNodeHasPain(Map<String, Object> node) {
        if (node == null || node.isEmpty()) {
            return false;
        }
        if (Boolean.TRUE.equals(node.get("pain_present"))) {
            return true;
        }
        return isSfmaPainResult(toStringValue(node.get("result_type")))
                || isSfmaPainResult(toStringValue(node.get("result")))
                || isSfmaPainResult(toStringValue(node.get("left_result")))
                || isSfmaPainResult(toStringValue(node.get("right_result")));
    }

    private String deriveMseSingleLegBilateralSummary(String left, String right) {
        if (StrUtil.isBlank(left) || StrUtil.isBlank(right)) {
            return "";
        }
        if (StrUtil.equals(left, CLASS_FN) && StrUtil.equals(right, CLASS_FN)) {
            return "bilateral_FN";
        }
        if (!StrUtil.equals(left, CLASS_FN) && !StrUtil.equals(right, CLASS_FN)) {
            return "bilateral_abnormal";
        }
        return "unilateral_abnormal";
    }

    private String deriveMseNode4ResultType(String left, String right) {
        if (StrUtil.isBlank(left) || StrUtil.isBlank(right)) {
            return "";
        }
        if (StrUtil.equals(left, CLASS_FN) && StrUtil.equals(right, CLASS_FN)) {
            return CLASS_FN;
        }
        if (StrUtil.equalsAny(left, CLASS_DP, CLASS_FP) || StrUtil.equalsAny(right, CLASS_DP, CLASS_FP)) {
            return StrUtil.equalsAny(left, CLASS_DP, CLASS_FP) || StrUtil.equalsAny(right, CLASS_DP, CLASS_FP)
                    ? (StrUtil.equalsAny(left, CLASS_DP) || StrUtil.equalsAny(right, CLASS_DP) ? CLASS_DP : CLASS_FP)
                    : "";
        }
        return CLASS_DN;
    }

    private String deriveMseNode5ResultType(String left, String right) {
        if (StrUtil.isBlank(left) || StrUtil.isBlank(right)) {
            return "";
        }
        if (StrUtil.equalsAny(left, CLASS_DP, CLASS_FP) || StrUtil.equalsAny(right, CLASS_DP, CLASS_FP)) {
            return StrUtil.equalsAny(left, CLASS_DP) || StrUtil.equalsAny(right, CLASS_DP) ? CLASS_DP : CLASS_FP;
        }
        if (StrUtil.equals(left, CLASS_DN) && StrUtil.equals(right, CLASS_DN)) {
            return "bilateral_DN";
        }
        if ((StrUtil.equals(left, CLASS_DN) && StrUtil.equals(right, CLASS_FN))
                || (StrUtil.equals(left, CLASS_FN) && StrUtil.equals(right, CLASS_DN))) {
            return "unilateral_DN";
        }
        if (StrUtil.equals(left, CLASS_FN) && StrUtil.equals(right, CLASS_FN)) {
            return CLASS_FN;
        }
        return "";
    }

    private String deriveMseNode6ResultType(String left, String right) {
        if (StrUtil.isBlank(left) || StrUtil.isBlank(right)) {
            return "";
        }
        if (StrUtil.equalsAny(left, CLASS_DP, CLASS_FP) || StrUtil.equalsAny(right, CLASS_DP, CLASS_FP)) {
            return StrUtil.equalsAny(left, CLASS_DP) || StrUtil.equalsAny(right, CLASS_DP) ? CLASS_DP : CLASS_FP;
        }
        if (StrUtil.equals(left, CLASS_DN) && StrUtil.equals(right, CLASS_DN)) {
            return "bilateral_DN";
        }
        if ((StrUtil.equals(left, CLASS_DN) && StrUtil.equals(right, CLASS_FN))
                || (StrUtil.equals(left, CLASS_FN) && StrUtil.equals(right, CLASS_DN))) {
            return "unilateral_DN";
        }
        if (StrUtil.equals(left, CLASS_FN) && StrUtil.equals(right, CLASS_FN)) {
            return "bilateral_FN";
        }
        return "";
    }

    private Map<String, Object> normalizeDedicatedMsfBreakout(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("breakout_status", normalizeDedicatedMsfBreakoutStatus(input == null ? null : input.get("breakout_status")));
        result.put("breakout_reason_from_top_tier",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_reason_from_top_tier")), ""));
        result.put("breakout_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_note")), ""));
        result.put("single_leg_standing_forward_flexion_result",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("single_leg_standing_forward_flexion_result")), ""));
        result.put("single_leg_standing_forward_flexion_asymmetry",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("single_leg_standing_forward_flexion_asymmetry")), ""));
        result.put("single_leg_standing_forward_flexion_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("single_leg_standing_forward_flexion_note")), ""));
        result.put("long_sit_toe_touch_result",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("long_sit_toe_touch_result")), ""));
        result.put("long_sit_toe_touch_reach_status",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("long_sit_toe_touch_reach_status")), ""));
        result.put("long_sit_sacral_angle_deg",
                normalizeNumber(input == null ? null : input.get("long_sit_sacral_angle_deg")));
        result.put("long_sit_sacral_angle_status",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("long_sit_sacral_angle_status")), ""));
        result.put("long_sit_toe_touch_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("long_sit_toe_touch_note")), ""));
        result.put("rolling_result",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("rolling_result")), ""));
        result.put("rolling_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("rolling_note")), ""));
        result.put("aslr_result",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("aslr_result")), ""));
        result.put("aslr_left_deg", normalizeNumber(input == null ? null : input.get("aslr_left_deg")));
        result.put("aslr_right_deg", normalizeNumber(input == null ? null : input.get("aslr_right_deg")));
        result.put("aslr_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("aslr_note")), ""));
        result.put("pslr_result",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pslr_result")), ""));
        result.put("pslr_left_deg", normalizeNumber(input == null ? null : input.get("pslr_left_deg")));
        result.put("pslr_right_deg", normalizeNumber(input == null ? null : input.get("pslr_right_deg")));
        result.put("pslr_vs_aslr_interpretation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pslr_vs_aslr_interpretation")), ""));
        result.put("pslr_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pslr_note")), ""));
        result.put("prone_rock_back_result",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("prone_rock_back_result")), ""));
        result.put("prone_rock_back_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("prone_rock_back_note")), ""));
        result.put("supine_knees_to_chest_result",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("supine_knees_to_chest_result")), ""));
        result.put("supine_knees_to_chest_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("supine_knees_to_chest_note")), ""));
        result.put("flow_next_step",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("flow_next_step")), ""));
        result.put("flow_algorithm_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("flow_algorithm_note")), ""));
        Number sacralThreshold = normalizeNumber(input == null ? null : input.get("sacral_angle_threshold_ref"));
        Number aslrThreshold = normalizeNumber(input == null ? null : input.get("aslr_threshold_ref"));
        Number pslrThreshold = normalizeNumber(input == null ? null : input.get("pslr_threshold_ref"));
        result.put("sacral_angle_threshold_ref", sacralThreshold == null ? 80 : sacralThreshold.intValue());
        result.put("aslr_threshold_ref", aslrThreshold == null ? 70 : aslrThreshold.intValue());
        result.put("pslr_threshold_ref", pslrThreshold == null ? 80 : pslrThreshold.intValue());

        result.put("active_flexion_global_quality",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("active_flexion_global_quality")), ""));
        result.put("active_flexion_pain", Boolean.TRUE.equals(input == null ? null : input.get("active_flexion_pain")));
        result.put("active_flexion_pain_area", castStringList(input == null ? null : input.get("active_flexion_pain_area")));
        result.put("active_flexion_pain_other_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("active_flexion_pain_other_note")), ""));
        result.put("fingertips_to_floor_status",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("fingertips_to_floor_status")), ""));
        result.put("fingertips_to_floor_distance_cm",
                normalizeNumber(input == null ? null : input.get("fingertips_to_floor_distance_cm")));
        result.put("uniform_curve_observation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("uniform_curve_observation")), ""));
        result.put("movement_quality_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("movement_quality_note")), ""));

        result.put("hamstring_posterior_chain_tension",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("hamstring_posterior_chain_tension")), ""));
        result.put("left_right_posterior_chain_asymmetry",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("left_right_posterior_chain_asymmetry")), ""));
        result.put("ankle_dorsiflexion_influence",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("ankle_dorsiflexion_influence")), ""));
        result.put("knee_extension_limitation_influence",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("knee_extension_limitation_influence")), ""));
        result.put("lower_extremity_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("lower_extremity_note")), ""));

        result.put("hip_flexion_contribution",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("hip_flexion_contribution")), ""));
        result.put("pelvis_anterior_posterior_control",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pelvis_anterior_posterior_control")), ""));
        result.put("left_right_hip_asymmetry",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("left_right_hip_asymmetry")), ""));
        result.put("hip_pelvis_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("hip_pelvis_note")), ""));

        result.put("lumbar_flexion_participation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("lumbar_flexion_participation")), ""));
        result.put("thoracic_flexion_participation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("thoracic_flexion_participation")), ""));
        result.put("segmental_spinal_mobility_observation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("segmental_spinal_mobility_observation")), ""));
        result.put("spine_thorax_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("spine_thorax_note")), ""));

        result.put("shoulder_girdle_relaxation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("shoulder_girdle_relaxation")), ""));
        result.put("upper_extremity_hanging_pattern",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("upper_extremity_hanging_pattern")), ""));
        result.put("shoulder_upper_extremity_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("shoulder_upper_extremity_note")), ""));

        result.put("compensation_patterns", castStringList(input == null ? null : input.get("compensation_patterns")));
        result.put("compensation_other_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("compensation_other_note")), ""));
        result.put("pain_dominant_pattern",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pain_dominant_pattern")), ""));
        result.put("symptom_irritability",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("symptom_irritability")), ""));
        result.put("pain_control_priority_hint",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pain_control_priority_hint")), ""));

        result.put("breakout_preliminary_direction",
                castStringList(input == null ? null : input.get("breakout_preliminary_direction")));
        result.put("primary_restriction_chain",
                castStringList(input == null ? null : input.get("primary_restriction_chain")));
        result.put("primary_control_deficit_chain",
                castStringList(input == null ? null : input.get("primary_control_deficit_chain")));
        result.put("left_right_asymmetry_focus",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("left_right_asymmetry_focus")), ""));

        result.put("breakout_summary_text",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_summary_text")), ""));
        result.put("clinical_meaning_hint",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("clinical_meaning_hint")), ""));
        result.put("training_direction_hint",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("training_direction_hint")), ""));
        result.put("reassessment_priority", normalizeReassessmentPriority(input == null ? null : input.get("reassessment_priority")));
        result.put("pause_or_referral_hint",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pause_or_referral_hint")), ""));
        Map<String, Object> msfAnalysis = normalizeMsfAnalysis(castToMap(input == null ? null : input.get("msf_analysis")), result);
        result.put("msf_analysis", msfAnalysis);
        result.put("flow_next_step", deriveMsfFlowNextStep(result, msfAnalysis));
        if (StrUtil.isBlank(toStringValue(result.get("flow_algorithm_note")))) {
            Map<String, Object> analysisSummary = castToMap(msfAnalysis.get("summary"));
            result.put("flow_algorithm_note", toStringValue(analysisSummary == null ? null : analysisSummary.get("summary_text")));
        }

        boolean painDominant = StrUtil.equalsAny(
                toStringValue(result.get("pain_dominant_pattern")),
                "疑似是", "明显是"
        );
        boolean priorityManualReview = StrUtil.contains(toStringValue(result.get("pain_control_priority_hint")), "人工复核");
        boolean flowPainFlag = StrUtil.equalsAny(
                toStringValue(result.get("long_sit_toe_touch_result")),
                "FP", "DP"
        ) || StrUtil.equalsAny(toStringValue(result.get("rolling_result")), "FP", "DP")
                || StrUtil.equalsAny(toStringValue(result.get("aslr_result")), "FP", "DP")
                || StrUtil.equalsAny(toStringValue(result.get("pslr_result")), "FP", "DP")
                || StrUtil.equalsAny(toStringValue(result.get("prone_rock_back_result")), "FP", "DP")
                || StrUtil.equalsAny(toStringValue(result.get("supine_knees_to_chest_result")), "FP", "DP")
                || StrUtil.equals(toStringValue(result.get("flow_next_step")), "停止并优先处理疼痛");
        boolean manualReview = Boolean.TRUE.equals(input == null ? null : input.get("needs_manual_review"))
                || Boolean.TRUE.equals(result.get("active_flexion_pain"))
                || painDominant
                || priorityManualReview
                || flowPainFlag;
        result.put("needs_manual_review", manualReview);
        return result;
    }

    private String deriveMsfFlowNextStep(Map<String, Object> normalizedFields, Map<String, Object> msfAnalysis) {
        if (normalizedFields == null) {
            return "";
        }
        if (StrUtil.equals(toStringValue(normalizedFields.get("breakout_status")), BREAKOUT_STATUS_SKIPPED)) {
            return "";
        }
        Map<String, Object> summary = castToMap(msfAnalysis == null ? null : msfAnalysis.get("summary"));
        if (Boolean.TRUE.equals(summary == null ? null : summary.get("stop_and_treat_pain"))) {
            return "停止并优先处理疼痛";
        }

        Set<String> enabledNodes = new LinkedHashSet<>();
        enabledNodes.add("single_leg_stance_forward_bend");
        if (StrUtil.isNotBlank(toStringValue(normalizedFields.get("single_leg_standing_forward_flexion_result")))) {
            enabledNodes.add("long_sit_toe_touch");
        }

        Map<String, Object> flow = castToMap(msfAnalysis == null ? null : msfAnalysis.get("flow_nodes"));
        if (flow.isEmpty()) {
            flow = castToMap(msfAnalysis == null ? null : msfAnalysis.get("flexion_flow"));
        }
        Map<String, Object> longSitNode = castToMap(flow == null ? null : flow.get("long_sit_toe_touch"));
        String longSitResultType = toStringValue(longSitNode == null ? null : longSitNode.get("result_type"));
        if (StrUtil.isNotBlank(longSitResultType)) {
            if (StrUtil.equals(longSitResultType, "abnormal_with_sacrum_normal")) {
                enabledNodes.add("prone_backward_rocking");
            } else if (StrUtil.equals(longSitResultType, "abnormal_with_sacrum_limited")) {
                enabledNodes.add("active_straight_leg_raise");
            }
        }

        String aslrResult = toStringValue(normalizedFields.get("aslr_result"));
        if (enabledNodes.contains("active_straight_leg_raise") && StrUtil.isNotBlank(aslrResult)) {
            if (StrUtil.equals(aslrResult, CLASS_FN)) {
                enabledNodes.add("prone_backward_rocking");
            } else {
                enabledNodes.add("passive_straight_leg_raise");
            }
        }

        Map<String, Object> pslrNode = castToMap(flow == null ? null : flow.get("passive_straight_leg_raise"));
        String pslrResultType = toStringValue(pslrNode == null ? null : pslrNode.get("result_type"));
        if (enabledNodes.contains("passive_straight_leg_raise") && StrUtil.isNotBlank(pslrResultType)) {
            if (StrUtil.equals(pslrResultType, "fn_gt_80")) {
                enabledNodes.add("rolling_analysis_result");
            } else if (StrUtil.equalsAny(pslrResultType, "fn_gap_gt_10_and_lt_80", "fn_gt_aslr_by_10_but_lt_80", "dn_pslr_lte_aslr")) {
                enabledNodes.add("supine_double_knees_to_chest");
            }
        }

        if (enabledNodes.contains("long_sit_toe_touch") && StrUtil.isBlank(toStringValue(normalizedFields.get("long_sit_toe_touch_result")))) {
            // 长坐位节点以 result_type 作为“可分流完成”的标准，避免“未测占位”被误判完成
            if (StrUtil.isBlank(longSitResultType)) {
                return "继续长坐位触摸足趾";
            }
        }
        if (enabledNodes.contains("single_leg_stance_forward_bend")
                && StrUtil.isBlank(toStringValue(castToMap(flow.get("single_leg_stance_forward_bend")).get("result_type")))) {
            return "继续单腿站立体前屈";
        }
        if (enabledNodes.contains("long_sit_toe_touch") && StrUtil.isBlank(longSitResultType)) {
            return "继续长坐位触摸足趾";
        }
        if (enabledNodes.contains("active_straight_leg_raise")
                && StrUtil.isBlank(toStringValue(castToMap(flow.get("active_straight_leg_raise")).get("result_type")))) {
            return "继续主动直腿抬高";
        }
        if (enabledNodes.contains("passive_straight_leg_raise")
                && StrUtil.isBlank(toStringValue(castToMap(flow.get("passive_straight_leg_raise")).get("result_type")))) {
            return "继续被动直腿抬高";
        }
        if (enabledNodes.contains("prone_backward_rocking")
                && StrUtil.isBlank(toStringValue(castToMap(flow.get("prone_backward_rocking")).get("result_type")))) {
            return "继续俯卧位向后摆动";
        }
        if (enabledNodes.contains("supine_double_knees_to_chest")
                && StrUtil.isBlank(toStringValue(castToMap(flow.get("supine_double_knees_to_chest")).get("result_type")))) {
            return "继续仰卧位双膝触胸";
        }
        if (enabledNodes.contains("rolling_analysis_result")
                && StrUtil.isBlank(toStringValue(castToMap(flow.get("rolling_analysis_result")).get("result_type")))) {
            return "继续滚动解析测试";
        }
        if (StrUtil.equals(longSitResultType, "fn_and_sacrum_normal")) {
            return "继续进入旋转动作解析";
        }

        boolean hasAnyInput = StrUtil.isNotBlank(toStringValue(normalizedFields.get("single_leg_standing_forward_flexion_result")))
                || StrUtil.isNotBlank(toStringValue(normalizedFields.get("long_sit_toe_touch_result")))
                || StrUtil.isNotBlank(toStringValue(normalizedFields.get("rolling_result")))
                || StrUtil.isNotBlank(toStringValue(normalizedFields.get("aslr_result")))
                || StrUtil.isNotBlank(toStringValue(normalizedFields.get("pslr_result")))
                || StrUtil.isNotBlank(toStringValue(normalizedFields.get("prone_rock_back_result")))
                || StrUtil.isNotBlank(toStringValue(normalizedFields.get("supine_knees_to_chest_result")))
                || StrUtil.isNotBlank(toStringValue(normalizedFields.get("breakout_summary_text")))
                || StrUtil.isNotBlank(toStringValue(normalizedFields.get("flow_algorithm_note")));
        return hasAnyInput ? "流程已完成" : "";
    }

    private Map<String, Object> mapLegacyBreakoutToDedicatedMsf(Map<String, Object> legacy) {
        Map<String, Object> result = new LinkedHashMap<>();
        String legacyStatus = normalizeBreakoutStatus(legacy == null ? null : legacy.get("status"));
        result.put("breakout_status",
                StrUtil.equals(legacyStatus, BREAKOUT_STATUS_PARTIAL) ? "in_progress" : legacyStatus);
        result.put("breakout_reason_from_top_tier", "");
        result.put("breakout_note", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("clinician_note")), ""));
        result.put("single_leg_standing_forward_flexion_result", "");
        result.put("single_leg_standing_forward_flexion_asymmetry", "");
        result.put("single_leg_standing_forward_flexion_note", "");
        result.put("long_sit_toe_touch_result", "");
        result.put("long_sit_toe_touch_reach_status", "");
        result.put("long_sit_sacral_angle_deg", null);
        result.put("long_sit_sacral_angle_status", "");
        result.put("long_sit_toe_touch_note", "");
        result.put("rolling_result", "");
        result.put("rolling_note", "");
        result.put("aslr_result", "");
        result.put("aslr_left_deg", null);
        result.put("aslr_right_deg", null);
        result.put("aslr_note", "");
        result.put("pslr_result", "");
        result.put("pslr_left_deg", null);
        result.put("pslr_right_deg", null);
        result.put("pslr_vs_aslr_interpretation", "");
        result.put("pslr_note", "");
        result.put("prone_rock_back_result", "");
        result.put("prone_rock_back_note", "");
        result.put("supine_knees_to_chest_result", "");
        result.put("supine_knees_to_chest_note", "");
        result.put("flow_next_step", "");
        result.put("flow_algorithm_note", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("findings")), ""));
        result.put("sacral_angle_threshold_ref", 80);
        result.put("aslr_threshold_ref", 70);
        result.put("pslr_threshold_ref", 80);
        result.put("active_flexion_global_quality", "");
        result.put("active_flexion_pain", Boolean.TRUE.equals(legacy == null ? null : legacy.get("pain_present")));
        result.put("active_flexion_pain_area", Collections.emptyList());
        result.put("active_flexion_pain_other_note", "");
        result.put("fingertips_to_floor_status", "");
        result.put("fingertips_to_floor_distance_cm", null);
        result.put("uniform_curve_observation", "");
        result.put("movement_quality_note", "");
        result.put("hamstring_posterior_chain_tension", "");
        result.put("left_right_posterior_chain_asymmetry", "");
        result.put("ankle_dorsiflexion_influence", "");
        result.put("knee_extension_limitation_influence", "");
        result.put("lower_extremity_note", "");
        result.put("hip_flexion_contribution", "");
        result.put("pelvis_anterior_posterior_control", "");
        result.put("left_right_hip_asymmetry", "");
        result.put("hip_pelvis_note", "");
        result.put("lumbar_flexion_participation", "");
        result.put("thoracic_flexion_participation", "");
        result.put("segmental_spinal_mobility_observation", "");
        result.put("spine_thorax_note", "");
        result.put("shoulder_girdle_relaxation", "");
        result.put("upper_extremity_hanging_pattern", "");
        result.put("shoulder_upper_extremity_note", "");
        result.put("compensation_patterns", Collections.emptyList());
        result.put("compensation_other_note", "");
        result.put("pain_dominant_pattern", Boolean.TRUE.equals(legacy == null ? null : legacy.get("pain_present")) ? "疑似是" : "");
        result.put("symptom_irritability", "");
        result.put("pain_control_priority_hint",
                Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain")) ? "是，建议优先人工复核" : "");
        result.put("breakout_preliminary_direction", inferBreakoutDirection(legacy == null ? Collections.emptyMap() : legacy));
        result.put("primary_restriction_chain", Collections.emptyList());
        result.put("primary_control_deficit_chain", Collections.emptyList());
        result.put("left_right_asymmetry_focus",
                StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("asymmetry_signs")), ""));
        result.put("breakout_summary_text",
                StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("findings")), ""));
        result.put("clinical_meaning_hint", "");
        result.put("training_direction_hint", "");
        result.put("reassessment_priority", "medium");
        result.put("pause_or_referral_hint",
                Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain")) ? "建议优先人工复核" : "");
        result.put("needs_manual_review", Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain")));
        return normalizeDedicatedMsfBreakout(result);
    }

    private Map<String, Object> mapDedicatedMsfBreakoutToLegacy(Map<String, Object> dedicated) {
        Map<String, Object> normalized = normalizeDedicatedMsfBreakout(dedicated);
        Map<String, Object> msfAnalysis = castToMap(normalized.get("msf_analysis"));
        Map<String, Object> analysisSummary = castToMap(msfAnalysis == null ? null : msfAnalysis.get("summary"));
        String analysisSummaryText = toStringValue(analysisSummary == null ? null : analysisSummary.get("summary_text"));
        String status = mapDedicatedMsfStatusToLegacy(toStringValue(normalized.get("breakout_status")));
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("status", status);
        legacy.put("findings", distinct(Arrays.asList(
                toStringValue(normalized.get("breakout_summary_text")),
                toStringValue(normalized.get("flow_algorithm_note")),
                analysisSummaryText,
                toStringValue(normalized.get("clinical_meaning_hint")),
                toStringValue(normalized.get("training_direction_hint")),
                toStringValue(normalized.get("breakout_note"))
        )).stream().filter(StrUtil::isNotBlank).collect(Collectors.joining("；")));
        Number dist = normalizeNumber(normalized.get("fingertips_to_floor_distance_cm"));
        Number sacral = normalizeNumber(normalized.get("long_sit_sacral_angle_deg"));
        Number aslrLeft = normalizeNumber(normalized.get("aslr_left_deg"));
        Number aslrRight = normalizeNumber(normalized.get("aslr_right_deg"));
        Number pslrLeft = normalizeNumber(normalized.get("pslr_left_deg"));
        Number pslrRight = normalizeNumber(normalized.get("pslr_right_deg"));
        List<String> romTokens = new ArrayList<>();
        if (dist != null) {
            romTokens.add("指尖距地:" + dist + "cm");
        }
        if (sacral != null) {
            romTokens.add("骶骨角:" + sacral + "°");
        }
        if (aslrLeft != null) {
            romTokens.add("ASLR左:" + aslrLeft + "°");
        }
        if (aslrRight != null) {
            romTokens.add("ASLR右:" + aslrRight + "°");
        }
        if (pslrLeft != null) {
            romTokens.add("PSLR左:" + pslrLeft + "°");
        }
        if (pslrRight != null) {
            romTokens.add("PSLR右:" + pslrRight + "°");
        }
        legacy.put("rom_key_values", String.join(" | ", romTokens));
        boolean painPresent = Boolean.TRUE.equals(normalized.get("active_flexion_pain"))
                || StrUtil.equalsAny(toStringValue(normalized.get("pain_dominant_pattern")), "疑似是", "明显是")
                || StrUtil.equalsAny(toStringValue(normalized.get("long_sit_toe_touch_result")), "FP", "DP")
                || StrUtil.equalsAny(toStringValue(normalized.get("rolling_result")), "FP", "DP")
                || StrUtil.equalsAny(toStringValue(normalized.get("aslr_result")), "FP", "DP")
                || StrUtil.equalsAny(toStringValue(normalized.get("pslr_result")), "FP", "DP")
                || StrUtil.equalsAny(toStringValue(normalized.get("prone_rock_back_result")), "FP", "DP")
                || StrUtil.equalsAny(toStringValue(normalized.get("supine_knees_to_chest_result")), "FP", "DP");
        legacy.put("pain_present", painPresent);
        legacy.put("pain_vas", null);
        List<String> direction = castStringList(normalized.get("breakout_preliminary_direction"));
        legacy.put("mobility_restriction_signs",
                direction.stream()
                        .filter(item -> StrUtil.equalsAny(item, "更偏活动度限制", "更偏后侧链张力限制", "更偏髋/骨盆参与不足", "更偏脊柱分节活动受限"))
                        .collect(Collectors.joining("、")));
        legacy.put("motor_control_signs",
                direction.stream()
                        .filter(item -> StrUtil.equals(item, "更偏运动控制问题"))
                        .collect(Collectors.joining("、")));
        String asymmetry = toStringValue(normalized.get("left_right_asymmetry_focus"));
        String flowAsymmetry = toStringValue(normalized.get("single_leg_standing_forward_flexion_asymmetry"));
        if (StrUtil.isBlank(asymmetry) || StrUtil.equals(asymmetry, "无明显左右差")) {
            asymmetry = flowAsymmetry;
        }
        legacy.put("asymmetry_signs", StrUtil.equals(asymmetry, "无明显左右差") ? "" : StrUtil.blankToDefault(asymmetry, ""));
        boolean stopDueToPain = StrUtil.equals(toStringValue(normalized.get("flow_next_step")), "停止并优先处理疼痛")
                || Boolean.TRUE.equals(analysisSummary == null ? null : analysisSummary.get("stop_and_treat_pain"))
                || (StrUtil.equals(toStringValue(normalized.get("pause_or_referral_hint")), "建议优先人工复核") && painPresent);
        legacy.put("stop_due_to_pain", stopDueToPain);
        legacy.put("stop_reason", stopDueToPain
                ? StrUtil.blankToDefault(
                toStringValue(normalized.get("flow_next_step")),
                toStringValue(normalized.get("breakout_note")))
                : "");
        legacy.put("clinician_note", toStringValue(normalized.get("breakout_note")));
        legacy.put("method", "");
        legacy.put("scale", "");
        legacy.put("source_id", "");
        legacy.put("date", "");
        legacy.put("sls_time_sec", null);
        return legacy;
    }

    private String normalizeDedicatedMseBreakoutStatus(Object value) {
        String status = StrUtil.trimToEmpty(toStringValue(value));
        if (StrUtil.equals(status, "partial")) {
            return "in_progress";
        }
        if (StrUtil.equals(status, BREAKOUT_STATUS_STOPPED_PAIN)) {
            return "in_progress";
        }
        if (StrUtil.equalsAny(status, "not_started", "in_progress", "completed", "skipped")) {
            return status;
        }
        return "not_started";
    }

    private String mapDedicatedMseStatusToLegacy(String dedicatedStatus) {
        if (StrUtil.equals(dedicatedStatus, "in_progress")) {
            return BREAKOUT_STATUS_PARTIAL;
        }
        if (StrUtil.equalsAny(dedicatedStatus, BREAKOUT_STATUS_NOT_STARTED, BREAKOUT_STATUS_COMPLETED, BREAKOUT_STATUS_SKIPPED)) {
            return dedicatedStatus;
        }
        return BREAKOUT_STATUS_NOT_STARTED;
    }

    private Map<String, Object> normalizeDedicatedMseBreakout(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("breakout_status", normalizeDedicatedMseBreakoutStatus(input == null ? null : input.get("breakout_status")));
        result.put("breakout_reason_from_top_tier",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_reason_from_top_tier")), ""));
        result.put("breakout_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_note")), ""));

        result.put("active_extension_global_quality",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("active_extension_global_quality")), ""));
        result.put("active_extension_pain", Boolean.TRUE.equals(input == null ? null : input.get("active_extension_pain")));
        result.put("active_extension_pain_area", castStringList(input == null ? null : input.get("active_extension_pain_area")));
        result.put("active_extension_pain_other_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("active_extension_pain_other_note")), ""));
        result.put("extension_curve_observation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("extension_curve_observation")), ""));
        result.put("center_of_mass_control",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("center_of_mass_control")), ""));
        result.put("movement_quality_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("movement_quality_note")), ""));

        result.put("ankle_stability_or_mobility_influence",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("ankle_stability_or_mobility_influence")), ""));
        result.put("knee_extension_or_locking_pattern",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("knee_extension_or_locking_pattern")), ""));
        result.put("lower_extremity_support_symmetry",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("lower_extremity_support_symmetry")), ""));
        result.put("lower_extremity_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("lower_extremity_note")), ""));

        result.put("hip_extension_contribution",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("hip_extension_contribution")), ""));
        result.put("anterior_hip_mobility_limitation_suspected",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("anterior_hip_mobility_limitation_suspected")), ""));
        result.put("pelvis_control_pattern",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pelvis_control_pattern")), ""));
        result.put("lphc_control_during_extension",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("lphc_control_during_extension")), ""));
        result.put("left_right_hip_asymmetry",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("left_right_hip_asymmetry")), ""));
        result.put("hip_pelvis_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("hip_pelvis_note")), ""));

        result.put("lumbar_extension_participation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("lumbar_extension_participation")), ""));
        result.put("thoracic_extension_participation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("thoracic_extension_participation")), ""));
        result.put("extension_distribution_observation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("extension_distribution_observation")), ""));
        result.put("posterior_chain_loading_pattern",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("posterior_chain_loading_pattern")), ""));
        result.put("spine_thorax_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("spine_thorax_note")), ""));

        result.put("shoulder_flexion_contribution",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("shoulder_flexion_contribution")), ""));
        result.put("shoulder_girdle_compensation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("shoulder_girdle_compensation")), ""));
        result.put("overhead_pattern_limitation_suspected",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("overhead_pattern_limitation_suspected")), ""));
        result.put("shoulder_upper_extremity_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("shoulder_upper_extremity_note")), ""));

        result.put("compensation_patterns", castStringList(input == null ? null : input.get("compensation_patterns")));
        result.put("compensation_other_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("compensation_other_note")), ""));
        result.put("pain_dominant_pattern",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pain_dominant_pattern")), ""));
        result.put("symptom_irritability",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("symptom_irritability")), ""));
        result.put("pain_control_priority_hint",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pain_control_priority_hint")), ""));

        result.put("breakout_preliminary_direction",
                castStringList(input == null ? null : input.get("breakout_preliminary_direction")));
        result.put("primary_restriction_chain",
                castStringList(input == null ? null : input.get("primary_restriction_chain")));
        result.put("primary_control_deficit_chain",
                castStringList(input == null ? null : input.get("primary_control_deficit_chain")));
        result.put("left_right_asymmetry_focus",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("left_right_asymmetry_focus")), ""));

        result.put("breakout_summary_text",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_summary_text")), ""));
        result.put("clinical_meaning_hint",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("clinical_meaning_hint")), ""));
        result.put("training_direction_hint",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("training_direction_hint")), ""));
        result.put("reassessment_priority", normalizeReassessmentPriority(input == null ? null : input.get("reassessment_priority")));
        result.put("pause_or_referral_hint",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pause_or_referral_hint")), ""));
        result.put("mse_analysis", normalizeMseAnalysis(castToMap(input == null ? null : input.get("mse_analysis")), result));

        boolean painDominant = StrUtil.equalsAny(toStringValue(result.get("pain_dominant_pattern")), "疑似是", "明显是");
        boolean priorityManualReview = StrUtil.contains(toStringValue(result.get("pain_control_priority_hint")), "人工复核");
        Map<String, Object> mseAnalysis = castToMap(result.get("mse_analysis"));
        Map<String, Object> analysisSummary = castToMap(mseAnalysis == null ? null : mseAnalysis.get("summary"));
        boolean flowPainFlag = Boolean.TRUE.equals(analysisSummary == null ? null : analysisSummary.get("stop_and_treat_pain"));
        boolean flowManualReview = Boolean.TRUE.equals(analysisSummary == null ? null : analysisSummary.get("manual_review_required"));
        boolean manualReview = Boolean.TRUE.equals(input == null ? null : input.get("needs_manual_review"))
                || Boolean.TRUE.equals(result.get("active_extension_pain"))
                || painDominant
                || priorityManualReview
                || flowPainFlag
                || flowManualReview;
        result.put("needs_manual_review", manualReview);
        return result;
    }

    private Map<String, Object> mapLegacyBreakoutToDedicatedMse(Map<String, Object> legacy) {
        Map<String, Object> result = new LinkedHashMap<>();
        String legacyStatus = normalizeBreakoutStatus(legacy == null ? null : legacy.get("status"));
        result.put("breakout_status",
                StrUtil.equals(legacyStatus, BREAKOUT_STATUS_PARTIAL) ? "in_progress" : legacyStatus);
        result.put("breakout_reason_from_top_tier", "");
        result.put("breakout_note", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("clinician_note")), ""));
        result.put("needs_manual_review", Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain")));
        result.put("active_extension_global_quality", "");
        result.put("active_extension_pain", Boolean.TRUE.equals(legacy == null ? null : legacy.get("pain_present")));
        result.put("active_extension_pain_area", Collections.emptyList());
        result.put("active_extension_pain_other_note", "");
        result.put("extension_curve_observation", "");
        result.put("center_of_mass_control", "");
        result.put("movement_quality_note", "");
        result.put("ankle_stability_or_mobility_influence", "");
        result.put("knee_extension_or_locking_pattern", "");
        result.put("lower_extremity_support_symmetry", "");
        result.put("lower_extremity_note", "");
        result.put("hip_extension_contribution", "");
        result.put("anterior_hip_mobility_limitation_suspected", "");
        result.put("pelvis_control_pattern", "");
        result.put("lphc_control_during_extension", "");
        result.put("left_right_hip_asymmetry", "");
        result.put("hip_pelvis_note", "");
        result.put("lumbar_extension_participation", "");
        result.put("thoracic_extension_participation", "");
        result.put("extension_distribution_observation", "");
        result.put("posterior_chain_loading_pattern", "");
        result.put("spine_thorax_note", "");
        result.put("shoulder_flexion_contribution", "");
        result.put("shoulder_girdle_compensation", "");
        result.put("overhead_pattern_limitation_suspected", "");
        result.put("shoulder_upper_extremity_note", "");
        result.put("compensation_patterns", Collections.emptyList());
        result.put("compensation_other_note", "");
        result.put("pain_dominant_pattern", Boolean.TRUE.equals(legacy == null ? null : legacy.get("pain_present")) ? "疑似是" : "");
        result.put("symptom_irritability", "");
        result.put("pain_control_priority_hint",
                Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain")) ? "是，建议优先人工复核" : "");
        result.put("breakout_preliminary_direction", inferBreakoutDirection(legacy == null ? Collections.emptyMap() : legacy));
        result.put("primary_restriction_chain", Collections.emptyList());
        result.put("primary_control_deficit_chain", Collections.emptyList());
        result.put("left_right_asymmetry_focus",
                StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("asymmetry_signs")), ""));
        result.put("breakout_summary_text",
                StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("findings")), ""));
        result.put("clinical_meaning_hint", "");
        result.put("training_direction_hint", "");
        result.put("reassessment_priority", "medium");
        result.put("pause_or_referral_hint",
                Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain")) ? "建议优先人工复核" : "");
        result.put("mse_analysis", castToMap(legacy == null ? null : legacy.get("mse_analysis")));
        return normalizeDedicatedMseBreakout(result);
    }

    private Map<String, Object> mapDedicatedMseBreakoutToLegacy(Map<String, Object> dedicated) {
        Map<String, Object> normalized = normalizeDedicatedMseBreakout(dedicated);
        Map<String, Object> mseAnalysis = castToMap(normalized.get("mse_analysis"));
        Map<String, Object> analysisSummary = castToMap(mseAnalysis == null ? null : mseAnalysis.get("summary"));
        String analysisSummaryText = toStringValue(analysisSummary == null ? null : analysisSummary.get("summary_text"));
        String status = mapDedicatedMseStatusToLegacy(toStringValue(normalized.get("breakout_status")));
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("status", status);
        legacy.put("findings", distinct(Arrays.asList(
                toStringValue(normalized.get("breakout_summary_text")),
                analysisSummaryText,
                toStringValue(normalized.get("clinical_meaning_hint")),
                toStringValue(normalized.get("training_direction_hint")),
                toStringValue(normalized.get("breakout_note"))
        )).stream().filter(StrUtil::isNotBlank).collect(Collectors.joining("；")));
        legacy.put("rom_key_values", "");
        boolean painPresent = Boolean.TRUE.equals(normalized.get("active_extension_pain"))
                || StrUtil.equalsAny(toStringValue(normalized.get("pain_dominant_pattern")), "疑似是", "明显是");
        legacy.put("pain_present", painPresent);
        legacy.put("pain_vas", null);
        List<String> direction = castStringList(normalized.get("breakout_preliminary_direction"));
        legacy.put("mobility_restriction_signs",
                direction.stream()
                        .filter(item -> StrUtil.equalsAny(item, "更偏活动度限制", "更偏髋伸展不足", "更偏胸椎伸展不足", "更偏肩带/上肢参与不足"))
                        .collect(Collectors.joining("、")));
        legacy.put("motor_control_signs",
                direction.stream()
                        .filter(item -> StrUtil.equalsAny(item, "更偏腰盆控制问题", "更偏运动控制问题"))
                        .collect(Collectors.joining("、")));
        String asymmetry = toStringValue(normalized.get("left_right_asymmetry_focus"));
        legacy.put("asymmetry_signs", StrUtil.equals(asymmetry, "无明显左右差") ? "" : asymmetry);
        boolean stopDueToPain = StrUtil.equals(toStringValue(normalized.get("pause_or_referral_hint")), "建议优先人工复核")
                && painPresent
                || Boolean.TRUE.equals(analysisSummary == null ? null : analysisSummary.get("stop_and_treat_pain"));
        legacy.put("stop_due_to_pain", stopDueToPain);
        legacy.put("stop_reason", stopDueToPain
                ? StrUtil.blankToDefault(toStringValue(normalized.get("breakout_note")), analysisSummaryText)
                : "");
        legacy.put("clinician_note", toStringValue(normalized.get("breakout_note")));
        legacy.put("method", "");
        legacy.put("scale", "");
        legacy.put("source_id", "");
        legacy.put("date", "");
        legacy.put("sls_time_sec", null);
        return legacy;
    }

    private String normalizeDedicatedArmsDownSquatBreakoutStatus(Object value) {
        String status = StrUtil.trimToEmpty(toStringValue(value));
        if (StrUtil.equals(status, BREAKOUT_STATUS_PARTIAL) || StrUtil.equals(status, BREAKOUT_STATUS_STOPPED_PAIN)) {
            return "in_progress";
        }
        if (StrUtil.equalsAny(status, BREAKOUT_STATUS_NOT_STARTED, "in_progress", BREAKOUT_STATUS_COMPLETED, BREAKOUT_STATUS_SKIPPED)) {
            return status;
        }
        return BREAKOUT_STATUS_NOT_STARTED;
    }

    private String mapDedicatedArmsDownSquatStatusToLegacy(String dedicatedStatus) {
        if (StrUtil.equals(dedicatedStatus, "in_progress")) {
            return BREAKOUT_STATUS_PARTIAL;
        }
        if (StrUtil.equalsAny(dedicatedStatus, BREAKOUT_STATUS_NOT_STARTED, BREAKOUT_STATUS_COMPLETED, BREAKOUT_STATUS_SKIPPED)) {
            return dedicatedStatus;
        }
        return BREAKOUT_STATUS_NOT_STARTED;
    }

    private Map<String, Object> normalizeDedicatedArmsDownSquatBreakout(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("breakout_status",
                normalizeDedicatedArmsDownSquatBreakoutStatus(input == null ? null : input.get("breakout_status")));
        result.put("breakout_reason_from_screening",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_reason_from_screening")), ""));
        result.put("breakout_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_note")), ""));

        result.put("squat_global_quality", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("squat_global_quality")), ""));
        result.put("squat_depth_level", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("squat_depth_level")), ""));
        result.put("descent_control", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("descent_control")), ""));
        result.put("ascent_control", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("ascent_control")), ""));
        result.put("squat_rhythm_observation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("squat_rhythm_observation")), ""));
        result.put("movement_quality_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("movement_quality_note")), ""));

        result.put("ankle_dorsiflexion_limitation_suspected",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("ankle_dorsiflexion_limitation_suspected")), ""));
        result.put("heel_rise_pattern", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("heel_rise_pattern")), ""));
        result.put("foot_pronation_control", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("foot_pronation_control")), ""));
        result.put("rearfoot_stability", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("rearfoot_stability")), ""));
        result.put("foot_tripod_or_pressure_strategy",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("foot_tripod_or_pressure_strategy")), ""));
        result.put("foot_ankle_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("foot_ankle_note")), ""));

        result.put("knee_valgus_control", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("knee_valgus_control")), ""));
        result.put("knee_varus_or_outward_shift",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("knee_varus_or_outward_shift")), ""));
        result.put("knee_forward_translation_pattern",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("knee_forward_translation_pattern")), ""));
        result.put("knee_wobble_or_instability",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("knee_wobble_or_instability")), ""));
        result.put("knee_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("knee_note")), ""));

        result.put("hip_flexion_contribution",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("hip_flexion_contribution")), ""));
        result.put("hip_control_asymmetry",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("hip_control_asymmetry")), ""));
        result.put("pelvic_shift_pattern", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pelvic_shift_pattern")), ""));
        result.put("pelvic_rotation_suspected",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pelvic_rotation_suspected")), ""));
        result.put("hip_pelvis_dissociation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("hip_pelvis_dissociation")), ""));
        result.put("hip_pelvis_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("hip_pelvis_note")), ""));

        result.put("excessive_forward_lean",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("excessive_forward_lean")), ""));
        result.put("lumbar_rounding", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("lumbar_rounding")), ""));
        result.put("lumbar_extension_or_arching",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("lumbar_extension_or_arching")), ""));
        result.put("lphc_control_observation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("lphc_control_observation")), ""));
        result.put("trunk_shift_or_rotation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("trunk_shift_or_rotation")), ""));
        result.put("trunk_lphc_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("trunk_lphc_note")), ""));

        result.put("left_right_asymmetry_global",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("left_right_asymmetry_global")), ""));
        result.put("compensation_patterns", castStringList(input == null ? null : input.get("compensation_patterns")));
        result.put("compensation_other_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("compensation_other_note")), ""));
        result.put("primary_compensation_chain_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("primary_compensation_chain_note")), ""));

        result.put("pain_present", Boolean.TRUE.equals(input == null ? null : input.get("pain_present")));
        result.put("pain_vas", normalizeNumber(input == null ? null : input.get("pain_vas")));
        result.put("pain_area", castStringList(input == null ? null : input.get("pain_area")));
        result.put("pain_area_other_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pain_area_other_note")), ""));
        result.put("pain_dominant_pattern",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pain_dominant_pattern")), ""));
        result.put("symptom_irritability",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("symptom_irritability")), ""));
        result.put("pain_control_priority_hint",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pain_control_priority_hint")), ""));

        result.put("breakout_preliminary_direction",
                castStringList(input == null ? null : input.get("breakout_preliminary_direction")));
        result.put("primary_restriction_chain",
                castStringList(input == null ? null : input.get("primary_restriction_chain")));
        result.put("primary_control_deficit_chain",
                castStringList(input == null ? null : input.get("primary_control_deficit_chain")));
        result.put("risk_precheck_level",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("risk_precheck_level")), ""));
        result.put("risk_tags", castStringList(input == null ? null : input.get("risk_tags")));

        result.put("breakout_summary_text",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_summary_text")), ""));
        result.put("clinical_meaning_hint",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("clinical_meaning_hint")), ""));
        result.put("training_direction_hint",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("training_direction_hint")), ""));
        result.put("reassessment_priority",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("reassessment_priority")), "medium"));
        result.put("pause_or_referral_hint",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("pause_or_referral_hint")), ""));

        boolean painRelated = Boolean.TRUE.equals(result.get("pain_present"))
                || StrUtil.equalsAny(toStringValue(result.get("pain_dominant_pattern")), "疑似是", "明显是")
                || StrUtil.equalsAny(toStringValue(result.get("pain_control_priority_hint")),
                "是，建议优先疼痛管理", "是，建议优先人工复核");
        boolean multiChain = castStringList(result.get("primary_restriction_chain")).size() >= 2
                || castStringList(result.get("primary_control_deficit_chain")).size() >= 2
                || castStringList(result.get("risk_tags")).size() >= 3;
        result.put("needs_manual_review",
                Boolean.TRUE.equals(input == null ? null : input.get("needs_manual_review")) || painRelated || multiChain);
        return result;
    }

    private Map<String, Object> mapLegacyBreakoutToDedicatedArmsDownSquat(Map<String, Object> legacy) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("breakout_status", normalizeDedicatedArmsDownSquatBreakoutStatus(legacy == null ? null : legacy.get("status")));
        result.put("breakout_reason_from_screening", "");
        result.put("breakout_note", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("clinician_note")), ""));
        result.put("needs_manual_review",
                Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain"))
                        || Boolean.TRUE.equals(legacy == null ? null : legacy.get("pain_present")));
        result.put("squat_global_quality", "");
        result.put("squat_depth_level", "");
        result.put("descent_control", "");
        result.put("ascent_control", "");
        result.put("squat_rhythm_observation", "");
        result.put("movement_quality_note", "");
        result.put("ankle_dorsiflexion_limitation_suspected", "");
        result.put("heel_rise_pattern", "");
        result.put("foot_pronation_control", "");
        result.put("rearfoot_stability", "");
        result.put("foot_tripod_or_pressure_strategy", "");
        result.put("foot_ankle_note", "");
        result.put("knee_valgus_control", "");
        result.put("knee_varus_or_outward_shift", "");
        result.put("knee_forward_translation_pattern", "");
        result.put("knee_wobble_or_instability", "");
        result.put("knee_note", "");
        result.put("hip_flexion_contribution", "");
        result.put("hip_control_asymmetry", "");
        result.put("pelvic_shift_pattern", "");
        result.put("pelvic_rotation_suspected", "");
        result.put("hip_pelvis_dissociation", "");
        result.put("hip_pelvis_note", "");
        result.put("excessive_forward_lean", "");
        result.put("lumbar_rounding", "");
        result.put("lumbar_extension_or_arching", "");
        result.put("lphc_control_observation", "");
        result.put("trunk_shift_or_rotation", "");
        result.put("trunk_lphc_note", "");
        result.put("left_right_asymmetry_global",
                StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("asymmetry_signs")), ""));
        result.put("compensation_patterns", Collections.emptyList());
        result.put("compensation_other_note", "");
        result.put("primary_compensation_chain_note", "");
        result.put("pain_present", Boolean.TRUE.equals(legacy == null ? null : legacy.get("pain_present")));
        result.put("pain_vas", normalizeNumber(legacy == null ? null : legacy.get("pain_vas")));
        result.put("pain_area", Collections.emptyList());
        result.put("pain_area_other_note", "");
        result.put("pain_dominant_pattern",
                Boolean.TRUE.equals(legacy == null ? null : legacy.get("pain_present")) ? "疑似是" : "");
        result.put("symptom_irritability", "");
        result.put("pain_control_priority_hint",
                Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain")) ? "是，建议优先人工复核" : "");

        List<String> direction = new ArrayList<>();
        if (StrUtil.isNotBlank(toStringValue(legacy == null ? null : legacy.get("mobility_restriction_signs")))) {
            direction.add("更偏踝活动度限制");
        }
        if (StrUtil.isNotBlank(toStringValue(legacy == null ? null : legacy.get("motor_control_signs")))) {
            direction.add("更偏骨盆/LPHC控制问题");
        }
        if (Boolean.TRUE.equals(legacy == null ? null : legacy.get("pain_present"))) {
            direction.add("更偏疼痛主导");
        }
        result.put("breakout_preliminary_direction", distinct(direction));
        result.put("primary_restriction_chain", Collections.emptyList());
        result.put("primary_control_deficit_chain", Collections.emptyList());
        result.put("risk_precheck_level", "");
        result.put("risk_tags", Collections.emptyList());
        result.put("breakout_summary_text",
                StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("findings")), ""));
        result.put("clinical_meaning_hint", "");
        result.put("training_direction_hint", "");
        result.put("reassessment_priority", "medium");
        result.put("pause_or_referral_hint",
                Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain")) ? "建议优先人工复核" : "");
        return normalizeDedicatedArmsDownSquatBreakout(result);
    }

    private Map<String, Object> mapDedicatedArmsDownSquatBreakoutToLegacy(Map<String, Object> dedicated) {
        Map<String, Object> normalized = normalizeDedicatedArmsDownSquatBreakout(dedicated);
        String status = mapDedicatedArmsDownSquatStatusToLegacy(toStringValue(normalized.get("breakout_status")));
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("status", status);
        legacy.put("findings", distinct(Arrays.asList(
                        toStringValue(normalized.get("breakout_summary_text")),
                        toStringValue(normalized.get("clinical_meaning_hint")),
                        toStringValue(normalized.get("training_direction_hint")),
                        toStringValue(normalized.get("primary_compensation_chain_note")),
                        toStringValue(normalized.get("breakout_note"))
                )).stream().filter(StrUtil::isNotBlank).collect(Collectors.joining("；")));
        legacy.put("rom_key_values", "");
        boolean painPresent = Boolean.TRUE.equals(normalized.get("pain_present"))
                || StrUtil.equalsAny(toStringValue(normalized.get("pain_dominant_pattern")), "疑似是", "明显是");
        legacy.put("pain_present", painPresent);
        legacy.put("pain_vas", normalizeNumber(normalized.get("pain_vas")));
        List<String> direction = castStringList(normalized.get("breakout_preliminary_direction"));
        legacy.put("mobility_restriction_signs",
                direction.stream()
                        .filter(item -> StrUtil.equalsAny(item, "更偏踝活动度限制", "更偏髋活动度限制"))
                        .collect(Collectors.joining("、")));
        legacy.put("motor_control_signs",
                direction.stream()
                        .filter(item -> StrUtil.equalsAny(item, "更偏足踝稳定不足", "更偏膝对线/控制问题", "更偏髋控制问题",
                                "更偏骨盆/LPHC控制问题", "更偏躯干控制问题", "更偏左右不对称"))
                        .collect(Collectors.joining("、")));
        String asymmetry = toStringValue(normalized.get("left_right_asymmetry_global"));
        legacy.put("asymmetry_signs", StrUtil.equals(asymmetry, "无明显左右差") ? "" : asymmetry);
        boolean stopDueToPain = StrUtil.equals(toStringValue(normalized.get("pain_control_priority_hint")), "是，建议优先人工复核")
                || StrUtil.equals(toStringValue(normalized.get("symptom_irritability")), "高");
        legacy.put("stop_due_to_pain", stopDueToPain);
        legacy.put("stop_reason", stopDueToPain
                ? StrUtil.blankToDefault(toStringValue(normalized.get("breakout_note")),
                toStringValue(normalized.get("pain_control_priority_hint")))
                : "");
        legacy.put("clinician_note", toStringValue(normalized.get("breakout_note")));
        legacy.put("method", "");
        legacy.put("scale", "");
        legacy.put("source_id", "");
        legacy.put("date", "");
        legacy.put("sls_time_sec", null);
        return legacy;
    }

    private List<String> buildMsfPrimaryFindings(Map<String, Object> msf) {
        List<String> findings = new ArrayList<>();
        String singleLegResult = toStringValue(msf.get("single_leg_standing_forward_flexion_result"));
        if (StrUtil.isNotBlank(singleLegResult)) {
            String asymmetry = toStringValue(msf.get("single_leg_standing_forward_flexion_asymmetry"));
            findings.add("单腿站立体前屈：" + singleLegResult + (StrUtil.isBlank(asymmetry) ? "" : "（" + asymmetry + "）"));
        }
        String longSitResult = toStringValue(msf.get("long_sit_toe_touch_result"));
        if (StrUtil.isNotBlank(longSitResult)) {
            String sacralStatus = toStringValue(msf.get("long_sit_sacral_angle_status"));
            Number sacralDeg = normalizeNumber(msf.get("long_sit_sacral_angle_deg"));
            findings.add("长坐触趾：" + longSitResult
                    + (StrUtil.isBlank(sacralStatus) ? "" : "（" + sacralStatus + (sacralDeg == null ? "" : "，" + sacralDeg + "°") + "）"));
        }
        String aslrResult = toStringValue(msf.get("aslr_result"));
        if (StrUtil.isNotBlank(aslrResult)) {
            Number left = normalizeNumber(msf.get("aslr_left_deg"));
            Number right = normalizeNumber(msf.get("aslr_right_deg"));
            findings.add("ASLR：" + aslrResult
                    + (left == null && right == null ? "" : "（左" + (left == null ? "-" : left) + "°/右" + (right == null ? "-" : right) + "°）"));
        }
        String pslrResult = toStringValue(msf.get("pslr_result"));
        if (StrUtil.isNotBlank(pslrResult)) {
            String compare = toStringValue(msf.get("pslr_vs_aslr_interpretation"));
            findings.add("PSLR：" + pslrResult + (StrUtil.isBlank(compare) ? "" : "（" + compare + "）"));
        }
        String rollingResult = toStringValue(msf.get("rolling_result"));
        if (StrUtil.isNotBlank(rollingResult)) {
            findings.add("滚动解析：" + rollingResult);
        }
        String proneResult = toStringValue(msf.get("prone_rock_back_result"));
        if (StrUtil.isNotBlank(proneResult)) {
            findings.add("俯卧位向后摆动：" + proneResult);
        }
        String kneesResult = toStringValue(msf.get("supine_knees_to_chest_result"));
        if (StrUtil.isNotBlank(kneesResult)) {
            findings.add("仰卧位双膝触胸：" + kneesResult);
        }
        String flowNext = toStringValue(msf.get("flow_next_step"));
        if (StrUtil.isNotBlank(flowNext)) {
            findings.add("流程建议：" + flowNext);
        }
        findings.addAll(castStringList(msf.get("breakout_preliminary_direction")).stream()
                .map(item -> "方向：" + item).collect(Collectors.toList()));
        return distinct(findings);
    }

    private String buildMsfBreakoutSummaryText(String status,
                                               List<String> findings,
                                               List<String> direction,
                                               boolean needsManualReview) {
        if (StrUtil.equals(status, "not_started")) {
            return "多部位屈曲分解评估尚未开始。";
        }
        if (StrUtil.equals(status, "skipped")) {
            return "多部位屈曲分解评估暂未执行，建议结合后续复测决定是否补充。";
        }
        String findingText = findings.isEmpty() ? "暂未提取到明确异常线索" : String.join("、", findings);
        String directionText = direction.isEmpty() ? "方向待补充" : String.join("、", direction);
        String statusZh = StrUtil.equals(status, "completed") ? "已完成" : "进行中";
        return "多部位屈曲分解评估" + statusZh + "，依据单腿站立前屈、长坐触趾、ASLR/PSLR、滚动及俯卧后摆等流程，主要表现为："
                + findingText + "；初步方向：" + directionText + (needsManualReview ? "；建议人工复核。" : "。");
    }

    private List<String> buildMsePrimaryFindings(Map<String, Object> mse) {
        List<String> findings = new ArrayList<>();
        String globalQuality = toStringValue(mse.get("active_extension_global_quality"));
        if (StrUtil.isNotBlank(globalQuality)) {
            findings.add("整体伸展质量：" + globalQuality);
        }
        String extensionCurve = toStringValue(mse.get("extension_curve_observation"));
        if (StrUtil.isNotBlank(extensionCurve)) {
            findings.add("伸展曲线：" + extensionCurve);
        }
        String centerControl = toStringValue(mse.get("center_of_mass_control"));
        if (StrUtil.isNotBlank(centerControl)) {
            findings.add("重心控制：" + centerControl);
        }
        String hip = toStringValue(mse.get("hip_extension_contribution"));
        if (StrUtil.isNotBlank(hip)) {
            findings.add("髋伸展参与：" + hip);
        }
        String lumbar = toStringValue(mse.get("lumbar_extension_participation"));
        if (StrUtil.isNotBlank(lumbar)) {
            findings.add("腰椎伸展参与：" + lumbar);
        }
        String thoracic = toStringValue(mse.get("thoracic_extension_participation"));
        if (StrUtil.isNotBlank(thoracic)) {
            findings.add("胸椎伸展参与：" + thoracic);
        }
        List<String> compensation = castStringList(mse.get("compensation_patterns"));
        if (!compensation.isEmpty()) {
            findings.add("代偿模式：" + String.join("、", compensation));
        }
        Map<String, Object> mseAnalysis = castToMap(mse.get("mse_analysis"));
        Map<String, Object> analysisSummary = castToMap(mseAnalysis == null ? null : mseAnalysis.get("summary"));
        List<String> likelyPattern = castStringList(analysisSummary == null ? null : analysisSummary.get("likely_pattern"));
        if (!likelyPattern.isEmpty()) {
            findings.add("流程判断：" + String.join("、", likelyPattern));
        }
        findings.addAll(castStringList(mse.get("breakout_preliminary_direction")).stream()
                .map(item -> "方向：" + item).collect(Collectors.toList()));
        return distinct(findings);
    }

    private String buildMseBreakoutSummaryText(String status,
                                               List<String> findings,
                                               List<String> direction,
                                               boolean needsManualReview) {
        if (StrUtil.equals(status, "not_started")) {
            return "多部位伸展分解评估尚未开始。";
        }
        if (StrUtil.equals(status, "skipped")) {
            return "多部位伸展分解评估暂未执行，建议结合后续复测决定是否补充。";
        }
        String findingText = findings.isEmpty() ? "暂未提取到明确异常线索" : String.join("、", findings);
        String directionText = direction.isEmpty() ? "方向待补充" : String.join("、", direction);
        String statusZh = StrUtil.equals(status, "completed") ? "已完成" : "进行中";
        return "多部位伸展分解评估" + statusZh + "，主要表现为：" + findingText
                + "；初步方向：" + directionText + (needsManualReview ? "；建议人工复核。" : "。");
    }

    private List<String> buildArmsDownSquatPrimaryFindings(Map<String, Object> armsDown) {
        List<String> findings = new ArrayList<>();
        String globalQuality = toStringValue(armsDown.get("squat_global_quality"));
        if (StrUtil.isNotBlank(globalQuality)) {
            findings.add("整体深蹲质量：" + globalQuality);
        }
        String depth = toStringValue(armsDown.get("squat_depth_level"));
        if (StrUtil.isNotBlank(depth)) {
            findings.add("深蹲深度：" + depth);
        }
        String dorsiflex = toStringValue(armsDown.get("ankle_dorsiflexion_limitation_suspected"));
        if (StrUtil.isNotBlank(dorsiflex)) {
            findings.add("踝背屈观察：" + dorsiflex);
        }
        String kneeValgus = toStringValue(armsDown.get("knee_valgus_control"));
        if (StrUtil.isNotBlank(kneeValgus)) {
            findings.add("膝对线：" + kneeValgus);
        }
        String hipControl = toStringValue(armsDown.get("hip_control_asymmetry"));
        if (StrUtil.isNotBlank(hipControl)) {
            findings.add("髋控制：" + hipControl);
        }
        String lphc = toStringValue(armsDown.get("lphc_control_observation"));
        if (StrUtil.isNotBlank(lphc)) {
            findings.add("LPHC控制：" + lphc);
        }
        String asymmetry = toStringValue(armsDown.get("left_right_asymmetry_global"));
        if (StrUtil.isNotBlank(asymmetry) && !StrUtil.equals(asymmetry, "无明显左右差")) {
            findings.add("左右差：" + asymmetry);
        }
        List<String> compensation = castStringList(armsDown.get("compensation_patterns"));
        if (!compensation.isEmpty()) {
            findings.add("代偿模式：" + String.join("、", compensation));
        }
        List<String> riskTags = castStringList(armsDown.get("risk_tags"));
        if (!riskTags.isEmpty()) {
            findings.add("风险标签：" + String.join("、", riskTags));
        }
        findings.addAll(castStringList(armsDown.get("breakout_preliminary_direction")).stream()
                .map(item -> "方向：" + item).collect(Collectors.toList()));
        return distinct(findings);
    }

    private String buildArmsDownSquatBreakoutSummaryText(String status,
                                                         List<String> findings,
                                                         List<String> direction,
                                                         boolean needsManualReview) {
        if (StrUtil.equals(status, BREAKOUT_STATUS_NOT_STARTED)) {
            return "垂臂下蹲分解评估尚未开始。";
        }
        if (StrUtil.equals(status, BREAKOUT_STATUS_SKIPPED)) {
            return "垂臂下蹲分解评估暂未执行，建议结合后续复测决定是否补充。";
        }
        String findingText = findings.isEmpty() ? "暂未提取到明确异常线索" : String.join("、", findings);
        String directionText = direction.isEmpty() ? "方向待补充" : String.join("、", direction);
        String statusZh = StrUtil.equals(status, BREAKOUT_STATUS_COMPLETED) ? "已完成" : "进行中";
        return "垂臂下蹲分解评估" + statusZh + "，主要表现为：" + findingText
                + "；初步方向：" + directionText + (needsManualReview ? "；建议人工复核。" : "。");
    }

    private Map<String, Object> normalizeDedicatedCervicalBreakout(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("breakout_status", normalizeBreakoutStatus(input == null ? null : input.get("breakout_status")));
        result.put("breakout_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_note")), ""));
        result.put("active_cervical_flexion_quality", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("active_cervical_flexion_quality")), ""));
        result.put("active_cervical_flexion_pain", Boolean.TRUE.equals(input == null ? null : input.get("active_cervical_flexion_pain")));
        result.put("active_cervical_flexion_rom_key", normalizeNumber(input == null ? null : input.get("active_cervical_flexion_rom_key")));
        result.put("active_cervical_flexion_end_feel_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("active_cervical_flexion_end_feel_note")), ""));
        result.put("passive_cervical_flexion_quality", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("passive_cervical_flexion_quality")), ""));
        result.put("passive_cervical_flexion_pain", Boolean.TRUE.equals(input == null ? null : input.get("passive_cervical_flexion_pain")));
        result.put("passive_cervical_flexion_rom_key", normalizeNumber(input == null ? null : input.get("passive_cervical_flexion_rom_key")));
        result.put("passive_vs_active_difference", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("passive_vs_active_difference")), ""));
        result.put("upper_cervical_flexion_observation", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("upper_cervical_flexion_observation")), ""));
        result.put("upper_cervical_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("upper_cervical_note")), ""));
        result.put("compensation_patterns", castStringList(input == null ? null : input.get("compensation_patterns")));
        result.put("compensation_other_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("compensation_other_note")), ""));
        result.put("related_region_influence", castStringList(input == null ? null : input.get("related_region_influence")));
        result.put("breakout_preliminary_direction", castStringList(input == null ? null : input.get("breakout_preliminary_direction")));
        result.put("breakout_summary_text", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_summary_text")), ""));
        boolean needsManualReview = Boolean.TRUE.equals(input == null ? null : input.get("needs_manual_review"))
                || Boolean.TRUE.equals(input == null ? null : input.get("active_cervical_flexion_pain"))
                || Boolean.TRUE.equals(input == null ? null : input.get("passive_cervical_flexion_pain"))
                || StrUtil.equals(toStringValue(input == null ? null : input.get("breakout_status")), BREAKOUT_STATUS_STOPPED_PAIN);
        result.put("needs_manual_review", needsManualReview);
        return result;
    }

    private Map<String, Object> mapLegacyBreakoutToDedicatedCervical(Map<String, Object> legacy) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("breakout_status", normalizeBreakoutStatus(legacy == null ? null : legacy.get("status")));
        result.put("breakout_note", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("clinician_note")), ""));
        result.put("active_cervical_flexion_quality", "");
        result.put("active_cervical_flexion_pain", Boolean.TRUE.equals(legacy == null ? null : legacy.get("pain_present")));
        result.put("active_cervical_flexion_rom_key", null);
        result.put("active_cervical_flexion_end_feel_note", "");
        result.put("passive_cervical_flexion_quality", "");
        result.put("passive_cervical_flexion_pain", false);
        result.put("passive_cervical_flexion_rom_key", null);
        result.put("passive_vs_active_difference", "");
        result.put("upper_cervical_flexion_observation", "");
        result.put("upper_cervical_note", "");
        result.put("compensation_patterns", Collections.emptyList());
        result.put("compensation_other_note", "");
        result.put("related_region_influence", Collections.emptyList());
        result.put("breakout_preliminary_direction", inferBreakoutDirection(legacy == null ? Collections.emptyMap() : legacy));
        result.put("breakout_summary_text", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("findings")), ""));
        result.put("needs_manual_review", Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain")));
        return normalizeDedicatedCervicalBreakout(result);
    }

    private Map<String, Object> mapDedicatedCervicalBreakoutToLegacy(Map<String, Object> dedicated) {
        Map<String, Object> normalized = normalizeDedicatedCervicalBreakout(dedicated);
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("status", normalized.get("breakout_status"));
        legacy.put("findings", StrUtil.blankToDefault(
                toStringValue(normalized.get("breakout_summary_text")),
                toStringValue(normalized.get("breakout_note"))
        ));
        legacy.put("rom_key_values", buildCervicalRomText(normalized, "flexion"));
        legacy.put("pain_present", Boolean.TRUE.equals(normalized.get("active_cervical_flexion_pain"))
                || Boolean.TRUE.equals(normalized.get("passive_cervical_flexion_pain")));
        legacy.put("pain_vas", null);
        legacy.put("mobility_restriction_signs",
                castStringList(normalized.get("breakout_preliminary_direction")).contains("更偏活动度限制")
                        ? "更偏活动度限制" : "");
        legacy.put("motor_control_signs",
                castStringList(normalized.get("breakout_preliminary_direction")).contains("更偏运动控制问题")
                        ? "更偏运动控制问题" : "");
        legacy.put("asymmetry_signs", "");
        legacy.put("stop_due_to_pain", StrUtil.equals(toStringValue(normalized.get("breakout_status")), BREAKOUT_STATUS_STOPPED_PAIN));
        legacy.put("stop_reason", toStringValue(normalized.get("breakout_note")));
        legacy.put("clinician_note", toStringValue(normalized.get("breakout_note")));
        legacy.put("method", "");
        legacy.put("scale", "");
        legacy.put("source_id", "");
        legacy.put("date", "");
        legacy.put("sls_time_sec", null);
        return legacy;
    }

    private Map<String, Object> normalizeDedicatedCervicalExtensionBreakout(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("breakout_status", normalizeBreakoutStatus(input == null ? null : input.get("breakout_status")));
        result.put("breakout_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_note")), ""));
        result.put("active_cervical_extension_quality",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("active_cervical_extension_quality")), ""));
        result.put("active_cervical_extension_pain",
                Boolean.TRUE.equals(input == null ? null : input.get("active_cervical_extension_pain")));
        result.put("active_cervical_extension_rom_key",
                normalizeNumber(input == null ? null : input.get("active_cervical_extension_rom_key")));
        result.put("active_cervical_extension_end_feel_note",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("active_cervical_extension_end_feel_note")), ""));
        result.put("passive_cervical_extension_quality",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("passive_cervical_extension_quality")), ""));
        result.put("passive_cervical_extension_pain",
                Boolean.TRUE.equals(input == null ? null : input.get("passive_cervical_extension_pain")));
        result.put("passive_cervical_extension_rom_key",
                normalizeNumber(input == null ? null : input.get("passive_cervical_extension_rom_key")));
        result.put("passive_vs_active_difference",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("passive_vs_active_difference")), ""));
        result.put("upper_cervical_extension_observation",
                StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("upper_cervical_extension_observation")), ""));
        result.put("upper_cervical_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("upper_cervical_note")), ""));
        result.put("compensation_patterns", castStringList(input == null ? null : input.get("compensation_patterns")));
        result.put("compensation_other_note", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("compensation_other_note")), ""));
        result.put("related_region_influence", castStringList(input == null ? null : input.get("related_region_influence")));
        result.put("breakout_preliminary_direction", castStringList(input == null ? null : input.get("breakout_preliminary_direction")));
        result.put("breakout_summary_text", StrUtil.blankToDefault(toStringValue(input == null ? null : input.get("breakout_summary_text")), ""));
        boolean needsManualReview = Boolean.TRUE.equals(input == null ? null : input.get("needs_manual_review"))
                || Boolean.TRUE.equals(input == null ? null : input.get("active_cervical_extension_pain"))
                || Boolean.TRUE.equals(input == null ? null : input.get("passive_cervical_extension_pain"))
                || StrUtil.equals(toStringValue(input == null ? null : input.get("breakout_status")), BREAKOUT_STATUS_STOPPED_PAIN);
        result.put("needs_manual_review", needsManualReview);
        return result;
    }

    private Map<String, Object> mapLegacyBreakoutToDedicatedCervicalExtension(Map<String, Object> legacy) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("breakout_status", normalizeBreakoutStatus(legacy == null ? null : legacy.get("status")));
        result.put("breakout_note", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("clinician_note")), ""));
        result.put("active_cervical_extension_quality", "");
        result.put("active_cervical_extension_pain", Boolean.TRUE.equals(legacy == null ? null : legacy.get("pain_present")));
        result.put("active_cervical_extension_rom_key", null);
        result.put("active_cervical_extension_end_feel_note", "");
        result.put("passive_cervical_extension_quality", "");
        result.put("passive_cervical_extension_pain", false);
        result.put("passive_cervical_extension_rom_key", null);
        result.put("passive_vs_active_difference", "");
        result.put("upper_cervical_extension_observation", "");
        result.put("upper_cervical_note", "");
        result.put("compensation_patterns", Collections.emptyList());
        result.put("compensation_other_note", "");
        result.put("related_region_influence", Collections.emptyList());
        result.put("breakout_preliminary_direction", inferBreakoutDirection(legacy == null ? Collections.emptyMap() : legacy));
        result.put("breakout_summary_text", StrUtil.blankToDefault(toStringValue(legacy == null ? null : legacy.get("findings")), ""));
        result.put("needs_manual_review", Boolean.TRUE.equals(legacy == null ? null : legacy.get("stop_due_to_pain")));
        return normalizeDedicatedCervicalExtensionBreakout(result);
    }

    private Map<String, Object> mapDedicatedCervicalExtensionBreakoutToLegacy(Map<String, Object> dedicated) {
        Map<String, Object> normalized = normalizeDedicatedCervicalExtensionBreakout(dedicated);
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("status", normalized.get("breakout_status"));
        legacy.put("findings", StrUtil.blankToDefault(
                toStringValue(normalized.get("breakout_summary_text")),
                toStringValue(normalized.get("breakout_note"))
        ));
        legacy.put("rom_key_values", buildCervicalRomText(normalized, "extension"));
        legacy.put("pain_present", Boolean.TRUE.equals(normalized.get("active_cervical_extension_pain"))
                || Boolean.TRUE.equals(normalized.get("passive_cervical_extension_pain")));
        legacy.put("pain_vas", null);
        legacy.put("mobility_restriction_signs",
                castStringList(normalized.get("breakout_preliminary_direction")).contains("更偏活动度限制")
                        ? "更偏活动度限制" : "");
        legacy.put("motor_control_signs",
                castStringList(normalized.get("breakout_preliminary_direction")).contains("更偏运动控制问题")
                        ? "更偏运动控制问题" : "");
        legacy.put("asymmetry_signs", "");
        legacy.put("stop_due_to_pain", StrUtil.equals(toStringValue(normalized.get("breakout_status")), BREAKOUT_STATUS_STOPPED_PAIN));
        legacy.put("stop_reason", toStringValue(normalized.get("breakout_note")));
        legacy.put("clinician_note", toStringValue(normalized.get("breakout_note")));
        legacy.put("method", "");
        legacy.put("scale", "");
        legacy.put("source_id", "");
        legacy.put("date", "");
        legacy.put("sls_time_sec", null);
        return legacy;
    }

    private String buildCervicalRomText(Map<String, Object> normalized, String motion) {
        List<String> tokens = new ArrayList<>();
        String activeKey = "extension".equals(motion) ? "active_cervical_extension_rom_key" : "active_cervical_flexion_rom_key";
        String passiveKey = "extension".equals(motion) ? "passive_cervical_extension_rom_key" : "passive_cervical_flexion_rom_key";
        Number active = normalizeNumber(normalized.get(activeKey));
        Number passive = normalizeNumber(normalized.get(passiveKey));
        if (active != null) {
            tokens.add("主动ROM:" + active);
        }
        if (passive != null) {
            tokens.add("被动ROM:" + passive);
        }
        return String.join(" | ", tokens);
    }

    private String toStringValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if ("null".equalsIgnoreCase(text) || "undefined".equalsIgnoreCase(text)) {
            return "";
        }
        return text;
    }

    private Number normalizeNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return (Number) value;
        }
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (Exception ignore) {
            return null;
        }
    }

    private int normalizeInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    private String joinList(Object value, String separator) {
        if (!(value instanceof List)) {
            return "";
        }
        List<?> list = (List<?>) value;
        List<String> texts = list.stream().map(String::valueOf).filter(StrUtil::isNotBlank).collect(Collectors.toList());
        return String.join(separator, texts);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToMap(Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castToMapList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map) {
                result.add((Map<String, Object>) item);
            }
        }
        return result;
    }

    private Map<String, Object> normalizeToMap(Object rawDataJson) {
        if (rawDataJson == null) {
            return new LinkedHashMap<>();
        }
        if (rawDataJson instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) rawDataJson);
        }
        if (rawDataJson instanceof String) {
            String text = (String) rawDataJson;
            if (StrUtil.isBlank(text)) {
                return new LinkedHashMap<>();
            }
            Map<String, Object> parsed = JsonUtils.parseObject(text, new TypeReference<Map<String, Object>>() {});
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        }
        Map<String, Object> parsed = JsonUtils.parseObject(JsonUtils.toJsonString(rawDataJson),
                new TypeReference<Map<String, Object>>() {});
        return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
    }

    private Map<String, Object> extractSfmaPayload(Map<String, Object> rawPayload) {
        Map<String, Object> nested = castToMap(rawPayload.get("sfma"));
        if (nested == null) {
            return rawPayload;
        }
        Map<String, Object> normalized = normalizeToMap(nested);
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return rawPayload;
    }

    private Map<String, Object> wrapSfmaPayload(Map<String, Object> rawPayload, Map<String, Object> sfmaPayload) {
        Map<String, Object> result = new LinkedHashMap<>(rawPayload);
        Map<String, Object> sfma = new LinkedHashMap<>(sfmaPayload);
        sfma.remove("sfma");
        result.put("sfma", sfma);
        for (String key : LEGACY_MIRROR_KEYS) {
            if (sfma.containsKey(key)) {
                result.put(key, sfma.get(key));
            }
        }
        return result;
    }

    private Map<String, Object> ensureMap(Map<String, Object> source, String key) {
        Map<String, Object> map = castToMap(source.get(key));
        if (map == null) {
            map = new LinkedHashMap<>();
            source.put(key, map);
        }
        return map;
    }

    private static SfmaTopTierDefinition def(String testCode, String testNameZh, String side, String breakoutKey, String group) {
        return new SfmaTopTierDefinition(testCode, testNameZh, side, breakoutKey, group);
    }

    private static List<String> distinct(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream().filter(StrUtil::isNotBlank).distinct().collect(Collectors.toList());
    }

    private static void inc(Map<String, Integer> scoreMap, String key) {
        scoreMap.put(key, scoreMap.getOrDefault(key, 0) + 1);
    }

    @Data
    @AllArgsConstructor
    private static class SfmaTopTierDefinition {
        private String testCode;
        private String testNameZh;
        private String side;
        private String breakoutKey;
        private String group;
    }

    @Data
    @AllArgsConstructor
    private static class ClassificationResult {
        private String primaryClassification;
        private List<String> secondaryClassification;
        private String confidence;
        private boolean mixedPatternPossible;
        private String caveat;
    }
}
