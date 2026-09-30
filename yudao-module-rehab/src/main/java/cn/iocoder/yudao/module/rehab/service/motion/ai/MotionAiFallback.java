package cn.iocoder.yudao.module.rehab.service.motion.ai;

import cn.iocoder.yudao.module.rehab.service.motion.MotionLabels;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 关闭/失败/被拦截时的确定性模板草稿：只复述系统结果与证据 ID，不做任何推断。
 */
public final class MotionAiFallback {

    private static final int MAX_RULE_FINDINGS = 15;

    private MotionAiFallback() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> build(Map<String, Object> payload, String reason) {
        List<Map<String, Object>> findings = new ArrayList<Map<String, Object>>();
        Set<String> used = new LinkedHashSet<String>();
        int tests = 0;
        for (Map<String, Object> s : (List<Map<String, Object>>) payload.get("scores")) {
            tests++;
            Object finalScore = s.get("final_score");
            Object systemScore = s.get("system_score");
            String side = MotionLabels.side(String.valueOf(s.get("side")));
            StringBuilder text = new StringBuilder(MotionLabels.test(String.valueOf(s.get("test_code"))));
            if (!side.isEmpty()) {
                text.append("（").append(side).append("）");
            }
            if (finalScore != null) {
                text.append("：治疗师最终分 ").append(finalScore);
            } else if (systemScore != null) {
                text.append("：系统建议 ").append(systemScore).append("，待治疗师确认");
            } else {
                text.append("：系统未给出建议分（").append(s.get("system_status")).append("），需治疗师判定");
            }
            findings.add(claim(text.toString(), String.valueOf(s.get("id"))));
            used.add(String.valueOf(s.get("id")));
        }
        int ruleCount = 0;
        for (Map<String, Object> r : (List<Map<String, Object>>) payload.get("rule_results")) {
            String outcome = String.valueOf(r.get("outcome"));
            boolean rated = outcome.startsWith("rated_") && !"rated_0".equals(outcome);
            if (!("not_met".equals(outcome) || "present".equals(outcome) || "candidate_not_met".equals(outcome)
                    || "candidate_true".equals(outcome) || rated)) {
                continue;
            }
            if (ruleCount++ >= MAX_RULE_FINDINGS) {
                break;
            }
            String text = MotionLabels.test(String.valueOf(r.get("test_code"))) + " · " + r.get("rule_id") + " "
                    + r.get("label") + "：" + describe(outcome)
                    + ("auto_candidate".equals(r.get("decided_by")) ? "（候选判定，待确认）" : "");
            findings.add(claim(text, String.valueOf(r.get("id"))));
            used.add(String.valueOf(r.get("id")));
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("therapist_summary", "【系统模板，未使用 AI：" + reason + "】本草稿仅列出系统计算结果与证据，"
                + "解释、训练建议与最终结论由治疗师填写。");
        out.put("findings", findings);
        out.put("training_suggestions", new ArrayList<Object>());
        // 草稿被接受后会原样进入已签署的患者版报告：措辞在签署前后都须成立，且按评分条目计数（YBT 左右各一条）。
        out.put("patient_summary", "本次评估共包含 " + tests + " 个评分条目。各项结果以治疗师签署确认的最终分为准，"
                + "具体含义请与您的治疗师沟通。");
        List<String> limitations = new ArrayList<String>();
        Object lim = payload.get("limitations");
        if (lim instanceof List) {
            for (Object o : (List<Object>) lim) {
                limitations.add(String.valueOf(o));
            }
        }
        out.put("limitations", limitations);
        out.put("evidence_refs", new ArrayList<String>(used));
        return out;
    }

    static String describe(String outcome) {
        if ("present".equals(outcome)) {
            return "治疗师确认出现";
        }
        if ("candidate_true".equals(outcome)) {
            return "系统候选：可能出现";
        }
        if (outcome.startsWith("rated_")) {
            return "治疗师评定 " + outcome.substring(6);
        }
        return "未满足";
    }

    private static Map<String, Object> claim(String text, String ref) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("text", text);
        List<String> refs = new ArrayList<String>();
        refs.add(ref);
        m.put("evidence_refs", refs);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static String render(Map<String, Object> content) {
        StringBuilder sb = new StringBuilder();
        sb.append(content.get("therapist_summary")).append("\n\n主要发现：\n");
        for (Map<String, Object> f : (List<Map<String, Object>>) content.get("findings")) {
            sb.append("- ").append(f.get("text")).append(" [").append(join(f.get("evidence_refs"))).append("]\n");
        }
        List<Map<String, Object>> sug = (List<Map<String, Object>>) content.get("training_suggestions");
        if (sug != null && !sug.isEmpty()) {
            sb.append("\n训练建议（由治疗师决定是否采纳）：\n");
            for (Map<String, Object> f : sug) {
                sb.append("- ").append(f.get("text")).append(" [").append(join(f.get("evidence_refs"))).append("]\n");
            }
        }
        sb.append("\n患者版摘要：\n").append(content.get("patient_summary")).append('\n');
        return sb.toString();
    }

    private static String join(Object refs) {
        if (!(refs instanceof List)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object o : (List<?>) refs) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(o);
        }
        return sb.toString();
    }

}
