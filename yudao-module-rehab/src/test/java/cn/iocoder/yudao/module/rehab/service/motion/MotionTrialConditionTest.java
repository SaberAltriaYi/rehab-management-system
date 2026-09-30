package cn.iocoder.yudao.module.rehab.service.motion;

import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTrialDO;
import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
}
