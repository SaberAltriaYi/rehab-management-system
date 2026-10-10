package cn.iocoder.yudao.module.system.framework.license;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Synthetic service-level refusal and anti-rollback tests. Transactional
 * isolation itself is separately exercised against disposable MySQL 8.4.
 */
public class TenantLicenseImportServiceTest {
    private static final String TENANT = "f12b2687-09a1-40b5-b47a-747d75ce5551";
    private static final String DEVICE = "d94123bf-2c94-4af4-9d5b-7ab8ce690509";
    private static final long T = 1791600000000L;

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TenantLicenseGuard verifier = mock(TenantLicenseGuard.class);
    private final TenantLicenseImportService service = new TenantLicenseImportService(jdbc, verifier);

    private Map<String, Object> lease() {
        Map<String, Object> result = new HashMap<>();
        result.put("revocationEpoch", 3);
        result.put("issuedAt", "2026-10-10T02:40:00.000Z");
        return result;
    }
    private Map<String, Object> status(String state, long epoch, String observedAt) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", state);
        result.put("revocationEpoch", epoch);
        result.put("observedAt", observedAt);
        return result;
    }
    private Map<String, Object> oldBinding(long epoch, long time) {
        Map<String, Object> row = new HashMap<>();
        row.put("tenant_uuid", TENANT);
        row.put("installation_uuid", DEVICE);
        row.put("revocation_epoch", epoch);
        row.put("trusted_time_ms", time);
        return row;
    }
    @Test public void cannotImportForUnknownNumericTenant() {
        assertEquals("INVALID_VERIFICATION_CONTEXT", assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class,
                () -> service.importSignedLease(null, TENANT, DEVICE, "lease", "status")).getCode());
        verifyNoInteractions(jdbc, verifier);
    }
    @Test public void firstActivationRequiresTwoAuthenticallySignedProofs() {
        when(jdbc.queryForList(anyString(), eq(88L))).thenReturn(Collections.emptyList());
        when(verifier.verifyExternal(eq(TENANT), eq(DEVICE), eq("lease"), anyLong(), anyLong()))
                .thenReturn(lease());
        when(verifier.verifyStatusExternal(eq(TENANT), eq(DEVICE), eq("status"), anyLong()))
                .thenReturn(status("ACTIVE", 3, "2026-10-10T02:40:00.000Z"));
        service.importSignedLease(88L, TENANT, DEVICE, "lease", "status");
        verify(jdbc, times(1)).update(contains("INSERT INTO rehab_tenant_license_binding"),
                eq(88L), eq(TENANT), eq(DEVICE), eq("lease"), eq("status"), eq(3L), eq(T));
        verify(verifier, times(2)).verifyExternal(eq(TENANT), eq(DEVICE),
                eq("lease"), anyLong(), anyLong());
    }
    @Test public void rejectedStatusDoesNotWriteAnActivation() {
        when(jdbc.queryForList(anyString(), eq(88L))).thenReturn(Collections.emptyList());
        when(verifier.verifyExternal(anyString(), anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(lease());
        when(verifier.verifyStatusExternal(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(status("REVOKED", 4, "2026-10-10T02:40:00.000Z"));
        assertEquals("TENANT_NOT_ACTIVE", assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () ->
                        service.importSignedLease(88L, TENANT, DEVICE, "lease", "status")).getCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test public void tenantRebindingRequiresSeparatelyApprovedDeviceReplacement() {
        Map<String, Object> wrong = oldBinding(3, T);
        wrong.put("installation_uuid", "d7e25109-ff6a-488a-bccc-6b5266830c73");
        when(jdbc.queryForList(anyString(), eq(88L))).thenReturn(Collections.singletonList(wrong));
        when(verifier.verifyExternal(anyString(), anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(lease());
        when(verifier.verifyStatusExternal(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(status("ACTIVE", 3, "2026-10-10T02:40:00.000Z"));
        assertEquals("LICENSE_REBIND_REQUIRES_APPROVAL", assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () ->
                        service.importSignedLease(88L, TENANT, DEVICE, "lease", "status")).getCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test public void independentlySignedRevocationStateAdvancesMonotonically() {
        when(jdbc.queryForList(anyString(), eq(88L)))
                .thenReturn(Collections.singletonList(oldBinding(3, T)));
        when(verifier.verifyStatusExternal(eq(TENANT), eq(DEVICE), eq("new-status"), eq(T)))
                .thenReturn(status("REVOKED", 4, "2026-10-10T02:41:00.000Z"));
        service.refreshSignedStatus(88L, "new-status");
        verify(jdbc).update(contains("SET status_proof"), eq("new-status"), eq(4L),
                eq(T + 60_000), eq(88L));
    }
    @Test public void olderSignedStatusCannotRollBackOwnerEpoch() {
        when(jdbc.queryForList(anyString(), eq(88L)))
                .thenReturn(Collections.singletonList(oldBinding(9, T)));
        when(verifier.verifyStatusExternal(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(status("ACTIVE", 8, "2026-10-10T02:41:00.000Z"));
        assertEquals("STATUS_ROLLBACK", assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () ->
                        service.refreshSignedStatus(88L, "old-state")).getCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test public void oldStatusTimeCannotBeUsedToTurnBackClock() {
        when(jdbc.queryForList(anyString(), eq(88L)))
                .thenReturn(Collections.singletonList(oldBinding(3, T + 60_000)));
        when(verifier.verifyStatusExternal(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(status("ACTIVE", 3, "2026-10-10T02:40:00.000Z"));
        assertEquals("STATUS_ROLLBACK", assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () ->
                        service.refreshSignedStatus(88L, "old-time")).getCode());
    }
}
