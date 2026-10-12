package cn.iocoder.yudao.module.system.framework.saas;
import cn.iocoder.yudao.module.system.service.saas.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import javax.servlet.FilterChain;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SaasAccessFilterTest {
    private final SaasTenantAccessService tenants=mock(SaasTenantAccessService.class);
    private final SaasPlatformAuthority platform=mock(SaasPlatformAuthority.class);
    private final SaasAccessFilter filter=new SaasAccessFilter(tenants,platform);
    @Test void activeTenantContinues() throws Exception {
        FilterChain chain=mock(FilterChain.class);
        MockHttpServletRequest req=new MockHttpServletRequest("GET","/admin-api/rehab/patient/page");
        MockHttpServletResponse res=new MockHttpServletResponse();
        filter.doFilter(req,res,chain); verify(tenants).requireCurrentTenant(); verify(chain).doFilter(req,res);
    }
    @Test void suspendedTenantCannotReadReports() throws Exception {
        denied("GET","/app-api/rehab/report/get",false);
    }
    @Test void loginAndRefreshAreSubscriptionGated() throws Exception {
        denied("POST","/admin-api/system/auth/login",false);
        denied("POST","/admin-api/system/auth/refresh-token",false);
    }
    @Test void legacyTenantManagementRequiresPlatformAuthority() throws Exception {
        denied("POST","/admin-api/system/tenant/create",true);
    }
    @Test void platformConsoleRequiresPlatformAuthority() throws Exception {
        denied("GET","/admin-api/system/saas/platform/tenants",true);
    }
    @Test void publicRegistrationIsExactPostOnly() throws Exception {
        FilterChain chain=mock(FilterChain.class);
        MockHttpServletRequest req=new MockHttpServletRequest("POST","/admin-api/system/saas/register");
        MockHttpServletResponse res=new MockHttpServletResponse();
        filter.doFilter(req,res,chain); verify(chain).doFilter(req,res); verifyNoInteractions(tenants,platform);
        denied("GET","/admin-api/system/saas/register",false);
        denied("POST","/admin-api/system/saas/register/extra",false);
    }
    private void denied(String method,String path,boolean controlPlane) throws Exception {
        reset(tenants,platform);
        if(controlPlane) when(platform.requireAdmin()).thenThrow(new IllegalStateException("denied"));
        else when(tenants.requireCurrentTenant()).thenThrow(new IllegalStateException("denied"));
        FilterChain chain=mock(FilterChain.class);
        MockHttpServletResponse res=new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(method,path),res,chain);
        assertEquals(403,res.getStatus()); verifyNoInteractions(chain);
    }
}
