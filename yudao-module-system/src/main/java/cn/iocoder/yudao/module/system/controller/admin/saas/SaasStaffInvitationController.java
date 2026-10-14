package cn.iocoder.yudao.module.system.controller.admin.saas;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.ClientIpRateLimiterKeyResolver;
import cn.iocoder.yudao.module.system.service.saas.SaasStaffInvitationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;
import javax.validation.constraints.*;
import lombok.Data;
import java.util.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @Validated
@RequestMapping("/system/saas/staff-invitations")
@ConditionalOnProperty(name="yudao.deployment.mode",havingValue="saas")
public class SaasStaffInvitationController {
    private final SaasStaffInvitationService service;
    public SaasStaffInvitationController(SaasStaffInvitationService service){this.service=service;}
    @PostMapping @PreAuthorize("isAuthenticated()")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @RateLimiter(time=60,count=10)
    public CommonResult<Map<String,Object>> issue(@Valid @RequestBody Recipient request){return success(service.issue(request.email));}
    @GetMapping @PreAuthorize("isAuthenticated()")
    public CommonResult<List<Map<String,Object>>> list(@RequestParam(defaultValue="0") @Min(0) long afterId){return success(service.list(afterId));}
    @PostMapping("/revoke") @PreAuthorize("isAuthenticated()")
    public CommonResult<Boolean> revoke(@RequestParam @Min(1) long id){service.revoke(id);return success(true);}
    @PostMapping("/accept") @PermitAll
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @RateLimiter(time=60,count=6,keyResolver=ClientIpRateLimiterKeyResolver.class)
    public CommonResult<Long> accept(@Valid @RequestBody Enrollment request){return success(service.accept(request.token,request.username,request.password,request.nickname));}
    @Data public static class Recipient {@NotBlank @Email @Size(max=50) private String email;}
    @Data public static class Enrollment {
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") @lombok.ToString.Exclude private String token;
        @NotBlank @Pattern(regexp="[a-zA-Z0-9]{4,30}") private String username;
        @NotBlank @Size(min=12,max=16) @lombok.ToString.Exclude private String password;
        @NotBlank @Size(max=30) private String nickname;
    }
}
