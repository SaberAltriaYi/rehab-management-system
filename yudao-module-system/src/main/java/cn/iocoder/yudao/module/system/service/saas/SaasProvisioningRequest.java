1: package cn.iocoder.yudao.module.system.service.saas;
2: 
3: import java.time.LocalDateTime;
4: 
5: /** Internal owner-to-main-system provisioning contract. Password is used only in memory and is never persisted. */
6: public class SaasProvisioningRequest {
7:     public String idempotencyKey;
8:     public String tenantUuid;
9:     public String organizationName;
10:     public String contactName;
11:     public String contactMobile;
12:     public long planId;
13:     public long packageId;
14:     public LocalDateTime expiresAt;
15:     public String adminUsername;
16:     public String adminPassword;
17: }