package cn.iocoder.yudao.framework.apilog.core.interceptor;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import cn.iocoder.yudao.framework.common.util.spring.SpringUtils;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mockStatic;

class ApiAccessLogInterceptorTest {

    @Test
    void enabledDevelopmentLogStillOmitsClinicalInvitationsAndSmsPayloads() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ApiAccessLogInterceptor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class)) {
            spring.when(SpringUtils::isProd).thenReturn(false);
            ApiAccessLogInterceptor interceptor = new ApiAccessLogInterceptor(true);
            for (String path : new String[]{"/app-api/app-patient/auth/bind",
                    "/app-api/member/auth/sms-login", "/admin-api/rehab/patient/app-invitation"}) {
                MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
                request.setContentType("application/json");
                request.setContent("{\"invitationCode\":\"SYNTHETIC_PRIVATE_INVITE\"}"
                        .getBytes(StandardCharsets.UTF_8));
                request.addParameter("code", "SYNTHETIC_PRIVATE_SMS");
                MockHttpServletResponse response = new MockHttpServletResponse();
                assertTrue(interceptor.preHandle(request, response, null));
                interceptor.afterCompletion(request, response, null, null);
            }
            String messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n"));
            assertTrue(messages.contains("参数已按隐私策略省略"));
            assertFalse(messages.contains("SYNTHETIC_PRIVATE_INVITE"));
            assertFalse(messages.contains("SYNTHETIC_PRIVATE_SMS"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void disabledConsoleLog_shouldNotPrintSensitiveRequestBody() throws Exception {
        ApiAccessLogInterceptor interceptor = new ApiAccessLogInterceptor(false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin-api/system/auth/login");
        request.setContentType("application/json");
        request.setContent("{\"username\":\"tester\",\"password\":\"secret\"}"
                .getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(),
                TestController.class.getDeclaredMethod("login"));

        Logger logger = (Logger) LoggerFactory.getLogger(ApiAccessLogInterceptor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertTrue(interceptor.preHandle(request, response, handlerMethod));
            interceptor.afterCompletion(request, response, handlerMethod, null);
            assertTrue(appender.list.isEmpty());
            assertSame(handlerMethod, request.getAttribute(ApiAccessLogInterceptor.ATTRIBUTE_HANDLER_METHOD));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static class TestController {

        @SuppressWarnings("unused")
        public void login() {
        }
    }

}
