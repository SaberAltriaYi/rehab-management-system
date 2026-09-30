package cn.iocoder.yudao.module.rehab.service.motion.report;

import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionMetricDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionScoreDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTrialDO;
import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 初评/复评对比（纯函数）。
 * <p>
 * 只报告数值差值以及是否超出 OpenCap 测量误差（旋转 4.5°、平移 12.3 mm）；不输出“改善/恶化”等临床判断，
 * 由治疗师结合情况解读。评分对比优先使用治疗师最终分，未审核时使用系统分并标注。
 */
public final class MotionComparator {

    private MotionComparator() {
    }

    public static Map<String, Object> compare(List<RehabMotionScoreDO> baseScores, List<RehabMotionScoreDO> curScores,
                                              List<RehabMotionTrialDO> baseTrials, List<RehabMotionMetricDO> baseMetrics,
                                              List<RehabMotionTrialDO> curTrials, List<RehabMotionMetricDO> curMetrics) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("scores", compareScores(baseScores, curScores));
        out.put("metrics", compareMetrics(aggregate(baseTrials, baseMetrics), aggregate(curTrials, curMetrics)));
        out.put("measurement_error", "旋转 " + RehabMotionConstants.MAE_ROTATION_DEG + "°，平移 "
                + RehabMotionConstants.MAE_TRANSLATION_M * 1000 + " mm（OpenCap 与光学动捕对比的平均绝对误差）");
        out.put("note", "差值在测量误差内时不应解读为变化；超出测量误差也需治疗师结合临床判断。");
        return out;
    }

    static List<Map<String, Object>> compareScores(List<RehabMotionScoreDO> base, List<RehabMotionScoreDO> cur) {
        Map<String, RehabMotionScoreDO> b = new LinkedHashMap<String, RehabMotionScoreDO>();
        for (RehabMotionScoreDO s : base) {
            b.put(key(s), s);
        }
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        Map<String, RehabMotionScoreDO> c = new LinkedHashMap<String, RehabMotionScoreDO>();
        for (RehabMotionScoreDO s : cur) {
            c.put(key(s), s);
        }
        java.util.Set<String> keys = new java.util.LinkedHashSet<String>(c.keySet());
        keys.addAll(b.keySet());
        for (String k : keys) {
            RehabMotionScoreDO x = b.get(k);
            RehabMotionScoreDO y = c.get(k);
            RehabMotionScoreDO any = y != null ? y : x;
            if ("NASM_EVIDENCE".equals(any.getScoringScheme())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("test_code", any.getTestCode());
            row.put("side", any.getSide());
            row.put("scheme", any.getScoringScheme());
            BigDecimal bv = value(x);
            BigDecimal cv = value(y);
            row.put("baseline", bv);
            row.put("baseline_source", source(x));
            row.put("current", cv);
            row.put("current_source", source(y));
            row.put("delta", bv != null && cv != null ? cv.subtract(bv) : null);
            rows.add(row);
        }
        return rows;
    }

    private static String key(RehabMotionScoreDO s) {
        return s.getFamily() + "|" + s.getTestCode() + "|" + s.getSide();
    }

    static BigDecimal value(RehabMotionScoreDO s) {
        if (s == null) {
            return null;
        }
        if (s.getFinalScore() != null && RehabMotionConstants.FINAL_REVIEWED.contains(s.getFinalStatus())) {
            return s.getFinalScore();
        }
        return s.getSystemScore();
    }

    static String source(RehabMotionScoreDO s) {
        if (s == null) {
            return null;
        }
        if (s.getFinalScore() != null && RehabMotionConstants.FINAL_REVIEWED.contains(s.getFinalStatus())) {
            return "final";
        }
        return s.getSystemScore() == null ? null : "system_unreviewed";
    }

    /** test|code|side|phase → 汇总值（同一测试多个有效 Trial 取均值；只用汇总行 rep=null） */
    static Map<String, double[]> aggregate(List<RehabMotionTrialDO> trials, List<RehabMotionMetricDO> metrics) {
        Map<Long, RehabMotionTrialDO> byId = new HashMap<Long, RehabMotionTrialDO>();
        for (RehabMotionTrialDO t : trials) {
            byId.put(t.getId(), t);
        }
        Map<String, double[]> acc = new TreeMap<String, double[]>();
        Map<String, String> units = new HashMap<String, String>();
        for (RehabMotionMetricDO m : metrics) {
            RehabMotionTrialDO t = byId.get(m.getTrialId());
            if (t == null || Boolean.FALSE.equals(t.getValid()) || m.getValueNum() == null || m.getRepNo() != null
                    || "fail".equals(t.getQcStatus())) {
                continue;
            }
            String k = t.getTestCode() + "|" + m.getCode() + "|" + m.getSide() + "|" + m.getPhase() + "|"
                    + (m.getUnit() == null ? "" : m.getUnit());
            double[] a = acc.get(k);
            if (a == null) {
                a = new double[2];
                acc.put(k, a);
            }
            a[0] += m.getValueNum().doubleValue();
            a[1] += 1;
        }
        return acc;
    }

    static List<Map<String, Object>> compareMetrics(Map<String, double[]> base, Map<String, double[]> cur) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, double[]> e : cur.entrySet()) {
            double[] b = base.get(e.getKey());
            if (b == null) {
                continue;
            }
            String[] parts = e.getKey().split("\\|", -1);
            double bv = b[0] / b[1];
            double cv = e.getValue()[0] / e.getValue()[1];
            double delta = cv - bv;
            String unit = parts[4];
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("test_code", parts[0]);
            row.put("code", parts[1]);
            row.put("side", parts[2]);
            row.put("phase", parts[3]);
            row.put("unit", unit);
            row.put("baseline", round(bv));
            row.put("current", round(cv));
            row.put("delta", round(delta));
            row.put("beyond_measurement_error", beyond(unit, delta));
            rows.add(row);
        }
        return rows;
    }

    /** @return null = 该单位没有可用的误差参考 */
    static Boolean beyond(String unit, double delta) {
        if ("deg".equals(unit)) {
            return Math.abs(delta) > RehabMotionConstants.MAE_ROTATION_DEG;
        }
        if ("m".equals(unit)) {
            return Math.abs(delta) > RehabMotionConstants.MAE_TRANSLATION_M;
        }
        if ("cm".equals(unit)) {
            return Math.abs(delta) > RehabMotionConstants.MAE_TRANSLATION_M * 100;
        }
        return null;
    }

    private static BigDecimal round(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
    }

}
