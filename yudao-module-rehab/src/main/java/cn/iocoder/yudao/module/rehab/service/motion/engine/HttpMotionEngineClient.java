package cn.iocoder.yudao.module.rehab.service.motion.engine;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 基于 RestTemplate 的引擎客户端。令牌只来自环境变量 MOTION_ENGINE_TOKEN，不落库、不写日志。
 * 超时：连接 5s；读取默认 180s（仅后台 worker 调用，不影响用户 HTTP 请求）。
 */
@Component
@Slf4j
public class HttpMotionEngineClient implements MotionEngineClient {

    private static final Pattern SAFE_CODE = Pattern.compile("^[A-Z0-9_]{2,64}$");
    private static final Pattern UUID_RE = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Value("${yudao.rehab.motion.engine-url:${MOTION_ENGINE_URL:}}")
    private String baseUrl;
    @Value("${yudao.rehab.motion.engine-token:${MOTION_ENGINE_TOKEN:}}")
    private String token;
    @Value("${yudao.rehab.motion.engine-connect-timeout-ms:5000}")
    private int connectTimeoutMs;
    @Value("${yudao.rehab.motion.engine-read-timeout-ms:180000}")
    private int readTimeoutMs;

    private volatile RestTemplate restTemplate;

    public HttpMotionEngineClient() {
    }

    /** 测试用 */
    public HttpMotionEngineClient(String baseUrl, String token, RestTemplate restTemplate) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean isConfigured() {
        return StrUtil.isNotBlank(baseUrl) && StrUtil.length(token) >= 16;
    }

    @Override
    public byte[] analyze(byte[] zip) {
        return exchange("/internal/v1/analyze", HttpMethod.POST, zip, MediaType.valueOf("application/zip"),
                byte[].class);
    }

    @Override
    public Map<String, Object> protocols() {
        byte[] body = exchange("/internal/v1/protocols", HttpMethod.GET, null, null, byte[].class);
        return parse(body);
    }

    @Override
    public Map<String, Object> opencapTrialStatus(String sessionId, List<String> trialIds) {
        Map<String, Object> req = new LinkedHashMap<String, Object>();
        req.put("session_id", checkUuid(sessionId));
        if (trialIds != null && !trialIds.isEmpty()) {
            for (String id : trialIds) {
                checkUuid(id);
            }
            req.put("trial_ids", trialIds);
        }
        byte[] body = exchange("/internal/v1/opencap/trial-status", HttpMethod.POST, JsonUtils.toJsonByte(req),
                MediaType.APPLICATION_JSON, byte[].class);
        return parse(body);
    }

    @Override
    public byte[] opencapDownload(String sessionId, List<String> trialIds) {
        Map<String, Object> req = new LinkedHashMap<String, Object>();
        req.put("session_id", checkUuid(sessionId));
        for (String id : trialIds) {
            checkUuid(id);
        }
        req.put("trial_ids", trialIds);
        return exchange("/internal/v1/opencap/download", HttpMethod.POST, JsonUtils.toJsonByte(req),
                MediaType.APPLICATION_JSON, byte[].class);
    }

    private static String checkUuid(String value) {
        if (value == null || !UUID_RE.matcher(value).matches()) {
            throw new MotionEngineException("INVALID_OPENCAP_ID", "OpenCap ID 必须是 UUID", false);
        }
        return value.toLowerCase();
    }

    private <T> T exchange(String path, HttpMethod method, byte[] body, MediaType contentType, Class<T> type) {
        if (!isConfigured()) {
            throw new MotionEngineException("MOTION_ENGINE_NOT_CONFIGURED",
                    "未配置 MOTION_ENGINE_URL / MOTION_ENGINE_TOKEN", false);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        String requestId = UUID.randomUUID().toString();
        headers.set("X-Request-ID", requestId);
        if (contentType != null) {
            headers.setContentType(contentType);
        }
        long started = System.currentTimeMillis();
        try {
            ResponseEntity<T> resp = template().exchange(StrUtil.removeSuffix(baseUrl, "/") + path, method,
                    new HttpEntity<byte[]>(body, headers), type);
            log.info("[motion-engine] path={} status={} latencyMs={} requestId={}", path,
                    resp.getStatusCodeValue(), System.currentTimeMillis() - started, requestId);
            return resp.getBody();
        } catch (HttpStatusCodeException ex) {
            int status = ex.getRawStatusCode();
            String code = extractCode(ex.getResponseBodyAsByteArray(), status);
            log.warn("[motion-engine] path={} status={} code={} latencyMs={} requestId={}", path, status, code,
                    System.currentTimeMillis() - started, requestId);
            boolean retryable = status >= 500 || status == 429;
            throw new MotionEngineException(code, "引擎返回 HTTP " + status + "（" + code + "）", retryable);
        } catch (ResourceAccessException ex) {
            log.warn("[motion-engine] path={} unreachable/timeout latencyMs={} requestId={}", path,
                    System.currentTimeMillis() - started, requestId);
            throw new MotionEngineException("MOTION_ENGINE_UNREACHABLE", "引擎连接失败或超时", true);
        } catch (RestClientException ex) {
            log.warn("[motion-engine] path={} client error={} requestId={}", path, ex.getClass().getSimpleName(),
                    requestId);
            throw new MotionEngineException("MOTION_ENGINE_ERROR", "引擎调用失败", true);
        }
    }

    /** 只提取引擎错误码（大写下划线），不回传任意响应文本。 */
    static String extractCode(byte[] body, int status) {
        try {
            Map<String, Object> map = JsonUtils.parseObject(body, new TypeReference<Map<String, Object>>() {
            }.getType());
            Object error = map == null ? null : map.get("error");
            if (error instanceof String && SAFE_CODE.matcher((String) error).matches()) {
                return (String) error;
            }
        } catch (RuntimeException ignored) {
            // 非 JSON
        }
        return "HTTP_" + status;
    }

    private static Map<String, Object> parse(byte[] body) {
        if (body == null) {
            throw new MotionEngineException("MOTION_ENGINE_EMPTY_RESPONSE", "引擎返回空响应", true);
        }
        try {
            return JsonUtils.parseObject(body, new TypeReference<Map<String, Object>>() {
            }.getType());
        } catch (RuntimeException ex) {
            throw new MotionEngineException("MOTION_ENGINE_BAD_RESPONSE", "引擎响应不是合法 JSON", false);
        }
    }

    private RestTemplate template() {
        RestTemplate local = restTemplate;
        if (local == null) {
            synchronized (this) {
                if (restTemplate == null) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(connectTimeoutMs);
                    factory.setReadTimeout(readTimeoutMs);
                    restTemplate = new RestTemplate(factory);
                }
                local = restTemplate;
            }
        }
        return local;
    }

}
