package cn.iocoder.yudao.module.rehab.service.motion.engine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 引擎客户端：真实 HTTP（JDK HttpServer）验证重试分类、超时、Bearer 认证、错误信息脱敏。
 */
class HttpMotionEngineClientTest {

    private static final String TOKEN = "test-token-0123456789abcdef";

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> body = new AtomicReference<String>("{\"protocols\":[]}");
    private final AtomicReference<String> auth = new AtomicReference<String>();
    private final AtomicInteger delayMs = new AtomicInteger(0);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                auth.set(ex.getRequestHeaders().getFirst("Authorization"));
                if (delayMs.get() > 0) {
                    try {
                        Thread.sleep(delayMs.get());
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                byte[] b = body.get().getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.sendResponseHeaders(status.get(), b.length);
                OutputStream os = ex.getResponseBody();
                os.write(b);
                os.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HttpMotionEngineClient client(int readTimeoutMs) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(1000);
        f.setReadTimeout(readTimeoutMs);
        return new HttpMotionEngineClient("http://127.0.0.1:" + server.getAddress().getPort() + "/", TOKEN,
                new RestTemplate(f));
    }

    private static MotionEngineException call(HttpMotionEngineClient c) {
        return assertThrows(MotionEngineException.class, c::protocols);
    }

    @Test
    void successSendsBearerToken() {
        Map<String, Object> r = client(2000).protocols();
        assertTrue(r.containsKey("protocols"));
        assertEquals("Bearer " + TOKEN, auth.get());
    }

    @Test
    void serverErrorsAndRateLimitAreRetryable() {
        status.set(503);
        body.set("{\"error\":\"ENGINE_BUSY\"}");
        MotionEngineException e = call(client(2000));
        assertTrue(e.isRetryable());
        assertEquals("ENGINE_BUSY", e.getCode());
        status.set(429);
        body.set("too many");
        e = call(client(2000));
        assertTrue(e.isRetryable());
        assertEquals("HTTP_429", e.getCode());
    }

    @Test
    void authAndValidationErrorsAreFatal() {
        status.set(401);
        body.set("{\"error\":\"UNAUTHORIZED\"}");
        MotionEngineException e = call(client(2000));
        assertFalse(e.isRetryable());
        status.set(422);
        body.set("{\"error\":\"BUNDLE_INVALID\",\"detail\":\"patient 张三 file x\"}");
        e = call(client(2000));
        assertFalse(e.isRetryable());
        assertEquals("BUNDLE_INVALID", e.getCode());
        assertFalse(e.getMessage().contains("张三"), "不得回传引擎响应中的任意文本");
    }

    @Test
    void timeoutIsRetryableAndMessageHasNoSecrets() {
        delayMs.set(1500);
        MotionEngineException e = call(client(200));
        assertEquals("MOTION_ENGINE_UNREACHABLE", e.getCode());
        assertTrue(e.isRetryable());
        assertFalse(e.getMessage().contains(TOKEN));
    }

    @Test
    void unconfiguredAndInvalidIdsFailFastWithoutNetwork() {
        HttpMotionEngineClient shortToken = new HttpMotionEngineClient("http://127.0.0.1:1", "short", new RestTemplate());
        assertFalse(shortToken.isConfigured());
        MotionEngineException e = assertThrows(MotionEngineException.class, shortToken::protocols);
        assertEquals("MOTION_ENGINE_NOT_CONFIGURED", e.getCode());
        auth.set(null);
        e = assertThrows(MotionEngineException.class,
                () -> client(2000).opencapDownload("../../etc/passwd", Collections.<String>emptyList()));
        assertEquals("INVALID_OPENCAP_ID", e.getCode());
        assertFalse(e.isRetryable());
        assertNull(auth.get(), "非法 ID 不应发出请求");
    }

    @Test
    void extractCodeOnlyAcceptsSafeCodes() {
        assertEquals("OK_CODE", HttpMotionEngineClient.extractCode("{\"error\":\"OK_CODE\"}".getBytes(), 400));
        assertEquals("HTTP_400", HttpMotionEngineClient.extractCode("{\"error\":\"<script>\"}".getBytes(), 400));
        assertEquals("HTTP_500", HttpMotionEngineClient.extractCode("not json".getBytes(), 500));
        assertEquals("HTTP_502", HttpMotionEngineClient.extractCode(new byte[0], 502));
    }

}
