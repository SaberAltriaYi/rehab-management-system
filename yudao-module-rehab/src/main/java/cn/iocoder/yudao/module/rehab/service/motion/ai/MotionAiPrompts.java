package cn.iocoder.yudao.module.rehab.service.motion.ai;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动作评估 AI 提示词与输出 Schema（版本见 RehabMotionConstants.PROMPT_VERSION）。
 */
public final class MotionAiPrompts {

    public static final String SCHEMA_NAME = "rehab_motion_report_draft";

    public static final String SYSTEM_PROMPT = String.join("\n",
            "你是运动康复评估报告的写作助手，只负责解释、总结和起草，不负责计算或判定。",
            "硬性规则：",
            "1. 分数、规则结论和指标全部来自输入 JSON，你不得计算、修改、推断或补充任何分数与阈值。",
            "2. 每条 findings 和 training_suggestions 必须在 evidence_refs 中引用输入里存在的 id（s:/r:/n:/t:/l:/m: 开头）。",
            "3. 不做医学诊断，不提手术、药物或注射，不给出风险等级，不把未验证指标称为正常或异常。",
            "4. validation_status 为 descriptive_pending_validation 的指标只能描述数值，并注明“描述性、待验证”。",
            "5. final_status 不是 confirmed/modified 的评分须表述为“系统建议，待治疗师确认”。",
            "6. 训练建议为一般性方向，最终方案由治疗师决定；患者版使用通俗语言，不出现专业术语缩写堆砌。",
            "7. 输入缺失或质控未通过时如实说明，不要猜测。只输出符合 schema 的 JSON。");

    private MotionAiPrompts() {
    }

    public static Map<String, Object> schema() {
        Map<String, Object> claim = new LinkedHashMap<String, Object>();
        claim.put("type", "object");
        claim.put("additionalProperties", false);
        claim.put("required", Arrays.asList("text", "evidence_refs"));
        Map<String, Object> claimProps = new LinkedHashMap<String, Object>();
        claimProps.put("text", stringSchema());
        claimProps.put("evidence_refs", arrayOf(stringSchema()));
        claim.put("properties", claimProps);

        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("therapist_summary", stringSchema());
        props.put("findings", arrayOf(claim));
        props.put("training_suggestions", arrayOf(claim));
        props.put("patient_summary", stringSchema());
        props.put("limitations", arrayOf(stringSchema()));
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", Arrays.asList("therapist_summary", "findings", "training_suggestions",
                "patient_summary", "limitations"));
        schema.put("properties", props);
        return Collections.unmodifiableMap(schema);
    }

    /** 严格模式 JSON Schema 只用最基础关键字；长度与条数限制由 MotionAiOutputValidator 执行。 */
    private static Map<String, Object> stringSchema() {
        Map<String, Object> s = new LinkedHashMap<String, Object>();
        s.put("type", "string");
        return s;
    }

    private static Map<String, Object> arrayOf(Map<String, Object> item) {
        Map<String, Object> s = new LinkedHashMap<String, Object>();
        s.put("type", "array");
        s.put("items", item);
        return s;
    }

}
