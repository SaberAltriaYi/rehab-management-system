package cn.iocoder.yudao.framework.tenant.core.job;

/** Optional security policy evaluated per tenant and per job execution. */
public interface TenantJobExecutionGuard {
    void requireActiveTenant(Long tenantId);
}
