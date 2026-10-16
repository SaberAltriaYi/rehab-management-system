package cn.iocoder.yudao.module.system.controller.admin.saas;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.system.service.saas.SaasProvisioningRequest;
import cn.iocoder.yudao.module.system.service.saas.SaasProvisioningService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** Machine-to-machine endpoint. Keep it disabled unless the private network token is configured. */
@RestController
@RequestMapping("/system/saas/internal")
@TenantIgnore
@ConditionalOnProperty(name = "yudao.provisioning.enabled", havingValue = "true")
public class SaasProvisioningController {
    private final SaasProvisioningService service;
    private final String provisioningToken;

    public SaasProvisioningController(SaasProvisioningService service,
                                      @Value("${yudao.provisioning.token:}") String provisioningToken) {
        this.service = service;
        this.provisioningToken = provisioningToken;
    }

    @PostMapping("/provisioning")
    public CommonResult<Map<String, Object>> provision(
            @RequestHeader(value = "X-Rehab-Provisioning-Token", required = false) String token,
            @RequestBody SaasProvisioningRequest request) {
        if (provisioningToken == null || provisioningToken.length() < 24 || !provisioningToken.equals(token)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "provisioning token invalid");
        }
        return success(service.provision(request));
    }
}
