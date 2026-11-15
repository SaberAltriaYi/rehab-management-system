package cn.iocoder.yudao.module.rehab.service.assessment;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. Synthetic clinical markers only. */
@ExtendWith(MockitoExtension.class)
class RehabAssessmentSummaryPrivacyTest {
    private RehabAssessmentServiceImpl service;
    @Mock private RehabStaticAssessmentSummaryBuilder staticBuilder;
    @Mock private RehabNasmCesSummaryBuilder nasmBuilder;
    @Mock private RehabSfmaSummaryBuilder sfmaBuilder;
    private Logger logger;
    private ListAppender<ILoggingEvent> logs;
    private final Map<String, Object> raw = new LinkedHashMap<>();

    @BeforeEach void open() {
        service = new RehabAssessmentServiceImpl();
        ReflectionTestUtils.setField(service, "staticAssessmentSummaryBuilder", staticBuilder);
        ReflectionTestUtils.setField(service, "nasmCesSummaryBuilder", nasmBuilder);
        ReflectionTestUtils.setField(service, "sfmaSummaryBuilder", sfmaBuilder);
        raw.put("zero", 0); raw.put("sourceNote", "SYNTHETIC_RAW_KEEP");
        logger = (Logger) LoggerFactory.getLogger(RehabAssessmentServiceImpl.class);
        logs = new ListAppender<>(); logs.start(); logger.addAppender(logs);
    }
    @AfterEach void close() { logger.detachAppender(logs); logs.stop(); }

    @ParameterizedTest
    @CsvSource({"static,false", "static,true", "nasm,false", "nasm,true", "sfma,false", "sfma,true"})
    void errorMessagesAndCausesAreNeverDiagnosticsOrFallbackReason(String module, boolean failsFallback) {
        RuntimeException privateError = new IllegalArgumentException("SYNTHETIC_SECRET_CLINICAL",
                new IllegalStateException("SYNTHETIC_SECRET_CAUSE"));
        Map<String, Object> conservative = new LinkedHashMap<>(raw);
        conservative.put("summaryStatus", "fallback");
        if ("static".equals(module)) {
            when(staticBuilder.enrichWithSummary(raw)).thenThrow(privateError);
            if (failsFallback) when(staticBuilder.enrichWithFallback(eq(raw), anyString())).thenThrow(privateError);
            else when(staticBuilder.enrichWithFallback(eq(raw), anyString())).thenReturn(conservative);
        } else if ("nasm".equals(module)) {
            when(nasmBuilder.enrichWithSummary(raw)).thenThrow(privateError);
            if (failsFallback) when(nasmBuilder.enrichWithFallback(eq(raw), anyString())).thenThrow(privateError);
            else when(nasmBuilder.enrichWithFallback(eq(raw), anyString())).thenReturn(conservative);
        } else {
            when(sfmaBuilder.enrichWithSummary(raw)).thenThrow(privateError);
            if (failsFallback) when(sfmaBuilder.enrichWithFallback(eq(raw), anyString())).thenThrow(privateError);
            else when(sfmaBuilder.enrichWithFallback(eq(raw), anyString())).thenReturn(conservative);
        }
        String result = ReflectionTestUtils.invokeMethod(service, "buildAndSerializeModuleData", module, raw);
        assertEquals(JsonUtils.toJsonString(failsFallback ? raw : conservative), result);
        assertEquals(0, raw.get("zero")); assertEquals("SYNTHETIC_RAW_KEEP", raw.get("sourceNote"));
        assertEquals(failsFallback ? 2 : 1, logs.list.size());
        for (ILoggingEvent event : logs.list) {
            assertFalse(event.getFormattedMessage().contains("SYNTHETIC_"), "summary diagnostic retained private text");
            assertNull(event.getThrowableProxy(), "summary diagnostic retained private cause or stack");
            assertNull(event.getArgumentArray(), "summary diagnostic retained private arguments");
        }
        if ("static".equals(module)) verify(staticBuilder).enrichWithFallback(raw, "自动摘要未完成，请人工复核");
        else if ("nasm".equals(module)) verify(nasmBuilder).enrichWithFallback(raw, "自动摘要未完成，请人工复核");
        else verify(sfmaBuilder).enrichWithFallback(raw, "自动摘要未完成，请人工复核");
    }

    @Test void otherRawModulesAreStillSerializedWithoutSummarySideEffects() {
        assertEquals(JsonUtils.toJsonString(raw), ReflectionTestUtils.invokeMethod(service, "buildAndSerializeModuleData", "fms", raw));
        assertTrue(logs.list.isEmpty()); verifyNoInteractions(staticBuilder, nasmBuilder, sfmaBuilder);
    }
}
