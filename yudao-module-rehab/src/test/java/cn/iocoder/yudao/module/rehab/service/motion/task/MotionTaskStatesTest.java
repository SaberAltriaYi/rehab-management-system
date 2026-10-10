package cn.iocoder.yudao.module.rehab.service.motion.task;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class MotionTaskStatesTest {

    @Test
    void opencapPipelineWalksAllStatesInOrder() {
        assertEquals(Arrays.asList(STATE_OPENCAP_PROCESSING, STATE_DOWNLOADING, STATE_PARSING, STATE_RULES,
                STATE_AI_GENERATING), MotionTaskStates.sequence(TASK_PIPELINE, "opencap"));
        String s = MotionTaskStates.initialState(TASK_PIPELINE, "opencap");
        int last = -1;
        while (!STATE_COMPLETED.equals(s)) {
            int p = MotionTaskStates.progress(TASK_PIPELINE, "opencap", s);
            assertTrue(p > last && p < 100);
            last = p;
            s = MotionTaskStates.next(TASK_PIPELINE, "opencap", s);
        }
        assertEquals(100, MotionTaskStates.progress(TASK_PIPELINE, "opencap", STATE_COMPLETED));
    }

    @Test
    void uploadAndRescoreSkipOpencapSteps() {
        assertEquals(STATE_PARSING, MotionTaskStates.initialState(TASK_PIPELINE, "upload"));
        assertEquals(STATE_PARSING, MotionTaskStates.initialState(TASK_RESCORE, "opencap"));
        assertFalse(MotionTaskStates.sequence(TASK_RESCORE, "opencap").contains(STATE_DOWNLOADING));
        assertEquals(STATE_PDF_RENDERING, MotionTaskStates.initialState(TASK_PDF, null));
        assertEquals("pdf", MotionTaskStates.group(TASK_PDF));
        assertEquals("analysis", MotionTaskStates.group(TASK_AI));
        assertThrows(IllegalStateException.class, () -> MotionTaskStates.next(TASK_PIPELINE, "upload", STATE_DOWNLOADING));
    }

    @Test
    void retryResumesFromFailedStepWithoutRedownloading() {
        assertEquals(STATE_RULES, MotionTaskStates.resumeState(TASK_PIPELINE, "opencap", STATE_RULES));
        assertEquals(STATE_PARSING, MotionTaskStates.resumeState(TASK_PIPELINE, "upload", "BOGUS"));
        assertEquals(STATE_PARSING, MotionTaskStates.resumeState(TASK_PIPELINE, "upload", null));
    }

    @Test
    void backoffIsExponentialAndCapped() {
        assertEquals(30, MotionTaskStates.backoffSeconds(0));
        assertEquals(30, MotionTaskStates.backoffSeconds(1));
        assertEquals(60, MotionTaskStates.backoffSeconds(2));
        assertEquals(120, MotionTaskStates.backoffSeconds(3));
        assertEquals(MotionTaskStates.MAX_BACKOFF_SECONDS, MotionTaskStates.backoffSeconds(40));
    }

    @Test
    void terminalStates() {
        for (String s : new String[]{STATE_COMPLETED, STATE_FAILED, STATE_CANCELLED, STATE_PENDING_REVIEW}) {
            assertTrue(MotionTaskStates.isTerminal(s), s);
        }
        assertFalse(MotionTaskStates.isTerminal(STATE_RULES));
    }

}
