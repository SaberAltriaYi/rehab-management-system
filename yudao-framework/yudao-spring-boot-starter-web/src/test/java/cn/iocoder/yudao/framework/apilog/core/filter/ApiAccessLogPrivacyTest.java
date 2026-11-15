package cn.iocoder.yudao.framework.apilog.core.filter;

import cn.iocoder.yudao.framework.apilog.core.util.ApiLogPrivacy;
import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiAccessLogCommonApi;
import cn.iocoder.yudao.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ApiAccessLogPrivacyTest {
    private Object previousWebProperties;

    @BeforeEach
    void configureWebUtility() {
        previousWebProperties = ReflectionTestUtils.getField(WebFrameworkUtils.class, "properties");
        new WebFrameworkUtils(new WebProperties());
    }

    @AfterEach
    void restoreWebUtility() {
        ReflectionTestUtils.setField(WebFrameworkUtils.class, "properties", previousWebProperties);
    }

    @Test
    void authenticationAndClinicalPathsOmitPayloadIncludingEncodedAndMalformedPaths() {
        for (String path : new String[]{"/app-api/app-patient/auth/bind", "/app-api/member/auth/sms-login",
                "/admin-api/rehab/patient/app-invitation", "/admin-api/app-admin/patients/summary",
                "/admin-api/system/auth/login", "/app-api/app-patient/checkin/create",
                "/app-api/member/user/profile", "/app-api/app-patient/%61uth/bind", "/invalid%"}) {
            assertTrue(ApiLogPrivacy.omitPayload(path, null), path);
            ApiAccessLogCreateReqDTO result = build(path, "{\"invitationCode\":\"SYNTHETIC_INVITE\"}");
            assertNull(result.getRequestParams(), path);
            assertNull(result.getResponseBody(), path);
            assertEquals(path, result.getRequestUrl());
            assertEquals("POST", result.getRequestMethod());
            assertNotNull(result.getDuration());
        }
    }

    @Test
    void generalEndpointsRetainMetadataButStripInvitationAndSmsFields() {
        ApiAccessLogCreateReqDTO result = build("/admin-api/example/create",
                "{\"invitationCode\":\"SYNTHETIC_INVITE\",\"code\":\"SYNTHETIC_SMS\",\"safe\":\"retained\"}");
        assertNotNull(result.getRequestParams());
        assertTrue(result.getRequestParams().contains("retained"));
        assertFalse(result.getRequestParams().contains("SYNTHETIC_INVITE"));
        assertFalse(result.getRequestParams().contains("SYNTHETIC_SMS"));
    }

    @Test
    void malformedJsonDoesNotFallBackToUnredactedPayload() {
        Logger parserLogger = (Logger) LoggerFactory.getLogger(JsonUtils.class);
        Logger filterLogger = (Logger) LoggerFactory.getLogger(ApiAccessLogFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        parserLogger.addAppender(appender);
        filterLogger.addAppender(appender);
        try {
            ApiAccessLogCreateReqDTO result = build("/admin-api/example/create", "SYNTHETIC_UNPARSEABLE_SECRET");
            assertFalse(result.getRequestParams().contains("SYNTHETIC_UNPARSEABLE_SECRET"));
            assertTrue(appender.list.stream().noneMatch(event ->
                    event.getFormattedMessage().contains("SYNTHETIC_UNPARSEABLE_SECRET")));
        } finally {
            parserLogger.detachAppender(appender);
            filterLogger.detachAppender(appender);
            appender.stop();
        }
    }

    private ApiAccessLogCreateReqDTO build(String path, String body) {
        ApiAccessLogFilter filter = new ApiAccessLogFilter(new WebProperties(), "synthetic-test",
                mock(ApiAccessLogCommonApi.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("invitationCode", "SYNTHETIC_QUERY_INVITE");
        ApiAccessLogCreateReqDTO result = new ApiAccessLogCreateReqDTO();
        Boolean enabled = ReflectionTestUtils.invokeMethod(filter, "buildApiAccessLog",
                result, request, LocalDateTime.now(), query, body, null);
        assertEquals(Boolean.TRUE, enabled);
        assertFalse(String.valueOf(result.getRequestParams()).contains("SYNTHETIC_QUERY_INVITE"));
        return result;
    }
}
