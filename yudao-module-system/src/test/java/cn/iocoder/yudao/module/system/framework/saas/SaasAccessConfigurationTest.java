package cn.iocoder.yudao.module.system.framework.saas;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import cn.iocoder.yudao.module.system.service.saas.SaasTenantAccessService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SaasAccessConfigurationTest {
    @Test void tenantIsolationCannotBeDisabled(){ assertThrows(IllegalStateException.class,()->new SaasAccessConfiguration(new MockEnvironment())); }
    @Test void mutuallyExclusiveLicenseModeIsRejected(){ assertThrows(IllegalStateException.class,()->new SaasAccessConfiguration(new MockEnvironment().withProperty("yudao.tenant.enable","true").withProperty("yudao.license.enforce","true"))); }
    @Test void backgroundJobCannotChangeTenantIdentity(){
        SaasAccessConfiguration config=new SaasAccessConfiguration(new MockEnvironment().withProperty("yudao.tenant.enable","true"));
        SaasTenantAccessService service=mock(SaasTenantAccessService.class);
        TenantContextHolder.setTenantId(1L);
        try { assertThrows(IllegalStateException.class,()->config.saasTenantJobGuard(service).requireActiveTenant(2L));verifyNoInteractions(service);config.saasTenantJobGuard(service).requireActiveTenant(1L);verify(service).requireCurrentTenant(); }
        finally {TenantContextHolder.clear();}
    }
}
