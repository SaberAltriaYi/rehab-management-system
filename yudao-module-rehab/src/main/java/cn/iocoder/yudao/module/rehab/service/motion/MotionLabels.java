package cn.iocoder.yudao.module.rehab.service.motion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试与计分方案的中文名称（界面/报告/模板草稿共用）。
 */
public final class MotionLabels {

    public static final Map<String, String> TESTS;

    static {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("FMS_DEEP_SQUAT", "FMS 深蹲");
        m.put("FMS_HURDLE_STEP", "FMS 跨栏步");
        m.put("FMS_INLINE_LUNGE", "FMS 直线弓步");
        m.put("FMS_SHOULDER_MOBILITY", "FMS 肩部灵活性");
        m.put("FMS_ASLR", "FMS 主动直腿抬高");
        m.put("FMS_TSPU", "FMS 躯干稳定俯卧撑");
        m.put("FMS_ROTARY_STABILITY", "FMS 旋转稳定性");
        m.put("NASM_OHS", "NASM 过顶深蹲");
        m.put("NASM_SLS", "NASM 单腿蹲");
        m.put("NASM_PUSHUP", "NASM 俯卧撑");
        m.put("NASM_ROW", "NASM 站姿划船");
        m.put("NASM_DB_PRESS", "NASM 站姿哑铃推举");
        m.put("NASM_SHOULDER_HABD", "NASM 肩水平外展");
        m.put("NASM_SHOULDER_ROT", "NASM 肩旋转");
        m.put("NASM_SHOULDER_FLEX", "NASM 肩前屈");
        m.put("NASM_GAIT", "NASM 步态");
        m.put("YBT_LQ", "Y-Balance 下肢");
        m.put("TUCK_JUMP", "10 秒团身跳");
        m.put("LESS", "LESS 落地误差评分");
        TESTS = Collections.unmodifiableMap(m);
    }

    private MotionLabels() {
    }

    public static String test(String code) {
        String label = TESTS.get(code);
        return label == null ? code : label;
    }

    public static String side(String side) {
        if ("left".equals(side)) {
            return "左";
        }
        if ("right".equals(side)) {
            return "右";
        }
        if ("bilateral".equals(side)) {
            return "双侧";
        }
        return "";
    }

    public static String finalStatus(String status) {
        if ("confirmed".equals(status)) {
            return "已确认";
        }
        if ("modified".equals(status)) {
            return "已修改";
        }
        if ("not_applicable".equals(status)) {
            return "不适用";
        }
        if ("needs_recheck".equals(status)) {
            return "需复核";
        }
        return "待审核";
    }

}
