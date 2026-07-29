package cn.iocoder.yudao.module.rehab.service.ai.client;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.*;

/**
 * OpenAI Responses API 客户端（Step7 双阶段兼容）
 */
@Component
@Slf4j
public class OpenAiResponsesClient implements RehabAiClient {

    @Value("${yudao.rehab.ai.openai.api-key:${OPENAI_API_KEY:}}")
    private String apiKey;

    @Value("${yudao.rehab.ai.openai.base-url:${OPENAI_BASE_URL:https://api.openai.com/v1}}")
    private String baseUrl;

    @Value("${yudao.rehab.ai.openai.model:${OPENAI_MODEL:gpt-4.1-mini}}")
    private String defaultModel;

    @Value("${yudao.rehab.ai.openai.timeout-seconds:${OPENAI_TIMEOUT_SECONDS:45}}")
    private Integer defaultTimeoutSeconds;

    @Value("${yudao.rehab.ai.openai.max-retries:${OPENAI_MAX_RETRIES:2}}")
    private Integer defaultMaxRetries;

    @Value("${yudao.rehab.ai.openai.reasoning-effort:${OPENAI_REASONING_EFFORT:medium}}")
    private String defaultReasoningEffort;

    @Override
    @SuppressWarnings("unchecked")
    public RehabAiClientResponse generateStructured(String systemPrompt, String userPrompt, String schemaName,
                                                    Map<String, Object> jsonSchema, RehabAiClientOptions options) {
        long begin = System.currentTimeMillis();
        RehabAiClientResponse response = new RehabAiClientResponse();
        response.setModel(resolveModel(options));
        if (Boolean.TRUE.equals(options.getMockMode())) {
            String mockJson = JsonUtils.toJsonString(buildMockJsonBySchema(jsonSchema));
            response.setSuccess(true);
            response.setOutputJson(mockJson);
            response.setOutputText(mockJson);
            response.setTokenUsageJson("{}");
            response.setRawResponseJson(mockJson);
            response.setLatencyMs(System.currentTimeMillis() - begin);
            return response;
        }

        if (StrUtil.isBlank(apiKey)) {
            response.setSuccess(false);
            response.setErrorMessage("OPENAI_API_KEY missing");
            response.setLatencyMs(System.currentTimeMillis() - begin);
            return response;
        }

        String requestUrl = resolveResponsesUrl();
        int maxRetries = ObjUtil.defaultIfNull(options.getMaxRetries(), defaultMaxRetries);
        Exception lastException = null;
        String lastError = null;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                HttpEntity<String> requestEntity = buildRequestEntity(systemPrompt, userPrompt, schemaName, jsonSchema, options);
                RestTemplate restTemplate = buildRestTemplate(options);
                ResponseEntity<String> httpResp = restTemplate.exchange(requestUrl, HttpMethod.POST, requestEntity, String.class);
                String rawBody = httpResp.getBody();
                Map<String, Object> map = JsonUtils.parseObject(rawBody, Map.class);

                String outputText = extractOutputText(map);
                String outputJson = normalizeJsonText(outputText);
                if (StrUtil.isBlank(outputJson)) {
                    throw new IllegalStateException("OpenAI response missing output_text");
                }
                Object usage = map.get("usage");

                response.setSuccess(true);
                response.setOutputText(outputText);
                response.setOutputJson(outputJson);
                response.setRawResponseJson(rawBody);
                response.setTokenUsageJson(usage == null ? "{}" : JsonUtils.toJsonString(usage));
                response.setLatencyMs(System.currentTimeMillis() - begin);
                return response;
            } catch (Exception ex) {
                lastException = ex;
                lastError = ex.getMessage();
                if (i < maxRetries) {
                    try {
                        Thread.sleep(200L * (i + 1));
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        log.warn("[rehab-ai-openai] call failed, model={}, error={}", response.getModel(), lastError, lastException);
        response.setSuccess(false);
        response.setErrorMessage(StrUtil.blankToDefault(lastError, "OpenAI call failed"));
        response.setLatencyMs(System.currentTimeMillis() - begin);
        return response;
    }

    private HttpEntity<String> buildRequestEntity(String systemPrompt, String userPrompt, String schemaName,
                                                  Map<String, Object> jsonSchema, RehabAiClientOptions options) {
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("model", resolveModel(options));
        request.put("input", buildInputMessages(systemPrompt, userPrompt));
        request.put("temperature", ObjUtil.defaultIfNull(options.getTemperature(), 0.2D));
        request.put("max_output_tokens", ObjUtil.defaultIfNull(options.getMaxOutputTokens(), 1200));

        Map<String, Object> reasoning = new LinkedHashMap<String, Object>();
        reasoning.put("effort", StrUtil.blankToDefault(options.getReasoningEffort(), defaultReasoningEffort));
        request.put("reasoning", reasoning);

        Map<String, Object> format = new LinkedHashMap<String, Object>();
        format.put("type", "json_schema");
        format.put("name", schemaName);
        format.put("schema", jsonSchema);
        format.put("strict", true);
        Map<String, Object> text = new LinkedHashMap<String, Object>();
        text.put("format", format);
        request.put("text", text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return new HttpEntity<String>(JsonUtils.toJsonString(request), headers);
    }

    private RestTemplate buildRestTemplate(RehabAiClientOptions options) {
        int timeout = ObjUtil.defaultIfNull(options.getTimeoutSeconds(), defaultTimeoutSeconds);
        timeout = timeout <= 0 ? 45 : timeout;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout * 1000);
        factory.setReadTimeout(timeout * 1000);
        return new RestTemplate(factory);
    }

    private List<Map<String, Object>> buildInputMessages(String systemPrompt, String userPrompt) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        list.add(buildMessage("system", systemPrompt));
        list.add(buildMessage("user", userPrompt));
        return list;
    }

    private Map<String, Object> buildMessage(String role, String text) {
        Map<String, Object> message = new LinkedHashMap<String, Object>();
        message.put("role", role);
        List<Map<String, Object>> content = new ArrayList<Map<String, Object>>();
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("type", "input_text");
        item.put("text", text);
        content.add(item);
        message.put("content", content);
        return message;
    }

    @SuppressWarnings("unchecked")
    private String extractOutputText(Map<String, Object> map) {
        Object outputText = map.get("output_text");
        if (outputText instanceof String && StrUtil.isNotBlank((String) outputText)) {
            return (String) outputText;
        }

        Object output = map.get("output");
        if (!(output instanceof List)) {
            return null;
        }
        for (Object item : (List<Object>) output) {
            if (!(item instanceof Map)) {
                continue;
            }
            Object content = ((Map<String, Object>) item).get("content");
            if (!(content instanceof List)) {
                continue;
            }
            for (Object c : (List<Object>) content) {
                if (!(c instanceof Map)) {
                    continue;
                }
                Map<String, Object> contentMap = (Map<String, Object>) c;
                Object text = contentMap.get("text");
                if (text instanceof String && StrUtil.isNotBlank((String) text)) {
                    return (String) text;
                }
                if (text instanceof Map) {
                    Object value = ((Map<String, Object>) text).get("value");
                    if (value instanceof String && StrUtil.isNotBlank((String) value)) {
                        return (String) value;
                    }
                }
            }
        }
        return null;
    }

    private String normalizeJsonText(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```[a-zA-Z]*", "");
            normalized = normalized.replaceAll("```$", "").trim();
        }
        if (JsonUtils.isJsonObject(normalized)) {
            return normalized;
        }
        int begin = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        if (begin >= 0 && end > begin) {
            String candidate = normalized.substring(begin, end + 1);
            if (JsonUtils.isJsonObject(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildMockJsonBySchema(Map<String, Object> schema) {
        Map<String, Object> output = new LinkedHashMap<String, Object>();
        Object propertiesObj = schema.get("properties");
        if (!(propertiesObj instanceof Map)) {
            return output;
        }
        Map<String, Object> properties = (Map<String, Object>) propertiesObj;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            output.put(entry.getKey(), buildMockValueByProperty((Map<String, Object>) entry.getValue(), entry.getKey()));
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    private Object buildMockValueByProperty(Map<String, Object> property, String field) {
        String type = String.valueOf(property.get("type"));
        if ("array".equals(type)) {
            Object itemsObj = property.get("items");
            if (itemsObj instanceof Map) {
                Object itemMock = buildMockValueByProperty((Map<String, Object>) itemsObj, field + "_item");
                List<Object> list = new ArrayList<Object>();
                list.add(itemMock);
                return list;
            }
            return Collections.singletonList(field + "_sample");
        }
        if ("object".equals(type)) {
            Map<String, Object> childProperties = (Map<String, Object>) property.get("properties");
            Map<String, Object> child = new LinkedHashMap<String, Object>();
            if (childProperties != null) {
                for (Map.Entry<String, Object> e : childProperties.entrySet()) {
                    if (e.getValue() instanceof Map) {
                        child.put(e.getKey(), buildMockValueByProperty((Map<String, Object>) e.getValue(), e.getKey()));
                    }
                }
            }
            return child;
        }
        if ("number".equals(type) || "integer".equals(type)) {
            return 1;
        }
        if ("boolean".equals(type)) {
            return Boolean.TRUE;
        }
        return field + "_sample";
    }

    private String resolveModel(RehabAiClientOptions options) {
        return StrUtil.blankToDefault(options.getModel(), defaultModel);
    }

    private String resolveResponsesUrl() {
        if (baseUrl.endsWith("/responses")) {
            return baseUrl;
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl + "responses";
        }
        return baseUrl + "/responses";
    }
}
