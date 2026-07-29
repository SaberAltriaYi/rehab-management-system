package cn.iocoder.yudao.module.rehab.service.assessment;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SFMA 原书版分解评估协议。
 *
 * <p>协议依据《动作-功能性动作系统筛查评估与纠正策略》第 7、8 章及附录 3 整理。
 * 这里只固化测试名称、顺序、关键量化标准和安全规则，不替代治疗师的临床判断。</p>
 */
@Component
public class RehabSfmaBookProtocol {

    public static final String PROTOCOL_ID = "sfma_book_cn";
    public static final String PROTOCOL_VERSION = "2026.07";

    private static final Set<String> CLASSIFICATIONS =
            new LinkedHashSet<>(Arrays.asList("", "FN", "FP", "DN", "DP"));
    private static final Set<String> STEP_STATUSES =
            new LinkedHashSet<>(Arrays.asList("pending", "completed", "skipped", "not_applicable", "stopped_due_to_pain"));
    private static final Set<String> WORKFLOW_STATUSES =
            new LinkedHashSet<>(Arrays.asList("not_started", "in_progress", "completed", "skipped", "stopped_due_to_pain"));

    private static final List<WorkflowDefinition> WORKFLOWS = Arrays.asList(
            workflow("cervical", "颈椎模式分解", 10,
                    Arrays.asList("cervical_flexion", "cervical_extension",
                            "cervical_rotation_left", "cervical_rotation_right"),
                    step("cervical_supine_active_flexion", "仰卧位颈椎主动屈曲", "none", "下颌触及胸骨", ""),
                    step("cervical_supine_passive_flexion", "仰卧位颈椎被动屈曲", "none", "比较主动与被动活动", "主动屈曲受限时继续"),
                    step("oa_active_flexion", "仰卧位寰枕关节主动屈曲", "bilateral", "左右各约 20°", "颈椎屈曲分支"),
                    step("cervical_supine_active_rotation", "仰卧位颈椎主动旋转", "bilateral", "左右各约 80°", ""),
                    step("cervical_passive_rotation", "颈椎被动旋转", "bilateral", "左右各约 80°", "主动旋转受限时继续"),
                    step("c1_c2_rotation", "C1～C2 旋转", "bilateral", "左右各约 40°", "颈椎旋转分支"),
                    step("cervical_supine_extension", "仰卧位颈椎后伸", "none", "面部可达垂直地面", "颈椎后伸分支")
            ),
            workflow("upper_extremity_pattern_1", "上肢模式 1 分解", 20,
                    Arrays.asList("upper_extremity_pattern1_left", "upper_extremity_pattern1_right"),
                    step("ue1_prone_active", "俯卧位上肢主动动作模式 1", "bilateral", "比较左右侧动作质量", ""),
                    step("ue1_prone_passive", "俯卧位上肢被动动作模式 1", "bilateral", "比较主动与被动活动", "主动模式受限时继续"),
                    step("ue1_supine_reciprocal", "仰卧位上肢交互动作模式 1", "bilateral", "观察交互动作与躯干控制", "活动度清除后评估控制")
            ),
            workflow("upper_extremity_pattern_2", "上肢模式 2 分解", 21,
                    Arrays.asList("upper_extremity_pattern2_left", "upper_extremity_pattern2_right"),
                    step("ue2_prone_active", "俯卧位上肢主动动作模式 2", "bilateral", "比较左右侧动作质量", ""),
                    step("ue2_prone_passive", "俯卧位上肢被动动作模式 2", "bilateral", "比较主动与被动活动", "主动模式受限时继续"),
                    step("ue2_supine_reciprocal", "仰卧位上肢交互动作模式 2", "bilateral", "观察交互动作与躯干控制", "活动度清除后评估控制")
            ),
            workflow("msf", "多节段屈曲（MSF）分解", 30,
                    Collections.singletonList("multi_segmental_flexion"),
                    step("msf_single_leg_forward_bend", "单腿站立体前屈", "bilateral", "比较左右侧与双腿模式", ""),
                    step("msf_long_sit_toe_touch", "长坐位触摸足趾", "none", "观察脊柱、骨盆和下肢贡献", ""),
                    step("msf_active_straight_leg_raise", "主动直腿抬高", "bilateral", "记录左右侧角度", ""),
                    step("msf_passive_straight_leg_raise", "被动直腿抬高", "bilateral", "临床参考约 80°；主动/被动差异大于 10°需关注", "主动直腿抬高受限时继续"),
                    step("msf_prone_rock_back", "俯卧位向后摆动", "none", "观察髋屈曲与腰盆控制", ""),
                    step("msf_supine_knees_to_chest", "仰卧位双膝触胸", "none", "观察髋、骨盆与腰椎贡献", ""),
                    step("msf_upper_body_roll_prone_to_supine", "上半身俯卧位滚至仰卧位", "bilateral", "活动度测试均无痛且功能正常后使用", "运动控制分支"),
                    step("msf_lower_body_roll_prone_to_supine", "下半身俯卧位滚至仰卧位", "bilateral", "活动度测试均无痛且功能正常后使用", "运动控制分支")
            ),
            workflow("mse", "多节段伸展（MSE）分解", 31,
                    Collections.singletonList("multi_segmental_extension"),
                    step("mse_no_arms_extension", "无上肢参与的躯体后伸", "none", "观察整体后伸模式", ""),
                    step("mse_single_leg_extension", "单腿站立躯体后伸", "bilateral", "比较左右侧", ""),
                    step("mse_push_up", "俯卧撑", "none", "观察躯干与肩带控制", ""),
                    step("mse_lumbar_locked_ir_active", "腰部固定（内旋）主动旋转/伸展", "bilateral", "观察胸椎伸展与旋转", ""),
                    step("mse_standing_hip_extension", "站立位髋关节后伸", "bilateral", "比较左右侧", ""),
                    step("mse_prone_active_hip_extension", "俯卧位髋关节主动后伸", "bilateral", "约 10°", ""),
                    step("mse_prone_passive_hip_extension", "俯卧位髋关节被动后伸", "bilateral", "比较主动与被动活动", "主动髋后伸受限时继续"),
                    step("mse_modified_thomas", "改良托马斯试验", "bilateral", "记录髋屈肌长度与左右差异", ""),
                    step("mse_single_shoulder_extension", "单肩后伸", "bilateral", "比较左右侧", ""),
                    step("mse_lat_stretch_hips_flexed", "仰卧位双髋屈曲背阔肌拉伸", "none", "观察肩屈曲与腰盆代偿", ""),
                    step("mse_lat_stretch_hips_extended", "仰卧位双髋伸展背阔肌拉伸", "none", "观察肩屈曲与腰盆代偿", ""),
                    step("mse_lumbar_locked_er", "腰部固定（外旋）旋转/伸展", "bilateral", "观察胸椎贡献", ""),
                    step("mse_lumbar_locked_ir_passive", "腰部固定（内旋）被动旋转/伸展", "bilateral", "比较主动与被动活动", ""),
                    step("mse_upper_body_roll_supine_to_prone", "上半身仰卧位滚至俯卧位", "bilateral", "活动度清除后评估控制", "运动控制分支"),
                    step("mse_lower_body_roll_supine_to_prone", "下半身仰卧位滚至俯卧位", "bilateral", "活动度清除后评估控制", "运动控制分支")
            ),
            workflow("msr", "多节段旋转（MSR）分解", 40,
                    Arrays.asList("multi_segmental_rotation_left", "multi_segmental_rotation_right"),
                    step("msr_seated_rotation", "坐位旋转", "bilateral", "左右各约 50°", ""),
                    step("msr_lumbar_locked_er_active", "腰部固定（外旋）主动旋转/伸展", "bilateral", "比较左右侧", ""),
                    step("msr_lumbar_locked_ir_active", "腰部固定（内旋）主动旋转/伸展", "bilateral", "比较左右侧", ""),
                    step("msr_rolling", "四种滚动模式", "bilateral", "分别评估上下半身、俯卧与仰卧起始", "活动度清除后的运动控制分支"),
                    step("msr_lumbar_locked_ir_passive", "腰部固定（内旋）被动旋转/伸展", "bilateral", "比较主动与被动活动", ""),
                    step("msr_prone_elbow_rotation", "俯卧位肘支撑旋转/伸展", "bilateral", "约 30°", ""),
                    step("msr_seated_hip_external_rotation", "坐位髋关节主动/被动外旋", "bilateral", "约 40°", ""),
                    step("msr_prone_hip_external_rotation", "俯卧位髋关节主动/被动外旋", "bilateral", "约 40°", ""),
                    step("msr_seated_hip_internal_rotation", "坐位髋关节主动/被动内旋", "bilateral", "约 30°", ""),
                    step("msr_prone_hip_internal_rotation", "俯卧位髋关节主动/被动内旋", "bilateral", "约 30°", ""),
                    step("msr_seated_tibial_internal_rotation", "坐位胫骨主动/被动内旋", "bilateral", "约 20°", ""),
                    step("msr_seated_tibial_external_rotation", "坐位胫骨主动/被动外旋", "bilateral", "约 20°", "")
            ),
            workflow("sls", "单腿站立（SLS）分解", 50,
                    Arrays.asList("single_leg_stance_left", "single_leg_stance_right"),
                    step("sls_ctsib", "感觉统合与平衡临床测试（CTSIB）", "none", "双侧闭眼单腿站立 DN 时进行", "条件测试"),
                    step("sls_narrow_base_half_kneeling", "窄基底单膝跪地", "bilateral", "比较左右侧姿势控制", ""),
                    step("sls_rolling", "四种滚动模式", "bilateral", "分别评估上下半身、俯卧与仰卧起始", "运动控制分支"),
                    step("sls_quadruped_diagonal_reach", "四点支撑并对角线伸出", "bilateral", "比较左右对角线控制", ""),
                    step("sls_heel_walk", "足跟走 10 步", "none", "观察背屈能力与控制", ""),
                    step("sls_passive_ankle_dorsiflexion", "俯卧位踝关节被动背伸", "bilateral", "膝伸直与屈曲约 45°取平均，参考 20°～30°", ""),
                    step("sls_toe_walk", "足趾走 10 步", "none", "观察跖屈能力与控制", ""),
                    step("sls_passive_ankle_plantarflexion", "俯卧位踝关节被动跖屈", "bilateral", "膝伸直与屈曲约 45°取平均，参考 30°～40°", ""),
                    step("sls_seated_ankle_inversion_eversion", "坐位踝关节内翻/外翻", "bilateral", "比较左右侧", "")
            ),
            workflow("ods", "过顶深蹲（ODS）分解", 60,
                    Collections.singletonList("arms_down_deep_squat"),
                    step("ods_hands_behind_neck_squat", "双手十指于颈后交叉深蹲", "none", "观察去除上肢影响后的深蹲", ""),
                    step("ods_assisted_squat", "辅助深蹲", "none", "比较辅助前后动作", ""),
                    step("ods_half_kneeling_ankle_dorsiflexion", "单膝跪位踝关节背伸", "bilateral", "膝可超过足趾约 10 cm；参考 20°～30°", ""),
                    step("ods_supine_knees_to_chest_hold_shins", "仰卧位双手抱小腿膝触胸", "none", "观察髋与腰盆活动", ""),
                    step("ods_supine_knees_to_chest_hold_thighs", "仰卧位双手抱大腿膝触胸", "none", "比较去除膝屈限制后的表现", "")
            )
    );

    public Map<String, Object> getProtocol() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocol_id", PROTOCOL_ID);
        result.put("protocol_version", PROTOCOL_VERSION);
        result.put("source", "《动作-功能性动作系统筛查评估与纠正策略》第7、8章及附录3");
        result.put("classification_options", Arrays.asList("FN", "FP", "DN", "DP"));
        result.put("workflow_order", WORKFLOWS.stream().map(WorkflowDefinition::getCode).collect(Collectors.toList()));
        result.put("rules", Arrays.asList(
                "先完成全部 Top Tier，再进入分解评估",
                "分解顺序按 DN、FP、DP 排列",
                "FN 不进入分解；FP/DP 应谨慎进行",
                "任一分解测试出现 FP 或 DP 时，终止该流程后续测试并优先处理疼痛"
        ));
        result.put("workflows", WORKFLOWS.stream().map(WorkflowDefinition::toMap).collect(Collectors.toList()));
        return result;
    }

    /**
     * 兼容旧数据：只有提交了 book_protocol 才启用严格协议校验。
     */
    public void validate(Object rawDataJson) {
        Map<String, Object> payload = normalizeToMap(rawDataJson);
        Map<String, Object> nested = castToMap(payload.get("sfma"));
        if (nested != null && !nested.isEmpty()) {
            payload = nested;
        }
        Map<String, Object> protocol = castToMap(payload.get("book_protocol"));
        if (protocol == null || protocol.isEmpty()) {
            return;
        }
        require(PROTOCOL_ID.equals(stringValue(protocol.get("protocol_id"))), "协议标识不正确");
        require(PROTOCOL_VERSION.equals(stringValue(protocol.get("protocol_version"))), "协议版本不正确");

        Map<String, Object> submittedWorkflows = castToMap(protocol.get("workflows"));
        require(submittedWorkflows != null, "缺少 workflows");
        Map<String, WorkflowDefinition> definitionMap = WORKFLOWS.stream()
                .collect(Collectors.toMap(WorkflowDefinition::getCode, item -> item, (a, b) -> a, LinkedHashMap::new));

        for (String workflowCode : submittedWorkflows.keySet()) {
            require(definitionMap.containsKey(workflowCode), "未知分解流程：" + workflowCode);
        }
        for (Map.Entry<String, Object> entry : submittedWorkflows.entrySet()) {
            Map<String, Object> workflow = castToMap(entry.getValue());
            require(workflow != null, "分解流程格式不正确：" + entry.getKey());
            String workflowStatus = stringValue(workflow.get("status"));
            require(WORKFLOW_STATUSES.contains(workflowStatus), "分解流程状态不正确：" + entry.getKey());
            validateSteps(definitionMap.get(entry.getKey()), workflow);
        }
    }

    private void validateSteps(WorkflowDefinition definition, Map<String, Object> workflow) {
        Object rawSteps = workflow.get("steps");
        require(rawSteps instanceof List, "缺少分解步骤：" + definition.getCode());
        List<String> expectedCodes = definition.getSteps().stream().map(StepDefinition::getCode).collect(Collectors.toList());
        Set<String> seen = new LinkedHashSet<>();
        int previousIndex = -1;
        boolean painEncountered = false;

        for (Object rawStep : (List<?>) rawSteps) {
            Map<String, Object> step = castToMap(rawStep);
            require(step != null, "分解步骤格式不正确：" + definition.getCode());
            String code = stringValue(step.get("test_code"));
            require(expectedCodes.contains(code), "未知分解步骤：" + code);
            require(seen.add(code), "分解步骤重复：" + code);
            int currentIndex = expectedCodes.indexOf(code);
            require(currentIndex > previousIndex, "分解步骤顺序不符合原书：" + code);
            previousIndex = currentIndex;

            String status = StrUtil.blankToDefault(stringValue(step.get("status")), "pending");
            require(STEP_STATUSES.contains(status), "分解步骤状态不正确：" + code);
            String classification = normalizeClassification(step.get("classification"));
            String leftClassification = normalizeClassification(step.get("left_classification"));
            String rightClassification = normalizeClassification(step.get("right_classification"));
            require(CLASSIFICATIONS.contains(classification), "结果分类不正确：" + code);
            require(CLASSIFICATIONS.contains(leftClassification), "左侧结果分类不正确：" + code);
            require(CLASSIFICATIONS.contains(rightClassification), "右侧结果分类不正确：" + code);

            boolean hasRecordedResult = "completed".equals(status)
                    || StrUtil.isNotBlank(classification)
                    || StrUtil.isNotBlank(leftClassification)
                    || StrUtil.isNotBlank(rightClassification);
            require(!painEncountered || !hasRecordedResult, "疼痛终止后不得继续记录后续步骤：" + code);
            if (isPainful(classification) || isPainful(leftClassification) || isPainful(rightClassification)) {
                painEncountered = true;
            }
        }
    }

    private static boolean isPainful(String classification) {
        return "FP".equals(classification) || "DP".equals(classification);
    }

    private static String normalizeClassification(Object value) {
        return stringValue(value).toUpperCase(Locale.ROOT);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castToMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeToMap(Object rawDataJson) {
        if (rawDataJson == null) {
            return new LinkedHashMap<>();
        }
        if (rawDataJson instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) rawDataJson);
        }
        if (rawDataJson instanceof String && StrUtil.isBlank((String) rawDataJson)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> parsed = JsonUtils.parseObject(
                rawDataJson instanceof String ? (String) rawDataJson : JsonUtils.toJsonString(rawDataJson),
                new TypeReference<Map<String, Object>>() {});
        return parsed == null ? new LinkedHashMap<>() : parsed;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static WorkflowDefinition workflow(String code, String name, int order, List<String> triggers,
                                               StepDefinition... steps) {
        return new WorkflowDefinition(code, name, order, triggers, Arrays.asList(steps));
    }

    private static StepDefinition step(String code, String name, String side, String criterion, String condition) {
        return new StepDefinition(code, name, side, criterion, condition);
    }

    private static class WorkflowDefinition {
        private final String code;
        private final String name;
        private final int order;
        private final List<String> triggerTestCodes;
        private final List<StepDefinition> steps;

        private WorkflowDefinition(String code, String name, int order, List<String> triggerTestCodes,
                                   List<StepDefinition> steps) {
            this.code = code;
            this.name = name;
            this.order = order;
            this.triggerTestCodes = triggerTestCodes;
            this.steps = steps;
        }

        public String getCode() {
            return code;
        }

        public List<StepDefinition> getSteps() {
            return steps;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", code);
            result.put("name", name);
            result.put("order", order);
            result.put("trigger_test_codes", triggerTestCodes);
            result.put("steps", steps.stream().map(StepDefinition::toMap).collect(Collectors.toList()));
            return result;
        }
    }

    private static class StepDefinition {
        private final String code;
        private final String name;
        private final String side;
        private final String criterion;
        private final String condition;

        private StepDefinition(String code, String name, String side, String criterion, String condition) {
            this.code = code;
            this.name = name;
            this.side = side;
            this.criterion = criterion;
            this.condition = condition;
        }

        public String getCode() {
            return code;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("test_code", code);
            result.put("test_name_zh", name);
            result.put("side", side);
            result.put("criterion", criterion);
            result.put("condition", condition);
            return result;
        }
    }
}
