package cn.iocoder.yudao.module.system.framework.license;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * L3 activation handoff on the built-in single studio tenant (ID=1).
 * Reads only a signed, read-only startup manifest. Never accepts remote
 * request parameters or unsigned local DB rows as licensing authority.
 */
@Component
@ConditionalOnProperty(prefix = "yudao.license", name = "enforce", havingValue = "true")
public class TenantLicenseBootImporter implements ApplicationRunner {
    private static final long STUDIO_TENANT_ID = 1L;
    private static final Set<String> FIELDS = new HashSet<>(Arrays.asList(
            "schemaVersion", "tenantId", "installationId", "token", "statusProof"));

    private final TenantLicenseImportService importer;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Value("${yudao.license.bootstrap-manifest:/app/license/activation.json}")
    private String manifestPath;

    public TenantLicenseBootImporter(TenantLicenseImportService importer,
                                     JdbcTemplate jdbc, ObjectMapper mapper) {
        this.importer = importer;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Path path = Paths.get(manifestPath).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(path) ||
                !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ||
                Files.size(path) > 20000) {
            throw new IllegalStateException("Signed startup activation manifest is missing or unsafe");
        }
        JsonNode node = mapper.readTree(Files.readAllBytes(path));
        if (node == null || !node.isObject() || node.size() != 5 ||
                !FIELDS.equals(fieldSet(node)) ||
                !node.path("schemaVersion").isIntegralNumber() ||
                node.path("schemaVersion").asInt() != 1) {
            throw new IllegalStateException("Invalid signed startup manifest structure");
        }
        for (String key : Arrays.asList("tenantId", "installationId", "token", "statusProof")) {
            if (!node.path(key).isTextual()) {
                throw new IllegalStateException("Startup activation manifest has malformed fields");
            }
        }
        // The v1 desktop contains only the built-in studio (tenant 1).
        // Multi-tenant imports require a separately reviewed owner binding.
        Integer present = jdbc.queryForObject(
                "SELECT COUNT(*) FROM system_tenant WHERE id=? AND deleted=0",
                Integer.class, STUDIO_TENANT_ID);
        if (present == null || present != 1) {
            throw new IllegalStateException("Built-in studio tenant not found");
        }
        // This transaction verifies owner signatures and denies any
        // unauthorized rebind of existing tenant/installation identifiers.
        importer.importSignedLease(STUDIO_TENANT_ID,
                node.get("tenantId").asText(), node.get("installationId").asText(),
                node.get("token").asText(), node.get("statusProof").asText());
    }

    private static Set<String> fieldSet(JsonNode node) {
        Set<String> result = new HashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }
}
