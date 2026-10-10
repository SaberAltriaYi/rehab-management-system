package cn.iocoder.yudao.module.rehab.service.motion.ai;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionMetricDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionRuleResultDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionScoreDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTrialDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 只解释：结构固定、每条结论引用证据、不得改写分数、不得诊断；载荷去标识化；失败时模板降级。
 */
class MotionAiGuardrailTest {

    private static Map<String, Object> claim(String text, String... refs) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("text", text);
        m.put("evidence_refs", Arrays.asList(refs));
        return m;
    }

    private static Map<String, Object> output(List<Map<String, Object>> findings) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("therapist_summary", "深蹲系统分 2 分，待治疗师确认。");
        m.put("findings", findings);
        m.put("training_suggestions", new ArrayList<Object>());
        m.put("patient_summary", "本次动作评估已完成，请与治疗师沟通结果。");
        m.put("limitations", Collections.singletonList("OpenCap 与光学动捕存在约 4.5° 误差"));
        return m;
    }

    @Test
    void schemaRejectsExtraFieldsSuchAsScores() {
        Map<String, Object> out = output(new ArrayList<Map<String, Object>>());
        assertNull(MotionAiOutputValidator.structural(out));
        out.put("scores", Collections.singletonMap("FMS_DEEP_SQUAT", 3));
        assertTrue(MotionAiOutputValidator.structural(out).startsWith("unexpected_field"));
        Map<String, Object> missing = output(new ArrayList<Map<String, Object>>());
        missing.remove("limitations");
        assertEquals("missing_field:limitations", MotionAiOutputValidator.structural(missing));
    }

    @Test
    void claimsMustCiteKnownEvidenceAndCannotChangeScores() {
        Set<String> ids = new HashSet<String>(Arrays.asList("s:FMS_DEEP_SQUAT:overall", "m:ds1:knee:right:bottom:1"));
        Set<BigDecimal> scores = new HashSet<BigDecimal>(Collections.singletonList(BigDecimal.valueOf(2)));
        List<Map<String, Object>> findings = new ArrayList<Map<String, Object>>();
        findings.add(claim("深蹲系统分为 2 分，底部膝屈曲角度见证据。", "s:FMS_DEEP_SQUAT:overall", "m:ds1:knee:right:bottom:1"));
        findings.add(claim("建议将深蹲改为 3 分。", "s:FMS_DEEP_SQUAT:overall"));
        findings.add(claim("没有证据的结论。"));
        findings.add(claim("引用不存在的证据。", "m:fake"));
        findings.add(claim("诊断为髌股关节病变。", "s:FMS_DEEP_SQUAT:overall"));
        findings.add(claim("该指标异常，属于高风险。", "m:ds1:knee:right:bottom:1"));
        MotionAiOutputValidator.Result r = MotionAiOutputValidator.validate(output(findings), ids, scores);
        assertEquals(1, r.keptClaims);
        assertEquals(5, r.droppedReasons.size());
        assertTrue(r.droppedReasons.get(0).endsWith("score_mismatch"), r.droppedReasons.toString());
        assertTrue(r.droppedReasons.get(1).endsWith("no_evidence"));
        assertTrue(r.droppedReasons.get(2).endsWith("unknown_evidence"));
        assertTrue(r.droppedReasons.get(3).contains("forbidden"));
        assertTrue(r.droppedReasons.get(4).contains("forbidden"));
        assertEquals("深蹲系统分 2 分，待治疗师确认。", r.cleaned.get("therapist_summary"));
        assertFalse(r.cleaned.containsKey("scores"));
    }

    @Test
    void payloadIsDeidentifiedAndOnlyCarriesReferencedMetrics() {
        RehabMotionTrialDO trial = RehabMotionTrialDO.builder().id(1L).trialKey("ds1").testCode("FMS_DEEP_SQUAT")
                .side("bilateral").pain(false).qcStatus("pass").repCount(3).opencapTrialName("zhangsan_squat").build();
        RehabMotionScoreDO score = RehabMotionScoreDO.builder().testCode("FMS_DEEP_SQUAT").side("overall")
                .scoringScheme("FMS_0_3").systemScore(BigDecimal.valueOf(2)).finalStatus("pending").build();
        RehabMotionRuleResultDO rule = RehabMotionRuleResultDO.builder().ruleKey("r:ds1:DS-01:bilateral").ruleId("DS-01")
                .testCode("FMS_DEEP_SQUAT").outcome("candidate_met")
                .evidenceJson("[{\"metric_id\":\"m:ds1:knee:right:bottom:1\"}]").build();
        RehabMotionMetricDO used = RehabMotionMetricDO.builder().trialId(1L).metricKey("m:ds1:knee:right:bottom:1")
                .code("knee").valueNum(new BigDecimal("95.5")).unit("deg").build();
        RehabMotionMetricDO unused = RehabMotionMetricDO.builder().trialId(1L).metricKey("m:ds1:other:none:all:all")
                .code("other").valueNum(BigDecimal.ONE).build();
        MotionAiPayloadBuilder.Payload p = MotionAiPayloadBuilder.build(Collections.singletonList(trial),
                Collections.singletonList(score), Collections.singletonList(rule), Arrays.asList(used, unused),
                Collections.singletonList("限制"));
        String json = JsonUtils.toJsonString(p.data);
        assertFalse(json.contains("zhangsan"), "不得发送 OpenCap 试次名（可能含姓名）");
        assertFalse(json.contains("m:ds1:other"), "只发送被规则引用的指标");
        assertTrue(p.evidenceIds.contains("m:ds1:knee:right:bottom:1"));
        assertTrue(p.evidenceIds.contains("s:FMS_DEEP_SQUAT:overall"));
        assertEquals(64, p.inputHash.length());
    }

    @Test
    void fallbackDraftWorksWithoutAiAndCitesEvidence() {
        RehabMotionScoreDO score = RehabMotionScoreDO.builder().testCode("FMS_HURDLE_STEP").side("overall")
                .scoringScheme("FMS_0_3").systemScore(BigDecimal.ZERO).systemStatus("pain_zero").finalStatus("pending").build();
        MotionAiPayloadBuilder.Payload p = MotionAiPayloadBuilder.build(new ArrayList<RehabMotionTrialDO>(),
                Collections.singletonList(score), new ArrayList<RehabMotionRuleResultDO>(),
                new ArrayList<RehabMotionMetricDO>(), Collections.singletonList("限制"));
        Map<String, Object> draft = MotionAiFallback.build(p.data, "ai_disabled");
        assertNull(MotionAiOutputValidator.structural(stripRefs(draft)));
        String text = MotionAiFallback.render(draft);
        assertTrue(text.length() > 0);
        MotionAiOutputValidator.Result r = MotionAiOutputValidator.validate(stripRefs(draft), p.evidenceIds, p.scoreValues);
        assertTrue(r.droppedReasons.isEmpty(), "模板草稿自身必须通过同一套校验：" + r.droppedReasons);
    }

    private static Map<String, Object> stripRefs(Map<String, Object> draft) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>(draft);
        copy.keySet().retainAll(Arrays.asList("therapist_summary", "findings", "training_suggestions", "patient_summary",
                "limitations"));
        return copy;
    }

}
