package cn.iocoder.yudao.module.rehab.service.motion;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionMetricDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionRuleResultDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionScoreDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTrialDO;
import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把引擎结果 JSON 映射为规范化表行（纯函数）。系统分只取引擎给出的值；缺失保持 null，不补 0。
 */
public final class MotionResultMapper {

    private MotionResultMapper() {
    }

    @Data
    public static class Mapped {
        private String engineVersion;
        private String ruleVersion;
        private String schemaVersion;
        private String sessionQualityStatus;
        private Map<String, Object> protocols = new LinkedHashMap<String, Object>();
        private List<RehabMotionMetricDO> metrics = new ArrayList<RehabMotionMetricDO>();
        private List<RehabMotionRuleResultDO> ruleResults = new ArrayList<RehabMotionRuleResultDO>();
        private List<RehabMotionScoreDO> scores = new ArrayList<RehabMotionScoreDO>();
        /** trialKey -> 仅含分析字段的更新对象 */
        private Map<String, RehabMotionTrialDO> trialUpdates = new LinkedHashMap<String, RehabMotionTrialDO>();
    }

    public static Mapped map(Map<String, Object> result, Long assessmentId, Integer revision,
                             Map<String, Long> trialIdByKey) {
        Mapped out = new Mapped();
        out.setEngineVersion(str(result.get("engine_version")));
        out.setRuleVersion(str(result.get("rule_engine_version")));
        out.setSchemaVersion(str(result.get("schema_version")));
        out.setSessionQualityStatus(str(map(result.get("session_quality")).get("status")));
        out.setProtocols(map(result.get("protocols")));

        for (Map<String, Object> t : list(result.get("trials"))) {
            String key = str(t.get("trial_key"));
            Long trialId = trialIdByKey.get(key);
            Map<String, Object> quality = map(t.get("quality"));
            Map<String, Object> detection = map(t.get("phase_detection"));
            RehabMotionTrialDO upd = new RehabMotionTrialDO();
            upd.setId(trialId);
            upd.setQcStatus(str(quality.get("status")));
            upd.setQcIssuesJson(JsonUtils.toJsonString(list(quality.get("issues"))));
            upd.setPhasesJson(JsonUtils.toJsonString(list(t.get("phases"))));
            upd.setPhaseDetectionJson(JsonUtils.toJsonString(detection));
            upd.setRepCount(toInt(detection.get("repetitions")));
            upd.setDurationS(decimal(detection.get("trial_duration_s"), 3));
            out.getTrialUpdates().put(key, upd);
            for (Map<String, Object> m : list(t.get("metrics"))) {
                out.getMetrics().add(RehabMotionMetricDO.builder()
                        .assessmentId(assessmentId)
                        .trialId(trialId)
                        .metricKey(StrUtil.maxLength(str(m.get("id")), 188))
                        .code(StrUtil.maxLength(str(m.get("code")), 61))
                        .label(StrUtil.maxLength(str(m.get("label")), 125))
                        .side(str(m.get("side")))
                        .phase(StrUtil.maxLength(str(m.get("phase")), 29))
                        .repNo(toInt(m.get("rep")))
                        .valueNum(decimal(m.get("value"), 6))
                        .unit(StrUtil.maxLength(str(m.get("unit")), 13))
                        .classification(str(m.get("classification")))
                        .validationStatus(StrUtil.maxLength(str(m.get("validation_status")), 45))
                        .unavailableReason(StrUtil.maxLength(str(m.get("unavailable_reason")), 125))
                        .sourceSignals(StrUtil.maxLength(joinList(m.get("source_signals")), 497))
                        .method(StrUtil.maxLength(str(m.get("method")), 497))
                        .analysisRevision(revision)
                        .build());
            }
        }

        mapFms(map(result.get("fms")), assessmentId, revision, trialIdByKey, out);
        mapNasm(map(result.get("nasm")), assessmentId, revision, trialIdByKey, out);
        mapYbt(map(result.get("ybt")), assessmentId, revision, out);
        mapTuckJump(map(result.get("tuck_jump")), assessmentId, revision, trialIdByKey, out);
        mapLess(map(result.get("less")), assessmentId, revision, trialIdByKey, out);
        return out;
    }

    private static void mapFms(Map<String, Object> fms, Long aid, Integer rev, Map<String, Long> ids, Mapped out) {
        if (fms.isEmpty()) {
            return;
        }
        for (Map<String, Object> r : list(fms.get("rule_results"))) {
            out.getRuleResults().add(RehabMotionRuleResultDO.builder()
                    .assessmentId(aid)
                    .trialId(ids.get(str(r.get("trial_key"))))
                    .ruleKey(StrUtil.maxLength(str(r.get("id")), 188))
                    .ruleId(StrUtil.maxLength(str(r.get("rule_id")), 32))
                    .family(RehabMotionConstants.FAMILY_FMS)
                    .testCode(str(r.get("test_code")))
                    .side(str(r.get("side")))
                    .label(StrUtil.maxLength(str(r.get("label")), 252))
                    .outcome(str(r.get("outcome")))
                    .autoOutcome(outcomeStr(r.get("auto_outcome")))
                    .manualOutcome(outcomeStr(r.get("manual_outcome")))
                    .decidedBy(str(r.get("decided_by")))
                    .autoPolicy(str(r.get("auto_policy")))
                    .thresholdStatus(StrUtil.maxLength(str(r.get("threshold_status")), 45))
                    .criterion(StrUtil.maxLength(str(r.get("criterion")), 497))
                    .source(StrUtil.maxLength(str(r.get("source")), 252))
                    .evidenceJson(JsonUtils.toJsonString(list(r.get("evidence"))))
                    .note(StrUtil.maxLength(str(r.get("note")), 497))
                    .analysisRevision(rev)
                    .build());
        }
        Map<String, Set<String>> ruleIdsByTest = new LinkedHashMap<String, Set<String>>();
        Map<String, Set<String>> trialsByTest = new LinkedHashMap<String, Set<String>>();
        for (Map<String, Object> t : list(fms.get("trials"))) {
            String test = str(t.get("test_code"));
            if (!ruleIdsByTest.containsKey(test)) {
                ruleIdsByTest.put(test, new LinkedHashSet<String>());
                trialsByTest.put(test, new LinkedHashSet<String>());
            }
            for (Object id : list0(t.get("rule_ids"))) {
                ruleIdsByTest.get(test).add(String.valueOf(id));
            }
            trialsByTest.get(test).add(str(t.get("trial_key")));
        }
        for (Map<String, Object> item : list(fms.get("items"))) {
            String test = str(item.get("test_code"));
            Map<String, Object> evidence = new LinkedHashMap<String, Object>();
            evidence.put("rule_ids", ruleIdsByTest.containsKey(test) ? ruleIdsByTest.get(test) : Collections.emptySet());
            evidence.put("trial_keys", trialsByTest.containsKey(test) ? trialsByTest.get(test) : Collections.emptySet());
            out.getScores().add(score(aid, rev, RehabMotionConstants.FAMILY_FMS, test, "overall", "FMS_0_3",
                    decimal(item.get("system_score"), 3), decimal(item.get("provisional_score"), 3),
                    str(item.get("status")), item, evidence));
        }
    }

    private static void mapNasm(Map<String, Object> nasm, Long aid, Integer rev, Map<String, Long> ids, Mapped out) {
        for (Map<String, Object> test : list(nasm.get("tests"))) {
            String testCode = str(test.get("test_code"));
            List<String> ruleKeys = new ArrayList<String>();
            List<Object> trialKeys = list0(test.get("trial_keys"));
            Long trialId = trialKeys.size() == 1 ? ids.get(String.valueOf(trialKeys.get(0))) : null;
            for (Map<String, Object> cp : list(test.get("checkpoints"))) {
                String code = str(cp.get("code"));
                String key = "n:" + testCode + ":" + code;
                ruleKeys.add(key);
                Map<String, Object> obs = map(cp.get("therapist_observation"));
                out.getRuleResults().add(RehabMotionRuleResultDO.builder()
                        .assessmentId(aid)
                        .trialId(trialId)
                        .ruleKey(key)
                        .ruleId(StrUtil.maxLength(code, 32))
                        .family(RehabMotionConstants.FAMILY_NASM)
                        .testCode(testCode)
                        .side("bilateral")
                        .label(StrUtil.maxLength(str(cp.get("label")), 252))
                        .outcome(nasmOutcome(obs))
                        .manualOutcome(obs.isEmpty() ? null : StrUtil.maxLength(compact(obs), 32))
                        .decidedBy(obs.isEmpty() ? null : "manual")
                        .autoPolicy("evidence_only")
                        .thresholdStatus(StrUtil.maxLength(str(cp.get("classification")), 45))
                        .criterion(StrUtil.maxLength(str(cp.get("criterion")), 497))
                        .source(StrUtil.maxLength(str(cp.get("source")), 252))
                        .evidenceJson(JsonUtils.toJsonString(list(cp.get("evidence"))))
                        .note(StrUtil.maxLength(str(cp.get("limitations")), 497))
                        .analysisRevision(rev)
                        .build());
            }
            Map<String, Object> evidence = new LinkedHashMap<String, Object>();
            evidence.put("rule_ids", ruleKeys);
            evidence.put("trial_keys", trialKeys);
            out.getScores().add(score(aid, rev, RehabMotionConstants.FAMILY_NASM, testCode, "overall",
                    "NASM_EVIDENCE", null, null, "evidence_only", test, evidence));
        }
    }

    private static void mapYbt(Map<String, Object> ybt, Long aid, Integer rev, Mapped out) {
        if (ybt.isEmpty()) {
            return;
        }
        Map<String, Object> composite = map(ybt.get("composite_percent"));
        Map<String, Object> limb = map(ybt.get("limb_length_cm"));
        for (String side : new String[]{"left", "right"}) {
            BigDecimal value = decimal(composite.get(side), 3);
            String status = value != null ? "computed" : (limb.get(side) == null ? "LIMB_LENGTH_MISSING" : "pending_data");
            Map<String, Object> evidence = new LinkedHashMap<String, Object>();
            evidence.put("formula", "composite = (ANT+PM+PL) / (3 × limb length) × 100");
            out.getScores().add(score(aid, rev, RehabMotionConstants.FAMILY_YBT, "YBT_LQ", side,
                    "YBT_COMPOSITE_PERCENT", value, null, status, ybt, evidence));
        }
    }

    private static void mapTuckJump(Map<String, Object> tj, Long aid, Integer rev, Map<String, Long> ids, Mapped out) {
        if (tj.isEmpty()) {
            return;
        }
        Long trialId = ids.get(str(tj.get("trial_key")));
        List<String> keys = new ArrayList<String>();
        for (Map<String, Object> item : list(tj.get("items"))) {
            String code = str(item.get("code"));
            String key = "t:" + code;
            keys.add(key);
            Object rating = item.get("rating");
            Map<String, Object> cand = map(item.get("candidate"));
            out.getRuleResults().add(RehabMotionRuleResultDO.builder()
                    .assessmentId(aid).trialId(trialId).ruleKey(key).ruleId(code)
                    .family(RehabMotionConstants.FAMILY_TJA).testCode("TUCK_JUMP").side("bilateral")
                    .label(StrUtil.maxLength(str(item.get("label")), 252))
                    .outcome(rating == null ? (cand.isEmpty() ? "pending_rating" : "candidate") : "rated_" + rating)
                    .manualOutcome(rating == null ? null : String.valueOf(rating))
                    .decidedBy(rating == null ? null : "manual")
                    .autoPolicy(StrUtil.maxLength(str(item.get("decision_policy")), 32))
                    .thresholdStatus(StrUtil.maxLength(str(item.get("classification")), 45))
                    .evidenceJson(JsonUtils.toJsonString(item.get("evidence") == null ? cand : item.get("evidence")))
                    .analysisRevision(rev)
                    .build());
        }
        Map<String, Object> protocol = map(tj.get("protocol"));
        Map<String, Object> evidence = new LinkedHashMap<String, Object>();
        evidence.put("rule_ids", keys);
        out.getScores().add(score(aid, rev, RehabMotionConstants.FAMILY_TJA, "TUCK_JUMP", "overall",
                StrUtil.blankToDefault(str(protocol.get("variant")), "TJA_MODIFIED_0_2"),
                decimal(tj.get("total"), 3), null, str(tj.get("total_status")), tj, evidence));
    }

    private static void mapLess(Map<String, Object> less, Long aid, Integer rev, Map<String, Long> ids, Mapped out) {
        if (less.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<String>();
        for (Map<String, Object> trial : list(less.get("trials"))) {
            String trialKey = str(trial.get("trial_key"));
            for (Map<String, Object> item : list(trial.get("items"))) {
                String code = str(item.get("code"));
                String key = "l:" + trialKey + ":" + code;
                keys.add(key);
                Object rating = item.get("confirmed_rating");
                Map<String, Object> cand = map(item.get("candidate"));
                Object candErr = cand.get("candidate_error");
                out.getRuleResults().add(RehabMotionRuleResultDO.builder()
                        .assessmentId(aid).trialId(ids.get(trialKey)).ruleKey(StrUtil.maxLength(key, 188))
                        .ruleId(code).family(RehabMotionConstants.FAMILY_LESS).testCode("LESS").side("bilateral")
                        .label(StrUtil.maxLength(str(item.get("label")), 252))
                        .outcome(rating != null ? "rated_" + rating
                                : (candErr != null ? "candidate_" + candErr : "pending_rating"))
                        .autoOutcome(candErr == null ? null : String.valueOf(candErr))
                        .manualOutcome(rating == null ? null : String.valueOf(rating))
                        .decidedBy(rating == null ? (candErr == null ? null : "auto_candidate") : "manual")
                        .autoPolicy("candidate")
                        .criterion(StrUtil.maxLength(str(cand.get("rule")), 497))
                        .evidenceJson(JsonUtils.toJsonString(cand))
                        .analysisRevision(rev)
                        .build());
            }
        }
        Map<String, Object> evidence = new LinkedHashMap<String, Object>();
        evidence.put("rule_ids", keys);
        out.getScores().add(score(aid, rev, RehabMotionConstants.FAMILY_LESS, "LESS", "overall", "LESS_MEAN_OF_3",
                decimal(less.get("final_score"), 3), null, str(less.get("status")), less, evidence));
    }

    private static RehabMotionScoreDO score(Long aid, Integer rev, String family, String test, String side,
                                            String scheme, BigDecimal system, BigDecimal provisional, String status,
                                            Object detail, Object evidence) {
        return RehabMotionScoreDO.builder()
                .assessmentId(aid).family(family).testCode(test).side(side).scoringScheme(scheme)
                .systemScore(system).provisionalScore(provisional)
                .systemStatus(StrUtil.maxLength(status, 45))
                .detailJson(JsonUtils.toJsonString(detail))
                .evidenceJson(JsonUtils.toJsonString(evidence))
                .analysisRevision(rev)
                .build();
    }

    private static String nasmOutcome(Map<String, Object> obs) {
        if (obs.isEmpty()) {
            return "evidence_only";
        }
        boolean anyTrue = false;
        boolean allKnown = true;
        for (Object v : obs.values()) {
            Object present = v instanceof Map ? ((Map<?, ?>) v).get("present") : v;
            if (Boolean.TRUE.equals(present)) {
                anyTrue = true;
            } else if (!Boolean.FALSE.equals(present)) {
                allKnown = false;
            }
        }
        if (anyTrue) {
            return "present";
        }
        return allKnown ? "absent" : "evidence_only";
    }

    private static String compact(Map<String, Object> obs) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : obs.entrySet()) {
            Object v = e.getValue() instanceof Map ? ((Map<?, ?>) e.getValue()).get("present") : e.getValue();
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(e.getKey().charAt(0)).append('=').append(Boolean.TRUE.equals(v) ? 'Y' : Boolean.FALSE.equals(v) ? 'N' : '?');
        }
        return sb.toString();
    }

    // ===== 工具 =====

    static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    static String outcomeStr(Object o) {
        if (o == null) {
            return null;
        }
        return StrUtil.maxLength(String.valueOf(o), 29);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Collections.<String, Object>emptyMap();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> list(Object o) {
        if (!(o instanceof Collection)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Object x : (Collection<Object>) o) {
            if (x instanceof Map) {
                out.add((Map<String, Object>) x);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<Object> list0(Object o) {
        return o instanceof List ? (List<Object>) o : Collections.emptyList();
    }

    static String joinList(Object o) {
        if (o instanceof Collection) {
            return StrUtil.join(",", (Collection<?>) o);
        }
        return str(o);
    }

    static Integer toInt(Object o) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        return null;
    }

    static BigDecimal decimal(Object o, int scale) {
        if (!(o instanceof Number)) {
            return null;
        }
        double d = ((Number) o).doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d) || Math.abs(d) >= 1e11) {
            return null;
        }
        return BigDecimal.valueOf(d).setScale(scale, RoundingMode.HALF_UP);
    }

}
