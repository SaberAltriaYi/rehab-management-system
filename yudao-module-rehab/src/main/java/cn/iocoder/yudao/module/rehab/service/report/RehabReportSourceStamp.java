package cn.iocoder.yudao.module.rehab.service.report;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. Hashes report inputs, never logs them. */
final class RehabReportSourceStamp {
    private static final ObjectMapper CODEC = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
    private static Map<String,Object> fields(Map<String,Object> payload) {
        Map<String,Object> fields = new TreeMap<>();
        for (String name : new String[]{"dataOrigin", "patient", "episode", "assessment", "rawModules"}) if (payload.containsKey(name)) fields.put(name,payload.get(name));
        return fields;
    }
    static Map<String, Object> of(Map<String, Object> payload, Long assessmentId) {
        try {
            // Hash the representation actually persisted by the web codec, not
            // a different LocalDate/null representation in this private codec.
            Map<String,Object> normalized = CODEC.readValue(JsonUtils.toJsonString(fields(payload)),Map.class);
            Map<String, Object> stamp = new LinkedHashMap<>();
            stamp.put("schema", "rehab-report-source-v1");
            stamp.put("codec", "canonical-persisted-json-v1");
            stamp.put("assessmentId", assessmentId);
            stamp.put("coverage", new ArrayList<>(normalized.keySet()));
            stamp.put("sha256", DigestUtil.sha256Hex(CODEC.writeValueAsBytes(normalized)));
            return stamp;
        } catch (Exception ignored) { throw new IllegalStateException("报告源数据摘要生成失败；原始内容已省略"); }
    }
    static boolean matches(String storedJson) {
        try {
            Map<String,Object> payload = CODEC.readValue(storedJson,Map.class);
            Map<?,?> stamp = (Map<?,?>) payload.get("sourceSnapshot");
            return stamp != null && "rehab-report-source-v1".equals(stamp.get("schema"))
                    && "canonical-persisted-json-v1".equals(stamp.get("codec"))
                    && Objects.equals(stamp.get("sha256"),DigestUtil.sha256Hex(CODEC.writeValueAsBytes(fields(payload))));
        } catch (Exception ignored) { return false; }
    }
}
