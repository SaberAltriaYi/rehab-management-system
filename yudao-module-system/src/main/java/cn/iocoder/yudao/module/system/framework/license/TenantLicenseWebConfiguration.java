package cn.iocoder.yudao.module.system.framework.license;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TenantLicenseWebConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "yudao.license", name = "enforce", havingValue = "true")
    public FilterRegistrationBean<TenantLicenseEnforcementFilter> tenantLicenseEnforcementFilter(
            TenantLicenseGuard guard) {
        FilterRegistrationBean<TenantLicenseEnforcementFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TenantLicenseEnforcementFilter(guard));
        registration.setOrder(-97); // after Spring Security (-100) + Tenant Security (-99)
        registration.addUrlPatterns("/admin-api/*", "/app-api/*");
        return registration;
    }
}
