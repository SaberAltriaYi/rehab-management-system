package cn.iocoder.yudao.module.rehab.service.motion.ai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionMetricDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionRuleResultDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionScoreDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTrialDO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 构造发送给 AI 的去标识化载荷（纯函数）。
 * <p>
 * 只包含：测试代码、质控状态、评分（系统/最终）、规则结论及其证据 ID、少量关键指标数值与分类、局限性。
 * 不包含：姓名、电话、证件、病史、患者/评估数据库 ID、日期、自由文本备注、视频或原始数据。
 * 每条可被引用的证据都有稳定 ID，AI 输出的每条结论必须引用这些 ID。
 */
public final class MotionAiPayloadBuilder {

    /** 每个 trial 最多发送的指标数，控制 token 与暴露面 */
    public static final int MAX_METRICS_PER_TRIAL = 40;

    private MotionAiPayloadBuilder() {
    }

    public static final class Payload {
        public final Map<String, Object> data;
        public final Set<String> evidenceIds;
        public final Set<BigDecimal> scoreValues;
        public final String inputHash;

        Payload(Map<String, Object> data, Set<String> evidenceIds, Set<BigDecimal> scoreValues) {
            this.data = data;
            this.evidenceIds = evidenceIds;
            this.scoreValues = scoreValues;
            this.inputHash = DigestUtil.sha256Hex(JsonUtils.toJsonString(data));
        }
    }

    public static Payload build(List<RehabMotionTrialDO> trials, List<RehabMotionScoreDO> scores,
                                List<RehabMotionRuleResultDO> rules, List<RehabMotionMetricDO> metrics,
                                Collection<String> limitations) {
        Set<String> ids = new LinkedHashSet<String>();
        Set<BigDecimal> scoreValues = new HashSet<BigDecimal>();
        Map<String, Object> data = new LinkedHashMap<String, Object>();

        Map<Long, String> keyById = new LinkedHashMap<Long, String>();
        List<Map<String, Object>> trialList = new ArrayList<Map<String, Object>>();
        for (RehabMotionTrialDO t : trials) {
            keyById.put(t.getId(), t.getTrialKey());
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("trial_key", t.getTrialKey());
            m.put("test_code", t.getTestCode());
            m.put("side", t.getSide());
            m.put("condition", t.getConditionCode());
            m.put("valid", !Boolean.FALSE.equals(t.getValid()));
            m.put("pain_recorded", t.getPain() == null ? "unknown" : (t.getPain() ? "yes" : "no"));
            m.put("qc_status", t.getQcStatus());
            m.put("repetitions", t.getRepCount());
            trialList.add(m);
        }
        data.put("trials", trialList);

        List<Map<String, Object>> scoreList = new ArrayList<Map<String, Object>>();
        for (RehabMotionScoreDO s : scores) {
            String id = "s:" + s.getTestCode() + ":" + s.getSide();
            ids.add(id);
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", id);
            m.put("test_code", s.getTestCode());
            m.put("side", s.getSide());
            m.put("scheme", s.getScoringScheme());
            m.put("system_score", plain(s.getSystemScore()));
            m.put("system_status", s.getSystemStatus());
            m.put("final_score", plain(s.getFinalScore()));
            m.put("final_status", s.getFinalStatus());
            if (s.getSystemScore() != null) {
                scoreValues.add(s.getSystemScore().stripTrailingZeros());
            }
            if (s.getFinalScore() != null) {
                scoreValues.add(s.getFinalScore().stripTrailingZeros());
            }
            scoreList.add(m);
        }
        data.put("scores", scoreList);

        Set<String> referencedMetricIds = new HashSet<String>();
        List<Map<String, Object>> ruleList = new ArrayList<Map<String, Object>>();
        for (RehabMotionRuleResultDO r : rules) {
            if (r.getOutcome() == null) {
                continue;
            }
            ids.add(r.getRuleKey());
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", r.getRuleKey());
            m.put("rule_id", r.getRuleId());
            m.put("test_code", r.getTestCode());
            m.put("side", r.getSide());
            m.put("label", r.getLabel());
            m.put("outcome", r.getOutcome());
            m.put("decided_by", r.getDecidedBy());
            m.put("threshold_status", r.getThresholdStatus());
            ruleList.add(m);
            collectMetricIds(r.getEvidenceJson(), referencedMetricIds);
        }
        data.put("rule_results", ruleList);

        Map<Long, Integer> perTrial = new LinkedHashMap<Long, Integer>();
        List<Map<String, Object>> metricList = new ArrayList<Map<String, Object>>();
        for (RehabMotionMetricDO m : metrics) {
            if (m.getValueNum() == null || !referencedMetricIds.contains(m.getMetricKey())) {
                continue;
            }
            Integer n = perTrial.get(m.getTrialId());
            if (n != null && n >= MAX_METRICS_PER_TRIAL) {
                continue;
            }
            perTrial.put(m.getTrialId(), n == null ? 1 : n + 1);
            ids.add(m.getMetricKey());
            Map<String, Object> x = new LinkedHashMap<String, Object>();
            x.put("id", m.getMetricKey());
            x.put("trial_key", keyById.get(m.getTrialId()));
            x.put("code", m.getCode());
            x.put("side", m.getSide());
            x.put("phase", m.getPhase());
            x.put("value", plain(m.getValueNum()));
            x.put("unit", m.getUnit());
            x.put("classification", m.getClassification());
            x.put("validation_status", m.getValidationStatus());
            metricList.add(x);
        }
        data.put("metrics", metricList);
        data.put("limitations", new ArrayList<String>(limitations));
        return new Payload(data, ids, scoreValues);
    }

    static BigDecimal plain(BigDecimal v) {
        return v == null ? null : new BigDecimal(v.stripTrailingZeros().toPlainString());
    }

    @SuppressWarnings("unchecked")
    private static void collectMetricIds(String evidenceJson, Set<String> out) {
        if (StrUtil.isBlank(evidenceJson)) {
            return;
        }
        Object parsed;
        try {
            parsed = JsonUtils.parseObject(evidenceJson, Object.class);
        } catch (RuntimeException ex) {
            return;
        }
        walk(parsed, out);
    }

    @SuppressWarnings("unchecked")
    private static void walk(Object node, Set<String> out) {
        if (node instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) node).entrySet()) {
                if ("metric_id".equals(e.getKey()) && e.getValue() instanceof String) {
                    out.add((String) e.getValue());
                } else if ("metric_ids".equals(e.getKey()) && e.getValue() instanceof List) {
                    for (Object id : (List<Object>) e.getValue()) {
                        if (id instanceof String) {
                            out.add((String) id);
                        }
                    }
                } else {
                    walk(e.getValue(), out);
                }
            }
        } else if (node instanceof List) {
            for (Object o : (List<Object>) node) {
                walk(o, out);
            }
        }
    }

}
