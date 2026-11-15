package cn.iocoder.yudao.module.rehab.service.assessment;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. Malformed synthetic inputs only. */
class RehabAssessmentJsonPrivacyTest {
    private Logger logger;
    private ListAppender<ILoggingEvent> logs;
    @BeforeEach void open() {
        logger = (Logger) LoggerFactory.getLogger(JsonUtils.class);
        logs = new ListAppender<>(); logs.start(); logger.addAppender(logs);
    }
    @AfterEach void close() { logger.detachAppender(logs); logs.stop(); }
    private Map<String, Object> normalize(String module, String text) {
        if ("protocol".equals(module)) {
            return ReflectionTestUtils.invokeMethod(RehabSfmaBookProtocol.class, "normalizeToMap", text);
        }
        Object builder = "static".equals(module) ? new RehabStaticAssessmentSummaryBuilder()
                : "nasm".equals(module) ? new RehabNasmCesSummaryBuilder() : new RehabSfmaSummaryBuilder();
        return ReflectionTestUtils.invokeMethod(builder, "normalizeToMap", text);
    }
    @ParameterizedTest
    @CsvSource({"static,false", "nasm,false", "sfma,true", "protocol,true"})
    void malformedClinicalJsonDoesNotReachGeneralParserDiagnostics(String module, boolean strict) {
        String invalid = "{\"sourceNote\":\"SYNTHETIC_SECRET_PARSE\"";
        if (strict) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> normalize(module, invalid));
            assertEquals("评估JSON数据格式无效", error.getMessage()); assertNull(error.getCause());
        } else assertTrue(normalize(module, invalid).isEmpty());
        assertTrue(logs.list.isEmpty(), "general parser emitted private diagnostics");
    }
    @ParameterizedTest
    @ValueSource(strings = {"static", "nasm", "sfma", "protocol"})
    void validRawNumbersAndNotesAreNotChanged(String module) {
        Map<String, Object> parsed = normalize(module, "{\"zero\":0,\"negative\":-2.5,\"sourceNote\":\"SYNTHETIC_RAW_KEEP\"}");
        assertEquals(0, parsed.get("zero")); assertEquals(-2.5, ((Number) parsed.get("negative")).doubleValue());
        assertEquals("SYNTHETIC_RAW_KEEP", parsed.get("sourceNote")); assertTrue(logs.list.isEmpty());
    }
}
