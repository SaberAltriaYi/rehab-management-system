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
 * NASM-CES 汇总构建器（Step 8.3）
 *
 * 说明：
 * 1. 仅做结构化结果整理与谨慎表达，不输出诊断结论
 * 2. 输出写回 nasm module 的 data_json.ces_summary / data_json.action_summaries
 * 3. 支持部分录入，缺失数据不阻断主链路
 */
@Component
public class RehabNasmCesSummaryBuilder {

    private static final String NOT_ASSESSED = "not_assessed";
    private static final String PARTIAL = "partial";
    private static final String COMPLETED = "completed";
    private static final String RISK_LOW = "low";
    private static final String RISK_MEDIUM = "medium";
    private static final String RISK_HIGH = "high";

    private static final String TAG_LOWER_EXT_ALIGNMENT = "lower_extremity_alignment_attention";
    private static final String TAG_LPHC_STABILITY = "lphc_stability_attention";
    private static final String TAG_ASYMMETRY = "asymmetry_attention";
    private static final String TAG_OVERHEAD = "overhead_pattern_attention";
    private static final String TAG_UPPER_EXT_STABILITY = "upper_extremity_stability_attention";
    private static final String TAG_LANDING = "landing_control_attention";
    private static final String TAG_GAIT = "gait_control_attention";
    private static final String TAG_DYNAMIC_BALANCE = "dynamic_balance_attention";
    private static final String TAG_PAIN = "pain_attention";
    private static final String TAG_REASSESSMENT = "reassessment_attention";

    private static final Set<String> NORMAL_TEXT_VALUES = new HashSet<>(Arrays.asList(
            "正常", "未评估", "中立", "平坦", "柔和的", "普通的", "出色的", "否"
    ));

    private static final Set<String> GENERIC_IGNORED_KEYS = new HashSet<>(Arrays.asList(
            "note", "summary_note", "general_note", "repetition_quality_note", "variation_note",
            "trial_no", "point_distance_inch", "point_distance_cm", "duration_sec", "total_repetition_count",
            "total_findings_count", "less_total_score", "reps_target", "humerus_wall_angle_deg"
    ));

    private static final List<ActionDefinition> TRANSITION_ACTIONS = Arrays.asList(
            action("overhead_squat", "过顶深蹲", "transition_assessments.overhead_squat"),
            action("single_leg_squat", "单腿蹲", "transition_assessments.single_leg_squat"),
            action("push_up", "俯卧撑", "transition_assessments.push_up"),
            action("standing_row", "站立划船", "transition_assessments.standing_row"),
            action("standing_dumbbell_overhead_press", "站立哑铃过头举", "transition_assessments.standing_dumbbell_overhead_press"),
            action("upper_extremity_transition", "上肢过渡评估", "transition_assessments.upper_extremity_transition"),
            action("star_excursion_balance_deviation_test", "星行平衡偏移测试", "transition_assessments.star_excursion_balance_deviation_test")
    );

    private static final List<ActionDefinition> DYNAMIC_ACTIONS = Arrays.asList(
            action("gait_analysis", "步态分析", "dynamic_assessments.gait_analysis"),
            action("tuck_jump_assessment", "团身跳评估", "dynamic_assessments.tuck_jump_assessment")
    );

    private static final ActionDefinition DAVIES_ACTION = action(
            "upper_extremity_davies_test", "上肢戴维斯测试", "upper_extremity_davies_test"
    );
    private static final ActionDefinition LESS_ACTION = action(
            "less_test", "LESS 测试", "less_test"
    );

    public Map<String, Object> enrichWithSummary(Object rawDataJson) {
        Map<String, Object> payload = normalizeToMap(rawDataJson);

        Map<String, ActionExtraction> transitionActionMap = extractActions(payload, TRANSITION_ACTIONS);
        Map<String, ActionExtraction> dynamicActionMap = extractActions(payload, DYNAMIC_ACTIONS);
        DaviesExtraction daviesExtraction = extractDavies(payload);
        LessExtraction lessExtraction = extractLess(payload);

        List<Map<String, Object>> actionSummaries = buildActionSummaries(
                transitionActionMap, dynamicActionMap, daviesExtraction, lessExtraction
        );
        Map<String, Object> cesSummary = buildCesSummary(
                transitionActionMap, dynamicActionMap, daviesExtraction, lessExtraction, payload
        );
        cesSummary.put("action_summaries", actionSummaries);

        Map<String, Object> riskPrecheck = buildOverallRiskPrecheck(payload, cesSummary, actionSummaries);
        Map<String, Object> reportMapping = buildReportMapping(cesSummary, riskPrecheck);

        Map<String, Object> overallSummary = castToMap(cesSummary.get("overall_summary"));
        if (overallSummary != null) {
            overallSummary.put("overall_risk_precheck", riskPrecheck);
            overallSummary.put("movement_pattern_flags", toMovementPatternFlags(castStringList(riskPrecheck.get("risk_tags"))));
        }

        payload.put("ces_summary", cesSummary);
        payload.put("risk_precheck", riskPrecheck);
        payload.put("report_mapping", reportMapping);
        // 向后兼容旧展示读取
        payload.put("action_summaries", actionSummaries);
        return payload;
    }

    public Map<String, Object> enrichWithFallback(Object rawDataJson, String reason) {
        Map<String, Object> payload = normalizeToMap(rawDataJson);

        Map<String, Object> transitionSummary = new LinkedHashMap<>();
        transitionSummary.put("completed_items", 0);
        transitionSummary.put("missing_items", TRANSITION_ACTIONS.size());
        transitionSummary.put("abnormal_findings", Collections.emptyList());
        transitionSummary.put("left_right_asymmetry_findings", Collections.emptyList());
        transitionSummary.put("summary_text", "过渡动作评估汇总生成失败，需结合人工复核。");

        Map<String, Object> dynamicSummary = new LinkedHashMap<>();
        dynamicSummary.put("completed_items", 0);
        dynamicSummary.put("missing_items", DYNAMIC_ACTIONS.size());
        dynamicSummary.put("abnormal_findings", Collections.emptyList());
        dynamicSummary.put("summary_text", "动态动作评估汇总生成失败，需结合人工复核。");

        Map<String, Object> daviesSummary = new LinkedHashMap<>();
        daviesSummary.put("trial_count", 0);
        daviesSummary.put("results", Collections.emptyList());
        daviesSummary.put("summary_text", "上肢戴维斯测试汇总生成失败，需结合人工复核。");

        Map<String, Object> lessSummary = new LinkedHashMap<>();
        lessSummary.put("filled_item_count", 0);
        lessSummary.put("less_total_score", null);
        lessSummary.put("key_findings", Collections.emptyList());
        lessSummary.put("summary_text", "LESS 测试汇总生成失败，需结合人工复核。");

        Map<String, Object> overallSummary = new LinkedHashMap<>();
        overallSummary.put("key_findings", Collections.emptyList());
        overallSummary.put("priority_regions", Collections.emptyList());
        overallSummary.put("movement_pattern_flags", Collections.emptyList());
        overallSummary.put("overall_risk_precheck", buildFallbackRiskPrecheck());
        overallSummary.put("summary_text", "CES 评估汇总生成失败，需结合人工复核。");
        overallSummary.put("fallback_reason", StrUtil.blankToDefault(reason, "unknown"));

        Map<String, Object> cesSummary = new LinkedHashMap<>();
        cesSummary.put("transition_assessments_summary", transitionSummary);
        cesSummary.put("dynamic_assessments_summary", dynamicSummary);
        cesSummary.put("davies_test_summary", daviesSummary);
        cesSummary.put("less_test_summary", lessSummary);
        cesSummary.put("overall_summary", overallSummary);
        cesSummary.put("action_summaries", Collections.emptyList());

        payload.put("ces_summary", cesSummary);
        payload.put("action_summaries", Collections.emptyList());
        payload.put("risk_precheck", buildFallbackRiskPrecheck());
        payload.put("report_mapping", buildFallbackReportMapping());
        return payload;
    }

    private Map<String, Object> buildCesSummary(Map<String, ActionExtraction> transitionActionMap,
                                                Map<String, ActionExtraction> dynamicActionMap,
                                                DaviesExtraction daviesExtraction,
                                                LessExtraction lessExtraction,
                                                Map<String, Object> payload) {
        Map<String, Object> transitionSummary = buildTransitionSummary(transitionActionMap);
        Map<String, Object> dynamicSummary = buildDynamicSummary(dynamicActionMap);
        Map<String, Object> daviesSummary = buildDaviesSummary(daviesExtraction);
        Map<String, Object> lessSummary = buildLessSummary(lessExtraction);
        Map<String, Object> overallSummary = buildOverallSummary(
                transitionActionMap, dynamicActionMap, daviesExtraction, lessExtraction, payload
        );

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("transition_assessments_summary", transitionSummary);
        summary.put("dynamic_assessments_summary", dynamicSummary);
        summary.put("davies_test_summary", daviesSummary);
        summary.put("less_test_summary", lessSummary);
        summary.put("overall_summary", overallSummary);
        return summary;
    }

    private Map<String, Object> buildTransitionSummary(Map<String, ActionExtraction> transitionActionMap) {
        int completed = (int) transitionActionMap.values().stream().filter(ActionExtraction::isAssessed).count();
        int total = TRANSITION_ACTIONS.size();

        List<Map<String, Object>> abnormalFindings = transitionActionMap.values().stream()
                .flatMap(item -> item.getAbnormalFindings().stream())
                .collect(Collectors.toList());
        List<Map<String, Object>> asymmetryFindings = transitionActionMap.values().stream()
                .flatMap(item -> item.getAsymmetryFindings().stream())
                .collect(Collectors.toList());

        String summaryText;
        if (completed == 0) {
            summaryText = "过渡动作评估暂未录入完整评估结果。";
        } else if (abnormalFindings.isEmpty()) {
            summaryText = "过渡动作评估已完成 " + completed + " 项，当前录入结果暂未见明显异常代偿项。";
        } else {
            String topFindings = abnormalFindings.stream().limit(4)
                    .map(item -> item.get("action_name_zh") + "：" + item.get("finding_text"))
                    .collect(Collectors.joining("；"));
            summaryText = "过渡动作评估中可见异常代偿项，主要包括：" + topFindings + "。";
        }
        List<String> keyFindings = abnormalFindings.stream().limit(8)
                .map(item -> String.valueOf(item.get("action_name_zh")) + "：" + String.valueOf(item.get("finding_text")))
                .collect(Collectors.toList());
        Map<String, Object> riskPrecheck = buildModuleRiskPrecheck(
                "过渡动作评估", abnormalFindings, asymmetryFindings, completed, total
        );

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("completed_items", completed);
        summary.put("missing_items", total - completed);
        summary.put("abnormal_findings", abnormalFindings);
        summary.put("left_right_asymmetry_findings", asymmetryFindings);
        summary.put("key_findings", keyFindings);
        summary.put("risk_precheck", riskPrecheck);
        summary.put("summary_text", summaryText);
        return summary;
    }

    private Map<String, Object> buildDynamicSummary(Map<String, ActionExtraction> dynamicActionMap) {
        int completed = (int) dynamicActionMap.values().stream().filter(ActionExtraction::isAssessed).count();
        int total = DYNAMIC_ACTIONS.size();
        List<Map<String, Object>> abnormalFindings = dynamicActionMap.values().stream()
                .flatMap(item -> item.getAbnormalFindings().stream())
                .collect(Collectors.toList());

        String summaryText;
        if (completed == 0) {
            summaryText = "动态动作评估暂未录入完整评估结果。";
        } else if (abnormalFindings.isEmpty()) {
            summaryText = "动态动作评估已完成 " + completed + " 项，当前记录中未见明显异常代偿项。";
        } else {
            String topFindings = abnormalFindings.stream().limit(4)
                    .map(item -> item.get("action_name_zh") + "：" + item.get("finding_text"))
                    .collect(Collectors.joining("；"));
            summaryText = "动态动作评估提示存在动作控制偏移，主要包括：" + topFindings + "。";
        }
        List<String> keyFindings = abnormalFindings.stream().limit(8)
                .map(item -> String.valueOf(item.get("action_name_zh")) + "：" + String.valueOf(item.get("finding_text")))
                .collect(Collectors.toList());
        Map<String, Object> riskPrecheck = buildModuleRiskPrecheck(
                "动态动作评估", abnormalFindings, Collections.emptyList(), completed, total
        );

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("completed_items", completed);
        summary.put("missing_items", total - completed);
        summary.put("abnormal_findings", abnormalFindings);
        summary.put("left_right_asymmetry_findings", Collections.emptyList());
        summary.put("key_findings", keyFindings);
        summary.put("risk_precheck", riskPrecheck);
        summary.put("summary_text", summaryText);
        return summary;
    }

    private Map<String, Object> buildDaviesSummary(DaviesExtraction daviesExtraction) {
        String summaryText;
        if (!daviesExtraction.isAssessed()) {
            summaryText = "上肢戴维斯测试暂未录入有效测试结果。";
        } else {
            String repetitionText = daviesExtraction.getTotalRepetitionCount() == null
                    ? "总次数暂未录入"
                    : "总次数 " + daviesExtraction.getTotalRepetitionCount();
            summaryText = "上肢戴维斯测试共记录 " + daviesExtraction.getTrialCount() + " 组，" + repetitionText + "。";
        }
        Map<String, Object> riskPrecheck = buildDaviesRiskPrecheck(daviesExtraction);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("trial_count", daviesExtraction.getTrialCount());
        summary.put("results", daviesExtraction.getResults());
        summary.put("key_findings", daviesExtraction.getKeyFindings());
        summary.put("risk_precheck", riskPrecheck);
        summary.put("summary_text", summaryText);
        return summary;
    }

    private Map<String, Object> buildLessSummary(LessExtraction lessExtraction) {
        String summaryText;
        if (!lessExtraction.isAssessed()) {
            summaryText = "LESS 测试暂未录入完整评分结果。";
        } else if (lessExtraction.getKeyFindings().isEmpty()) {
            summaryText = "LESS 测试已录入，当前高风险选项较少。";
        } else {
            summaryText = "LESS 测试中主要表现为：" + String.join("；", lessExtraction.getKeyFindings()) + "。";
        }
        Map<String, Object> riskPrecheck = buildLessRiskPrecheck(lessExtraction);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("filled_item_count", lessExtraction.getFilledItemCount());
        summary.put("less_total_score", lessExtraction.getLessTotalScore());
        summary.put("key_findings", lessExtraction.getKeyFindings());
        summary.put("risk_precheck", riskPrecheck);
        summary.put("summary_text", summaryText);
        return summary;
    }

    private Map<String, Object> buildOverallSummary(Map<String, ActionExtraction> transitionActionMap,
                                                    Map<String, ActionExtraction> dynamicActionMap,
                                                    DaviesExtraction daviesExtraction,
                                                    LessExtraction lessExtraction,
                                                    Map<String, Object> payload) {
        List<Map<String, Object>> allFindings = new ArrayList<>();
        transitionActionMap.values().forEach(item -> allFindings.addAll(item.getAbnormalFindings()));
        dynamicActionMap.values().forEach(item -> allFindings.addAll(item.getAbnormalFindings()));

        allFindings.addAll(daviesExtraction.getKeyFindings().stream()
                .map(text -> simpleOverallFinding(DAVIES_ACTION.getCode(), DAVIES_ACTION.getNameZh(), text))
                .collect(Collectors.toList()));
        allFindings.addAll(lessExtraction.getKeyFindings().stream()
                .map(text -> simpleOverallFinding(LESS_ACTION.getCode(), LESS_ACTION.getNameZh(), text))
                .collect(Collectors.toList()));

        List<String> keyFindings = allFindings.stream().limit(10)
                .map(item -> String.valueOf(item.get("action_name_zh")) + "：" + String.valueOf(item.get("finding_text")))
                .collect(Collectors.toList());
        List<String> priorityRegions = resolvePriorityRegions(allFindings);
        List<String> movementPatternFlags = resolveMovementPatternFlagsFromFindings(allFindings);
        List<String> manualNotes = extractManualSummaryNotes(payload);

        String summaryText;
        if (keyFindings.isEmpty()) {
            summaryText = "CES 评估显示当前录入信息有限，证据不足，需结合人工复核。";
        } else {
            String regionsText = priorityRegions.isEmpty() ? "动作质量" : String.join("、", priorityRegions);
            summaryText = "综合 CES 动作表现，主要表现为：" + String.join("；", keyFindings) +
                    "。从当前动作质量表现看，优先关注" + regionsText + "。";
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("key_findings", keyFindings);
        summary.put("priority_regions", priorityRegions);
        summary.put("movement_pattern_flags", movementPatternFlags);
        summary.put("manual_notes", manualNotes);
        summary.put("summary_text", summaryText);
        return summary;
    }

    private List<Map<String, Object>> buildActionSummaries(Map<String, ActionExtraction> transitionActionMap,
                                                           Map<String, ActionExtraction> dynamicActionMap,
                                                           DaviesExtraction daviesExtraction,
                                                           LessExtraction lessExtraction) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        TRANSITION_ACTIONS.forEach(action -> summaries.add(buildGenericActionSummary(transitionActionMap.get(action.getCode()))));
        DYNAMIC_ACTIONS.forEach(action -> summaries.add(buildGenericActionSummary(dynamicActionMap.get(action.getCode()))));
        summaries.add(buildDaviesActionSummary(daviesExtraction));
        summaries.add(buildLessActionSummary(lessExtraction));
        return summaries;
    }

    private Map<String, Object> buildGenericActionSummary(ActionExtraction extraction) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("action_code", extraction.getActionCode());
        summary.put("action_name_zh", extraction.getActionNameZh());
        String status = resolveActionStatus(extraction);
        List<String> primaryRegions = resolvePriorityRegions(extraction.getAbnormalFindings());
        Map<String, Object> riskPrecheck = buildActionRiskPrecheck(extraction, primaryRegions, status);

        if (NOT_ASSESSED.equals(status)) {
            summary.put("status", NOT_ASSESSED);
            summary.put("abnormal_findings", Collections.emptyList());
            summary.put("left_right_asymmetry_findings", Collections.emptyList());
            summary.put("primary_regions", Collections.emptyList());
            summary.put("key_findings", Collections.emptyList());
            summary.put("risk_precheck", riskPrecheck);
            summary.put("summary_text", extraction.getActionNameZh() + "：未评估。");
            return summary;
        }

        List<String> findings = extraction.getAbnormalFindings().stream().limit(3)
                .map(item -> String.valueOf(item.get("finding_text")))
                .collect(Collectors.toList());

        String summaryText;
        if (findings.isEmpty()) {
            summaryText = extraction.getActionNameZh() + "：已评估，当前记录中未见明显异常代偿项。";
        } else {
            summaryText = extraction.getActionNameZh() + "：可见 " + String.join("；", findings) + "。";
        }

        summary.put("status", status);
        summary.put("abnormal_findings", extraction.getAbnormalFindings());
        summary.put("left_right_asymmetry_findings", extraction.getAsymmetryFindings());
        summary.put("primary_regions", primaryRegions);
        summary.put("key_findings", findings);
        summary.put("risk_precheck", riskPrecheck);
        summary.put("summary_text", summaryText);
        return summary;
    }

    private Map<String, Object> buildDaviesActionSummary(DaviesExtraction extraction) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("action_code", DAVIES_ACTION.getCode());
        summary.put("action_name_zh", DAVIES_ACTION.getNameZh());
        Map<String, Object> riskPrecheck = buildDaviesRiskPrecheck(extraction);
        if (!extraction.isAssessed()) {
            summary.put("status", NOT_ASSESSED);
            summary.put("abnormal_findings", Collections.emptyList());
            summary.put("left_right_asymmetry_findings", Collections.emptyList());
            summary.put("primary_regions", Collections.singletonList("上肢闭链稳定"));
            summary.put("key_findings", Collections.emptyList());
            summary.put("risk_precheck", riskPrecheck);
            summary.put("summary_text", "上肢戴维斯测试：未评估。");
            return summary;
        }

        List<String> findings = extraction.getKeyFindings();
        List<Map<String, Object>> abnormal = findings.stream()
                .map(item -> simpleOverallFinding(DAVIES_ACTION.getCode(), DAVIES_ACTION.getNameZh(), item))
                .collect(Collectors.toList());
        summary.put("status", COMPLETED);
        summary.put("abnormal_findings", abnormal);
        summary.put("left_right_asymmetry_findings", Collections.emptyList());
        summary.put("primary_regions", Collections.singletonList("上肢闭链稳定"));
        summary.put("key_findings", findings);
        summary.put("risk_precheck", riskPrecheck);
        summary.put("summary_text", "上肢戴维斯测试：" + (findings.isEmpty() ? "已评估。" : String.join("；", findings) + "。"));
        return summary;
    }

    private Map<String, Object> buildLessActionSummary(LessExtraction extraction) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("action_code", LESS_ACTION.getCode());
        summary.put("action_name_zh", LESS_ACTION.getNameZh());
        Map<String, Object> riskPrecheck = buildLessRiskPrecheck(extraction);
        if (!extraction.isAssessed()) {
            summary.put("status", NOT_ASSESSED);
            summary.put("abnormal_findings", Collections.emptyList());
            summary.put("left_right_asymmetry_findings", Collections.emptyList());
            summary.put("primary_regions", Collections.singletonList("跳跃/落地控制"));
            summary.put("key_findings", Collections.emptyList());
            summary.put("risk_precheck", riskPrecheck);
            summary.put("summary_text", "LESS 测试：未评估。");
            return summary;
        }
        List<Map<String, Object>> abnormal = extraction.getKeyFindings().stream()
                .map(item -> simpleOverallFinding(LESS_ACTION.getCode(), LESS_ACTION.getNameZh(), item))
                .collect(Collectors.toList());
        summary.put("status", COMPLETED);
        summary.put("abnormal_findings", abnormal);
        summary.put("left_right_asymmetry_findings", Collections.emptyList());
        summary.put("primary_regions", Arrays.asList("跳跃/落地控制", "动态控制"));
        summary.put("key_findings", extraction.getKeyFindings());
        summary.put("risk_precheck", riskPrecheck);
        summary.put("summary_text", "LESS 测试：" + (extraction.getKeyFindings().isEmpty()
                ? "已录入评分。"
                : String.join("；", extraction.getKeyFindings()) + "。"));
        return summary;
    }

    private Map<String, ActionExtraction> extractActions(Map<String, Object> payload, List<ActionDefinition> actions) {
        Map<String, ActionExtraction> map = new LinkedHashMap<>();
        for (ActionDefinition action : actions) {
            Map<String, Object> actionNode = getMapByPath(payload, action.getPath());
            ActionExtraction extraction = new ActionExtraction(action.getCode(), action.getNameZh());
            if (actionNode != null) {
                walkActionNode(actionNode, "", extraction);
            }
            map.put(action.getCode(), extraction);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private void walkActionNode(Object node, String path, ActionExtraction extraction) {
        if (node == null) {
            return;
        }
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;

            if (map.containsKey("present")) {
                Object present = map.get("present");
                if (present != null) {
                    extraction.setAssessed(true);
                    extraction.markObserved();
                }
                if (Boolean.TRUE.equals(present)) {
                    String side = resolveBooleanSide(map);
                    extraction.addAbnormal(buildFinding(extraction, path, "异常表现", side));
                }
                detectBooleanSideAsymmetry(map, extraction, path);
            }

            if (map.containsKey("left") && map.containsKey("right")) {
                handleBilateralPair(map, path, extraction);
            }

            if (Boolean.TRUE.equals(map.get("overall"))) {
                extraction.setAssessed(true);
                extraction.addAbnormal(buildFinding(extraction, path, "整体异常", "global"));
            }

            if (map.containsKey("value")) {
                String valueText = normalizeText(map.get("value"));
                if (StrUtil.isNotBlank(valueText)) {
                    extraction.setAssessed(true);
                    extraction.markObserved();
                    if (isAbnormalValue(valueText)) {
                        extraction.addAbnormal(buildFinding(extraction, path, valueText, "global"));
                    }
                }
            }

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                if (GENERIC_IGNORED_KEYS.contains(key)
                        || "present".equals(key)
                        || "left".equals(key)
                        || "right".equals(key)
                        || "overall".equals(key)
                        || "value".equals(key)) {
                    continue;
                }
                String nextPath = buildPath(path, key);
                Object value = entry.getValue();
                if (value instanceof Map || value instanceof List) {
                    walkActionNode(value, nextPath, extraction);
                    continue;
                }
                if (value instanceof String) {
                    String text = normalizeText(value);
                    if (StrUtil.isBlank(text)) {
                        continue;
                    }
                    if ("未评估".equals(text)) {
                        continue;
                    }
                    extraction.setAssessed(true);
                    extraction.markObserved();
                    if (isAbnormalValue(text)) {
                        extraction.addAbnormal(buildFinding(extraction, nextPath, text, "global"));
                    }
                } else if (value instanceof Number) {
                    if (isMeaningfulNumberKey(key) && value != null) {
                        extraction.setAssessed(true);
                        extraction.markObserved();
                    }
                } else if (value instanceof Boolean) {
                    if (Boolean.TRUE.equals(value)) {
                        extraction.setAssessed(true);
                        extraction.markObserved();
                    }
                }
            }
            return;
        }
        if (node instanceof List) {
            List<?> list = (List<?>) node;
            for (int i = 0; i < list.size(); i++) {
                walkActionNode(list.get(i), buildPath(path, "[" + i + "]"), extraction);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private DaviesExtraction extractDavies(Map<String, Object> payload) {
        Map<String, Object> daviesMap = getMapByPath(payload, DAVIES_ACTION.getPath());
        DaviesExtraction extraction = new DaviesExtraction();
        if (daviesMap == null) {
            return extraction;
        }
        Object trialsNode = daviesMap.get("trials");
        if (!(trialsNode instanceof List)) {
            return extraction;
        }
        List<Object> trials = (List<Object>) trialsNode;
        for (Object trialNode : trials) {
            if (!(trialNode instanceof Map)) {
                continue;
            }
            Map<String, Object> trial = (Map<String, Object>) trialNode;
            Integer trialNo = toInteger(trial.get("trial_no"));
            Integer duration = toInteger(trial.get("duration_sec"));
            Integer repetition = toInteger(trial.get("repetition_count"));
            String qualityNote = normalizeText(trial.get("repetition_quality_note"));

            boolean hasInput = repetition != null || StrUtil.isNotBlank(qualityNote);
            if (!hasInput) {
                continue;
            }
            extraction.setAssessed(true);
            extraction.setTrialCount(extraction.getTrialCount() + 1);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("trial_no", trialNo);
            result.put("duration_sec", duration);
            result.put("repetition_count", repetition);
            result.put("repetition_quality_note", qualityNote);
            extraction.getResults().add(result);
            if (repetition != null) {
                extraction.setTotalRepetitionCount(
                        extraction.getTotalRepetitionCount() == null
                                ? repetition
                                : extraction.getTotalRepetitionCount() + repetition
                );
            }
        }
        if (extraction.getTotalRepetitionCount() != null) {
            extraction.getKeyFindings().add("累计次数：" + extraction.getTotalRepetitionCount());
        }
        return extraction;
    }

    @SuppressWarnings("unchecked")
    private LessExtraction extractLess(Map<String, Object> payload) {
        Map<String, Object> lessMap = getMapByPath(payload, LESS_ACTION.getPath());
        LessExtraction extraction = new LessExtraction();
        if (lessMap == null) {
            return extraction;
        }

        extraction.setLessTotalScore(toInteger(lessMap.get("less_total_score")));
        Map<String, Object> items = lessMap.get("items") instanceof Map
                ? (Map<String, Object>) lessMap.get("items")
                : Collections.emptyMap();
        if (!items.isEmpty()) {
            for (Map.Entry<String, Object> entry : items.entrySet()) {
                if (!(entry.getValue() instanceof Map)) {
                    continue;
                }
                Map<String, Object> item = (Map<String, Object>) entry.getValue();
                String value = normalizeText(item.get("value"));
                if (StrUtil.isBlank(value)) {
                    continue;
                }
                extraction.setAssessed(true);
                extraction.setFilledItemCount(extraction.getFilledItemCount() + 1);
                if (isLessRiskValue(value)) {
                    extraction.getKeyFindings().add(entry.getKey() + "：" + value);
                }
            }
        }

        if (extraction.getLessTotalScore() != null) {
            extraction.setAssessed(true);
        }
        return extraction;
    }

    private Map<String, Object> buildFinding(ActionExtraction extraction, String path, String value, String side) {
        Map<String, Object> finding = new LinkedHashMap<>();
        finding.put("action_code", extraction.getActionCode());
        finding.put("action_name_zh", extraction.getActionNameZh());
        finding.put("observation_path", path);
        finding.put("observation_plane", resolvePlane(path));
        finding.put("check_point", resolveCheckPoint(path));
        finding.put("observation_item", resolveObservationItem(path));
        finding.put("value", value);
        finding.put("side", side);
        finding.put("finding_text", buildFindingText(path, value, side));
        return finding;
    }

    private Map<String, Object> buildAsymmetryFinding(ActionExtraction extraction, String path, String leftValue, String rightValue) {
        Map<String, Object> finding = new LinkedHashMap<>();
        finding.put("action_code", extraction.getActionCode());
        finding.put("action_name_zh", extraction.getActionNameZh());
        finding.put("observation_path", path);
        finding.put("left_value", leftValue);
        finding.put("right_value", rightValue);
        finding.put("summary_text", "左右侧记录不一致（左：" + leftValue + "，右：" + rightValue + "）");
        return finding;
    }

    private Map<String, Object> simpleOverallFinding(String actionCode, String actionNameZh, String findingText) {
        Map<String, Object> finding = new LinkedHashMap<>();
        finding.put("action_code", actionCode);
        finding.put("action_name_zh", actionNameZh);
        finding.put("finding_text", findingText);
        return finding;
    }

    private void handleBilateralPair(Map<String, Object> map, String path, ActionExtraction extraction) {
        String leftValue = normalizeLeafValue(map.get("left"));
        String rightValue = normalizeLeafValue(map.get("right"));

        if (StrUtil.isNotBlank(leftValue) && !"未评估".equals(leftValue)) {
            extraction.setAssessed(true);
            extraction.markObserved();
            if (isAbnormalValue(leftValue)) {
                extraction.addAbnormal(buildFinding(extraction, buildPath(path, "left"), leftValue, "left"));
            }
        }
        if (StrUtil.isNotBlank(rightValue) && !"未评估".equals(rightValue)) {
            extraction.setAssessed(true);
            extraction.markObserved();
            if (isAbnormalValue(rightValue)) {
                extraction.addAbnormal(buildFinding(extraction, buildPath(path, "right"), rightValue, "right"));
            }
        }
        if (StrUtil.isNotBlank(leftValue) && StrUtil.isNotBlank(rightValue) && !Objects.equals(leftValue, rightValue)) {
            extraction.addAsymmetry(buildAsymmetryFinding(extraction, path, leftValue, rightValue));
        }
    }

    private void detectBooleanSideAsymmetry(Map<String, Object> map, ActionExtraction extraction, String path) {
        Object left = map.get("left");
        Object right = map.get("right");
        if (!(left instanceof Boolean) || !(right instanceof Boolean)) {
            return;
        }
        if (!Objects.equals(left, right)) {
            extraction.addAsymmetry(buildAsymmetryFinding(extraction, path,
                    Boolean.TRUE.equals(left) ? "异常" : "未见异常",
                    Boolean.TRUE.equals(right) ? "异常" : "未见异常"));
        }
    }

    private String resolveBooleanSide(Map<String, Object> map) {
        boolean left = Boolean.TRUE.equals(map.get("left"));
        boolean right = Boolean.TRUE.equals(map.get("right"));
        if (left && right) {
            return "bilateral";
        }
        if (left) {
            return "left";
        }
        if (right) {
            return "right";
        }
        return "global";
    }

    private List<String> resolvePriorityRegions(List<Map<String, Object>> findings) {
        LinkedHashSet<String> regions = new LinkedHashSet<>();
        for (Map<String, Object> finding : findings) {
            String actionCode = String.valueOf(finding.getOrDefault("action_code", ""));
            String path = String.valueOf(finding.getOrDefault("observation_path", ""));
            String target = (actionCode + "|" + path).toLowerCase(Locale.ROOT);
            if (containsAny(target, "head", "cervical", "neck")) {
                regions.add("头颈");
            }
            if (containsAny(target, "shoulder", "scapula")) {
                regions.add("肩");
            }
            if (containsAny(target, "elbow", "humerus")) {
                regions.add("肘");
            }
            if (containsAny(target, "lphc", "pelvis", "lumbar", "trunk", "hip")) {
                regions.add("LPHC");
            }
            if (containsAny(target, "knee", "thigh")) {
                regions.add("膝");
            }
            if (containsAny(target, "ankle", "foot", "arch", "achilles", "tibia", "toe")) {
                regions.add("踝足");
            }
            if (containsAny(target, "gait", "star_excursion")) {
                regions.add("动态控制");
            }
            if (containsAny(target, "tuck_jump", "less")) {
                regions.add("跳跃/落地控制");
            }
            if (containsAny(target, "davies", "upper_extremity_davies")) {
                regions.add("上肢稳定/闭链能力");
            }
        }
        return new ArrayList<>(regions);
    }

    private List<String> resolveMovementPatternFlagsFromFindings(List<Map<String, Object>> findings) {
        LinkedHashSet<String> flags = new LinkedHashSet<>();
        List<String> regions = resolvePriorityRegions(findings);
        for (String region : regions) {
            switch (region) {
                case "LPHC":
                    flags.add(TAG_LPHC_STABILITY);
                    break;
                case "膝":
                case "踝足":
                    flags.add(TAG_LOWER_EXT_ALIGNMENT);
                    break;
                case "肩":
                case "肘":
                case "上肢闭链稳定":
                    flags.add(TAG_UPPER_EXT_STABILITY);
                    break;
                case "动态控制":
                    flags.add(TAG_DYNAMIC_BALANCE);
                    break;
                case "跳跃/落地控制":
                    flags.add(TAG_LANDING);
                    break;
                default:
                    break;
            }
        }
        return new ArrayList<>(flags);
    }

    private List<String> extractManualSummaryNotes(Map<String, Object> payload) {
        Map<String, Object> summary = getMapByPath(payload, "summary");
        if (summary == null) {
            return Collections.emptyList();
        }
        List<String> notes = new ArrayList<>();
        String overall = normalizeText(summary.get("overall_ces_summary"));
        String dynamic = normalizeText(summary.get("dynamic_summary_note"));
        if (StrUtil.isNotBlank(overall)) {
            notes.add("治疗师总体汇总：" + overall);
        }
        if (StrUtil.isNotBlank(dynamic)) {
            notes.add("治疗师动态汇总：" + dynamic);
        }
        Map<String, Object> transitionSummary = summary.get("transition_summary") instanceof Map
                ? castToMap(summary.get("transition_summary"))
                : null;
        if (transitionSummary != null) {
            transitionSummary.forEach((key, value) -> {
                String text = normalizeText(value);
                if (StrUtil.isNotBlank(text)) {
                    notes.add("治疗师过渡汇总[" + key + "]：" + text);
                }
            });
        }
        return notes;
    }

    private boolean containsAny(String source, String... keys) {
        if (StrUtil.isBlank(source)) {
            return false;
        }
        for (String key : keys) {
            if (source.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAbnormalValue(String value) {
        if (StrUtil.isBlank(value)) {
            return false;
        }
        return !NORMAL_TEXT_VALUES.contains(value);
    }

    private boolean isLessRiskValue(String value) {
        if (StrUtil.isBlank(value)) {
            return false;
        }
        return Arrays.asList("是", "糟糕的", "僵硬的", "躯干未屈曲", "躯干未直立", "非足趾到足跟").contains(value);
    }

    private String resolveActionStatus(ActionExtraction extraction) {
        if (!extraction.isAssessed() || extraction.getObservedCount() <= 0) {
            return NOT_ASSESSED;
        }
        if (extraction.getObservedCount() < 3) {
            return PARTIAL;
        }
        return COMPLETED;
    }

    private Map<String, Object> buildActionRiskPrecheck(ActionExtraction extraction, List<String> primaryRegions, String status) {
        if (NOT_ASSESSED.equals(status)) {
            return buildRiskPrecheckMap(RISK_LOW, Collections.emptyList(),
                    "当前动作未评估，暂无法进行风险初筛。", Collections.emptyList(), true);
        }
        int abnormalCount = extraction.getAbnormalFindings().size();
        int asymmetryCount = extraction.getAsymmetryFindings().size();
        LinkedHashSet<String> tags = new LinkedHashSet<>(mapRegionsToRiskTags(primaryRegions));
        if (asymmetryCount > 0) {
            tags.add(TAG_ASYMMETRY);
        }
        if ("overhead_squat".equals(extraction.getActionCode())
                || "standing_dumbbell_overhead_press".equals(extraction.getActionCode())
                || "upper_extremity_transition".equals(extraction.getActionCode())) {
            tags.add(TAG_OVERHEAD);
        }
        if ("gait_analysis".equals(extraction.getActionCode())) {
            tags.add(TAG_GAIT);
        }
        if ("star_excursion_balance_deviation_test".equals(extraction.getActionCode())
                || "single_leg_squat".equals(extraction.getActionCode())) {
            tags.add(TAG_DYNAMIC_BALANCE);
        }
        if ("tuck_jump_assessment".equals(extraction.getActionCode())
                || "less_test".equals(extraction.getActionCode())) {
            tags.add(TAG_LANDING);
        }
        String level = RISK_LOW;
        if (abnormalCount >= 4 || (abnormalCount >= 2 && asymmetryCount >= 1)) {
            level = RISK_HIGH;
        } else if (abnormalCount >= 2 || asymmetryCount >= 1) {
            level = RISK_MEDIUM;
        }
        String reason = "从 CES 动作表现看，当前动作异常记录 " + abnormalCount + " 项，左右差异 " + asymmetryCount +
                " 项，提示需结合人工复核进行功能性风险关注。";
        return buildRiskPrecheckMap(level, new ArrayList<>(tags), reason,
                buildEvidenceRefs(extraction.getAbnormalFindings(), 8), true);
    }

    private Map<String, Object> buildModuleRiskPrecheck(String moduleName,
                                                        List<Map<String, Object>> abnormalFindings,
                                                        List<Map<String, Object>> asymmetryFindings,
                                                        int completed,
                                                        int total) {
        LinkedHashSet<String> tags = new LinkedHashSet<>(mapRegionsToRiskTags(resolvePriorityRegions(abnormalFindings)));
        if (!asymmetryFindings.isEmpty()) {
            tags.add(TAG_ASYMMETRY);
        }
        if (moduleName.contains("动态")) {
            tags.add(TAG_DYNAMIC_BALANCE);
        }
        int abnormalCount = abnormalFindings.size();
        String level = RISK_LOW;
        if (abnormalCount >= 8 || tags.size() >= 4) {
            level = RISK_HIGH;
        } else if (abnormalCount >= 3 || tags.size() >= 2) {
            level = RISK_MEDIUM;
        }
        String reason = "从 CES 动作表现看，" + moduleName + "已完成 " + completed + "/" + total +
                " 项，异常记录 " + abnormalCount + " 项。";
        List<Map<String, Object>> merged = new ArrayList<>(abnormalFindings);
        merged.addAll(asymmetryFindings);
        return buildRiskPrecheckMap(level, new ArrayList<>(tags), reason, buildEvidenceRefs(merged, 10), true);
    }

    private Map<String, Object> buildDaviesRiskPrecheck(DaviesExtraction extraction) {
        if (!extraction.isAssessed()) {
            return buildRiskPrecheckMap(RISK_LOW, Collections.singletonList(TAG_UPPER_EXT_STABILITY),
                    "戴维斯测试未评估，建议结合后续复测确认上肢闭链稳定表现。", Collections.emptyList(), true);
        }
        String level = RISK_LOW;
        if (extraction.getTrialCount() < 2 || (extraction.getTotalRepetitionCount() != null && extraction.getTotalRepetitionCount() < 30)) {
            level = RISK_MEDIUM;
        }
        String reason = "上肢戴维斯测试已记录 " + extraction.getTrialCount() + " 组，提示关注上肢闭链稳定能力。";
        List<Map<String, Object>> evidences = extraction.getResults().stream()
                .map(item -> {
                    Map<String, Object> evidence = new LinkedHashMap<>();
                    evidence.put("action_code", DAVIES_ACTION.getCode());
                    evidence.put("observation_path", "upper_extremity_davies_test.trials");
                    evidence.put("finding_text", "trial=" + item.get("trial_no") + ", repetition=" + item.get("repetition_count"));
                    return evidence;
                }).collect(Collectors.toList());
        return buildRiskPrecheckMap(level, Collections.singletonList(TAG_UPPER_EXT_STABILITY),
                reason, buildEvidenceRefs(evidences, 6), true);
    }

    private Map<String, Object> buildLessRiskPrecheck(LessExtraction extraction) {
        if (!extraction.isAssessed()) {
            return buildRiskPrecheckMap(RISK_LOW, Collections.singletonList(TAG_LANDING),
                    "LESS 测试未评估，建议后续补充以完善落地控制筛查。", Collections.emptyList(), true);
        }
        int score = extraction.getLessTotalScore() == null ? extraction.getKeyFindings().size() : extraction.getLessTotalScore();
        String level = score >= 8 ? RISK_HIGH : score >= 5 ? RISK_MEDIUM : RISK_LOW;
        String reason = "LESS 测试提示跳跃/落地控制关注，当前评分/关键项为 " + score + "。";
        return buildRiskPrecheckMap(level,
                Arrays.asList(TAG_LANDING, TAG_DYNAMIC_BALANCE),
                reason,
                extraction.getKeyFindings().stream().limit(6).collect(Collectors.toList()), true);
    }

    private Map<String, Object> buildOverallRiskPrecheck(Map<String, Object> payload,
                                                         Map<String, Object> cesSummary,
                                                         List<Map<String, Object>> actionSummaries) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        List<String> evidenceRefs = new ArrayList<>();

        int overheadAbnormal = countActionAbnormal(actionSummaries, "overhead_squat");
        int singleLegAbnormal = countActionAbnormal(actionSummaries, "single_leg_squat");
        int gaitAbnormal = countActionAbnormal(actionSummaries, "gait_analysis");
        int landingAbnormal = countActionAbnormal(actionSummaries, "tuck_jump_assessment");
        int pressAbnormal = countActionAbnormal(actionSummaries, "standing_dumbbell_overhead_press");
        int upperTransitionAbnormal = countActionAbnormal(actionSummaries, "upper_extremity_transition");
        int pushUpAbnormal = countActionAbnormal(actionSummaries, "push_up");
        int daviesAbnormal = countActionAbnormal(actionSummaries, "upper_extremity_davies_test");
        int asymmetryCount = actionSummaries.stream()
                .mapToInt(item -> sizeOfList(item.get("left_right_asymmetry_findings"))).sum();

        Map<String, Object> lessSummary = castToMap(cesSummary.get("less_test_summary"));
        Integer lessScore = toInteger(lessSummary == null ? null : lessSummary.get("less_total_score"));
        int lessRiskCount = lessSummary == null ? 0 : sizeOfList(lessSummary.get("key_findings"));
        int missingActions = (int) actionSummaries.stream().filter(item -> NOT_ASSESSED.equals(item.get("status"))).count();
        int totalAbnormal = actionSummaries.stream().mapToInt(item -> sizeOfList(item.get("abnormal_findings"))).sum();

        if (overheadAbnormal > 0 || singleLegAbnormal > 0 || gaitAbnormal > 0 || lessRiskCount > 0) {
            tags.add(TAG_LOWER_EXT_ALIGNMENT);
        }
        if (hasLphcCluster(actionSummaries)) {
            tags.add(TAG_LPHC_STABILITY);
        }
        if (overheadAbnormal > 0 || pressAbnormal > 0 || upperTransitionAbnormal > 0) {
            tags.add(TAG_OVERHEAD);
        }
        if (pushUpAbnormal > 0 || upperTransitionAbnormal > 0 || daviesAbnormal > 0) {
            tags.add(TAG_UPPER_EXT_STABILITY);
        }
        if (landingAbnormal >= 2 || (lessScore != null && lessScore >= 5) || lessRiskCount >= 3) {
            tags.add(TAG_LANDING);
        }
        if (gaitAbnormal >= 2) {
            tags.add(TAG_GAIT);
        }
        if (asymmetryCount >= 2) {
            tags.add(TAG_ASYMMETRY);
        }
        if (countActionAbnormal(actionSummaries, "star_excursion_balance_deviation_test") >= 2
                || singleLegAbnormal >= 2) {
            tags.add(TAG_DYNAMIC_BALANCE);
        }
        if (isPainAttention(payload)) {
            tags.add(TAG_PAIN);
        }
        if (tags.size() >= 3 || (missingActions >= 4 && totalAbnormal > 0)) {
            tags.add(TAG_REASSESSMENT);
        }

        Map<String, Object> overallSummary = castToMap(cesSummary.get("overall_summary"));
        List<String> priorityRegions = castStringList(overallSummary == null ? null : overallSummary.get("priority_regions"));
        for (Map<String, Object> item : actionSummaries) {
            evidenceRefs.addAll(buildEvidenceRefs(castList(item.get("abnormal_findings")), 2));
        }
        if (evidenceRefs.size() > 14) {
            evidenceRefs = new ArrayList<>(evidenceRefs.subList(0, 14));
        }

        int score = 0;
        for (String tag : tags) {
            if (TAG_LOWER_EXT_ALIGNMENT.equals(tag) || TAG_LPHC_STABILITY.equals(tag)
                    || TAG_LANDING.equals(tag) || TAG_PAIN.equals(tag)) {
                score += 2;
            } else {
                score += 1;
            }
        }
        if (lessScore != null && lessScore >= 8) {
            score += 1;
        }
        String level = score >= 7 ? RISK_HIGH : score >= 4 ? RISK_MEDIUM : RISK_LOW;

        String reasonText = "从 CES 动作表现看，提示存在" + tags.size() +
                "类功能性风险关注标签，异常记录 " + totalAbnormal + " 项，左右差异 " + asymmetryCount +
                " 项。结合当前证据，优先关注" + (priorityRegions.isEmpty() ? "动作控制" : String.join("、", priorityRegions)) + "。";

        Map<String, Object> risk = buildRiskPrecheckMap(level, new ArrayList<>(tags), reasonText, evidenceRefs, true);
        risk.put("overall_risk_level", level);
        risk.put("priority_regions", priorityRegions);
        risk.put("movement_pattern_flags", toMovementPatternFlags(new ArrayList<>(tags)));
        return risk;
    }

    private Map<String, Object> buildReportMapping(Map<String, Object> cesSummary, Map<String, Object> riskPrecheck) {
        List<Map<String, Object>> actionSummaries = castList(cesSummary.get("action_summaries"));
        List<Map<String, Object>> nasmBlocks = buildNasmActionReportBlocks(actionSummaries);
        Map<String, Object> overallSummary = castToMap(cesSummary.get("overall_summary"));
        String dynamicFunctionText = overallSummary == null
                ? "动态功能评估总结暂缺，需结合人工复核。"
                : String.valueOf(overallSummary.getOrDefault("summary_text", "动态功能评估总结暂缺，需结合人工复核。"));
        String overallRiskText = "整体主要风险指向（功能性初筛）：" +
                String.valueOf(riskPrecheck.getOrDefault("overall_risk_level", RISK_LOW)) +
                "，" + String.valueOf(riskPrecheck.getOrDefault("reason_text", "证据不足，需结合人工复核。"));

        List<String> priorityRegions = castStringList(riskPrecheck.get("priority_regions"));
        List<String> keyFindings = castStringList(overallSummary == null ? null : overallSummary.get("key_findings"));
        List<Map<String, Object>> priorityDraft = new ArrayList<>();
        int rank = 1;
        for (String region : priorityRegions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("priority_rank", rank++);
            row.put("region", region);
            row.put("focus", keyFindings.stream().filter(item -> item.contains(region)).findFirst().orElse("需结合动作链人工确认"));
            row.put("source", "nasm_ces");
            priorityDraft.add(row);
            if (priorityDraft.size() >= 3) {
                break;
            }
        }
        if (priorityDraft.isEmpty()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("priority_rank", 1);
            row.put("region", "动态控制");
            row.put("focus", "当前证据不足，建议优先补充关键动作复测。");
            row.put("source", "nasm_ces");
            priorityDraft.add(row);
        }

        Map<String, Object> qeiSummary = new LinkedHashMap<>();
        qeiSummary.put("qei_summary_text", "当前阶段未启用自动 QEI 计算，建议结合治疗师人工评分。");
        qeiSummary.put("qei_by_action", nasmBlocks.stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("action_code", item.get("action_code"));
            row.put("action_name_zh", item.get("action_name_zh"));
            row.put("qei", null);
            return row;
        }).collect(Collectors.toList()));

        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("nasm_ces_action_blocks", nasmBlocks);
        mapping.put("dynamic_function_summary_text", dynamicFunctionText);
        mapping.put("overall_risk_direction_text", overallRiskText);
        mapping.put("priority_intervention_draft", priorityDraft);
        mapping.put("qei_summary", qeiSummary);
        mapping.put("internal_rule_only", true);
        mapping.put("provisional_v1", true);
        return mapping;
    }

    private List<Map<String, Object>> buildNasmActionReportBlocks(List<Map<String, Object>> actionSummaries) {
        List<String> included = Arrays.asList(
                "overhead_squat", "single_leg_squat", "push_up",
                "standing_row", "standing_dumbbell_overhead_press", "upper_extremity_transition"
        );
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (String actionCode : included) {
            Map<String, Object> action = findAction(actionSummaries, actionCode);
            if (action == null) {
                continue;
            }
            String actionName = String.valueOf(action.getOrDefault("action_name_zh", actionCode));
            String status = String.valueOf(action.getOrDefault("status", NOT_ASSESSED));
            String observation = String.valueOf(action.getOrDefault("summary_text", actionName + "：未评估。"));
            Map<String, Object> actionRisk = castToMap(action.get("risk_precheck"));
            String riskText = "风险初筛：" + String.valueOf(actionRisk == null ? RISK_LOW : actionRisk.getOrDefault("risk_level", RISK_LOW)) +
                    "（功能性初筛）";

            List<String> evidenceRefs = buildEvidenceRefs(castList(action.get("abnormal_findings")), 8);
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("action_code", actionCode);
            block.put("action_name_zh", actionName);
            block.put("status", status);
            block.put("observation", observation);
            block.put("analysis", "从 CES 动作表现看，" + actionName + "提示存在动作质量关注点，需结合人工复核。");
            block.put("risk", riskText);
            block.put("suggestion", "结合训练目标与人工复核后制定干预方案。");
            block.put("qei", null);
            block.put("evidence_refs", evidenceRefs);
            blocks.add(block);
        }
        return blocks;
    }

    private Map<String, Object> buildFallbackRiskPrecheck() {
        Map<String, Object> risk = buildRiskPrecheckMap(
                RISK_LOW,
                Collections.singletonList(TAG_REASSESSMENT),
                "风险初筛生成失败，需结合人工复核。",
                Collections.emptyList(),
                true
        );
        risk.put("overall_risk_level", RISK_LOW);
        risk.put("priority_regions", Collections.emptyList());
        risk.put("movement_pattern_flags", Collections.singletonList(TAG_REASSESSMENT));
        return risk;
    }

    private Map<String, Object> buildFallbackReportMapping() {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("nasm_ces_action_blocks", Collections.emptyList());
        mapping.put("dynamic_function_summary_text", "动态功能评估总结生成失败，需结合人工复核。");
        mapping.put("overall_risk_direction_text", "整体主要风险指向生成失败，需结合人工复核。");
        mapping.put("priority_intervention_draft", Collections.emptyList());
        mapping.put("qei_summary", Collections.singletonMap("qei_summary_text", "当前无法自动生成 QEI 映射。"));
        mapping.put("internal_rule_only", true);
        mapping.put("provisional_v1", true);
        return mapping;
    }

    private Map<String, Object> buildRiskPrecheckMap(String level,
                                                     List<String> tags,
                                                     String reasonText,
                                                     List<?> evidenceRefs,
                                                     boolean internalRuleOnly) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("risk_level", level);
        map.put("risk_tags", tags);
        map.put("reason_text", reasonText);
        map.put("evidence_refs", evidenceRefs);
        map.put("internal_rule_only", internalRuleOnly);
        map.put("provisional_v1", true);
        return map;
    }

    private List<String> mapRegionsToRiskTags(List<String> regions) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String region : regions) {
            if ("LPHC".equals(region)) {
                tags.add(TAG_LPHC_STABILITY);
            } else if ("膝".equals(region) || "踝足".equals(region)) {
                tags.add(TAG_LOWER_EXT_ALIGNMENT);
            } else if ("肩".equals(region) || "肘".equals(region) || "上肢闭链稳定".equals(region)) {
                tags.add(TAG_UPPER_EXT_STABILITY);
            } else if ("动态控制".equals(region)) {
                tags.add(TAG_DYNAMIC_BALANCE);
            } else if ("跳跃/落地控制".equals(region)) {
                tags.add(TAG_LANDING);
            }
        }
        return new ArrayList<>(tags);
    }

    private List<String> toMovementPatternFlags(List<String> riskTags) {
        List<String> flags = new ArrayList<>();
        for (String tag : riskTags) {
            if (TAG_REASSESSMENT.equals(tag) || TAG_PAIN.equals(tag)) {
                continue;
            }
            flags.add(tag);
        }
        return flags;
    }

    private int countActionAbnormal(List<Map<String, Object>> actionSummaries, String actionCode) {
        Map<String, Object> action = findAction(actionSummaries, actionCode);
        if (action == null) {
            return 0;
        }
        return sizeOfList(action.get("abnormal_findings"));
    }

    private Map<String, Object> findAction(List<Map<String, Object>> actionSummaries, String actionCode) {
        return actionSummaries.stream()
                .filter(item -> actionCode.equals(item.get("action_code")))
                .findFirst()
                .orElse(null);
    }

    private boolean hasLphcCluster(List<Map<String, Object>> actionSummaries) {
        return actionSummaries.stream()
                .flatMap(item -> castList(item.get("abnormal_findings")).stream())
                .map(item -> String.valueOf(item.getOrDefault("observation_path", "")).toLowerCase(Locale.ROOT))
                .filter(path -> path.contains("lphc") || path.contains("lumbar") || path.contains("hip") || path.contains("trunk"))
                .count() >= 2;
    }

    private boolean isPainAttention(Map<String, Object> payload) {
        Integer painScore = toInteger(payload.get("pain_score"));
        if (painScore == null) {
            painScore = toInteger(payload.get("painScore"));
        }
        if (painScore != null && painScore >= 4) {
            return true;
        }
        String note = normalizeText(payload.get("general_note"));
        String summaryNote = normalizeText(getMapByPath(payload, "basic_info") == null ? null : getMapByPath(payload, "basic_info").get("summary_note"));
        String all = (StrUtil.blankToDefault(note, "") + "|" + StrUtil.blankToDefault(summaryNote, "")).toLowerCase(Locale.ROOT);
        return all.contains("pain_stop") || all.contains("疼痛停止") || all.contains("疼痛");
    }

    private int sizeOfList(Object value) {
        return castList(value).size();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        try {
            return (List<Map<String, Object>>) value;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private List<String> castStringList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<?> list = (List<?>) value;
        return list.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.toList());
    }

    private List<String> buildEvidenceRefs(List<Map<String, Object>> findings, int limit) {
        if (findings == null || findings.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> refs = new ArrayList<>();
        for (Map<String, Object> finding : findings) {
            String actionCode = String.valueOf(finding.getOrDefault("action_code", ""));
            String path = String.valueOf(finding.getOrDefault("observation_path", ""));
            if (StrUtil.isAllBlank(actionCode, path)) {
                continue;
            }
            refs.add(actionCode + ":" + path);
            if (refs.size() >= limit) {
                break;
            }
        }
        return refs;
    }

    private boolean isMeaningfulNumberKey(String key) {
        return "repetition_count".equals(key)
                || "humerus_wall_angle_deg".equals(key)
                || "less_total_score".equals(key)
                || "total_findings_count".equals(key);
    }

    private String normalizeLeafValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return Boolean.TRUE.equals(value) ? "异常" : "未见异常";
        }
        if (value instanceof Number) {
            return String.valueOf(value);
        }
        String text = normalizeText(value);
        return StrUtil.isBlank(text) ? null : text;
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StrUtil.isBlank(text) ? null : text;
    }

    private String resolvePlane(String path) {
        if (StrUtil.isBlank(path)) {
            return "全面观";
        }
        if (path.contains("front_view")) {
            return "前面观";
        }
        if (path.contains("lateral_view")) {
            return "侧面观";
        }
        if (path.contains("posterior_view")) {
            return "后面观";
        }
        if (path.contains("full_view")) {
            return "全面观";
        }
        return "全面观";
    }

    private String resolveCheckPoint(String path) {
        if (StrUtil.isBlank(path)) {
            return "";
        }
        String[] parts = path.split("\\.");
        if (parts.length < 2) {
            return path;
        }
        return parts[parts.length - 2];
    }

    private String resolveObservationItem(String path) {
        if (StrUtil.isBlank(path)) {
            return "";
        }
        String[] parts = path.split("\\.");
        return parts[parts.length - 1];
    }

    private String buildFindingText(String path, String value, String side) {
        String item = resolveObservationItem(path);
        String sideText = StrUtil.isBlank(side) || "global".equals(side) ? "" : ("（" + side + "）");
        return item + sideText + "：" + value;
    }

    private String buildPath(String base, String key) {
        if (StrUtil.isBlank(base)) {
            return key;
        }
        if (StrUtil.isBlank(key)) {
            return base;
        }
        return base + "." + key;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMapByPath(Map<String, Object> source, String path) {
        if (source == null || StrUtil.isBlank(path)) {
            return null;
        }
        Object current = source;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(part);
            if (current == null) {
                return null;
            }
        }
        if (!(current instanceof Map)) {
            return null;
        }
        return (Map<String, Object>) current;
    }

    private Map<String, Object> normalizeToMap(Object rawDataJson) {
        if (rawDataJson == null) {
            return new LinkedHashMap<>();
        }
        if (rawDataJson instanceof Map) {
            return JsonUtils.convertObject(rawDataJson, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        }
        if (rawDataJson instanceof String) {
            String text = (String) rawDataJson;
            if (StrUtil.isBlank(text)) {
                return new LinkedHashMap<>();
            }
            try {
                Map<String, Object> parsed = JsonUtils.parseObject(text, new TypeReference<LinkedHashMap<String, Object>>() {
                });
                return parsed == null ? new LinkedHashMap<>() : parsed;
            } catch (Exception ignore) {
                return new LinkedHashMap<>();
            }
        }
        return JsonUtils.convertObject(rawDataJson, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToMap(Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        return (Map<String, Object>) value;
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignore) {
            return null;
        }
    }

    private static ActionDefinition action(String code, String nameZh, String path) {
        return new ActionDefinition(code, nameZh, path);
    }

    @Data
    @AllArgsConstructor
    private static class ActionDefinition {
        private String code;
        private String nameZh;
        private String path;
    }

    @Data
    private static class ActionExtraction {
        private final String actionCode;
        private final String actionNameZh;
        private boolean assessed;
        private int observedCount;
        private final List<Map<String, Object>> abnormalFindings = new ArrayList<>();
        private final List<Map<String, Object>> asymmetryFindings = new ArrayList<>();
        private final Set<String> abnormalDedupe = new HashSet<>();
        private final Set<String> asymmetryDedupe = new HashSet<>();

        public ActionExtraction(String actionCode, String actionNameZh) {
            this.actionCode = actionCode;
            this.actionNameZh = actionNameZh;
            this.assessed = false;
            this.observedCount = 0;
        }

        public void markObserved() {
            this.observedCount++;
        }

        public void addAbnormal(Map<String, Object> finding) {
            String dedupeKey = String.valueOf(finding.get("observation_path")) + "|" + String.valueOf(finding.get("value"))
                    + "|" + String.valueOf(finding.get("side"));
            if (abnormalDedupe.add(dedupeKey)) {
                abnormalFindings.add(finding);
            }
        }

        public void addAsymmetry(Map<String, Object> finding) {
            String dedupeKey = String.valueOf(finding.get("observation_path")) + "|"
                    + String.valueOf(finding.get("left_value")) + "|" + String.valueOf(finding.get("right_value"));
            if (asymmetryDedupe.add(dedupeKey)) {
                asymmetryFindings.add(finding);
            }
        }
    }

    @Data
    private static class DaviesExtraction {
        private boolean assessed;
        private int trialCount;
        private Integer totalRepetitionCount;
        private final List<Map<String, Object>> results = new ArrayList<>();
        private final List<String> keyFindings = new ArrayList<>();
    }

    @Data
    private static class LessExtraction {
        private boolean assessed;
        private int filledItemCount;
        private Integer lessTotalScore;
        private final List<String> keyFindings = new ArrayList<>();
    }
}
