package cn.iocoder.yudao.module.rehab.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动作评估常量。状态码为英文（写库/接口），中文标签用于界面与报告。
 */
public interface RehabMotionConstants {

    String AUDIT_MODULE = "motion";
    String PROMPT_VERSION = "motion-report/1.0.0";
    String OPENSIM_MODEL = "LaiUhlrich2022";

    // ===== 流程状态（评估与任务共用） =====
    String STATE_WAITING_UPLOAD = "WAITING_UPLOAD";
    String STATE_UPLOADING = "UPLOADING";
    String STATE_OPENCAP_PROCESSING = "OPENCAP_PROCESSING";
    String STATE_DOWNLOADING = "DOWNLOADING";
    String STATE_PARSING = "PARSING";
    String STATE_RULES = "RULES";
    String STATE_AI_GENERATING = "AI_GENERATING";
    String STATE_PENDING_REVIEW = "PENDING_REVIEW";
    String STATE_COMPLETED = "COMPLETED";
    String STATE_FAILED = "FAILED";
    String STATE_CANCELLED = "CANCELLED";
    /** PDF 任务内部状态 */
    String STATE_PDF_RENDERING = "PDF_RENDERING";

    Map<String, String> STATE_LABELS = Collections.unmodifiableMap(new LinkedHashMap<String, String>() {{
        put(STATE_WAITING_UPLOAD, "待上传");
        put(STATE_UPLOADING, "上传中");
        put(STATE_OPENCAP_PROCESSING, "OpenCap处理中");
        put(STATE_DOWNLOADING, "下载结果");
        put(STATE_PARSING, "数据解析");
        put(STATE_RULES, "规则计算");
        put(STATE_AI_GENERATING, "AI报告生成");
        put(STATE_PENDING_REVIEW, "待治疗师审核");
        put(STATE_COMPLETED, "已完成");
        put(STATE_FAILED, "失败");
        put(STATE_CANCELLED, "已取消");
        put(STATE_PDF_RENDERING, "PDF生成");
    }});

    /** 任务可被 worker 执行的状态 */
    Set<String> RUNNABLE_STATES = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
            STATE_OPENCAP_PROCESSING, STATE_DOWNLOADING, STATE_PARSING, STATE_RULES, STATE_AI_GENERATING,
            STATE_PDF_RENDERING)));
    Set<String> TERMINAL_TASK_STATES = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
            STATE_PENDING_REVIEW, STATE_COMPLETED, STATE_FAILED, STATE_CANCELLED)));

    // ===== 任务类型 =====
    String TASK_PIPELINE = "PIPELINE";
    String TASK_RESCORE = "RESCORE";
    String TASK_AI = "AI";
    String TASK_PDF = "PDF";

    // ===== 文件类型 =====
    String FILE_MOT = "mot";
    String FILE_TRC = "trc";
    String FILE_OSIM = "osim";
    String FILE_METADATA = "metadata";
    String FILE_VIDEO = "video";
    String FILE_RESULT = "result";
    String FILE_PDF = "pdf";
    Set<String> KINEMATIC_FILE_KINDS = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
            FILE_MOT, FILE_TRC, FILE_OSIM, FILE_METADATA)));

    // ===== 体系与测试 =====
    String FAMILY_FMS = "FMS";
    String FAMILY_NASM = "NASM_CES";
    String FAMILY_YBT = "YBT_LQ";
    String FAMILY_TJA = "TUCK_JUMP";
    String FAMILY_LESS = "LESS";
    List<String> FAMILIES = Collections.unmodifiableList(Arrays.asList(
            FAMILY_FMS, FAMILY_NASM, FAMILY_YBT, FAMILY_TJA, FAMILY_LESS));

    List<String> FMS_TESTS = Collections.unmodifiableList(Arrays.asList(
            "FMS_DEEP_SQUAT", "FMS_HURDLE_STEP", "FMS_INLINE_LUNGE", "FMS_SHOULDER_MOBILITY",
            "FMS_ASLR", "FMS_TSPU", "FMS_ROTARY_STABILITY"));
    List<String> NASM_TESTS = Collections.unmodifiableList(Arrays.asList(
            "NASM_OHS", "NASM_SLS", "NASM_PUSHUP", "NASM_ROW", "NASM_DB_PRESS", "NASM_SHOULDER_HABD",
            "NASM_SHOULDER_ROT", "NASM_SHOULDER_FLEX", "NASM_GAIT"));
    List<String> OTHER_TESTS = Collections.unmodifiableList(Arrays.asList("YBT_LQ", "TUCK_JUMP", "LESS"));
    List<String> CLEARING_TESTS = Collections.unmodifiableList(Arrays.asList("CT-SM-L", "CT-SM-R", "CT-EXT", "CT-FLX"));
    Set<String> SIDES = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList("bilateral", "left", "right")));

    // ===== 评分状态 =====
    String FINAL_PENDING = "pending";
    String FINAL_CONFIRMED = "confirmed";
    String FINAL_MODIFIED = "modified";
    String FINAL_NOT_APPLICABLE = "not_applicable";
    String FINAL_NEEDS_RECHECK = "needs_recheck";
    Set<String> FINAL_REVIEWED = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
            FINAL_CONFIRMED, FINAL_MODIFIED, FINAL_NOT_APPLICABLE)));

    // ===== AI 草稿 =====
    String AI_GENERATED = "generated";
    String AI_FALLBACK = "fallback";
    String AI_ACCEPTED = "accepted";
    String AI_REJECTED = "rejected";
    String AI_STALE = "stale";

    // ===== 报告 =====
    String REPORT_THERAPIST = "therapist";
    String REPORT_PATIENT = "patient";
    String REPORT_SIGNED = "signed";
    String REPORT_SUPERSEDED = "superseded";

    /**
     * FMS 分级条件（官方：深蹲标准→足跟垫高；俯卧撑高位→低位手位；旋转稳定同侧→对角）。
     * 顺序即引擎分级顺序；第一个为默认/高分条件。
     */
    Map<String, List<String>> FMS_CONDITIONS = Collections.unmodifiableMap(new LinkedHashMap<String, List<String>>() {{
        put("FMS_DEEP_SQUAT", Collections.unmodifiableList(Arrays.asList("standard", "heels_elevated")));
        put("FMS_TSPU", Collections.unmodifiableList(Arrays.asList("high", "low")));
        put("FMS_ROTARY_STABILITY", Collections.unmodifiableList(Arrays.asList("unilateral", "diagonal")));
    }});

    /** 发送给引擎的条件：深蹲未填视为 standard（兼容旧数据），其余原样。 */
    static String engineCondition(String testCode, String condition) {
        String c = condition == null || condition.trim().isEmpty() ? null : condition.trim();
        if (c == null && "FMS_DEEP_SQUAT".equals(testCode)) {
            return "standard";
        }
        return c;
    }

    // ===== 测量误差（OpenCap vs 光学动捕，Uhlrich 2023）=====
    double MAE_ROTATION_DEG = 4.5;
    double MAE_TRANSLATION_M = 0.0123;

    static String stateLabel(String state) {
        String label = STATE_LABELS.get(state);
        return label == null ? state : label;
    }

    static String familyOf(String testCode) {
        if (testCode == null) {
            return null;
        }
        if (testCode.startsWith("FMS_")) {
            return FAMILY_FMS;
        }
        if (testCode.startsWith("NASM_")) {
            return FAMILY_NASM;
        }
        if ("YBT_LQ".equals(testCode)) {
            return FAMILY_YBT;
        }
        if ("TUCK_JUMP".equals(testCode)) {
            return FAMILY_TJA;
        }
        if ("LESS".equals(testCode)) {
            return FAMILY_LESS;
        }
        return null;
    }

}
