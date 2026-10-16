package cn.iocoder.yudao.module.system.controller.admin.saas;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.ClientIpRateLimiterKeyResolver;
import cn.iocoder.yudao.module.system.service.saas.SaasPlatformService;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/system/saas")
@Validated
@ConditionalOnProperty(name="yudao.deployment.mode", havingValue="saas")
@ConditionalOnProperty(name="yudao.legacy.saas-platform.enabled", havingValue="true")
public class SaasPlatformController {
    private final SaasPlatformService service;
    public SaasPlatformController(SaasPlatformService service) { this.service=service; }

    @PostMapping("/register") @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable=false)
    @RateLimiter(time=3600,count=5,keyResolver=ClientIpRateLimiterKeyResolver.class)
    public CommonResult<String> register(@Valid @RequestBody Registration request) {
        return success(service.register(request.organization,request.contact,request.email));
    }
    @GetMapping("/platform/registrations") @TenantIgnore @PreAuthorize("isAuthenticated()")
    public CommonResult<List<Map<String,Object>>> registrations(@RequestParam(defaultValue="0") @Min(0) long afterId) {
        return success(service.registrations(afterId));
    }
    @GetMapping("/platform/tenants") @TenantIgnore @PreAuthorize("isAuthenticated()")
    public CommonResult<List<Map<String,Object>>> tenants(@RequestParam(defaultValue="0") @Min(0) long afterId) {
        return success(service.tenants(afterId));
    }
    @GetMapping("/platform/plans") @TenantIgnore @PreAuthorize("isAuthenticated()")
    public CommonResult<List<Map<String,Object>>> plans() { return success(service.plans()); }
    @PostMapping("/platform/approve") @TenantIgnore @PreAuthorize("isAuthenticated()")
    @ApiAccessLog(requestEnable=false)
    public CommonResult<Long> approve(@Valid @RequestBody Approval request) {
        return success(service.approve(request.registrationId,request.planId,request.packageId,
                request.username,request.password,request.expires));
    }
    @PostMapping("/platform/reject") @TenantIgnore @PreAuthorize("isAuthenticated()")
    public CommonResult<Boolean> reject(@RequestParam @Min(1) long id) { service.reject(id); return success(true); }
    @PostMapping("/platform/suspend") @TenantIgnore @PreAuthorize("isAuthenticated()")
    public CommonResult<Boolean> suspend(@RequestParam @Min(1) long tenantId) { service.suspend(tenantId); return success(true); }
    @PostMapping("/platform/renew") @TenantIgnore @PreAuthorize("isAuthenticated()")
    public CommonResult<Boolean> renew(@Valid @RequestBody Renewal request) {
        service.renew(request.tenantId,request.expires); return success(true);
    }
    @PostMapping("/platform/plans") @TenantIgnore @PreAuthorize("isAuthenticated()")
    public CommonResult<Long> createPlan(@Valid @RequestBody Plan request) {
        return success(service.createPlan(request.name,request.maxUsers,request.maxPatients,request.motionQuota,request.storageQuota));
    }
    @Data public static class Renewal {
        @Min(1) private long tenantId;
        @NotNull @Future private LocalDateTime expires;
    }
    @Data public static class Plan {
        @NotBlank @Size(max=64) private String name;
        @Min(1) private int maxUsers;
        @Min(0) private int maxPatients;
        @Min(0) private long motionQuota;
        @Min(0) private long storageQuota;
    }
    @Data public static class Registration {
        @NotBlank @Size(max=80) private String organization;
        @NotBlank @Size(max=50) private String contact;
        @NotBlank @Email @Size(max=120) private String email;
    }
    @Data public static class Approval {
        @Min(1) private long registrationId;
        @Min(1) private long planId;
        @Min(1) private long packageId;
        @NotBlank @Pattern(regexp="[a-zA-Z0-9]{4,30}") private String username;
        @NotBlank @Size(min=12,max=16) private String password;
        @NotNull @Future private LocalDateTime expires;
    }
}