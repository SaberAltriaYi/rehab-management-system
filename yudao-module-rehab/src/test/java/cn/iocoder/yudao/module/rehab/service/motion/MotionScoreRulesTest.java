package cn.iocoder.yudao.module.rehab.service.motion;

import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionScoreDO;
import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FMS 总分（P9）、取值范围、重算后已审核分数的复核标记。
 */
class MotionScoreRulesTest {

    static RehabMotionScoreDO fms(String test, Integer system, Integer fin, String status) {
        return RehabMotionScoreDO.builder()
                .id((long) Math.abs(test.hashCode()))
                .family(RehabMotionConstants.FAMILY_FMS).testCode(test).side("overall").scoringScheme("FMS_0_3")
                .systemScore(system == null ? null : BigDecimal.valueOf(system))
                .finalScore(fin == null ? null : BigDecimal.valueOf(fin)).finalStatus(status).build();
    }

    static List<RehabMotionScoreDO> allSeven(String lastStatus) {
        List<RehabMotionScoreDO> out = new ArrayList<RehabMotionScoreDO>();
        for (String t : RehabMotionConstants.FMS_TESTS) {
            out.add(fms(t, 2, 2, RehabMotionConstants.FINAL_CONFIRMED));
        }
        out.get(6).setFinalStatus(lastStatus);
        return out;
    }

    @Test
    void fmsTotalOnlyWhenAllSevenReviewed() {
        assertEquals(0, new BigDecimal("14").compareTo(MotionScoreRules.fmsTotal(allSeven(RehabMotionConstants.FINAL_CONFIRMED))));
        assertNull(MotionScoreRules.fmsTotal(allSeven(RehabMotionConstants.FINAL_PENDING)));
        assertNull(MotionScoreRules.fmsTotal(allSeven(RehabMotionConstants.FINAL_NEEDS_RECHECK)));
        assertNull(MotionScoreRules.fmsTotal(allSeven(RehabMotionConstants.FINAL_NOT_APPLICABLE)),
                "某项不适用时不输出 0-21 总分");
        List<RehabMotionScoreDO> six = allSeven(RehabMotionConstants.FINAL_CONFIRMED);
        six.remove(3);
        assertNull(MotionScoreRules.fmsTotal(six));
    }

    @Test
    void validateFinalRangesPerScheme() {
        RehabMotionScoreDO f = fms("FMS_ASLR", 2, null, null);
        assertNull(MotionScoreRules.validateFinal(f, BigDecimal.valueOf(3), RehabMotionConstants.FINAL_MODIFIED));
        assertNotNull(MotionScoreRules.validateFinal(f, BigDecimal.valueOf(4), RehabMotionConstants.FINAL_MODIFIED));
        assertNotNull(MotionScoreRules.validateFinal(f, new BigDecimal("2.5"), RehabMotionConstants.FINAL_MODIFIED));
        assertNotNull(MotionScoreRules.validateFinal(f, null, RehabMotionConstants.FINAL_MODIFIED));
        assertNull(MotionScoreRules.validateFinal(f, null, RehabMotionConstants.FINAL_NOT_APPLICABLE));
        RehabMotionScoreDO less = RehabMotionScoreDO.builder().scoringScheme("LESS_MEAN_OF_3").build();
        assertNull(MotionScoreRules.validateFinal(less, new BigDecimal("5.667"), RehabMotionConstants.FINAL_MODIFIED));
        assertNotNull(MotionScoreRules.validateFinal(less, BigDecimal.valueOf(20), RehabMotionConstants.FINAL_MODIFIED));
        RehabMotionScoreDO tja = RehabMotionScoreDO.builder().scoringScheme("TJA_MYER_2008_DICHOTOMOUS").build();
        assertNotNull(MotionScoreRules.validateFinal(tja, BigDecimal.valueOf(11), RehabMotionConstants.FINAL_MODIFIED));
        RehabMotionScoreDO nasm = RehabMotionScoreDO.builder().scoringScheme("NASM_EVIDENCE").build();
        assertNotNull(MotionScoreRules.validateFinal(nasm, BigDecimal.ONE, RehabMotionConstants.FINAL_MODIFIED),
                "NASM 为定性观察，不得填写数值分");
    }

    @Test
    void reanalysisFlagsReviewedScoresWhoseSystemScoreChanged() {
        RehabMotionScoreDO reviewed = fms("FMS_TSPU", 2, 2, RehabMotionConstants.FINAL_CONFIRMED);
        RehabMotionScoreDO untouched = fms("FMS_ASLR", 3, 3, RehabMotionConstants.FINAL_CONFIRMED);
        RehabMotionScoreDO gone = fms("FMS_DEEP_SQUAT", 1, null, RehabMotionConstants.FINAL_PENDING);
        MotionScoreRules.MergePlan plan = MotionScoreRules.merge(Arrays.asList(reviewed, untouched, gone),
                Arrays.asList(fms("FMS_TSPU", 0, null, null), fms("FMS_ASLR", 3, null, null),
                        fms("FMS_ROTARY_STABILITY", 1, null, null)));
        assertEquals(1, plan.inserts.size());
        assertEquals(RehabMotionConstants.FINAL_PENDING, plan.inserts.get(0).getFinalStatus());
        assertEquals(2, plan.updates.size());
        RehabMotionScoreDO tspu = plan.updates.get(0);
        assertEquals(Boolean.TRUE, tspu.getSystemScoreChanged());
        assertEquals(RehabMotionConstants.FINAL_NEEDS_RECHECK, tspu.getFinalStatus());
        assertNull(tspu.getFinalScore(), "重算不改写治疗师最终分");
        assertNull(plan.updates.get(1).getSystemScoreChanged());
        assertEquals(Collections.singletonList(gone.getId()), plan.deletes);
    }

    @Test
    void unreviewedListsPendingAndRecheck() {
        List<RehabMotionScoreDO> s = allSeven(RehabMotionConstants.FINAL_NEEDS_RECHECK);
        assertEquals(Collections.singletonList("FMS_ROTARY_STABILITY"), MotionScoreRules.unreviewed(s));
    }

}
