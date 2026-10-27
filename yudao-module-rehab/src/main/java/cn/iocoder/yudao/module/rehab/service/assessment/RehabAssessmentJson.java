package cn.iocoder.yudao.module.rehab.service.assessment;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. Scoped clinical parsing, no diagnostics. */
final class RehabAssessmentJson {
    private RehabAssessmentJson() { }

    static <T> T read(String text, TypeReference<T> type) {
        try {
            return JsonUtils.getObjectMapper().readValue(text, type);
        } catch (IOException | IllegalArgumentException ignored) {
            // The general helper logs the raw JSON and Jackson cause before throwing.
            // Keep strict parse semantics but never retain either in this exception.
            throw new IllegalArgumentException("评估JSON数据格式无效");
        }
    }
}
