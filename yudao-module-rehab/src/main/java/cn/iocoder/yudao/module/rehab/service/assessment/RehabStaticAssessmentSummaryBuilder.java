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
 * 静态评估汇总构建器（Step 8.2）
 *
 * 说明：
 * 1. 仅做录入结果整理，不做诊断性结论
 * 2. 输出写回 static module 的 data_json.static_summary
 * 3. 冗余冲突策略：检测到中轴与左右冲突时标记需人工复核
 */
@Component
public class RehabStaticAssessmentSummaryBuilder {

    private static final String VIEW_NOT_FILLED = "该视角暂未录入完整评估结果";

    private static final List<FieldDefinition> POSTERIOR_FIELDS = Arrays.asList(
            field("posterior_view.left.ear_height", "耳朵高度（左）", "后面观", "正常"),
            field("posterior_view.right.ear_height", "耳朵高度（右）", "后面观", "正常"),
            field("posterior_view.left.shoulder_height", "肩膀高度（左）", "后面观", "正常"),
            field("posterior_view.right.shoulder_height", "肩膀高度（右）", "后面观", "正常"),
            field("posterior_view.left.scapula_adduction_abduction", "肩胛骨内收/外展（左）", "后面观", "正常"),
            field("posterior_view.right.scapula_adduction_abduction", "肩胛骨内收/外展（右）", "后面观", "正常"),
            field("posterior_view.left.scapula_inferior_angle", "肩胛下角（左）", "后面观", "正常"),
            field("posterior_view.right.scapula_inferior_angle", "肩胛下角（右）", "后面观", "正常"),
            field("posterior_view.left.scapula_rotation", "肩胛骨旋转（左）", "后面观", "正常"),
            field("posterior_view.right.scapula_rotation", "肩胛骨旋转（右）", "后面观", "正常"),
            field("posterior_view.left.winged_scapula", "翼状肩胛骨（左）", "后面观", "正常"),
            field("posterior_view.right.winged_scapula", "翼状肩胛骨（右）", "后面观", "正常"),
            field("posterior_view.left.upper_limb_posture", "上肢姿势（左）", "后面观", "正常"),
            field("posterior_view.right.upper_limb_posture", "上肢姿势（右）", "后面观", "正常"),
            field("posterior_view.left.elbow_posture", "手肘姿势（左）", "后面观", "正常"),
            field("posterior_view.right.elbow_posture", "手肘姿势（右）", "后面观", "正常"),
            field("posterior_view.left.hand_posture", "手部姿势（左）", "后面观", "正常"),
            field("posterior_view.right.hand_posture", "手部姿势（右）", "后面观", "正常"),
            field("posterior_view.left.psis_height", "髂后上棘（左）", "后面观", "正常"),
            field("posterior_view.right.psis_height", "髂后上棘（右）", "后面观", "正常"),
            field("posterior_view.left.gluteal_line_height", "臀线（左）", "后面观", "正常"),
            field("posterior_view.right.gluteal_line_height", "臀线（右）", "后面观", "正常"),
            field("posterior_view.left.knee_varus_valgus", "膝内翻/外翻（左）", "后面观", "正常"),
            field("posterior_view.right.knee_varus_valgus", "膝内翻/外翻（右）", "后面观", "正常"),
            field("posterior_view.left.lower_leg_midline", "小腿中线（左）", "后面观", "正常"),
            field("posterior_view.right.lower_leg_midline", "小腿中线（右）", "后面观", "正常"),
            field("posterior_view.left.achilles_tendon", "跟腱（左）", "后面观", "正常"),
            field("posterior_view.right.achilles_tendon", "跟腱（右）", "后面观", "正常"),
            field("posterior_view.left.ankle_height", "踝关节（左）", "后面观", "正常"),
            field("posterior_view.right.ankle_height", "踝关节（右）", "后面观", "正常"),
            field("posterior_view.left.foot_posture", "足部姿势（左）", "后面观", "正常"),
            field("posterior_view.right.foot_posture", "足部姿势（右）", "后面观", "正常"),
            field("posterior_view.midline.head_neck_tilt", "头颈部倾斜", "后面观", "正常"),
            field("posterior_view.midline.neck_rotation", "颈部旋转", "后面观", "正常"),
            field("posterior_view.midline.thoracic_spine_shift", "胸椎", "后面观", "正常"),
            field("posterior_view.midline.thorax_tilt", "胸廓倾斜", "后面观", "正常"),
            field("posterior_view.midline.thorax_rotation", "胸廓旋转", "后面观", "正常"),
            field("posterior_view.midline.pelvic_tilt", "骨盆区域", "后面观", "正常"),
            field("posterior_view.midline.pelvic_rotation", "骨盆旋转", "后面观", "正常")
    );

    private static final List<FieldDefinition> LATERAL_FIELDS = Arrays.asList(
            field("lateral_view.left.head_position", "头部姿势（左）", "侧面观", "正常"),
            field("lateral_view.right.head_position", "头部姿势（右）", "侧面观", "正常"),
            field("lateral_view.left.cervical_curve", "颈椎（左）", "侧面观", "正常"),
            field("lateral_view.right.cervical_curve", "颈椎（右）", "侧面观", "正常"),
            field("lateral_view.left.cervicothoracic_junction", "颈胸椎连接（左）", "侧面观", "正常"),
            field("lateral_view.right.cervicothoracic_junction", "颈胸椎连接（右）", "侧面观", "正常"),
            field("lateral_view.left.shoulder_position", "肩膀姿势（左）", "侧面观", "正常"),
            field("lateral_view.right.shoulder_position", "肩膀姿势（右）", "侧面观", "正常"),
            field("lateral_view.left.thoracic_curve", "胸部（左）", "侧面观", "正常"),
            field("lateral_view.right.thoracic_curve", "胸部（右）", "侧面观", "正常"),
            field("lateral_view.left.abdomen", "腹部（左）", "侧面观", "平坦"),
            field("lateral_view.right.abdomen", "腹部（右）", "侧面观", "平坦"),
            field("lateral_view.left.lumbar_curve", "腰椎（左）", "侧面观", "正常"),
            field("lateral_view.right.lumbar_curve", "腰椎（右）", "侧面观", "正常"),
            field("lateral_view.left.pelvis_tilt", "骨盆（左）", "侧面观", "正常"),
            field("lateral_view.right.pelvis_tilt", "骨盆（右）", "侧面观", "正常"),
            field("lateral_view.left.knee_position", "膝盖（左）", "侧面观", "正常"),
            field("lateral_view.right.knee_position", "膝盖（右）", "侧面观", "正常"),
            field("lateral_view.left.ankle_foot_position", "脚踝/足部（左）", "侧面观", "正常"),
            field("lateral_view.right.ankle_foot_position", "脚踝/足部（右）", "侧面观", "正常")
    );

    private static final List<FieldDefinition> ANTERIOR_FIELDS = Arrays.asList(
            field("anterior_view.midline.chest_shift", "胸部", "正面观", "正常"),
            field("anterior_view.midline.arm_symmetry", "手臂", "正面观", "正常"),
            field("anterior_view.midline.wrist_hand_position", "手部及手腕", "正面观", "正常"),
            field("anterior_view.midline.abdomen_alignment", "腹部（胸骨/耻骨联合/肚脐）", "正面观", "正常"),
            field("anterior_view.midline.pelvic_lateral_shift", "骨盆侧向位移", "正面观", "正常"),
            field("anterior_view.midline.pelvic_rotation", "骨盆旋转", "正面观", "正常"),
            field("anterior_view.midline.standing_pressure", "站立", "正面观", "正常"),
            field("anterior_view.left.knee_varus_valgus", "膝内翻/外翻（左）", "正面观", "正常"),
            field("anterior_view.right.knee_varus_valgus", "膝内翻/外翻（右）", "正面观", "正常"),
            field("anterior_view.left.knee_rotation", "膝盖旋转（左）", "正面观", "正常"),
            field("anterior_view.right.knee_rotation", "膝盖旋转（右）", "正面观", "正常"),
            field("anterior_view.left.patella_position", "髌骨位置（左）", "正面观", "正常"),
            field("anterior_view.right.patella_position", "髌骨位置（右）", "正面观", "正常"),
            field("anterior_view.left.tibial_rotation", "胫骨（左）", "正面观", "正常"),
            field("anterior_view.right.tibial_rotation", "胫骨（右）", "正面观", "正常"),
            field("anterior_view.left.ankle_rotation", "脚踝（左）", "正面观", "正常"),
            field("anterior_view.right.ankle_rotation", "脚踝（右）", "正面观", "正常"),
            field("anterior_view.left.foot_posture", "足部姿势（左）", "正面观", "正常"),
            field("anterior_view.right.foot_posture", "足部姿势（右）", "正面观", "正常"),
            field("anterior_view.left.arch_type", "足弓（左）", "正面观", "正常"),
            field("anterior_view.right.arch_type", "足弓（右）", "正面观", "正常")
    );

    private static final List<RedundantGroupDefinition> REDUNDANT_GROUPS = Arrays.asList(
            group("posterior_head_neck_tilt", "头颈部倾斜",
                    "posterior_view.midline.head_neck_tilt",
                    "posterior_view.left.head_neck_tilt", "posterior_view.right.head_neck_tilt"),
            group("posterior_neck_rotation", "颈部旋转",
                    "posterior_view.midline.neck_rotation",
                    "posterior_view.left.neck_rotation", "posterior_view.right.neck_rotation"),
            group("posterior_thorax_tilt", "胸廓倾斜",
                    "posterior_view.midline.thorax_tilt",
                    "posterior_view.left.thorax_tilt", "posterior_view.right.thorax_tilt"),
            group("posterior_thorax_rotation", "胸廓旋转",
                    "posterior_view.midline.thorax_rotation",
                    "posterior_view.left.thorax_rotation", "posterior_view.right.thorax_rotation"),
            group("posterior_pelvic_tilt", "骨盆区域",
                    "posterior_view.midline.pelvic_tilt",
                    "posterior_view.left.pelvic_tilt", "posterior_view.right.pelvic_tilt"),
            group("posterior_pelvic_rotation", "骨盆旋转",
                    "posterior_view.midline.pelvic_rotation",
                    "posterior_view.left.pelvic_rotation", "posterior_view.right.pelvic_rotation"),
            group("anterior_pelvic_rotation", "正面骨盆旋转",
                    "anterior_view.midline.pelvic_rotation",
                    "anterior_view.left.pelvic_rotation", "anterior_view.right.pelvic_rotation")
    );

    public Map<String, Object> enrichWithSummary(Object rawDataJson) {
        Map<String, Object> payload = normalizeToMap(rawDataJson);
        payload.put("static_summary", buildStaticSummary(payload));
        return payload;
    }

    public Map<String, Object> enrichWithFallback(Object rawDataJson, String reason) {
        Map<String, Object> payload = normalizeToMap(rawDataJson);
        payload.put("static_summary", buildFallbackSummary(reason));
        return payload;
    }

    private Map<String, Object> buildStaticSummary(Map<String, Object> payload) {
        Map<String, Object> posteriorSummary = buildViewSummary("后面观", POSTERIOR_FIELDS, payload);
        Map<String, Object> lateralSummary = buildViewSummary("侧面观", LATERAL_FIELDS, payload);
        Map<String, Object> anteriorSummary = buildViewSummary("正面观", ANTERIOR_FIELDS, payload);
        List<Map<String, Object>> conflicts = detectConflicts(payload);

        Map<String, Object> overallSummary = buildOverallSummary(
                posteriorSummary, lateralSummary, anteriorSummary, conflicts);

        Map<String, Object> staticSummary = new LinkedHashMap<>();
        staticSummary.put("posterior_view_summary", posteriorSummary);
        staticSummary.put("lateral_view_summary", lateralSummary);
        staticSummary.put("anterior_view_summary", anteriorSummary);
        staticSummary.put("overall_summary", overallSummary);
        return staticSummary;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildViewSummary(String viewName, List<FieldDefinition> fields, Map<String, Object> payload) {
        List<Map<String, Object>> abnormalItems = new ArrayList<>();
        List<Map<String, Object>> directionalItems = new ArrayList<>();
        int normalCount = 0;
        int missingCount = 0;

        for (FieldDefinition field : fields) {
            String value = getValueByPath(payload, field.getPath());
            if (StrUtil.isBlank(value)) {
                missingCount++;
                continue;
            }
            if (field.getNormalValues().contains(value)) {
                normalCount++;
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("field_path", field.getPath());
            item.put("field_label", field.getLabel());
            item.put("value", value);
            item.put("side", resolveSide(field.getPath()));
            abnormalItems.add(item);
            if (isDirectionalKeyword(field.getLabel()) || isDirectionalKeyword(value)) {
                directionalItems.add(item);
            }
        }

        String summaryText;
        if (missingCount == fields.size()) {
            summaryText = VIEW_NOT_FILLED;
        } else if (abnormalItems.isEmpty()) {
            summaryText = viewName + "已录入观察结果，当前记录中暂未见明显非正常项。";
        } else {
            String abnormalShort = abnormalItems.stream().limit(4)
                    .map(item -> item.get("field_label") + "：" + item.get("value"))
                    .collect(Collectors.joining("；"));
            summaryText = viewName + "可见非正常项 " + abnormalItems.size() + " 处，主要包括：" + abnormalShort + "。";
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("abnormal_items", abnormalItems);
        summary.put("directional_items", directionalItems);
        summary.put("normal_count", normalCount);
        summary.put("missing_count", missingCount);
        summary.put("summary_text", summaryText);
        return summary;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildOverallSummary(Map<String, Object> posteriorSummary,
                                                    Map<String, Object> lateralSummary,
                                                    Map<String, Object> anteriorSummary,
                                                    List<Map<String, Object>> conflicts) {
        List<Map<String, Object>> mergedAbnormalItems = new ArrayList<>();
        mergedAbnormalItems.addAll((List<Map<String, Object>>) posteriorSummary.get("abnormal_items"));
        mergedAbnormalItems.addAll((List<Map<String, Object>>) lateralSummary.get("abnormal_items"));
        mergedAbnormalItems.addAll((List<Map<String, Object>>) anteriorSummary.get("abnormal_items"));

        List<String> keyFindings = mergedAbnormalItems.stream().limit(8)
                .map(item -> item.get("field_label") + "：" + item.get("value"))
                .collect(Collectors.toList());

        String summaryText;
        if (mergedAbnormalItems.isEmpty()) {
            if (VIEW_NOT_FILLED.equals(posteriorSummary.get("summary_text"))
                    && VIEW_NOT_FILLED.equals(lateralSummary.get("summary_text"))
                    && VIEW_NOT_FILLED.equals(anteriorSummary.get("summary_text"))) {
                summaryText = "静态评估显示当前录入信息有限，证据不足，需结合人工复核。";
            } else {
                summaryText = "静态评估显示当前已录入项目中暂未见明确非正常项。";
            }
        } else {
            summaryText = "综合静态体态表现，主要表现为：" + String.join("；", keyFindings) + "。";
        }
        if (!conflicts.isEmpty()) {
            summaryText = summaryText + " 存在冗余记录冲突，需人工复核。";
        }

        Map<String, Object> overall = new LinkedHashMap<>();
        overall.put("key_findings", keyFindings);
        overall.put("conflicts", conflicts);
        overall.put("needs_manual_review", !conflicts.isEmpty());
        overall.put("summary_text", summaryText);
        return overall;
    }

    private List<Map<String, Object>> detectConflicts(Map<String, Object> payload) {
        List<Map<String, Object>> conflicts = new ArrayList<>();
        for (RedundantGroupDefinition group : REDUNDANT_GROUPS) {
            String midlineValue = getValueByPath(payload, group.getMidlinePath());
            String leftValue = getValueByPath(payload, group.getLeftPath());
            String rightValue = getValueByPath(payload, group.getRightPath());

            boolean hasMidline = StrUtil.isNotBlank(midlineValue);
            boolean hasLeft = StrUtil.isNotBlank(leftValue);
            boolean hasRight = StrUtil.isNotBlank(rightValue);
            if (!hasMidline && !hasLeft && !hasRight) {
                continue;
            }

            boolean conflict = (hasMidline && hasLeft && !Objects.equals(midlineValue, leftValue))
                    || (hasMidline && hasRight && !Objects.equals(midlineValue, rightValue))
                    || (hasLeft && hasRight && !Objects.equals(leftValue, rightValue));

            if (!conflict) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("group_key", group.getGroupKey());
            item.put("group_label", group.getGroupLabel());
            item.put("midline_value", midlineValue);
            item.put("left_value", leftValue);
            item.put("right_value", rightValue);
            item.put("message", "中轴与左右冗余记录不一致，需人工复核");
            conflicts.add(item);
        }
        return conflicts;
    }

    private Map<String, Object> buildFallbackSummary(String reason) {
        Map<String, Object> emptyView = new LinkedHashMap<>();
        emptyView.put("abnormal_items", Collections.emptyList());
        emptyView.put("directional_items", Collections.emptyList());
        emptyView.put("normal_count", 0);
        emptyView.put("missing_count", 0);
        emptyView.put("summary_text", "静态评估汇总生成失败，需人工复核。");

        Map<String, Object> overall = new LinkedHashMap<>();
        overall.put("key_findings", Collections.emptyList());
        overall.put("conflicts", Collections.emptyList());
        overall.put("needs_manual_review", true);
        overall.put("summary_text", "静态评估汇总生成失败，需人工复核。");
        overall.put("fallback_reason", StrUtil.blankToDefault(reason, "unknown"));

        Map<String, Object> staticSummary = new LinkedHashMap<>();
        staticSummary.put("posterior_view_summary", emptyView);
        staticSummary.put("lateral_view_summary", emptyView);
        staticSummary.put("anterior_view_summary", emptyView);
        staticSummary.put("overall_summary", overall);
        return staticSummary;
    }

    private String getValueByPath(Map<String, Object> payload, String path) {
        if (payload == null || StrUtil.isBlank(path)) {
            return null;
        }
        String[] parts = path.split("\\.");
        Object current = payload;
        for (String part : parts) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(part);
            if (current == null) {
                return null;
            }
        }
        if (current instanceof String) {
            String text = ((String) current).trim();
            return StrUtil.isBlank(text) ? null : text;
        }
        return String.valueOf(current);
    }

    private String resolveSide(String path) {
        if (path.contains(".left.")) {
            return "left";
        }
        if (path.contains(".right.")) {
            return "right";
        }
        if (path.contains(".midline.")) {
            return "midline";
        }
        return "global";
    }

    private boolean isDirectionalKeyword(String text) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        return text.contains("偏")
                || text.contains("旋")
                || text.contains("倾")
                || text.contains("移")
                || text.contains("内翻")
                || text.contains("外翻")
                || text.contains("内旋")
                || text.contains("外旋");
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

    private static FieldDefinition field(String path, String label, String viewName, String... normalValues) {
        return new FieldDefinition(path, label, viewName,
                normalValues == null ? Collections.singletonList("正常") : Arrays.asList(normalValues));
    }

    private static RedundantGroupDefinition group(String groupKey, String groupLabel, String midlinePath,
                                                  String leftPath, String rightPath) {
        return new RedundantGroupDefinition(groupKey, groupLabel, midlinePath, leftPath, rightPath);
    }

    @Data
    @AllArgsConstructor
    private static class FieldDefinition {
        private String path;
        private String label;
        private String viewName;
        private List<String> normalValues;
    }

    @Data
    @AllArgsConstructor
    private static class RedundantGroupDefinition {
        private String groupKey;
        private String groupLabel;
        private String midlinePath;
        private String leftPath;
        private String rightPath;
    }

}
