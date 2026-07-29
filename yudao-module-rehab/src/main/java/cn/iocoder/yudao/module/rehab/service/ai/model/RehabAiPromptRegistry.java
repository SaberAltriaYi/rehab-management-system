package cn.iocoder.yudao.module.rehab.service.ai.model;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.rehab.enums.RehabAiConstants;

import java.util.*;

/**
 * Step7 Prompt Registry（内置兜底模板）
 */
public final class RehabAiPromptRegistry {

    private RehabAiPromptRegistry() {
    }

    public static String resolveSchemaNameByOutputType(String outputType) {
        if (RehabAiConstants.OUTPUT_TYPE_THERAPIST_SUMMARY.equals(outputType)) {
            return "TherapistSummarySchema";
        }
        if (RehabAiConstants.OUTPUT_TYPE_ADMIN_SUMMARY.equals(outputType)) {
            return "AdminSummarySchema";
        }
        if (RehabAiConstants.OUTPUT_TYPE_PATIENT_SUMMARY.equals(outputType)) {
            return "PatientSummarySchema";
        }
        if (RehabAiConstants.OUTPUT_TYPE_RISK_EXPLANATION.equals(outputType)) {
            return "RiskExplanationSchema";
        }
        if (RehabAiConstants.OUTPUT_TYPE_PLAN_DRAFT.equals(outputType)) {
            return "PlanDraftSchema";
        }
        if (RehabAiConstants.OUTPUT_TYPE_FOLLOWUP_MESSAGE.equals(outputType)) {
            return "FollowupMessageSchema";
        }
        if (RehabAiConstants.OUTPUT_TYPE_PROGRESS_SUMMARY.equals(outputType)) {
            return "ProgressSummarySchema";
        }
        return "GenericSchema";
    }

    public static String defaultSystemPrompt(String moduleScope, String roleScope) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是运动康复评估辅助系统的一部分，不是医生。")
                .append("你只能基于已提供结构化数据分析，不得虚构数据，不得输出医学确诊。")
                .append("当证据不足时必须包含：证据不足、仅为功能学推测、需结合人工复核。")
                .append("允许用词：提示、疑似存在、从静态排列看、从动态表现看、从运动学表现看、结合当前证据，优先考虑。")
                .append("禁止用词：确诊、一定是、明确损伤、必然导致。")
                .append("输出必须是严格 JSON，不要附加解释文本。");
        if (StrUtil.isNotBlank(moduleScope)) {
            sb.append("当前任务模块：").append(moduleScope).append("。");
        }
        if (StrUtil.isNotBlank(roleScope)) {
            sb.append("目标读者：").append(roleScope).append("。");
        }
        return sb.toString();
    }

    public static String defaultUserPromptTemplate(String moduleScope) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据输入数据生成结构化结果。")
                .append("每条重要结论尽量绑定 evidence_refs。")
                .append("若证据不足请在 caveats 或对应字段明确说明。")
                .append("输入 JSON 如下：\n{{input_json}}\n");
        if (StrUtil.isNotBlank(moduleScope)) {
            sb.append("请按模块 ").append(moduleScope).append(" 的临床语境输出。");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> schemaByName(String schemaName) {
        if ("TherapistSummarySchema".equals(schemaName)) {
            return therapistSummarySchema();
        }
        if ("AdminSummarySchema".equals(schemaName)) {
            return adminSummarySchema();
        }
        if ("PatientSummarySchema".equals(schemaName)) {
            return patientSummarySchema();
        }
        if ("RiskExplanationSchema".equals(schemaName)) {
            return riskExplanationSchema();
        }
        if ("PlanDraftSchema".equals(schemaName)) {
            return planDraftSchema();
        }
        if ("FollowupMessageSchema".equals(schemaName)) {
            return followupMessageSchema();
        }
        if ("ProgressSummarySchema".equals(schemaName)) {
            return progressSummarySchema();
        }
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("summary", stringSchema());
        Map<String, Object> schema = objectSchema(properties, Collections.singletonList("summary"));
        schema.put("additionalProperties", true);
        return schema;
    }

    private static Map<String, Object> therapistSummarySchema() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("title", stringSchema());
        properties.put("executive_summary", stringSchema());
        properties.put("top_issues", arrayStringSchema());
        properties.put("priority_actions", arrayStringSchema());
        properties.put("risk_notes", arrayStringSchema());
        properties.put("evidence_refs", arrayStringSchema());
        properties.put("caveats", arrayStringSchema());
        return objectSchema(properties, Arrays.asList("title", "executive_summary", "top_issues", "priority_actions", "evidence_refs", "caveats"));
    }

    private static Map<String, Object> adminSummarySchema() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("title", stringSchema());
        properties.put("executive_summary", stringSchema());
        properties.put("management_focus", arrayStringSchema());
        properties.put("risk_overview", arrayStringSchema());
        properties.put("resource_hint", arrayStringSchema());
        properties.put("evidence_refs", arrayStringSchema());
        properties.put("caveats", arrayStringSchema());
        return objectSchema(properties, Arrays.asList("title", "executive_summary", "management_focus", "risk_overview", "evidence_refs", "caveats"));
    }

    private static Map<String, Object> patientSummarySchema() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("headline", stringSchema());
        properties.put("top_3_findings", arrayStringSchema());
        properties.put("top_3_goals", arrayStringSchema());
        properties.put("current_focus", stringSchema());
        properties.put("what_to_avoid", arrayStringSchema());
        properties.put("when_to_recheck", stringSchema());
        properties.put("supportive_message", stringSchema());
        return objectSchema(properties, Arrays.asList("headline", "top_3_findings", "top_3_goals", "current_focus", "what_to_avoid", "when_to_recheck", "supportive_message"));
    }

    private static Map<String, Object> riskExplanationSchema() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("overall_risk_level", stringSchema());
        properties.put("explanation", stringSchema());
        properties.put("likely_contributors", arrayStringSchema());
        properties.put("suggested_next_step", arrayStringSchema());
        properties.put("patient_visible_text", stringSchema());
        properties.put("evidence_refs", arrayStringSchema());
        properties.put("caveats", arrayStringSchema());
        return objectSchema(properties, Arrays.asList("overall_risk_level", "explanation", "likely_contributors", "suggested_next_step", "patient_visible_text", "evidence_refs", "caveats"));
    }

    private static Map<String, Object> planDraftSchema() {
        Map<String, Object> taskProperties = new LinkedHashMap<String, Object>();
        taskProperties.put("task_name", stringSchema());
        taskProperties.put("module_type", stringSchema());
        taskProperties.put("target_deficit", stringSchema());
        taskProperties.put("suggested_dosage", stringSchema());
        taskProperties.put("suggested_frequency", stringSchema());
        taskProperties.put("pain_limit_rule", stringSchema());
        taskProperties.put("progression_rule", stringSchema());
        taskProperties.put("regression_rule", stringSchema());
        taskProperties.put("home_or_clinic", stringSchema());
        taskProperties.put("rationale", stringSchema());
        Map<String, Object> taskSchema = objectSchema(taskProperties, Arrays.asList(
                "task_name", "module_type", "target_deficit", "suggested_dosage", "suggested_frequency",
                "pain_limit_rule", "progression_rule", "regression_rule", "home_or_clinic", "rationale"
        ));

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("plan_name", stringSchema());
        properties.put("plan_type", stringSchema());
        properties.put("short_term_goals", arrayStringSchema());
        properties.put("mid_term_goals", arrayStringSchema());
        properties.put("long_term_goals", arrayStringSchema());
        properties.put("precautions", arrayStringSchema());
        properties.put("suggested_tasks", arraySchema(taskSchema));
        properties.put("progression_strategy", stringSchema());
        properties.put("regression_strategy", stringSchema());
        properties.put("review_cycle_days", integerSchema());
        properties.put("evidence_refs", arrayStringSchema());
        properties.put("caveats", arrayStringSchema());
        return objectSchema(properties, Arrays.asList("plan_name", "plan_type", "short_term_goals", "suggested_tasks",
                "progression_strategy", "regression_strategy", "review_cycle_days", "evidence_refs", "caveats"));
    }

    private static Map<String, Object> followupMessageSchema() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("patient_message", stringSchema());
        properties.put("therapist_internal_note", stringSchema());
        properties.put("recommended_followup_interval_days", integerSchema());
        properties.put("recommended_reassessment_needed", booleanSchema());
        properties.put("trigger_level", stringSchema());
        properties.put("evidence_refs", arrayStringSchema());
        return objectSchema(properties, Arrays.asList("patient_message", "therapist_internal_note",
                "recommended_followup_interval_days", "recommended_reassessment_needed", "trigger_level", "evidence_refs"));
    }

    private static Map<String, Object> progressSummarySchema() {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("progress_status", stringSchema());
        properties.put("summary", stringSchema());
        properties.put("positive_changes", arrayStringSchema());
        properties.put("concerning_changes", arrayStringSchema());
        properties.put("adherence_comment", stringSchema());
        properties.put("next_action", arrayStringSchema());
        properties.put("evidence_refs", arrayStringSchema());
        return objectSchema(properties, Arrays.asList("progress_status", "summary", "positive_changes", "concerning_changes",
                "adherence_comment", "next_action", "evidence_refs"));
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> arrayStringSchema() {
        return arraySchema(stringSchema());
    }

    private static Map<String, Object> arraySchema(Map<String, Object> items) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "array");
        schema.put("items", items);
        return schema;
    }

    private static Map<String, Object> stringSchema() {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "string");
        return schema;
    }

    private static Map<String, Object> integerSchema() {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "integer");
        return schema;
    }

    private static Map<String, Object> booleanSchema() {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "boolean");
        return schema;
    }
}
