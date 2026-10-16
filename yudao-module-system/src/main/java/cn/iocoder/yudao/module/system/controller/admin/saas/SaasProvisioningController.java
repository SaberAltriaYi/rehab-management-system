1: package cn.iocoder.yudao.module.system.controller.admin.saas;
2: 
3: import cn.iocoder.yudao.framework.common.pojo.CommonResult;
4: import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
5: import cn.iocoder.yudao.module.system.service.saas.SaasProvisioningRequest;
6: import cn.iocoder.yudao.module.system.service.saas.SaasProvisioningService;
7: import org.springframework.beans.factory.annotation.Value;
8: import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
9: import org.springframework.http.HttpStatus;
10: import org.springframework.web.bind.annotation.*;
11: 
12: import java.util.Map;
13: 
14: import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
15: 
16: /** Machine-to-machine endpoint. Keep it disabled unless the private network token is configured. */
17: @RestController
18: @RequestMapping("/system/saas/internal")
19: @TenantIgnore
20: @ConditionalOnProperty(name = "yudao.provisioning.enabled", havingValue = "true")
21: public class SaasProvisioningController {
22:     private final SaasProvisioningService service;
23:     private final String provisioningToken;
24: 
25:     public SaasProvisioningController(SaasProvisioningService service,
26:                                       @Value("${yudao.provisioning.token:}") String provisioningToken) {
27:         this.service = service;
28:         this.provisioningToken = provisioningToken;
29:     }
30: 
31:     @PostMapping("/provisioning")
32:     public CommonResult<Map<String, Object>> provision(
33:             @RequestHeader(value = "X-Rehab-Provisioning-Token", required = false) String token,
34:             @RequestBody SaasProvisioningRequest request) {
35:         if (provisioningToken == null || provisioningToken.length() < 24 || !provisioningToken.equals(token)) {
36:             throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "provisioning token invalid");
37:         }
38:         return success(service.provision(request));
39:     }
40: }