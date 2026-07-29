package cn.iocoder.yudao.module.rehab.service.ai;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.rehab.enums.RehabAiConstants;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * AI 调用失败时的模板化降级
 */
@Component
public class RehabAiFallbackService {

    public FallbackOutput build(String outputType, String reason) {
        Map<String, Object> content = new LinkedHashMap<String, Object>();
        List<String> caveats = Arrays.asList("证据不足", "仅为功能学推测", "需结合人工复核");
        List<String> evidenceRefs = Collections.singletonList("fallback:template");

        String rendered;
        if (RehabAiConstants.OUTPUT_TYPE_PATIENT_SUMMARY.equals(outputType)) {
            content.put("headline", "当前结论以规则引擎输出为主");
            content.put("top_3_findings", Arrays.asList("提示存在功能性问题模式", "近期需要关注疼痛和动作质量", "建议按计划执行并复评"));
            content.put("top_3_goals", Arrays.asList("提升动作稳定性", "降低不适波动", "完成本阶段复测"));
            content.put("current_focus", "优先完成治疗师标注的核心任务");
            content.put("what_to_avoid", Arrays.asList("超过疼痛阈值的高负荷训练", "未经指导的动作加量"));
            content.put("when_to_recheck", "建议按既定复评节点复测");
            content.put("supportive_message", "保持规律训练，出现明显不适请及时联系治疗师。");
            rendered = "当前采用基础模板摘要。证据不足，仅为功能学推测，需结合人工复核。";
        } else if (RehabAiConstants.OUTPUT_TYPE_RISK_EXPLANATION.equals(outputType)) {
            content.put("overall_risk_level", "medium");
            content.put("explanation", "提示近期存在功能学风险因素，需要结合人工复核。");
            content.put("likely_contributors", Arrays.asList("执行质量波动", "疼痛反馈变化", "左右差变化"));
            content.put("suggested_next_step", Arrays.asList("优先复核训练执行情况", "必要时提前复评"));
            content.put("patient_visible_text", "近期训练建议保守推进，如不适加重请联系治疗师。");
            content.put("evidence_refs", evidenceRefs);
            content.put("caveats", caveats);
            rendered = "风险解释采用降级模板。证据不足，仅为功能学推测，需结合人工复核。";
        } else if (RehabAiConstants.OUTPUT_TYPE_PLAN_DRAFT.equals(outputType)) {
            List<Map<String, Object>> tasks = new ArrayList<Map<String, Object>>();
            Map<String, Object> task = new LinkedHashMap<String, Object>();
            task.put("task_name", "基础稳定性训练");
            task.put("module_type", "control");
            task.put("target_deficit", "动作控制不足");
            task.put("suggested_dosage", "2-3组，每组8-12次");
            task.put("suggested_frequency", "每周3-4次");
            task.put("pain_limit_rule", "疼痛超过 3/10 立即降阶");
            task.put("progression_rule", "连续 1 周动作质量稳定后小幅进阶");
            task.put("regression_rule", "出现明显代偿或疼痛升高时退阶");
            task.put("home_or_clinic", "both");
            task.put("rationale", "用于建立基础控制能力，降低代偿风险。");
            tasks.add(task);

            content.put("plan_name", "AI 降级计划草案");
            content.put("plan_type", "rehab");
            content.put("short_term_goals", Arrays.asList("恢复基础动作控制", "降低疼痛波动"));
            content.put("mid_term_goals", Arrays.asList("提升负荷耐受"));
            content.put("long_term_goals", Arrays.asList("恢复稳定训练与复评达标"));
            content.put("precautions", Arrays.asList("训练中关注疼痛与不适反馈"));
            content.put("suggested_tasks", tasks);
            content.put("progression_strategy", "以动作质量和疼痛反馈双指标进阶");
            content.put("regression_strategy", "不适增加则降阶或替代");
            content.put("review_cycle_days", 14);
            content.put("evidence_refs", evidenceRefs);
            content.put("caveats", caveats);
            rendered = "计划草案采用降级模板，仅供人工审核后使用。证据不足，仅为功能学推测，需结合人工复核。";
        } else if (RehabAiConstants.OUTPUT_TYPE_FOLLOWUP_MESSAGE.equals(outputType)) {
            content.put("patient_message", "请继续按计划完成本周训练，若疼痛明显上升请及时联系治疗师。");
            content.put("therapist_internal_note", "建议优先核对依从性和疼痛波动，必要时提前复评。");
            content.put("recommended_followup_interval_days", 7);
            content.put("recommended_reassessment_needed", true);
            content.put("trigger_level", "medium");
            content.put("evidence_refs", evidenceRefs);
            rendered = "随访文案采用降级模板。证据不足，仅为功能学推测，需结合人工复核。";
        } else if (RehabAiConstants.OUTPUT_TYPE_PROGRESS_SUMMARY.equals(outputType)) {
            content.put("progress_status", "insufficient_data");
            content.put("summary", "当前进展信息不足，建议结合近期打卡与复测综合判断。");
            content.put("positive_changes", Collections.singletonList("可见基础执行记录"));
            content.put("concerning_changes", Collections.singletonList("证据不足，趋势判断有限"));
            content.put("adherence_comment", "依从性需结合人工复核。");
            content.put("next_action", Arrays.asList("完善执行数据", "按节点复评"));
            content.put("evidence_refs", evidenceRefs);
            rendered = "进度总结采用降级模板。证据不足，仅为功能学推测，需结合人工复核。";
        } else {
            content.put("title", "AI 解读降级输出");
            content.put("executive_summary", "当前 AI 输出降级，建议以规则引擎结果为主。");
            content.put("top_issues", Collections.singletonList("证据不足"));
            content.put("priority_actions", Collections.singletonList("需结合人工复核"));
            content.put("risk_notes", Collections.singletonList("仅为功能学推测"));
            content.put("management_focus", Collections.singletonList("建议人工确认"));
            content.put("risk_overview", Collections.singletonList("风险解释为保守表述"));
            content.put("resource_hint", Collections.singletonList("后续可补充数据后重生成"));
            content.put("evidence_refs", evidenceRefs);
            content.put("caveats", caveats);
            rendered = "AI 文案已降级：证据不足，仅为功能学推测，需结合人工复核。";
        }

        FallbackOutput output = new FallbackOutput();
        output.setContent(content);
        output.setContentJson(JsonUtils.toJsonString(content));
        output.setRenderedText(rendered + "（原因：" + reason + "）");
        output.setEvidenceRefsJson(JsonUtils.toJsonString(evidenceRefs));
        output.setSafetyStatus(RehabAiConstants.SAFETY_STATUS_DOWNGRADED);
        return output;
    }

    @Data
    public static class FallbackOutput {
        private Map<String, Object> content;
        private String contentJson;
        private String renderedText;
        private String evidenceRefsJson;
        private String safetyStatus;
    }
}
