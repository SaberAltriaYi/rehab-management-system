package cn.iocoder.yudao.module.rehab.service.reporter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Adapter for the reporter-compatible engine endpoint.
 *
 * The engine remains the source of calculated evidence. This client only
 * requests the generated report payload and never turns warnings into a
 * clinical conclusion.
 */
@Component
public class RehabReporterClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String bearerToken;

    @Autowired
    public RehabReporterClient(RestTemplateBuilder builder,
                               @Value("${rehab.reporter-engine.base-url:http://127.0.0.1:8790}") String baseUrl,
                               @Value("${rehab.reporter-engine.token:}") String bearerToken) {
        this(builder.build(), baseUrl, bearerToken);
    }

    public RehabReporterClient(RestTemplate restTemplate, String baseUrl, String bearerToken) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.bearerToken = bearerToken;
    }

    public Map<String, Object> fetchRehearsalReport() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/v1/reports/rehearsal",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("REPORTER_ENGINE_REQUEST_FAILED");
        }
        return response.getBody();
    }
}
