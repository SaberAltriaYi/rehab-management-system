package cn.iocoder.yudao.module.system.framework.license;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class StudioLicenseOverviewServiceTest {
    private final TenantLicenseGuard guard = mock(TenantLicenseGuard.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final StudioLicenseOverviewService service = new StudioLicenseOverviewService(guard, jdbc);

    @AfterEach void clearContext() { TenantContextHolder.clear(); }

    @Test
    void noTenantContextCannotReadAnotherStudiosLicense() {
        TenantContextHolder.clear();
        assertThrows(LicenseLeaseVerifier.LicenseFailure.class, service::readCurrentStudio);
        verifyNoInteractions(guard, jdbc);
    }

    @Test
    void developmentModeCannotInventUnlimitedLicensesOrReadStaff() {
        TenantContextHolder.setTenantId(11L);
        when(guard.isEnforced()).thenReturn(false);
        Map<String, Object> status = service.readCurrentStudio();
        assertEquals("NOT_CONFIGURED", status.get("status"));
        assertFalse(status.containsKey("seatLimit"));
        verify(guard, never()).requireTenant(anyLong());
        verifyNoInteractions(jdbc);
    }

    private Map<String, Object> claims(Object seats) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tenantId", "f12b2687-09a1-40b5-b47a-747d75ce5551");
        claims.put("installationId", "d94123bf-2c94-4af4-9d5b-7ab8ce690509");
        claims.put("seatLimit", seats);
        claims.put("maxInstallations", 2);
        claims.put("mode", "offline");
        claims.put("modules", Arrays.asList("rehab", "report"));
        claims.put("expiresAt", "2026-10-12T00:00:00.000Z");
        return claims;
    }

    @Test
    void signedFiniteSeatLimitCountsOnlyCurrentTenantActiveStaff() {
        TenantContextHolder.setTenantId(12L);
        when(guard.isEnforced()).thenReturn(true);
        when(guard.requireTenant(12L)).thenReturn(claims(7));
        when(jdbc.queryForObject(contains("FROM system_users"), eq(Long.class), eq(12L))).thenReturn(5L);
        Map<String, Object> result = service.readCurrentStudio();
        assertEquals("GRACE", result.get("status"));
        assertEquals(7L, result.get("seatLimit"));
        assertEquals(5L, result.get("activeStaff"));
        assertEquals(2L, result.get("availableSeats"));
        verify(guard).requireTenant(12L);
        verify(jdbc).queryForObject(contains("tenant_id=?"), eq(Long.class), eq(12L));
        verify(guard, never()).requireTenant(13L);
    }

    @Test
    void explicitlySignedUnlimitedSeatHasNullAvailabilityWithoutInventedCap() {
        TenantContextHolder.setTenantId(20L);
        when(guard.isEnforced()).thenReturn(true);
        when(guard.requireTenant(20L)).thenReturn(claims(null));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(20L))).thenReturn(193L);
        Map<String, Object> result = service.readCurrentStudio();
        assertNull(result.get("seatLimit"));
        assertNull(result.get("availableSeats"));
        assertEquals(193L, result.get("activeStaff"));
    }

    @Test
    void revokedTenantFailsClosedBeforeReadingStaffCount() {
        TenantContextHolder.setTenantId(15L);
        when(guard.isEnforced()).thenReturn(true);
        when(guard.requireTenant(15L)).thenThrow(
                new LicenseLeaseVerifier.LicenseFailure("TENANT_NOT_ACTIVE"));
        assertThrows(LicenseLeaseVerifier.LicenseFailure.class, service::readCurrentStudio);
        verifyNoInteractions(jdbc);
    }

    @Test
    void mismatchedInvalidSeatLimitAndUnavailableCountNeverBecomePositiveAvailableSeats() {
        TenantContextHolder.setTenantId(16L);
        when(guard.isEnforced()).thenReturn(true);
        when(guard.requireTenant(16L)).thenReturn(claims(0));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(16L))).thenReturn(4L);
        assertThrows(LicenseLeaseVerifier.LicenseFailure.class, service::readCurrentStudio);
        when(guard.requireTenant(16L)).thenReturn(claims(3));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(16L))).thenReturn(6L);
        assertEquals(0L, service.readCurrentStudio().get("availableSeats"));
    }
}
