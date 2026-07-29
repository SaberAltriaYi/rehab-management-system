package cn.iocoder.yudao.module.rehab.service.ai.client;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 预留：未来桥接 yudao-module-ai
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "yudao.rehab.ai", name = "use-platform-bridge", havingValue = "true")
public class PlatformAiBridgeClient implements RehabAiClient {

    @Override
    public RehabAiClientResponse generateStructured(String systemPrompt, String userPrompt, String schemaName,
                                                    Map<String, Object> jsonSchema, RehabAiClientOptions options) {
        log.warn("[rehab-ai] platform bridge enabled but not implemented yet, fallback to mock text");
        RehabAiClientResponse response = new RehabAiClientResponse();
        response.setSuccess(true);
        response.setModel(options.getModel());
        response.setOutputJson(JsonUtils.toJsonString(jsonSchema));
        response.setOutputText("{}");
        response.setTokenUsageJson("{}");
        response.setRawResponseJson("{}");
        response.setLatencyMs(1L);
        return response;
    }
}
