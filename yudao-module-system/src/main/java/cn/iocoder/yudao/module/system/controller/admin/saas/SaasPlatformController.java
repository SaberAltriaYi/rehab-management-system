1: package cn.iocoder.yudao.module.system.controller.admin.saas;
2: 
3: import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
4: import cn.iocoder.yudao.framework.common.pojo.CommonResult;
5: import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
6: import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
7: import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.ClientIpRateLimiterKeyResolver;
8: import cn.iocoder.yudao.module.system.service.saas.SaasPlatformService;
9: import lombok.Data;
10: import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
11: import org.springframework.security.access.prepost.PreAuthorize;
12: import org.springframework.validation.annotation.Validated;
13: import org.springframework.web.bind.annotation.*;
14: import javax.annotation.security.PermitAll;
15: import javax.validation.Valid;
16: import javax.validation.constraints.*;
17: import java.time.LocalDateTime;
18: import java.util.*;
19: import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
20: 
21: @RestController
22: @RequestMapping("/system/saas")
23: @Validated
24: @ConditionalOnProperty(name="yudao.legacy.saas-platform.enabled", havingValue="true")
25: public class SaasPlatformController {
26:     private final SaasPlatformService service;
27:     public SaasPlatformController(SaasPlatformService service) { this.service=service; }
28: 
29:     @PostMapping("/register") @PermitAll @TenantIgnore
30:     @ApiAccessLog(requestEnable=false)
31:     @RateLimiter(time=3600,count=5,keyResolver=ClientIpRateLimiterKeyResolver.class)
32:     public CommonResult<String> register(@Valid @RequestBody Registration request) {
33:         return success(service.register(request.organization,request.contact,request.email));
34:     }
35:     @GetMapping("/platform/registrations") @TenantIgnore @PreAuthorize("isAuthenticated()")
36:     public CommonResult<List<Map<String,Object>>> registrations(@RequestParam(defaultValue="0") @Min(0) long afterId) {
37:         return success(service.registrations(afterId));
38:     }
39:     @GetMapping("/platform/tenants") @TenantIgnore @PreAuthorize("isAuthenticated()")
40:     public CommonResult<List<Map<String,Object>>> tenants(@RequestParam(defaultValue="0") @Min(0) long afterId) {
41:         return success(service.tenants(afterId));
42:     }
43:     @GetMapping("/platform/plans") @TenantIgnore @PreAuthorize("isAuthenticated()")
44:     public CommonResult<List<Map<String,Object>>> plans() { return success(service.plans()); }
45:     @PostMapping("/platform/approve") @TenantIgnore @PreAuthorize("isAuthenticated()")
46:     @ApiAccessLog(requestEnable=false)
47:     public CommonResult<Long> approve(@Valid @RequestBody Approval request) {
48:         return success(service.approve(request.registrationId,request.planId,request.packageId,
49:                 request.username,request.password,request.expires));
50:     }
51:     @PostMapping("/platform/reject") @TenantIgnore @PreAuthorize("isAuthenticated()")
52:     public CommonResult<Boolean> reject(@RequestParam @Min(1) long id) { service.reject(id); return success(true); }
53:     @PostMapping("/platform/suspend") @TenantIgnore @PreAuthorize("isAuthenticated()")
54:     public CommonResult<Boolean> suspend(@RequestParam @Min(1) long tenantId) { service.suspend(tenantId); return success(true); }
55:     @PostMapping("/platform/renew") @TenantIgnore @PreAuthorize("isAuthenticated()")
56:     public CommonResult<Boolean> renew(@Valid @RequestBody Renewal request) {
57:         service.renew(request.tenantId,request.expires); return success(true);
58:     }
59:     @PostMapping("/platform/plans") @TenantIgnore @PreAuthorize("isAuthenticated()")
60:     public CommonResult<Long> createPlan(@Valid @RequestBody Plan request) {
61:         return success(service.createPlan(request.name,request.maxUsers,request.maxPatients,request.motionQuota,request.storageQuota));
62:     }
63:     @Data public static class Renewal {
64:         @Min(1) private long tenantId;
65:         @NotNull @Future private LocalDateTime expires;
66:     }
67:     @Data public static class Plan {
68:         @NotBlank @Size(max=64) private String name;
69:         @Min(1) private int maxUsers;
70:         @Min(0) private int maxPatients;
71:         @Min(0) private long motionQuota;
72:         @Min(0) private long storageQuota;
73:     }
74:     @Data public static class Registration {
75:         @NotBlank @Size(max=80) private String organization;
76:         @NotBlank @Size(max=50) private String contact;
77:         @NotBlank @Email @Size(max=120) private String email;
78:     }
79:     @Data public static class Approval {
80:         @Min(1) private long registrationId;
81:         @Min(1) private long planId;
82:         @Min(1) private long packageId;
83:         @NotBlank @Pattern(regexp="[a-zA-Z0-9]{4,30}") private String username;
84:         @NotBlank @Size(min=12,max=16) private String password;
85:         @NotNull @Future private LocalDateTime expires;
86:     }
87: }