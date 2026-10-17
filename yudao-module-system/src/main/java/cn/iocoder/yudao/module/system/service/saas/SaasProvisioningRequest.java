package cn.iocoder.yudao.module.system.service.saas;

import java.time.LocalDateTime;

/** Internal owner-to-main-system provisioning contract. Password is used only in memory and is never persisted. */
public class SaasProvisioningRequest {
    public String idempotencyKey;
    public String tenantUuid;
    public String organizationName;
    public String contactName;
    public String contactMobile;
    public long planId;
    public long packageId;
    public LocalDateTime expiresAt;
    public String adminUsername;
    public String adminPassword;
}