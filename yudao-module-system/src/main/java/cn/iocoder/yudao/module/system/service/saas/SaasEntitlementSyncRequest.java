package cn.iocoder.yudao.module.system.service.saas;

/** Owner-authenticated entitlement update: contains no password or TOTP. */
public class SaasEntitlementSyncRequest {
    public String tenantUuid;
    public long revision;
    public long expiresAtMs;
    public Integer seatLimit;
}
