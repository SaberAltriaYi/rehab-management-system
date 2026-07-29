package cn.iocoder.yudao.framework.apilog.core.interceptor;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiAccessLogInterceptorTest {

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
