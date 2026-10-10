package cn.iocoder.yudao.module.system.framework.license;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Synthetic fail-closed configuration/store/seat tests; no real clinic database. */
public class TenantLicenseGuardTest {
    private TenantLicenseGuard create(JdbcTemplate jdbc, boolean enabled, String kid, String publicKey) {
        TenantLicenseGuard guard = new TenantLicenseGuard(jdbc,
                Clock.fixed(Instant.parse("2026-10-10T02:40:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(guard, "enforce", enabled);
        ReflectionTestUtils.setField(guard, "kid", kid);
        ReflectionTestUtils.setField(guard, "encodedPublicKey", publicKey);
        return guard;
    }

    @Test
    public void developmentDisabledDoesNotReadClinicDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantLicenseGuard guard = create(jdbc, false, "", "");
        guard.start();
        assertFalse(guard.isEnforced());
        assertTrue(guard.requireTenant(1L).isEmpty());
        guard.reserveSeat(1L, true);
        verifyNoInteractions(jdbc);
    }

    @Test
    public void enforcingWithoutPinnedSigningKeyFailsStartup() {
        TenantLicenseGuard blank = create(mock(JdbcTemplate.class), true, "", "");
        assertThrows(IllegalStateException.class, blank::start);
        TenantLicenseGuard malformed = create(mock(JdbcTemplate.class), true,
                "synthetic-owner", String.join("", Collections.nCopies(43, "a")));
        assertThrows(IllegalStateException.class, malformed::start);
    }

    @Test
    public void missingOrBrokenLicenseStoreDeniesEveryTenantRequest() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantLicenseGuard guard = create(jdbc, true, "synthetic-owner-v1",
                "wkf02RerH1dIPRuDdDjeul6axv8wb0WhDB4O9LZxNyI");
        guard.start();
        when(jdbc.queryForList(anyString(), eq(11L))).thenReturn(Collections.emptyList());
        assertEquals("TENANT_LICENSE_MISSING", assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () -> guard.requireTenant(11L)).getCode());
        when(jdbc.queryForList(anyString(), eq(11L)))
                .thenThrow(new IllegalStateException("synthetic DB down"));
        assertEquals("TENANT_LICENSE_STORE_UNAVAILABLE", assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () -> guard.requireTenant(11L)).getCode());
        assertEquals("TENANT_LICENSE_MISSING", assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () -> guard.requireTenant(null)).getCode());
    }

    @Test
    public void OwnerUnsignedDatabaseBindingNeverGrantsPermission() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantLicenseGuard guard = create(jdbc, true, "synthetic-owner-v1",
                "wkf02RerH1dIPRuDdDjeul6axv8wb0WhDB4O9LZxNyI");
        guard.start();
        Map<String, Object> forged = new HashMap<>();
        forged.put("tenant_uuid", "f12b2687-09a1-40b5-b47a-747d75ce5551");
        forged.put("installation_uuid", "d94123bf-2c94-4af4-9d5b-7ab8ce690509");
        forged.put("signed_lease", "RHL1.forged.payload.signature");
        forged.put("revocation_epoch", 0L);
        forged.put("trusted_time_ms", 1791600000000L);
        when(jdbc.queryForList(anyString(), eq(11L)))
                .thenReturn(Collections.singletonList(forged));
        assertEquals("UNTRUSTED_ISSUER_KEY", assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () -> guard.requireTenant(11L)).getCode());
    }

    @Test
    public void MissingLockedTenantRowNeverReservesASeat() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantLicenseGuard guard = create(jdbc, true, "synthetic-owner-v1",
                "wkf02RerH1dIPRuDdDjeul6axv8wb0WhDB4O9LZxNyI");
        guard.start();
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(11L)))
                .thenReturn(Collections.emptyList());
        assertEquals("TENANT_LICENSE_MISSING", assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () -> guard.reserveSeat(11L, true)).getCode());
        guard.reserveSeat(11L, false);
    }

    @Test
    public void multipleTrustedOwnerKeysCanCoexistDuringScheduledRotation() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantLicenseGuard guard = create(jdbc, true, "", "");
        String trusted = "old-issuer=wkf02RerH1dIPRuDdDjeul6axv8wb0WhDB4O9LZxNyI,"
                + "new-issuer=wkf02RerH1dIPRuDdDjeul6axv8wb0WhDB4O9LZxNyI";
        ReflectionTestUtils.setField(guard, "trustedPublicKeys", trusted);
        assertDoesNotThrow(guard::start);
        TenantLicenseGuard duplicated = create(jdbc, true, "", "");
        ReflectionTestUtils.setField(duplicated, "trustedPublicKeys",
                "same=wkf02RerH1dIPRuDdDjeul6axv8wb0WhDB4O9LZxNyI,"
                + "same=wkf02RerH1dIPRuDdDjeul6axv8wb0WhDB4O9LZxNyI");
        assertThrows(IllegalStateException.class, duplicated::start);
    }
}
