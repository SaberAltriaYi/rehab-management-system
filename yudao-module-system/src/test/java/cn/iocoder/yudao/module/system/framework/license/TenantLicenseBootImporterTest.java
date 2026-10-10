package cn.iocoder.yudao.module.system.framework.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Read-only L3 activation handoff tests with synthetic mocked SQL. */
public class TenantLicenseBootImporterTest {
    @TempDir Path folder;
    private final TenantLicenseImportService importer = mock(TenantLicenseImportService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private TenantLicenseBootImporter from(Path manifest) {
        TenantLicenseBootImporter loader = new TenantLicenseBootImporter(
                importer, jdbc, new ObjectMapper());
        ReflectionTestUtils.setField(loader, "manifestPath", manifest.toString());
        return loader;
    }
    private String syntheticManifest() {
        return "{\"schemaVersion\":1,"
                + "\"tenantId\":\"f12b2687-09a1-40b5-b47a-747d75ce5551\","
                + "\"installationId\":\"d94123bf-2c94-4af4-9d5b-7ab8ce690509\","
                + "\"token\":\"synthetic-invalid-lease\","
                + "\"statusProof\":\"synthetic-invalid-status\"}";
    }
    private void write(Path path, String value) throws Exception {
        Files.write(path, value.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void missingActivationManifestFailsBeforeTouchingClinicalDb() {
        assertThrows(IllegalStateException.class, () ->
                from(folder.resolve("absent.json")).run(new DefaultApplicationArguments()));
        verifyNoInteractions(importer, jdbc);
    }
    @Test
    public void malformedOrExtraActivationFieldsFailClosed() throws Exception {
        Path file = folder.resolve("invalid.json");
        write(file, "{\"schemaVersion\":1,\"unexpected\":true}");
        assertThrows(IllegalStateException.class, () ->
                from(file).run(new DefaultApplicationArguments()));
        verifyNoInteractions(importer, jdbc);
    }
    @Test
    public void approvedInstallationIsBoundOnlyToBuiltInTenantOne() throws Exception {
        Path file = folder.resolve("signed-fixture.json");
        write(file, syntheticManifest());
        when(jdbc.queryForObject(contains("FROM system_tenant"), eq(Integer.class), eq(1L)))
                .thenReturn(1);
        from(file).run(new DefaultApplicationArguments());
        verify(importer).importSignedLease(eq(1L),
                eq("f12b2687-09a1-40b5-b47a-747d75ce5551"),
                eq("d94123bf-2c94-4af4-9d5b-7ab8ce690509"),
                eq("synthetic-invalid-lease"), eq("synthetic-invalid-status"));
        // Mock importer only tests plumbing. Production importer independently
        // verifies both owner-signed proofs before DB commit.
    }
    @Test
    public void missingStudioCannotSilentlyBeCreatedOrMappedToAnotherTenant() throws Exception {
        Path file = folder.resolve("existing.json");
        write(file, syntheticManifest());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(0);
        assertThrows(IllegalStateException.class, () ->
                from(file).run(new DefaultApplicationArguments()));
        verifyNoInteractions(importer);
    }
    @Test
    public void invalidOwnerSignedLeasePreventsServerBootstrap() throws Exception {
        Path file = folder.resolve("tampered.json");
        write(file, syntheticManifest());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(1);
        doThrow(new LicenseLeaseVerifier.LicenseFailure("INVALID_SIGNATURE"))
                .when(importer).importSignedLease(anyLong(), anyString(), anyString(), anyString(), anyString());
        assertEquals("INVALID_SIGNATURE", assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () ->
                        from(file).run(new DefaultApplicationArguments())).getCode());
    }
}
