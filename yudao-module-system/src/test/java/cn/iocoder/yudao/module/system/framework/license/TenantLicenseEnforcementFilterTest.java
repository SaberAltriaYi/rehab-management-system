package cn.iocoder.yudao.module.system.framework.license;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TenantLicenseEnforcementFilterTest {
    @AfterEach
    public void cleanup() { TenantContextHolder.clear(); }

    private MockHttpServletRequest request(String path, String method) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.setRequestURI(path);
        return req;
    }

    @Test
    public void protectedRequestRequiresSignedLicenseEvenForPermitAllLogin() throws Exception {
        TenantLicenseGuard guard = mock(TenantLicenseGuard.class);
        doThrow(new LicenseLeaseVerifier.LicenseFailure("TENANT_LICENSE_MISSING"))
                .when(guard).requireTenant(any());
        TenantLicenseEnforcementFilter filter = new TenantLicenseEnforcementFilter(guard);
        TenantContextHolder.setTenantId(1L);
        MockHttpServletResponse denied = new MockHttpServletResponse();
        AtomicInteger calls = new AtomicInteger();
        filter.doFilter(request("/admin-api/system/auth/login", "POST"), denied,
                (req, res) -> calls.incrementAndGet());
        assertEquals(403, denied.getStatus());
        assertEquals(0, calls.get());
        filter.doFilter(request("/admin-api/rehab/report/download", "GET"),
                new MockHttpServletResponse(), (req, res) -> calls.incrementAndGet());
        assertEquals(0, calls.get());
        filter.doFilter(request("/app-api/rehab/patients/1", "GET"),
                new MockHttpServletResponse(), (req, res) -> calls.incrementAndGet());
        assertEquals(0, calls.get());
    }

    @Test
    public void exactLogoutAndPreflightAllowedButNotOtherAuthEndpoints() throws Exception {
        TenantLicenseGuard guard = mock(TenantLicenseGuard.class);
        doThrow(new LicenseLeaseVerifier.LicenseFailure("EXPIRED"))
                .when(guard).requireTenant(any());
        TenantLicenseEnforcementFilter filter = new TenantLicenseEnforcementFilter(guard);
        AtomicInteger count = new AtomicInteger();
        filter.doFilter(request("/admin-api/system/auth/logout", "POST"),
                new MockHttpServletResponse(), (req, res) -> count.incrementAndGet());
        filter.doFilter(request("/admin-api/system/auth/refresh-token", "OPTIONS"),
                new MockHttpServletResponse(), (req, res) -> count.incrementAndGet());
        assertEquals(2, count.get());
        filter.doFilter(request("/admin-api/system/auth/refresh-token", "POST"),
                new MockHttpServletResponse(), (req, res) -> count.incrementAndGet());
        assertEquals(2, count.get());
    }

    @Test
    public void normalSignedTenantCanAccessTheNextSecurityLayer() throws Exception {
        TenantLicenseGuard guard = mock(TenantLicenseGuard.class);
        TenantLicenseEnforcementFilter filter = new TenantLicenseEnforcementFilter(guard);
        TenantContextHolder.setTenantId(55L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger count = new AtomicInteger();
        filter.doFilter(request("/admin-api/rehab/assessment", "GET"), response,
                (req, res) -> count.incrementAndGet());
        assertEquals(1, count.get());
        verify(guard).requireTenant(55L);
    }

    @Test
    public void reportAndMotionEndpointsRequireTheirSpecificSignedModule() throws Exception {
        TenantLicenseGuard guard = mock(TenantLicenseGuard.class);
        when(guard.isEnforced()).thenReturn(true);
        when(guard.requireTenant(55L)).thenReturn(
                Collections.singletonMap("modules", Arrays.asList("rehab")));
        TenantContextHolder.setTenantId(55L);
        TenantLicenseEnforcementFilter filter = new TenantLicenseEnforcementFilter(guard);
        AtomicInteger allowed = new AtomicInteger();
        for (String deniedPath : Arrays.asList(
                "/admin-api/rehab/report/download", "/admin-api/rehab/motion/capture")) {
            MockHttpServletResponse denied = new MockHttpServletResponse();
            filter.doFilter(request(deniedPath, "GET"), denied,
                    (req, res) -> allowed.incrementAndGet());
            assertEquals(403, denied.getStatus());
        }
        filter.doFilter(request("/admin-api/rehab/assessment", "GET"),
                new MockHttpServletResponse(), (req, res) -> allowed.incrementAndGet());
        assertEquals(1, allowed.get());
    }
}
