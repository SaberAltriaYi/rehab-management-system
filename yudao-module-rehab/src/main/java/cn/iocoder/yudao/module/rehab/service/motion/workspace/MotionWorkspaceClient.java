package cn.iocoder.yudao.module.rehab.service.motion.workspace;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Fixed, operator-configured service destination. No patient payload or tokens in logs. */
@Component
public class MotionWorkspaceClient {
    @Value("${REHAB_ASSESSMENT_SERVICE_URL:}")
    private String serviceUrl;

    @Value("${REHAB_ASSESSMENT_PUBLIC_URL:}")
    private String publicUrl;

    @Value("${REHAB_CORE_BRIDGE_TOKEN:}")
    private String token;

    public MotionWorkspaceClient() {}

    public MotionWorkspaceClient(String serviceUrl, String publicUrl, String token) {
        this.serviceUrl = serviceUrl;
        this.publicUrl = publicUrl;
        this.token = token;
    }

    public static String checkedUrl(String value) {
        try {
            URI u = URI.create(value);
            boolean local =
                    "127.0.0.1".equals(u.getHost())
                            || "localhost".equals(u.getHost())
                            || "[::1]".equals(u.getHost());
            if (u.getHost() == null
                    || u.getUserInfo() != null
                    || u.getQuery() != null
                    || u.getFragment() != null
                    || !("https".equals(u.getScheme())
                            || (local && "http".equals(u.getScheme())))) {
                throw new IllegalArgumentException();
            }
            return value.replaceAll("/+$", "");
        } catch (RuntimeException ex) {
            throw new IllegalStateException("运动评估地址未配置或不安全；非本机连接必须使用可信 HTTPS");
        }
    }

    public String entryUrl(String ticket) {
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{40,80}"))
            throw new IllegalStateException("进入凭证无效");
        return checkedUrl(publicUrl) + "/#launch=" + ticket;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> exchange(
            String path, HttpMethod method, Map<String, Object> payload) {
        String base = checkedUrl(serviceUrl);
        if (token == null || token.length() < 32) throw new IllegalStateException("未配置运动评估强服务令牌");
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory() {
                    @Override
                    protected void prepareConnection(
                            java.net.HttpURLConnection connection, String method)
                            throws java.io.IOException {
                        super.prepareConnection(connection, method);
                        connection.setInstanceFollowRedirects(false);
                    }
                };
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        RestTemplate rest = new RestTemplate(factory);
        try {
            byte[] result =
                    rest.execute(
                            base + path,
                            method,
                            request -> {
                                request.getHeaders().setBearerAuth(token);
                                request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                                if (payload != null)
                                    request.getBody()
                                            .write(
                                                    JsonUtils.toJsonString(payload)
                                                            .getBytes(StandardCharsets.UTF_8));
                            },
                            response -> {
                                ByteArrayOutputStream out = new ByteArrayOutputStream();
                                byte[] buffer = new byte[8192];
                                int n;
                                while ((n = response.getBody().read(buffer)) != -1) {
                                    if (out.size() + n > 2 * 1024 * 1024)
                                        throw new java.io.IOException("response limit");
                                    out.write(buffer, 0, n);
                                }
                                return out.toByteArray();
                            });
            return MotionWorkspaceJson.read(result);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("运动评估服务请求失败：请检查服务、迁移、连接配置、证书和是否已提交结果");
        }
    }
}
