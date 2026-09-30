package cn.iocoder.yudao.module.rehab.service.motion;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionMetricDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionRuleResultDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionScoreDO;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实引擎输出（OpenCap LaiUhlrich2022 样例经 motion 引擎计算，指标已裁剪）→ 数据库行映射。
 * 按字段名解析；缺字段降级为 null，绝不补 0。
 */
class MotionResultMapperTest {

    @SuppressWarnings("unchecked")
    static Map<String, Object> sample() throws IOException {
        InputStream in = MotionResultMapperTest.class.getResourceAsStream("/motion/engine-result-sample.json");
        assertNotNull(in, "测试资源缺失");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return JsonUtils.parseObject(new String(out.toByteArray(), StandardCharsets.UTF_8), Map.class);
        } finally {
            in.close();
        }
    }

    private static Map<String, Long> ids() {
        Map<String, Long> ids = new HashMap<String, Long>();
        ids.put("ds1", 11L);
        ids.put("hsl1", 12L);
        ids.put("hsr1", 13L);
        return ids;
    }

    private static RehabMotionScoreDO score(List<RehabMotionScoreDO> scores, String test) {
        for (RehabMotionScoreDO s : scores) {
            if (test.equals(s.getTestCode())) {
                return s;
            }
        }
        fail("missing score " + test);
        return null;
    }

    @Test
    void mapsVersionsTrialsMetricsRulesAndScores() throws IOException {
        MotionResultMapper.Mapped m = MotionResultMapper.map(sample(), 100L, 4, ids());
        assertEquals("motion-engine/1.0.0", m.getEngineVersion());
        assertEquals("motion-rules/1.0.0", m.getRuleVersion());
        assertEquals("motion-result/1.0.0", m.getSchemaVersion());
        assertEquals("pass", m.getSessionQualityStatus());
        assertTrue(m.getProtocols().size() > 0);

        assertEquals(Arrays.asList("ds1", "hsl1", "hsr1"), new ArrayList<String>(m.getTrialUpdates().keySet()));
        assertEquals(Long.valueOf(11L), m.getTrialUpdates().get("ds1").getId());
        assertEquals("pass", m.getTrialUpdates().get("ds1").getQcStatus());
        assertEquals(Integer.valueOf(3), m.getTrialUpdates().get("ds1").getRepCount());
        // 未分析的 trial：重复次数未知 → null，而不是 0
        assertNull(m.getTrialUpdates().get("hsl1").getRepCount());

        assertEquals(30, m.getMetrics().size());
        for (RehabMotionMetricDO x : m.getMetrics()) {
            assertEquals(Long.valueOf(11L), x.getTrialId());
            assertEquals(Integer.valueOf(4), x.getAnalysisRevision());
            assertTrue(x.getMetricKey().startsWith("m:ds1:"));
            assertNotNull(x.getClassification(), "每个指标都必须带证据等级分类");
        }

        assertEquals(16, m.getRuleResults().size());
        for (RehabMotionRuleResultDO r : m.getRuleResults()) {
            assertTrue(r.getRuleKey().startsWith("r:"));
            assertNotNull(r.getTrialId(), "规则结果必须可追溯到 trial");
            assertNotNull(r.getEvidenceJson());
        }

        assertEquals(7, m.getScores().size(), "FMS 7 项都要有评分行（含待定）");
        RehabMotionScoreDO ds = score(m.getScores(), "FMS_DEEP_SQUAT");
        assertNull(ds.getSystemScore(), "深蹲缺抬脚跟试次且为候选判定 → 不自动给分");
        assertEquals("pending_manual", ds.getSystemStatus());
        RehabMotionScoreDO hs = score(m.getScores(), "FMS_HURDLE_STEP");
        assertEquals(0, hs.getSystemScore().compareTo(BigDecimal.ZERO), "左侧疼痛 → 取低侧 0 分");
        assertEquals("pain_zero", hs.getSystemStatus());
        assertTrue(hs.getEvidenceJson().contains("hsl1") && hs.getEvidenceJson().contains("HS-02"));
        RehabMotionScoreDO il = score(m.getScores(), "FMS_INLINE_LUNGE");
        assertNull(il.getSystemScore());
        assertEquals("pending_trial", il.getSystemStatus());
        for (RehabMotionScoreDO s : m.getScores()) {
            assertEquals("overall", s.getSide());
            assertEquals("FMS_0_3", s.getScoringScheme());
            assertNull(s.getFinalScore(), "映射阶段绝不写最终分");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingFieldsDegradeToNullNeverZero() throws IOException {
        Map<String, Object> r = sample();
        Map<String, Object> ds1 = ((List<Map<String, Object>>) r.get("trials")).get(0);
        ds1.remove("quality");
        ds1.remove("phase_detection");
        Map<String, Object> metric = ((List<Map<String, Object>>) ds1.get("metrics")).get(0);
        metric.remove("value");
        metric.put("unavailable_reason", "SIGNAL_MISSING:pelvis_tilt");
        r.remove("session_quality");
        r.remove("nasm");
        r.remove("less");
        MotionResultMapper.Mapped m = MotionResultMapper.map(r, 100L, 1, ids());
        assertNull(m.getSessionQualityStatus());
        assertNull(m.getTrialUpdates().get("ds1").getQcStatus());
        assertNull(m.getTrialUpdates().get("ds1").getRepCount());
        assertNull(m.getMetrics().get(0).getValueNum());
        assertEquals("SIGNAL_MISSING:pelvis_tilt", m.getMetrics().get(0).getUnavailableReason());
        assertEquals(7, m.getScores().size());

        MotionResultMapper.Mapped empty = MotionResultMapper.map(new LinkedHashMap<String, Object>(), 1L, 1,
                new HashMap<String, Long>());
        assertTrue(empty.getScores().isEmpty());
        assertTrue(empty.getMetrics().isEmpty());
    }

    @Test
    void lessAndTuckJumpMapWithTheirOwnSchemes() {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        Map<String, Object> less = new LinkedHashMap<String, Object>();
        less.put("status", "pending_rating");
        less.put("final_score", null);
        List<Map<String, Object>> trials = new ArrayList<Map<String, Object>>();
        for (String key : new String[]{"l1", "l2", "l3"}) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("code", "LESS-01");
            item.put("label", "IC 膝屈曲");
            Map<String, Object> cand = new LinkedHashMap<String, Object>();
            cand.put("candidate_error", 1);
            cand.put("rule", "knee flexion at IC < 30°");
            item.put("candidate", cand);
            Map<String, Object> t = new LinkedHashMap<String, Object>();
            t.put("trial_key", key);
            t.put("items", Arrays.asList(item));
            trials.add(t);
        }
        less.put("trials", trials);
        r.put("less", less);
        Map<String, Object> tj = new LinkedHashMap<String, Object>();
        Map<String, Object> protocol = new LinkedHashMap<String, Object>();
        protocol.put("variant", "TJA_MYER_2008_DICHOTOMOUS");
        tj.put("protocol", protocol);
        tj.put("trial_key", "tj1");
        tj.put("total", null);
        tj.put("total_status", "pending_rating");
        Map<String, Object> noise = new LinkedHashMap<String, Object>();
        noise.put("code", "TJ-07");
        noise.put("label", "落地声音过大");
        noise.put("rating", null);
        tj.put("items", Arrays.asList(noise));
        r.put("tuck_jump", tj);
        MotionResultMapper.Mapped m = MotionResultMapper.map(r, 1L, 1, new HashMap<String, Long>());
        RehabMotionScoreDO l = score(m.getScores(), "LESS");
        assertEquals("LESS_MEAN_OF_3", l.getScoringScheme());
        assertNull(l.getSystemScore(), "候选错误不等于评分：3 次试验未经人工确认不给 LESS 分");
        RehabMotionScoreDO t = score(m.getScores(), "TUCK_JUMP");
        assertEquals("TJA_MYER_2008_DICHOTOMOUS", t.getScoringScheme());
        assertNull(t.getSystemScore());
        int lessRules = 0;
        for (RehabMotionRuleResultDO rr : m.getRuleResults()) {
            if ("LESS".equals(rr.getTestCode())) {
                lessRules++;
                assertEquals("candidate_1", rr.getOutcome());
                assertEquals("auto_candidate", rr.getDecidedBy());
            } else {
                assertEquals("pending_rating", rr.getOutcome(), "落地声音只能人工评定");
            }
        }
        assertEquals(3, lessRules);
    }

}
