package cn.iocoder.yudao.module.system.framework.license;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Per-tenant owner status sync. A failed poll NEVER marks the lease active:
 * existing RHR1 status expires in <=72h and business guards fail closed.
 *
 * HTTPS relies on operator-installed trust roots, not a custom trust-all
 * manager. The response must pass pinned Ed25519 verification before storage.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "yudao.license", name = "enforce", havingValue = "true")
public class TenantLicenseStatusSynchronizer {
    @Value("${yudao.license.owner-status-url:}")
    private String ownerUrl;
    private final JdbcTemplate jdbc;
    private final TenantLicenseImportService importer;
    private final ObjectMapper mapper = new ObjectMapper();
    private int nextTenantIndex;

    public TenantLicenseStatusSynchronizer(JdbcTemplate jdbc, TenantLicenseImportService importer) {
        this.jdbc = jdbc;
        this.importer = importer;
    }

    @PostConstruct
    public void validateUrl() {
        if (ownerUrl == null || ownerUrl.isEmpty()) return; // deliberate offline-only operation
        try {
            URL url = new URL(ownerUrl);
            if (!"https".equals(url.getProtocol()) || url.getHost().isEmpty()
                    || url.toURI().getUserInfo() != null || url.getRef() != null || url.getQuery() != null
                    || !url.getPath().equals("/license/v1/status")) {
                throw new IllegalStateException("Owner status URL must be a fixed HTTPS status endpoint");
            }
        } catch (java.net.MalformedURLException | java.net.URISyntaxException ex) {
            throw new IllegalStateException("Malformed owner status URL", ex);
        }
    }

    @Scheduled(initialDelayString = "${yudao.license.status-poll-initial-delay-ms:60000}",
            fixedDelayString = "${yudao.license.status-poll-delay-ms:300000}")
    public synchronized void syncAll() {
        if (ownerUrl == null || ownerUrl.isEmpty()) return;
        final List<Map<String, Object>> bindings;
        try {
            bindings = jdbc.queryForList(
                    "SELECT tenant_id,tenant_uuid,installation_uuid FROM rehab_tenant_license_binding"
                            + " ORDER BY tenant_id");
        } catch (RuntimeException ex) {
            log.warn("[tenantLicenseStatusSync] license binding store unavailable");
            return;
        }
        if (bindings.isEmpty()) return;
        // Publisher is limited to 60 calls/IP/minute. Round-robin fifty
        // tenants per poll without starving later tenant IDs.
        int batchSize = Math.min(50, bindings.size());
        for (int index = 0; index < batchSize; index++) {
            Map<String, Object> binding =
                    bindings.get((nextTenantIndex + index) % bindings.size());
            try {
                Long tenantId = ((Number) binding.get("tenant_id")).longValue();
                String tenantUuid = (String) binding.get("tenant_uuid");
                String installationUuid = (String) binding.get("installation_uuid");
                String statusProof = fetchSignedStatus(tenantUuid, installationUuid);
                // Verifies publisher signature, matching installation, epoch
                // progression and monotonic server time in one DB transaction.
                importer.refreshSignedStatus(tenantId, statusProof);
            } catch (Exception ex) {
                // Never log API response bodies, signed tokens or clinical data.
                log.warn("[tenantLicenseStatusSync] one tenant renewal unavailable, existing expiry remains");
            }
        }
        nextTenantIndex = (nextTenantIndex + batchSize) % bindings.size();
    }

    protected String fetchSignedStatus(String tenantUuid, String installationUuid) throws Exception {
        URL url = new URL(ownerUrl);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        try {
            java.util.Map<String, String> body = new java.util.LinkedHashMap<>();
            body.put("tenantId", tenantUuid);
            body.put("installationId", installationUuid);
            byte[] requestBody = mapper.writeValueAsBytes(body);
            connection.getOutputStream().write(requestBody);
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException("Owner status endpoint returned non-success status");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[1024];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (out.size() + n > 8192) throw new IllegalStateException("Owner status response is too large");
                    out.write(buffer, 0, n);
                }
            }
            JsonNode json = mapper.readTree(out.toByteArray());
            if (json == null || json.size() != 1 || !json.has("statusProof")
                    || !json.path("statusProof").isTextual()) {
                throw new IllegalStateException("Owner status response is not a single signed proof");
            }
            return json.path("statusProof").asText();
        } finally {
            connection.disconnect();
        }
    }
}
