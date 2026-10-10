package cn.iocoder.yudao.module.rehab.service.motion;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTrialDO;
import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 组装引擎请求（纯函数）。只发送计算所需字段：不含姓名、电话、病史、自由文本备注之外的身份信息；
 * assessment_ref 使用内部编号。
 */
public final class MotionEngineRequestBuilder {

    public static final Pattern TRIAL_KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.:\\-]{0,63}$");
    private static final Pattern CRITERION_KEY = Pattern.compile("^[A-Z0-9_\\-]{1,32}$");
    private static final Pattern VALUE_KEY = Pattern.compile("^[a-z0-9_]{1,48}$");
    private static final Pattern TJ_CODE = Pattern.compile("^TJ-(0[1-9]|10)$");
    private static final Pattern LESS_CODE = Pattern.compile("^LESS-(0[1-9]|1[0-7])$");
    private static final Pattern CHECKPOINT = Pattern.compile("^[A-Z0-9_\\-]{1,32}$");

    private MotionEngineRequestBuilder() {
    }

    /** 引擎可分析的输入文件 */
    public static final class InputFile {
        public final String relativePath;
        public final byte[] data;

        public InputFile(String relativePath, byte[] data) {
            this.relativePath = relativePath;
            this.data = data;
        }
    }

    public static Map<String, Object> parseManualInputs(String json) {
        if (StrUtil.isBlank(json)) {
            return new LinkedHashMap<String, Object>();
        }
        Map<String, Object> map = JsonUtils.parseObject(json, new TypeReference<LinkedHashMap<String, Object>>() {
        });
        return map == null ? new LinkedHashMap<String, Object>() : map;
    }

    public static Map<String, Object> parseObject(String json) {
        if (StrUtil.isBlank(json)) {
            return new LinkedHashMap<String, Object>();
        }
        Map<String, Object> map = JsonUtils.parseObject(json, new TypeReference<LinkedHashMap<String, Object>>() {
        });
        return map == null ? new LinkedHashMap<String, Object>() : map;
    }

    /**
     * @return 错误列表（空 = 合法）。只做结构/取值校验；临床计算全部在引擎。
     */
    public static List<String> validateTrials(List<RehabMotionTrialDO> trials, Collection<String> motTrialNames) {
        List<String> errors = new ArrayList<String>();
        if (trials == null || trials.isEmpty()) {
            errors.add("至少需要配置一个 Trial");
            return errors;
        }
        Set<String> keys = new HashSet<String>();
        for (RehabMotionTrialDO t : trials) {
            String key = t.getTrialKey();
            if (key == null || !TRIAL_KEY.matcher(key).matches()) {
                errors.add("trial_key 不合法: " + StrUtil.maxLength(String.valueOf(key), 64));
                continue;
            }
            if (!keys.add(key)) {
                errors.add("trial_key 重复: " + key);
            }
            if (RehabMotionConstants.familyOf(t.getTestCode()) == null) {
                errors.add(key + ": 未知测试代码");
            }
            if (!RehabMotionConstants.SIDES.contains(t.getSide())) {
                errors.add(key + ": 侧别不合法");
            } else {
                List<String> sides = RehabMotionConstants.allowedSides(t.getTestCode());
                if (!sides.isEmpty() && !sides.contains(t.getSide())) {
                    errors.add(key + ": 侧别须为 " + (sides.size() == 1 ? "双侧" : "左/右（YBT 为支撑腿）"));
                }
            }
            if (t.getAttemptNo() == null || t.getAttemptNo() < 1 || t.getAttemptNo() > 20) {
                errors.add(key + ": 尝试序号须为 1-20");
            }
            if (StrUtil.isNotBlank(t.getOpencapTrialName())) {
                if (!MotionFileRules.TRIAL_NAME.matcher(t.getOpencapTrialName()).matches()) {
                    errors.add(key + ": OpenCap trial 名不合法");
                } else if (motTrialNames != null && !motTrialNames.contains(t.getOpencapTrialName())) {
                    errors.add(key + ": 缺少 " + t.getOpencapTrialName() + ".mot");
                }
            }
            if (Boolean.FALSE.equals(t.getValid()) && StrUtil.isBlank(t.getInvalidReason())) {
                errors.add(key + ": 无效 Trial 必须填写原因");
            }
            if (t.getConditionCode() != null && t.getConditionCode().length() > 32) {
                errors.add(key + ": 条件代码过长");
            }
            List<String> allowed = RehabMotionConstants.FMS_CONDITIONS.get(t.getTestCode());
            String cond = RehabMotionConstants.engineCondition(t.getTestCode(), t.getConditionCode());
            if (allowed != null && (cond == null || !allowed.contains(cond))) {
                errors.add(key + ": 条件须为 " + String.join("/", allowed));
            }
            checkCriteria(key, parseObject(t.getManualCriteriaJson()), errors);
            checkValues(key, parseObject(t.getManualValuesJson()), errors);
        }
        return errors;
    }

    private static void checkCriteria(String key, Map<String, Object> map, List<String> errors) {
        if (map.size() > 64) {
            errors.add(key + ": 人工判定项过多");
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if (!CRITERION_KEY.matcher(e.getKey()).matches() || !(v == null || v instanceof Boolean)) {
                errors.add(key + ": 人工判定项不合法 " + StrUtil.maxLength(e.getKey(), 32));
            }
        }
    }

    private static void checkValues(String key, Map<String, Object> map, List<String> errors) {
        if (map.size() > 32) {
            errors.add(key + ": 人工测量值过多");
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if (!VALUE_KEY.matcher(e.getKey()).matches() || !(v == null || v instanceof Number)) {
                errors.add(key + ": 人工测量值不合法 " + StrUtil.maxLength(e.getKey(), 48));
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static List<String> validateManualInputs(Map<String, Object> inputs) {
        List<String> errors = new ArrayList<String>();
        Set<String> allowed = new HashSet<String>(java.util.Arrays.asList(
                "clearing_tests", "ybt_trials", "tuck_jump_ratings", "less_trials", "nasm_observations"));
        for (String k : inputs.keySet()) {
            if (!allowed.contains(k)) {
                errors.add("未知字段: " + StrUtil.maxLength(k, 32));
            }
        }
        for (Map<String, Object> c : listOfMaps(inputs.get("clearing_tests"), "clearing_tests", errors)) {
            if (!RehabMotionConstants.CLEARING_TESTS.contains(c.get("code"))) {
                errors.add("清除测试代码不合法");
            }
            requireNullableBoolean(c.get("pain"), "清除测试 pain", errors);
        }
        for (Map<String, Object> y : listOfMaps(inputs.get("ybt_trials"), "ybt_trials", errors)) {
            if (!"left".equals(y.get("stance_side")) && !"right".equals(y.get("stance_side"))) {
                errors.add("YBT 支撑侧须为 left/right");
            }
            Object dir = y.get("direction");
            if (!"ANT".equals(dir) && !"PM".equals(dir) && !"PL".equals(dir)) {
                errors.add("YBT 方向须为 ANT/PM/PL");
            }
            Object reach = y.get("reach_cm");
            if (reach != null && (!(reach instanceof Number) || ((Number) reach).doubleValue() < 0
                    || ((Number) reach).doubleValue() > 300)) {
                errors.add("YBT 伸够距离须为 0-300 cm");
            }
            checkAttempt(y.get("attempt_no"), errors);
            if (Boolean.FALSE.equals(y.get("valid")) && StrUtil.isBlankIfStr(y.get("invalid_reason"))) {
                errors.add("YBT 无效尝试必须填写原因");
            }
        }
        Object tja = inputs.get("tuck_jump_ratings");
        if (tja != null) {
            if (!(tja instanceof Map)) {
                errors.add("tuck_jump_ratings 须为对象");
            } else {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) tja).entrySet()) {
                    if (!TJ_CODE.matcher(e.getKey()).matches() || !ratingOk(e.getValue(), 2)) {
                        errors.add("TJA 评分不合法: " + StrUtil.maxLength(e.getKey(), 8));
                    }
                }
            }
        }
        for (Map<String, Object> l : listOfMaps(inputs.get("less_trials"), "less_trials", errors)) {
            Object key = l.get("trial_key");
            if (!(key instanceof String) || !TRIAL_KEY.matcher((String) key).matches()) {
                errors.add("LESS trial_key 不合法");
            }
            Object ratings = l.get("item_ratings");
            if (ratings != null) {
                if (!(ratings instanceof Map)) {
                    errors.add("LESS item_ratings 须为对象");
                } else {
                    for (Map.Entry<String, Object> e : ((Map<String, Object>) ratings).entrySet()) {
                        if (!LESS_CODE.matcher(e.getKey()).matches() || !ratingOk(e.getValue(), 2)) {
                            errors.add("LESS 评分不合法: " + StrUtil.maxLength(e.getKey(), 8));
                        }
                    }
                }
            }
            if (Boolean.FALSE.equals(l.get("valid")) && StrUtil.isBlankIfStr(l.get("invalid_reason"))) {
                errors.add("LESS 无效 Trial 必须填写原因");
            }
        }
        for (Map<String, Object> n : listOfMaps(inputs.get("nasm_observations"), "nasm_observations", errors)) {
            if (!RehabMotionConstants.NASM_TESTS.contains(n.get("test_code"))) {
                errors.add("NASM 测试代码不合法");
            }
            Object cp = n.get("checkpoint");
            if (!(cp instanceof String) || !CHECKPOINT.matcher((String) cp).matches()) {
                errors.add("NASM 检查点不合法");
            }
            Object side = n.get("side");
            if (side != null && !RehabMotionConstants.SIDES.contains(side)) {
                errors.add("NASM 侧别不合法");
            }
            requireNullableBoolean(n.get("present"), "NASM present", errors);
            Object note = n.get("note");
            if (note != null && (!(note instanceof String) || ((String) note).length() > 500)) {
                errors.add("NASM 备注须为 500 字以内文本");
            }
        }
        return errors;
    }

    private static boolean ratingOk(Object v, int max) {
        if (v == null) {
            return true;
        }
        if (!(v instanceof Integer) && !(v instanceof Long)) {
            return false;
        }
        long x = ((Number) v).longValue();
        return x >= 0 && x <= max;
    }

    private static void checkAttempt(Object v, List<String> errors) {
        if (v != null && (!(v instanceof Number) || ((Number) v).intValue() < 1 || ((Number) v).intValue() > 20)) {
            errors.add("尝试序号须为 1-20");
        }
    }

    private static void requireNullableBoolean(Object v, String label, List<String> errors) {
        if (v != null && !(v instanceof Boolean)) {
            errors.add(label + " 须为 true/false/null");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object v, String label, List<String> errors) {
        if (v == null) {
            return Collections.emptyList();
        }
        if (!(v instanceof List)) {
            errors.add(label + " 须为数组");
            return Collections.emptyList();
        }
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Object o : (List<Object>) v) {
            if (o instanceof Map) {
                out.add((Map<String, Object>) o);
            } else {
                errors.add(label + " 元素须为对象");
            }
        }
        if (out.size() > 500) {
            errors.add(label + " 条目过多");
        }
        return out;
    }

    public static Map<String, Object> build(RehabMotionAssessmentDO a, List<RehabMotionTrialDO> trials,
                                            Map<String, Object> manual) {
        Map<String, Object> req = new LinkedHashMap<String, Object>();
        Map<String, Object> assessment = new LinkedHashMap<String, Object>();
        assessment.put("assessment_ref", "MA-" + a.getId());
        if ("male".equals(a.getSexGroup()) || "female".equals(a.getSexGroup())) {
            assessment.put("sex_group", a.getSexGroup());
        }
        req.put("assessment", assessment);
        Map<String, Object> session = new LinkedHashMap<String, Object>();
        session.put("camera_count", a.getCameraCount());
        req.put("session", session);

        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (RehabMotionTrialDO t : trials) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("trial_key", t.getTrialKey());
            m.put("test_code", t.getTestCode());
            m.put("side", t.getSide());
            m.put("condition", RehabMotionConstants.engineCondition(t.getTestCode(), t.getConditionCode()));
            m.put("attempt_no", t.getAttemptNo() == null ? 1 : t.getAttemptNo());
            m.put("opencap_trial_name", StrUtil.emptyToNull(t.getOpencapTrialName()));
            m.put("valid", !Boolean.FALSE.equals(t.getValid()));
            m.put("invalid_reason", Boolean.FALSE.equals(t.getValid()) ? t.getInvalidReason() : null);
            m.put("pain", t.getPain());
            m.put("manual_criteria", parseObject(t.getManualCriteriaJson()));
            m.put("manual_values", parseObject(t.getManualValuesJson()));
            list.add(m);
        }
        req.put("trials", list);

        Object clearing = manual.get("clearing_tests");
        if (clearing != null) {
            req.put("clearing_tests", clearing);
        }
        Object ybtTrials = manual.get("ybt_trials");
        if (ybtTrials != null || a.getLimbLengthLeftCm() != null || a.getLimbLengthRightCm() != null) {
            Map<String, Object> ybt = new LinkedHashMap<String, Object>();
            Map<String, Object> limb = new LinkedHashMap<String, Object>();
            limb.put("left", toDouble(a.getLimbLengthLeftCm()));
            limb.put("right", toDouble(a.getLimbLengthRightCm()));
            ybt.put("limb_length_cm", limb);
            ybt.put("trials", ybtTrials == null ? Collections.emptyList() : ybtTrials);
            req.put("ybt", ybt);
        }
        Object tja = manual.get("tuck_jump_ratings");
        boolean hasTja = tja != null || hasTest(trials, "TUCK_JUMP");
        if (hasTja) {
            Map<String, Object> tuck = new LinkedHashMap<String, Object>();
            tuck.put("variant", StrUtil.blankToDefault(a.getTjaVariant(), "TJA_MODIFIED_0_2"));
            tuck.put("ratings", tja == null ? Collections.emptyMap() : tja);
            req.put("tuck_jump", tuck);
        }
        Object less = manual.get("less_trials");
        if (less != null) {
            Map<String, Object> l = new LinkedHashMap<String, Object>();
            l.put("trials", less);
            req.put("less", l);
        }
        Object nasm = manual.get("nasm_observations");
        if (nasm != null) {
            req.put("nasm_observations", nasm);
        }
        return req;
    }

    private static boolean hasTest(List<RehabMotionTrialDO> trials, String code) {
        for (RehabMotionTrialDO t : trials) {
            if (code.equals(t.getTestCode())) {
                return true;
            }
        }
        return false;
    }

    private static Double toDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    /** request.json + OpenCap 目录结构。视频从不发送给引擎。 */
    public static byte[] zip(Map<String, Object> request, List<InputFile> files) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("request.json"));
            zos.write(JsonUtils.toJsonString(request).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            Set<String> seen = new HashSet<String>();
            for (InputFile f : files) {
                if (!seen.add(f.relativePath)) {
                    continue;
                }
                zos.putNextEntry(new ZipEntry(f.relativePath));
                zos.write(f.data);
                zos.closeEntry();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("zip build failed", ex);
        }
        return bos.toByteArray();
    }

}
