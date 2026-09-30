package cn.iocoder.yudao.module.rehab.service.motion.report;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAiDraftDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionMetricDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionRuleResultDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionScoreDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTrialDO;
import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;
import cn.iocoder.yudao.module.rehab.service.motion.MotionEngineRequestBuilder;
import cn.iocoder.yudao.module.rehab.service.motion.MotionLabels;
import cn.iocoder.yudao.module.rehab.service.motion.MotionScoreRules;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报告内容构建（纯函数）。同一份 content 同时用于网页展示、JSON 导出与 PDF 渲染：
 * {@code sections[]{title, lines[]}} 供阅读，其余字段为结构化数据。
 */
public final class MotionReportBuilder {

    public static final int MAX_METRIC_LINES = 120;
    public static final int MAX_RULE_LINES = 150;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static final String DISCLAIMER = "本报告为功能性动作评估结果，不构成医学诊断。所有评分以治疗师最终确认为准；"
            + "标注为“描述性、待验证”的指标仅用于描述，不代表正常或异常。";

    private MotionReportBuilder() {
    }

    /** 构建输入 */
    public static final class Input {
        public RehabMotionAssessmentDO assessment;
        public String patientName;
        public String patientNo;
        public String patientGender;
        public Integer patientAge;
        public List<RehabMotionTrialDO> trials = new ArrayList<RehabMotionTrialDO>();
        public List<RehabMotionScoreDO> scores = new ArrayList<RehabMotionScoreDO>();
        public List<RehabMotionRuleResultDO> rules = new ArrayList<RehabMotionRuleResultDO>();
        public List<RehabMotionMetricDO> metrics = new ArrayList<RehabMotionMetricDO>();
        public RehabMotionAiDraftDO acceptedDraft;
        public List<String> limitations = new ArrayList<String>();
        public Map<String, Object> comparison;
        public String signerName;
        public LocalDateTime signedTime;
        public Integer versionNo;
    }

    public static Map<String, Object> therapist(Input in) {
        RehabMotionAssessmentDO a = in.assessment;
        Map<String, Object> content = header(in, "动作评估报告（治疗师版）");
        List<Map<String, Object>> sections = new ArrayList<Map<String, Object>>();

        List<String> basic = new ArrayList<String>();
        basic.add("患者：" + StrUtil.blankToDefault(in.patientName, "-") + "　编号：" + StrUtil.blankToDefault(in.patientNo, "-")
                + "　性别：" + StrUtil.blankToDefault(in.patientGender, "-") + "　年龄：" + (in.patientAge == null ? "-" : in.patientAge));
        basic.add("评估类型：" + visitLabel(a.getVisitType()) + "　采集时间：" + time(a.getCaptureTime()));
        basic.add("数据来源：" + ("opencap".equals(a.getDataSource()) ? "OpenCap 会话" : "上传 OpenCap 导出数据")
                + "　相机数：" + a.getCameraCount() + "　模型：" + StrUtil.blankToDefault(a.getModelName(), RehabMotionConstants.OPENSIM_MODEL));
        sections.add(section("基本信息", basic));

        List<String> versions = new ArrayList<String>();
        versions.add("分析引擎：" + dash(a.getEngineVersion()) + "　规则引擎：" + dash(a.getRuleVersion()) + "　结果格式："
                + dash(a.getResultSchemaVersion()));
        versions.add("分析输入版本：" + a.getAnalyzedRevision() + "　分析时间：" + time(a.getAnalyzedTime()));
        if (in.acceptedDraft != null) {
            versions.add("AI 草稿：" + dash(in.acceptedDraft.getProvider()) + " / " + dash(in.acceptedDraft.getModel())
                    + "　提示词：" + dash(in.acceptedDraft.getPromptVersion()));
        }
        Map<String, Object> protocols = MotionEngineRequestBuilder.parseObject(a.getProtocolVersionsJson());
        for (Map.Entry<String, Object> e : protocols.entrySet()) {
            if (e.getValue() instanceof Map) {
                Map<?, ?> p = (Map<?, ?>) e.getValue();
                String sha = StrUtil.toStringOrNull(p.get("file_sha256"));
                versions.add("协议 " + e.getKey() + "：" + p.get("protocol_version")
                        + (sha == null ? "" : "（sha256 " + StrUtil.subPre(sha, 12) + "）"));
            }
        }
        sections.add(section("版本与追溯", versions));

        List<String> qc = new ArrayList<String>();
        qc.add("会话质量：" + qcLabel(a.getSessionQualityStatus()));
        for (RehabMotionTrialDO t : in.trials) {
            StringBuilder sb = new StringBuilder();
            sb.append(t.getTrialKey()).append("　").append(MotionLabels.test(t.getTestCode()));
            String side = MotionLabels.side(t.getSide());
            if (!side.isEmpty()) {
                sb.append(" ").append(side);
            }
            if (StrUtil.isNotBlank(t.getConditionCode())) {
                sb.append(" [").append(t.getConditionCode()).append("]");
            }
            sb.append(" 第").append(t.getAttemptNo()).append("次　质控：").append(qcLabel(t.getQcStatus()));
            if (t.getRepCount() != null) {
                sb.append("　重复：").append(t.getRepCount());
            }
            sb.append("　疼痛：").append(t.getPain() == null ? "未记录" : (t.getPain() ? "是" : "否"));
            if (Boolean.FALSE.equals(t.getValid())) {
                sb.append("　无效：").append(t.getInvalidReason());
            }
            String issues = issueCodes(t.getQcIssuesJson());
            if (!issues.isEmpty()) {
                sb.append("　问题：").append(issues);
            }
            qc.add(sb.toString());
        }
        sections.add(section("数据质量", qc));

        List<String> scoreLines = new ArrayList<String>();
        for (RehabMotionScoreDO s : in.scores) {
            StringBuilder sb = new StringBuilder(MotionLabels.test(s.getTestCode()));
            String side = MotionLabels.side(s.getSide());
            if (!side.isEmpty()) {
                sb.append("（").append(side).append("）");
            }
            if ("NASM_EVIDENCE".equals(s.getScoringScheme())) {
                sb.append("：定性观察（无数值分）　审核：").append(MotionLabels.finalStatus(s.getFinalStatus()));
            } else {
                sb.append("：系统 ").append(num(s.getSystemScore())).append("（").append(dash(s.getSystemStatus())).append("）")
                        .append(" → 最终 ").append(num(s.getFinalScore())).append("（")
                        .append(MotionLabels.finalStatus(s.getFinalStatus())).append("）");
            }
            if (StrUtil.isNotBlank(s.getChangeReason())) {
                sb.append("　原因：").append(s.getChangeReason());
            }
            scoreLines.add(sb.toString());
        }
        BigDecimal total = MotionScoreRules.fmsTotal(in.scores);
        if (total != null) {
            scoreLines.add("FMS 总分（描述性，0-21）：" + total.stripTrailingZeros().toPlainString());
        } else if (hasFamily(in.scores, RehabMotionConstants.FAMILY_FMS)) {
            scoreLines.add("FMS 总分：7 项未全部完成审核，不计算总分");
        }
        sections.add(section("评分", scoreLines));

        Map<Long, String> keyById = new HashMap<Long, String>();
        for (RehabMotionTrialDO t : in.trials) {
            keyById.put(t.getId(), t.getTrialKey());
        }
        List<String> metricLines = new ArrayList<String>();
        for (RehabMotionMetricDO m : in.metrics) {
            if (m.getRepNo() != null || m.getValueNum() == null) {
                continue;
            }
            if (metricLines.size() >= MAX_METRIC_LINES) {
                metricLines.add("……其余指标见 JSON 导出");
                break;
            }
            metricLines.add(keyById.get(m.getTrialId()) + "　" + StrUtil.blankToDefault(m.getLabel(), m.getCode())
                    + ("none".equals(m.getSide()) ? "" : " " + MotionLabels.side(m.getSide())) + "　" + m.getPhase() + "："
                    + m.getValueNum().stripTrailingZeros().toPlainString() + " " + StrUtil.nullToEmpty(m.getUnit())
                    + "　[" + classLabel(m.getClassification())
                    + ("descriptive_pending_validation".equals(m.getValidationStatus()) ? "，描述性、待验证" : "") + "]");
        }
        sections.add(section("关键指标", metricLines));

        List<String> ruleLines = new ArrayList<String>();
        for (RehabMotionRuleResultDO r : in.rules) {
            if (ruleLines.size() >= MAX_RULE_LINES) {
                ruleLines.add("……其余证据见 JSON 导出");
                break;
            }
            ruleLines.add("[" + r.getRuleKey() + "] " + MotionLabels.test(r.getTestCode()) + " " + dash(r.getRuleId()) + " "
                    + StrUtil.nullToEmpty(r.getLabel()) + "：" + dash(r.getOutcome()) + "（" + dash(r.getDecidedBy()) + "）");
        }
        sections.add(section("证据（规则判定）", ruleLines));

        List<String> ai = new ArrayList<String>();
        if (in.acceptedDraft == null) {
            ai.add("未采用 AI/模板草稿。");
        } else {
            String text = StrUtil.blankToDefault(in.acceptedDraft.getEditedText(), in.acceptedDraft.getRenderedText());
            for (String line : StrUtil.nullToEmpty(text).replace("\r", "").split("\n")) {
                ai.add(line);
            }
            ai.add(RehabMotionConstants.AI_FALLBACK.equals(in.acceptedDraft.getStatus()) || in.acceptedDraft.getFallbackReason() != null
                    ? "（系统模板草稿，经治疗师审核）" : "（AI 草稿，经治疗师审核；方括号内为证据编号）");
        }
        sections.add(section("解读与建议", ai));

        if (in.comparison != null) {
            sections.add(section("与基线对比", comparisonLines(in.comparison)));
        }
        List<String> lim = new ArrayList<String>(in.limitations);
        lim.add(DISCLAIMER);
        sections.add(section("局限性", lim));
        sections.add(section("签署", signLines(in)));

        content.put("sections", sections);
        content.put("fms_total", total);
        content.put("comparison", in.comparison);
        return content;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> patient(Input in) {
        RehabMotionAssessmentDO a = in.assessment;
        Map<String, Object> content = header(in, "动作评估结果（患者版）");
        List<Map<String, Object>> sections = new ArrayList<Map<String, Object>>();
        List<String> basic = new ArrayList<String>();
        basic.add("姓名：" + StrUtil.blankToDefault(in.patientName, "-") + "　评估日期：" + time(a.getCaptureTime()));
        sections.add(section("基本信息", basic));

        List<String> results = new ArrayList<String>();
        for (RehabMotionScoreDO s : in.scores) {
            if (!RehabMotionConstants.FINAL_REVIEWED.contains(s.getFinalStatus())
                    || RehabMotionConstants.FINAL_NOT_APPLICABLE.equals(s.getFinalStatus())) {
                continue;
            }
            String side = MotionLabels.side(s.getSide());
            String name = MotionLabels.test(s.getTestCode()) + (side.isEmpty() ? "" : "（" + side + "）");
            results.add("NASM_EVIDENCE".equals(s.getScoringScheme()) || s.getFinalScore() == null
                    ? name + "：已完成评估，详见治疗师说明"
                    : name + "：" + s.getFinalScore().stripTrailingZeros().toPlainString());
        }
        BigDecimal total = MotionScoreRules.fmsTotal(in.scores);
        if (total != null) {
            results.add("FMS 总分：" + total.stripTrailingZeros().toPlainString() + " / 21（仅供参考）");
        }
        sections.add(section("评估结果", results));

        List<String> summary = new ArrayList<String>();
        List<String> training = new ArrayList<String>();
        if (in.acceptedDraft != null) {
            Map<String, Object> draft = MotionEngineRequestBuilder.parseObject(in.acceptedDraft.getContentJson());
            String patientSummary = StrUtil.toStringOrNull(draft.get("patient_summary"));
            if (StrUtil.isNotBlank(patientSummary)) {
                summary.add(patientSummary);
            }
            Object sug = draft.get("training_suggestions");
            if (sug instanceof List) {
                for (Object o : (List<Object>) sug) {
                    if (o instanceof Map && ((Map<String, Object>) o).get("text") != null) {
                        training.add("· " + ((Map<String, Object>) o).get("text"));
                    }
                }
            }
        }
        if (summary.isEmpty()) {
            summary.add("您的治疗师已审核本次评估结果，请以治疗师面对面的讲解为准。");
        }
        sections.add(section("结果说明", summary));
        if (!training.isEmpty()) {
            training.add("具体训练方案请遵循治疗师的安排。");
            sections.add(section("训练方向", training));
        }
        List<String> note = new ArrayList<String>();
        note.add(DISCLAIMER);
        sections.add(section("说明", note));
        sections.add(section("签署", signLines(in)));
        content.put("sections", sections);
        return content;
    }

    private static Map<String, Object> header(Input in, String title) {
        Map<String, Object> content = new LinkedHashMap<String, Object>();
        content.put("title", title);
        content.put("assessment_ref", "MA-" + in.assessment.getId());
        content.put("version_no", in.versionNo);
        content.put("engine_version", in.assessment.getEngineVersion());
        content.put("rule_version", in.assessment.getRuleVersion());
        content.put("result_schema_version", in.assessment.getResultSchemaVersion());
        content.put("analysis_revision", in.assessment.getAnalyzedRevision());
        content.put("prompt_version", in.acceptedDraft == null ? null : in.acceptedDraft.getPromptVersion());
        content.put("ai_model", in.acceptedDraft == null ? null : in.acceptedDraft.getModel());
        content.put("signer_name", in.signerName);
        content.put("signed_time", time(in.signedTime));
        return content;
    }

    private static List<String> signLines(Input in) {
        List<String> sign = new ArrayList<String>();
        sign.add("签署治疗师：" + StrUtil.blankToDefault(in.signerName, "-") + "　签署时间：" + time(in.signedTime)
                + "　报告版本：v" + in.versionNo);
        return sign;
    }

    @SuppressWarnings("unchecked")
    static List<String> comparisonLines(Map<String, Object> comparison) {
        List<String> lines = new ArrayList<String>();
        Object scores = comparison.get("scores");
        if (scores instanceof List) {
            for (Map<String, Object> r : (List<Map<String, Object>>) scores) {
                String side = MotionLabels.side(String.valueOf(r.get("side")));
                lines.add(MotionLabels.test(String.valueOf(r.get("test_code"))) + (side.isEmpty() ? "" : "（" + side + "）")
                        + "：基线 " + num(r.get("baseline")) + " → 本次 " + num(r.get("current"))
                        + ("system_unreviewed".equals(r.get("current_source")) || "system_unreviewed".equals(r.get("baseline_source"))
                        ? "（含未审核系统分）" : ""));
            }
        }
        Object metrics = comparison.get("metrics");
        if (metrics instanceof List) {
            int n = 0;
            for (Map<String, Object> r : (List<Map<String, Object>>) metrics) {
                if (n++ >= 60) {
                    lines.add("……其余对比见 JSON 导出");
                    break;
                }
                Object beyond = r.get("beyond_measurement_error");
                lines.add(MotionLabels.test(String.valueOf(r.get("test_code"))) + " " + r.get("code") + " "
                        + MotionLabels.side(String.valueOf(r.get("side"))) + " " + r.get("phase") + "：" + num(r.get("baseline"))
                        + " → " + num(r.get("current")) + " " + StrUtil.nullToEmpty((String) r.get("unit")) + "（差值 "
                        + num(r.get("delta")) + "，" + (beyond == null ? "无误差参考" : (Boolean.TRUE.equals(beyond)
                        ? "超出测量误差" : "在测量误差内")) + "）");
            }
        }
        lines.add(String.valueOf(comparison.get("note")));
        return lines;
    }

    private static boolean hasFamily(List<RehabMotionScoreDO> scores, String family) {
        for (RehabMotionScoreDO s : scores) {
            if (family.equals(s.getFamily())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    static String issueCodes(String json) {
        if (StrUtil.isBlank(json)) {
            return "";
        }
        Object parsed;
        try {
            parsed = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(json, Object.class);
        } catch (RuntimeException ex) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (parsed instanceof List) {
            for (Object o : (List<Object>) parsed) {
                Object code = o instanceof Map ? ((Map<String, Object>) o).get("code") : o;
                if (code != null) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(code);
                }
            }
        }
        return sb.toString();
    }

    private static Map<String, Object> section(String title, List<String> lines) {
        Map<String, Object> s = new LinkedHashMap<String, Object>();
        s.put("title", title);
        s.put("lines", lines);
        return s;
    }

    static String visitLabel(String v) {
        if ("follow_up".equals(v)) {
            return "复评";
        }
        if ("discharge".equals(v)) {
            return "结案评估";
        }
        return "初评";
    }

    static String qcLabel(String s) {
        if ("pass".equals(s)) {
            return "通过";
        }
        if ("warn".equals(s)) {
            return "警告";
        }
        if ("fail".equals(s)) {
            return "未通过";
        }
        return s == null ? "-" : s;
    }

    static String classLabel(String c) {
        if ("standard_formula".equals(c)) {
            return "标准公式";
        }
        if ("mot_direct".equals(c)) {
            return ".mot 直接";
        }
        if ("derived".equals(c)) {
            return "派生";
        }
        if ("proxy".equals(c)) {
            return "代理";
        }
        if ("manual".equals(c)) {
            return "人工";
        }
        return dash(c);
    }

    private static String time(LocalDateTime t) {
        return t == null ? "-" : TIME.format(t);
    }

    private static String dash(String s) {
        return StrUtil.blankToDefault(s, "-");
    }

    private static String num(Object v) {
        if (v == null) {
            return "-";
        }
        if (v instanceof BigDecimal) {
            return ((BigDecimal) v).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(v);
    }

}
