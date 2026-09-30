package cn.iocoder.yudao.module.rehab.service.motion.ai;

import cn.hutool.core.util.StrUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 输出的二次校验（在 JSON Schema 校验之后，纯函数）：
 * <ol>
 *   <li>每条发现/建议必须引用至少一个载荷中存在的证据 ID，否则剔除；</li>
 *   <li>文本中出现的“X 分”必须等于载荷中的系统分或最终分，否则剔除（AI 不得改写分数）；</li>
 *   <li>不得出现诊断、手术/药物、风险等级判定等越权措辞，否则剔除；</li>
 *   <li>不得包含“正常/异常/高风险”等对未验证指标的定性结论，否则剔除。</li>
 * </ol>
 * 结果全部被剔除时由调用方降级为模板草稿。
 */
public final class MotionAiOutputValidator {

    private static final Pattern SCORE_MENTION = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*分(?![钟析别类布期解段开享组配数秒])");
    private static final List<String> FORBIDDEN = Collections.unmodifiableList(Arrays.asList(
            "确诊", "诊断为", "诊断是", "手术", "药物", "处方", "注射", "骨折", "撕裂", "损伤风险高", "高风险",
            "低风险", "风险等级", "正常范围", "异常", "不正常", "病理", "必然", "一定会"));

    private MotionAiOutputValidator() {
    }

    public static final class Result {
        public final Map<String, Object> cleaned;
        public final List<String> droppedReasons;
        public final Set<String> usedEvidence;
        public final int keptClaims;

        Result(Map<String, Object> cleaned, List<String> droppedReasons, Set<String> usedEvidence, int keptClaims) {
            this.cleaned = cleaned;
            this.droppedReasons = droppedReasons;
            this.usedEvidence = usedEvidence;
            this.keptClaims = keptClaims;
        }
    }

    private static final Set<String> TOP_LEVEL = new LinkedHashSet<String>(Arrays.asList(
            "therapist_summary", "findings", "training_suggestions", "patient_summary", "limitations"));
    public static final int MAX_FINDINGS = 20;
    public static final int MAX_SUGGESTIONS = 12;
    public static final int MAX_REFS = 12;

    /**
     * 结构校验：顶层字段集合与类型必须与 schema 一致（不接受额外字段，例如 AI 自行添加的 scores）。
     *
     * @return null 合法；否则为原因
     */
    public static String structural(Map<String, Object> output) {
        if (output == null) {
            return "not_object";
        }
        for (String key : output.keySet()) {
            if (!TOP_LEVEL.contains(key)) {
                return "unexpected_field:" + StrUtil.maxLength(key, 32);
            }
        }
        for (String key : TOP_LEVEL) {
            if (!output.containsKey(key)) {
                return "missing_field:" + key;
            }
        }
        if (!(output.get("therapist_summary") instanceof String) || !(output.get("patient_summary") instanceof String)) {
            return "summary_not_string";
        }
        for (String key : new String[]{"findings", "training_suggestions", "limitations"}) {
            if (!(output.get(key) instanceof List)) {
                return key + "_not_array";
            }
        }
        if (((List<?>) output.get("findings")).size() > MAX_FINDINGS
                || ((List<?>) output.get("training_suggestions")).size() > MAX_SUGGESTIONS) {
            return "too_many_items";
        }
        return null;
    }

    public static Result validate(Map<String, Object> output, Set<String> evidenceIds, Set<BigDecimal> scoreValues) {
        List<String> dropped = new ArrayList<String>();
        Set<String> used = new LinkedHashSet<String>();
        Map<String, Object> cleaned = new LinkedHashMap<String, Object>();
        int kept = 0;
        for (String section : new String[]{"findings", "training_suggestions"}) {
            List<Map<String, Object>> keptItems = new ArrayList<Map<String, Object>>();
            Object raw = output.get(section);
            if (raw instanceof List) {
                int index = 0;
                for (Object o : (List<?>) raw) {
                    index++;
                    String reason = checkClaim(o, evidenceIds, scoreValues);
                    if (reason != null) {
                        dropped.add(section + "#" + index + ":" + reason);
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> item = (Map<String, Object>) o;
                    for (Object ref : (List<?>) item.get("evidence_refs")) {
                        used.add(String.valueOf(ref));
                    }
                    keptItems.add(item);
                    kept++;
                }
            }
            cleaned.put(section, keptItems);
        }
        for (String field : new String[]{"therapist_summary", "patient_summary"}) {
            Object text = output.get(field);
            String reason = text instanceof String ? checkText((String) text, scoreValues) : "missing";
            if (reason != null) {
                dropped.add(field + ":" + reason);
                cleaned.put(field, "");
            } else {
                cleaned.put(field, text);
            }
        }
        List<String> limitations = new ArrayList<String>();
        Object lim = output.get("limitations");
        if (lim instanceof List) {
            for (Object o : (List<?>) lim) {
                if (o instanceof String && checkText((String) o, scoreValues) == null) {
                    limitations.add(StrUtil.maxLength((String) o, 300));
                }
            }
        }
        cleaned.put("limitations", limitations);
        cleaned.put("evidence_refs", new ArrayList<String>(used));
        return new Result(cleaned, dropped, used, kept);
    }

    private static String checkClaim(Object o, Set<String> evidenceIds, Set<BigDecimal> scoreValues) {
        if (!(o instanceof Map)) {
            return "not_object";
        }
        Map<?, ?> item = (Map<?, ?>) o;
        Object text = item.get("text");
        Object refs = item.get("evidence_refs");
        if (!(text instanceof String) || StrUtil.isBlank((String) text)) {
            return "empty_text";
        }
        if (!(refs instanceof List) || ((List<?>) refs).isEmpty()) {
            return "no_evidence";
        }
        if (((List<?>) refs).size() > MAX_REFS) {
            return "too_many_refs";
        }
        for (Object key : item.keySet()) {
            if (!"text".equals(key) && !"evidence_refs".equals(key)) {
                return "unexpected_field";
            }
        }
        for (Object ref : (List<?>) refs) {
            if (!(ref instanceof String) || !evidenceIds.contains(ref)) {
                return "unknown_evidence";
            }
        }
        return checkText((String) text, scoreValues);
    }

    static String checkText(String text, Set<BigDecimal> scoreValues) {
        if (text.length() > 2000) {
            return "too_long";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String word : FORBIDDEN) {
            if (lower.contains(word)) {
                return "forbidden:" + word;
            }
        }
        Matcher m = SCORE_MENTION.matcher(text);
        while (m.find()) {
            BigDecimal value;
            try {
                value = new BigDecimal(m.group(1)).stripTrailingZeros();
            } catch (NumberFormatException ex) {
                return "score_unparseable";
            }
            boolean known = false;
            for (BigDecimal s : scoreValues) {
                if (s.compareTo(value) == 0) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                return "score_mismatch";
            }
        }
        return null;
    }

}
