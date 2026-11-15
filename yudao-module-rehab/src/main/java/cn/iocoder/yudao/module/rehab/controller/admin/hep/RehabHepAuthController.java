package cn.iocoder.yudao.module.rehab.controller.admin.hep;

import cn.iocoder.yudao.module.rehab.service.hep.RehabHepTokenService;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.annotation.security.PermitAll;
import java.util.Map;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/rehab/hep-auth")
@ConditionalOnProperty(name="REHAB_HEP_BRIDGE_ENABLED",havingValue="true")
public class RehabHepAuthController {
    private final RehabHepTokenService signing;
    @Autowired public RehabHepAuthController(RehabHepTokenService signing){this.signing=signing;}

    @GetMapping("/jwks")
    @PermitAll
    @TenantIgnore // Public key distribution must not require a logged-in tenant.
    public Map<String,Object> jwks(){return signing.jwks();}

    @PostMapping("/therapist-token")
    @PreAuthorize("@ss.hasPermission('rehab:hep:prescription:create')")
    public CommonResult<Map<String,Object>> therapist(){return CommonResult.success(signing.token(getLoginUserId(),"THERAPIST"));}

    // Permission must be assigned explicitly to operators who may manage HEP
    // bindings and membership; it is not inferred from generic therapist role.
    @PostMapping("/admin-token")
    @PreAuthorize("@ss.hasPermission('rehab:hep:integration:admin')")
    public CommonResult<Map<String,Object>> admin(){return CommonResult.success(signing.token(getLoginUserId(),"ADMIN"));}
}
