package cn.iocoder.yudao.module.rehab.service.ai;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * AI 输出 schema 基础校验器（v1 顶层字段校验）
 */
@Component
public class RehabAiSchemaValidator {

    @SuppressWarnings("unchecked")
    public ValidationResult validate(String outputJson, Map<String, Object> schema) {
        if (StrUtil.isBlank(outputJson) || !JsonUtils.isJsonObject(outputJson)) {
            return ValidationResult.fail("output 不是合法 JSON 对象");
        }
        Map<String, Object> output = JsonUtils.parseObject(outputJson, Map.class);
        Object requiredObj = schema.get("required");
        if (!(requiredObj instanceof List)) {
            return ValidationResult.ok(output);
        }
        List<String> missing = new ArrayList<String>();
        for (Object req : (List<Object>) requiredObj) {
            String key = String.valueOf(req);
            Object value = output.get(key);
            if (value == null) {
                missing.add(key);
                continue;
            }
            if (value instanceof String && StrUtil.isBlank((String) value)) {
                missing.add(key);
            }
            if (value instanceof List && CollUtil.isEmpty((List<?>) value)) {
                missing.add(key);
            }
        }
        if (CollUtil.isNotEmpty(missing)) {
            return ValidationResult.fail("缺失字段: " + String.join(",", missing));
        }
        return ValidationResult.ok(output);
    }

    @Data
    public static class ValidationResult {
        private Boolean valid;
        private String message;
        private Map<String, Object> outputMap;

        public static ValidationResult ok(Map<String, Object> outputMap) {
            ValidationResult result = new ValidationResult();
            result.setValid(true);
            result.setMessage("ok");
            result.setOutputMap(outputMap);
            return result;
        }

        public static ValidationResult fail(String message) {
            ValidationResult result = new ValidationResult();
            result.setValid(false);
            result.setMessage(message);
            return result;
        }
    }
}
