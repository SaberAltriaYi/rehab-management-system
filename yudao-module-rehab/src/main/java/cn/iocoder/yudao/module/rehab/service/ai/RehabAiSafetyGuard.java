package cn.iocoder.yudao.module.rehab.service.ai;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.rehab.enums.RehabAiConstants;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 安全审校（v1）
 */
@Component
public class RehabAiSafetyGuard {

    private static final List<String> BLOCK_WORDS = Arrays.asList(
            "确诊", "一定是", "明确损伤", "必然导致", "手术建议", "药物建议"
    );

    private static final List<String> STRONG_WORDS = Arrays.asList(
            "高风险", "严重", "必须", "立刻", "紧急"
    );

    public SafetyResult check(String renderedText, Map<String, Object> outputMap, String safetyMode) {
        String text = StrUtil.blankToDefault(renderedText, "");
        String lower = text.toLowerCase(Locale.ROOT);
        for (String blockWord : BLOCK_WORDS) {
            if (lower.contains(blockWord.toLowerCase(Locale.ROOT))) {
                return SafetyResult.blocked("命中禁用词: " + blockWord);
            }
        }

        List<String> evidenceRefs = extractEvidenceRefs(outputMap);
        if (containsStrongConclusion(text) && CollUtil.isEmpty(evidenceRefs)) {
            String downgradedText = text + "\n证据不足；仅为功能学推测；需结合人工复核。";
            return SafetyResult.downgraded(downgradedText, "强结论缺少 evidence_refs");
        }

        if ("strict".equalsIgnoreCase(StrUtil.blankToDefault(safetyMode, "standard"))) {
            if (text.contains("明确") || text.contains("一定")) {
                String downgradedText = text.replace("明确", "提示").replace("一定", "倾向");
                if (!downgradedText.contains("证据不足")) {
                    downgradedText = downgradedText + "\n证据不足；仅为功能学推测；需结合人工复核。";
                }
                return SafetyResult.downgraded(downgradedText, "strict 模式降级措辞");
            }
        }
        return SafetyResult.passed(text);
    }

    @SuppressWarnings("unchecked")
    private List<String> extractEvidenceRefs(Map<String, Object> outputMap) {
        if (outputMap == null) {
            return Collections.emptyList();
        }
        Object evidence = outputMap.get("evidence_refs");
        if (evidence instanceof List) {
            return (List<String>) evidence;
        }
        if (evidence instanceof String && StrUtil.isNotBlank((String) evidence)) {
            return Collections.singletonList((String) evidence);
        }
        return Collections.emptyList();
    }

    private boolean containsStrongConclusion(String text) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        for (String strongWord : STRONG_WORDS) {
            if (text.contains(strongWord)) {
                return true;
            }
        }
        return false;
    }

    @Data
    public static class SafetyResult {
        private String safetyStatus;
        private String renderedText;
        private String reason;

        public static SafetyResult passed(String renderedText) {
            SafetyResult result = new SafetyResult();
            result.setSafetyStatus(RehabAiConstants.SAFETY_STATUS_PASSED);
            result.setRenderedText(renderedText);
            result.setReason("ok");
            return result;
        }

        public static SafetyResult downgraded(String renderedText, String reason) {
            SafetyResult result = new SafetyResult();
            result.setSafetyStatus(RehabAiConstants.SAFETY_STATUS_DOWNGRADED);
            result.setRenderedText(renderedText);
            result.setReason(reason);
            return result;
        }

        public static SafetyResult blocked(String reason) {
            SafetyResult result = new SafetyResult();
            result.setSafetyStatus(RehabAiConstants.SAFETY_STATUS_BLOCKED);
            result.setRenderedText(null);
            result.setReason(reason);
            return result;
        }
    }
}
