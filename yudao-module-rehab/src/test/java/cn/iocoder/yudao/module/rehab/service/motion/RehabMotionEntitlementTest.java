package cn.iocoder.yudao.module.rehab.service.motion;

import cn.iocoder.yudao.module.system.framework.license.TenantLicenseGuard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RehabMotionEntitlementTest {

    @Test
    void developmentModeKeepsLocalMotionWorkflowEnabled() {
        TenantLicenseGuard guard = mock(TenantLicenseGuard.class);
        when(guard.isEnforced()).thenReturn(false);

        assertTrue(new RehabMotionEntitlement(guard).enabled());
    }

    @Test
    void enforcedTenantNeedsAnOwnerSignedMotionModule() {
        TenantLicenseGuard guard = mock(TenantLicenseGuard.class);
        when(guard.isEnforced()).thenReturn(true);
        when(guard.requireCurrentTenant()).thenReturn(Map.of("modules", List.of("rehab", "motion")));
        assertTrue(new RehabMotionEntitlement(guard).enabled());

        when(guard.requireCurrentTenant()).thenReturn(Map.of("modules", List.of("rehab", "report")));
        assertFalse(new RehabMotionEntitlement(guard).enabled());
    }

    @Test
    void invalidOrMissingLicenseFailsClosed() {
        TenantLicenseGuard guard = mock(TenantLicenseGuard.class);
        when(guard.isEnforced()).thenReturn(true);
        when(guard.requireCurrentTenant()).thenThrow(new RuntimeException("missing binding"));

        assertFalse(new RehabMotionEntitlement(guard).enabled());
    }
}