package cn.iocoder.yudao.module.rehab.service.motion.workspace;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.util.Map;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. No clinical response diagnostics. */
final class MotionWorkspaceJson {
    private MotionWorkspaceJson() { }

    private static ObjectReader reader() {
        // Per-reader options only; never change the shared web mapper.
        return JsonUtils.getObjectMapper().readerFor(Map.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    static Map<String,Object> read(String text) {
        try { return required(reader().readValue(text)); }
        catch (IOException | IllegalArgumentException ignored) { throw invalid(); }
    }

    static Map<String,Object> read(byte[] bytes) {
        try { return required(reader().readValue(bytes)); }
        catch (IOException | IllegalArgumentException ignored) { throw invalid(); }
    }

    private static Map<String,Object> required(Map<String,Object> value) {
        if (value == null) throw invalid();
        return value;
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("运动评估返回的JSON格式无效；请核对服务版本或重新提交，原始内容已省略");
    }
}
