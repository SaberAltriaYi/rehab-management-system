package cn.iocoder.yudao.module.system.framework.saas;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJobExecutionGuard;
import cn.iocoder.yudao.module.system.service.saas.SaasTenantAccessService;
import cn.iocoder.yudao.module.system.service.saas.SaasPlatformAuthority;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import java.util.Objects;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="yudao.deployment.mode",havingValue="saas")
public class SaasAccessConfiguration {
    public SaasAccessConfiguration(Environment environment) {
        if (!environment.getProperty("yudao.tenant.enable",Boolean.class,false)
                || environment.getProperty("yudao.license.enforce",Boolean.class,false)) {
            throw new IllegalStateException("SaaS requires tenant isolation and cannot run with Enterprise license enforcement");
        }
    }
    @Bean public FilterRegistrationBean<SaasAccessFilter> saasAccessFilter(
            SaasTenantAccessService tenants,SaasPlatformAuthority platform) {
        FilterRegistrationBean<SaasAccessFilter> bean=new FilterRegistrationBean<>();
        bean.setFilter(new SaasAccessFilter(tenants,platform));
        bean.setOrder(-97);
        bean.addUrlPatterns("/admin-api/*","/app-api/*");
        return bean;
    }
    @Bean @Primary public TenantJobExecutionGuard saasTenantJobGuard(SaasTenantAccessService tenants) {
        return tenantId -> {
            if (tenantId==null || !Objects.equals(tenantId,TenantContextHolder.getTenantId()))
                throw new IllegalStateException("SaaS job tenant context mismatch");
            tenants.requireCurrentTenant();
        };
    }
}
