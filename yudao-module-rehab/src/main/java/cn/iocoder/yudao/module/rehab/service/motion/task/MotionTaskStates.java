package cn.iocoder.yudao.module.rehab.service.motion.task;

import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants.*;

/**
 * 任务状态机（纯函数）。
 * <pre>
 * PIPELINE(opencap): OPENCAP_PROCESSING → DOWNLOADING → PARSING → RULES → AI_GENERATING → COMPLETED
 * PIPELINE(upload) / RESCORE:                        PARSING → RULES → AI_GENERATING → COMPLETED
 * AI:                                                                   AI_GENERATING → COMPLETED
 * PDF:                                                                   PDF_RENDERING → COMPLETED
 * 任意运行态 → FAILED（不可重试错误或重试耗尽）/ CANCELLED（用户取消）
 * </pre>
 * 分析类任务完成后评估进入 PENDING_REVIEW（待治疗师审核）。
 */
public final class MotionTaskStates {

    public static final int MAX_BACKOFF_SECONDS = 1800;

    private static final List<String> PIPELINE_OPENCAP = Collections.unmodifiableList(Arrays.asList(
            STATE_OPENCAP_PROCESSING, STATE_DOWNLOADING, STATE_PARSING, STATE_RULES, STATE_AI_GENERATING));
    private static final List<String> ANALYSIS = Collections.unmodifiableList(Arrays.asList(
            STATE_PARSING, STATE_RULES, STATE_AI_GENERATING));

    private MotionTaskStates() {
    }

    public static boolean isAnalysis(String taskType) {
        return TASK_PIPELINE.equals(taskType) || TASK_RESCORE.equals(taskType);
    }

    /** 互斥组：分析与 AI 互斥（AI 读取评分）；PDF 独立 */
    public static String group(String taskType) {
        return TASK_PDF.equals(taskType) ? "pdf" : "analysis";
    }

    public static String initialState(String taskType, String dataSource) {
        if (TASK_PIPELINE.equals(taskType)) {
            return "opencap".equals(dataSource) ? STATE_OPENCAP_PROCESSING : STATE_PARSING;
        }
        if (TASK_RESCORE.equals(taskType)) {
            return STATE_PARSING;
        }
        if (TASK_AI.equals(taskType)) {
            return STATE_AI_GENERATING;
        }
        if (TASK_PDF.equals(taskType)) {
            return STATE_PDF_RENDERING;
        }
        throw new IllegalArgumentException("unknown task type");
    }

    /** 顺序中的下一状态；最后一步之后为 COMPLETED */
    public static String next(String taskType, String dataSource, String state) {
        List<String> seq = sequence(taskType, dataSource);
        int i = seq.indexOf(state);
        if (i < 0) {
            throw new IllegalStateException("state " + state + " not in " + taskType);
        }
        return i + 1 < seq.size() ? seq.get(i + 1) : STATE_COMPLETED;
    }

    public static List<String> sequence(String taskType, String dataSource) {
        if (TASK_PIPELINE.equals(taskType) && "opencap".equals(dataSource)) {
            return PIPELINE_OPENCAP;
        }
        if (isAnalysis(taskType)) {
            return ANALYSIS;
        }
        return Collections.singletonList(initialState(taskType, dataSource));
    }

    /** 重试：从失败的步骤继续（已下载的数据不重复下载）；无法识别时从头开始 */
    public static String resumeState(String taskType, String dataSource, String failedState) {
        if (failedState != null && sequence(taskType, dataSource).contains(failedState)) {
            return failedState;
        }
        return initialState(taskType, dataSource);
    }

    public static int progress(String taskType, String dataSource, String state) {
        if (STATE_COMPLETED.equals(state)) {
            return 100;
        }
        List<String> seq = sequence(taskType, dataSource);
        int i = seq.indexOf(state);
        if (i < 0) {
            return 0;
        }
        return 5 + (int) Math.round(90.0 * i / seq.size());
    }

    public static long backoffSeconds(int attempts) {
        int n = Math.max(1, attempts);
        long s = 30L << Math.min(n - 1, 10);
        return Math.min(s, MAX_BACKOFF_SECONDS);
    }

    public static boolean isTerminal(String state) {
        return STATE_COMPLETED.equals(state) || STATE_FAILED.equals(state) || STATE_CANCELLED.equals(state)
                || RehabMotionConstants.STATE_PENDING_REVIEW.equals(state);
    }

}
