package cn.iocoder.yudao.module.rehab.service.motion;

import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTrialDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FMS 分级条件（官方：深蹲标准/足跟垫高、俯卧撑高位/低位手位、旋转稳定同侧/对角）必须与引擎 tiers 一致，
 * 否则引擎会把试次判为 UNKNOWN_CONDITION（无效）。
 */
class MotionTrialConditionTest {

    private static RehabMotionTrialDO trial(String key, String test, String side, String cond) {
        return RehabMotionTrialDO.builder().trialKey(key).testCode(test).side(side).conditionCode(cond).attemptNo(1).build();
    }

    @Test
    void conditionsMatchEngineTiers() {
        assertEquals(Arrays.asList("standard", "heels_elevated"), RehabMotionConstants.FMS_CONDITIONS.get("FMS_DEEP_SQUAT"));
        assertEquals(Arrays.asList("high", "low"), RehabMotionConstants.FMS_CONDITIONS.get("FMS_TSPU"));
        assertEquals(Arrays.asList("unilateral", "diagonal"), RehabMotionConstants.FMS_CONDITIONS.get("FMS_ROTARY_STABILITY"));
        assertNull(RehabMotionConstants.FMS_CONDITIONS.get("FMS_HURDLE_STEP"));
    }

    @Test
    void limbLengthAloneDoesNotRequestYbtScoring() {
        // 隔离环境联调发现：只选 LESS+FMS 但填了下肢长度时，引擎会生成 YBT 待审核评分并阻塞签署。
        RehabMotionAssessmentDO a = new RehabMotionAssessmentDO();
        a.setId(1L);
        a.setCameraCount(3);
        a.setProtocolFamilies("LESS,FMS");
        a.setLimbLengthLeftCm(new BigDecimal("95"));
        a.setLimbLengthRightCm(new BigDecimal("95"));
        List<RehabMotionTrialDO> trials = Collections.singletonList(trial("less_1", "LESS", "bilateral", null));
        Map<String, Object> manual = new HashMap<String, Object>();
        assertFalse(MotionEngineRequestBuilder.build(a, trials, manual).containsKey("ybt"));

        a.setProtocolFamilies("LESS,YBT_LQ");
        Map<String, Object> selected = MotionEngineRequestBuilder.build(a, trials, manual);
        assertTrue(selected.containsKey("ybt"));

        a.setProtocolFamilies("LESS");
        List<RehabMotionTrialDO> ybt = Arrays.asList(trial("ybt_l", "YBT_LQ", "left", null));
        assertTrue(MotionEngineRequestBuilder.build(a, ybt, manual).containsKey("ybt"));
    }

    @Test
    void clinicalUpdateKeepsTheSavedCondition() {
        // 记录疼痛等临床字段时必须沿用已保存的分级条件，否则 TSPU/旋转稳定性永远无法记录疼痛（QC-06）。
        RehabMotionTrialDO tspu = trial("fms_10", "FMS_TSPU", "bilateral", "high");
        assertTrue(MotionEngineRequestBuilder.validateClinicalUpdate(tspu, true, null, null, null).isEmpty());
        RehabMotionTrialDO rotary = trial("fms_11", "FMS_ROTARY_STABILITY", "left", "diagonal");
        assertTrue(MotionEngineRequestBuilder.validateClinicalUpdate(rotary, true, null, null, null).isEmpty());
        List<String> invalid = MotionEngineRequestBuilder.validateClinicalUpdate(tspu, false, " ", null, null);
        assertEquals(1, invalid.size(), invalid.toString());
    }

    @Test
    void deepSquatWithoutConditionIsStandardForTheEngine() {
        assertEquals("standard", RehabMotionConstants.engineCondition("FMS_DEEP_SQUAT", null));
        assertEquals("standard", RehabMotionConstants.engineCondition("FMS_DEEP_SQUAT", " "));
        assertEquals("heels_elevated", RehabMotionConstants.engineCondition("FMS_DEEP_SQUAT", "heels_elevated"));
        assertNull(RehabMotionConstants.engineCondition("FMS_HURDLE_STEP", ""));
        assertNull(RehabMotionConstants.engineCondition("FMS_TSPU", null));
    }

    @Test
    void tieredTestsRequireAnOfficialCondition() {
        List<String> ok = MotionEngineRequestBuilder.validateTrials(Arrays.asList(
                trial("ds1", "FMS_DEEP_SQUAT", "bilateral", null),
                trial("ds2", "FMS_DEEP_SQUAT", "bilateral", "heels_elevated"),
                trial("pu1", "FMS_TSPU", "bilateral", "high"),
                trial("pu2", "FMS_TSPU", "bilateral", "low"),
                trial("rsl1", "FMS_ROTARY_STABILITY", "left", "unilateral"),
                trial("rsl2", "FMS_ROTARY_STABILITY", "left", "diagonal"),
                trial("hsl1", "FMS_HURDLE_STEP", "left", null)), null);
        assertEquals(Collections.emptyList(), ok);

        List<String> bad = MotionEngineRequestBuilder.validateTrials(Arrays.asList(
                trial("pu1", "FMS_TSPU", "bilateral", null),
                trial("rsl1", "FMS_ROTARY_STABILITY", "left", "heels_elevated"),
                trial("ds1", "FMS_DEEP_SQUAT", "bilateral", "diagonal")), null);
        assertEquals(3, bad.size(), bad.toString());
        assertTrue(bad.get(0).startsWith("pu1: 条件须为 high/low"));
        assertTrue(bad.get(1).startsWith("rsl1: 条件须为 unilateral/diagonal"));
        assertTrue(bad.get(2).startsWith("ds1: 条件须为 standard/heels_elevated"));
    }
    @Test
    void sidesMatchEngineRegistry() {
        // 与引擎 tests_registry.sides 对齐：保存时即拦截，避免异步任务在规则计算阶段才返回 INVALID_SIDE。
        for (String test : RehabMotionConstants.FMS_TESTS) {
            assertFalse(RehabMotionConstants.allowedSides(test).isEmpty(), test);
        }
        assertEquals(Collections.singletonList("bilateral"), RehabMotionConstants.allowedSides("LESS"));
        assertEquals(Collections.singletonList("bilateral"), RehabMotionConstants.allowedSides("FMS_DEEP_SQUAT"));
        assertEquals(Arrays.asList("left", "right"), RehabMotionConstants.allowedSides("YBT_LQ"));
        assertEquals(Arrays.asList("left", "right"), RehabMotionConstants.allowedSides("NASM_SLS"));
        assertEquals(Arrays.asList("left", "right"), RehabMotionConstants.allowedSides("FMS_HURDLE_STEP"));
        assertEquals(Collections.emptyList(), RehabMotionConstants.allowedSides("UNKNOWN"));

        List<String> bad = MotionEngineRequestBuilder.validateTrials(Arrays.asList(
                trial("ybt1", "YBT_LQ", "bilateral", null),
                trial("less1", "LESS", "left", null),
                trial("sls1", "NASM_SLS", "left", null)), null);
        assertEquals(2, bad.size(), bad.toString());
        assertTrue(bad.get(0).startsWith("ybt1: 侧别须为 左/右"), bad.get(0));
        assertTrue(bad.get(1).startsWith("less1: 侧别须为 双侧"), bad.get(1));
    }
}
