package cn.iocoder.yudao.module.rehab.service.motion;

import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionScoreDO;
import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评分合并与取值范围（纯函数）。
 * <ul>
 *   <li>重新分析只更新系统字段；治疗师最终分、原因、审核人永不被系统覆盖。</li>
 *   <li>已审核项的系统分发生变化 → 标记 system_score_changed 且 final_status=needs_recheck，签署前须重新确认。</li>
 *   <li>FMS 总分仅在 7 项全部审核后计算（P9），且只使用最终分。</li>
 * </ul>
 */
public final class MotionScoreRules {

    private MotionScoreRules() {
    }

    public static final class MergePlan {
        public final List<RehabMotionScoreDO> inserts = new ArrayList<RehabMotionScoreDO>();
        public final List<RehabMotionScoreDO> updates = new ArrayList<RehabMotionScoreDO>();
        public final List<Long> deletes = new ArrayList<Long>();
    }

    public static String key(RehabMotionScoreDO s) {
        return s.getFamily() + "|" + s.getTestCode() + "|" + s.getSide();
    }

    public static MergePlan merge(List<RehabMotionScoreDO> existing, List<RehabMotionScoreDO> fresh) {
        MergePlan plan = new MergePlan();
        Map<String, RehabMotionScoreDO> old = new HashMap<String, RehabMotionScoreDO>();
        for (RehabMotionScoreDO s : existing) {
            old.put(key(s), s);
        }
        for (RehabMotionScoreDO s : fresh) {
            RehabMotionScoreDO prev = old.remove(key(s));
            if (prev == null) {
                s.setFinalStatus(RehabMotionConstants.FINAL_PENDING);
                s.setSystemScoreChanged(false);
                plan.inserts.add(s);
                continue;
            }
            RehabMotionScoreDO upd = new RehabMotionScoreDO();
            upd.setId(prev.getId());
            upd.setScoringScheme(s.getScoringScheme());
            upd.setSystemScore(s.getSystemScore());
            upd.setProvisionalScore(s.getProvisionalScore());
            upd.setSystemStatus(s.getSystemStatus());
            upd.setDetailJson(s.getDetailJson());
            upd.setEvidenceJson(s.getEvidenceJson());
            upd.setAnalysisRevision(s.getAnalysisRevision());
            boolean reviewed = RehabMotionConstants.FINAL_REVIEWED.contains(prev.getFinalStatus())
                    || RehabMotionConstants.FINAL_NEEDS_RECHECK.equals(prev.getFinalStatus());
            if (reviewed && !sameScore(prev.getSystemScore(), s.getSystemScore())) {
                upd.setSystemScoreChanged(true);
                upd.setFinalStatus(RehabMotionConstants.FINAL_NEEDS_RECHECK);
            }
            plan.updates.add(upd);
        }
        for (RehabMotionScoreDO gone : old.values()) {
            plan.deletes.add(gone.getId());
        }
        return plan;
    }

    public static boolean sameScore(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.compareTo(b) == 0;
    }

    /**
     * @return null 表示合法，否则为错误说明
     */
    public static String validateFinal(RehabMotionScoreDO score, BigDecimal value, String status) {
        if (RehabMotionConstants.FINAL_NOT_APPLICABLE.equals(status)) {
            return value == null ? null : "不适用时不应填写分数";
        }
        String scheme = score.getScoringScheme();
        if ("NASM_EVIDENCE".equals(scheme)) {
            return value == null ? null : "NASM 为定性观察，不填写数值分";
        }
        if (value == null) {
            return "须填写最终分";
        }
        if ("FMS_0_3".equals(scheme)) {
            return isInteger(value) && between(value, 0, 3) ? null : "FMS 单项分须为 0-3 的整数";
        }
        if ("TJA_MODIFIED_0_2".equals(scheme)) {
            return isInteger(value) && between(value, 0, 20) ? null : "改良 TJA 总分须为 0-20 的整数";
        }
        if ("TJA_MYER_2008_DICHOTOMOUS".equals(scheme)) {
            return isInteger(value) && between(value, 0, 10) ? null : "Myer TJA 总分须为 0-10 的整数";
        }
        if ("LESS_MEAN_OF_3".equals(scheme)) {
            return between(value, 0, 19) ? null : "LESS 须在 0-19 之间";
        }
        if ("YBT_COMPOSITE_PERCENT".equals(scheme)) {
            return between(value, 0, 200) ? null : "YBT 综合分须在 0-200% 之间";
        }
        return "未知计分方案";
    }

    private static boolean isInteger(BigDecimal v) {
        return v.stripTrailingZeros().scale() <= 0;
    }

    private static boolean between(BigDecimal v, int lo, int hi) {
        return v.compareTo(BigDecimal.valueOf(lo)) >= 0 && v.compareTo(BigDecimal.valueOf(hi)) <= 0;
    }

    /**
     * FMS 总分：7 项均已审核且均有最终分时返回总和，否则 null（P9）。
     */
    public static BigDecimal fmsTotal(List<RehabMotionScoreDO> scores) {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (String test : RehabMotionConstants.FMS_TESTS) {
            RehabMotionScoreDO s = find(scores, RehabMotionConstants.FAMILY_FMS, test, "overall");
            if (s == null || s.getFinalScore() == null
                    || !(RehabMotionConstants.FINAL_CONFIRMED.equals(s.getFinalStatus())
                    || RehabMotionConstants.FINAL_MODIFIED.equals(s.getFinalStatus()))) {
                return null;
            }
            total = total.add(s.getFinalScore());
            count++;
        }
        return count == RehabMotionConstants.FMS_TESTS.size() ? total : null;
    }

    public static RehabMotionScoreDO find(List<RehabMotionScoreDO> scores, String family, String test, String side) {
        for (RehabMotionScoreDO s : scores) {
            if (family.equals(s.getFamily()) && test.equals(s.getTestCode()) && side.equals(s.getSide())) {
                return s;
            }
        }
        return null;
    }

    /** 签署前未完成审核的评分 */
    public static List<String> unreviewed(List<RehabMotionScoreDO> scores) {
        List<String> out = new ArrayList<String>();
        for (RehabMotionScoreDO s : scores) {
            if (!RehabMotionConstants.FINAL_REVIEWED.contains(s.getFinalStatus())) {
                out.add(s.getTestCode() + ("overall".equals(s.getSide()) ? "" : "(" + s.getSide() + ")"));
            }
        }
        return out;
    }

}
