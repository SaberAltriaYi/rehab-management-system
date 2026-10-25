package cn.iocoder.yudao.module.rehab.controller.app.patient;

import cn.iocoder.yudao.module.rehab.service.app.RehabAppPatientService;
import cn.iocoder.yudao.module.rehab.service.hep.RehabHepTokenService;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.Map;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/app-patient/hep-auth")
@ConditionalOnProperty(name="REHAB_HEP_BRIDGE_ENABLED",havingValue="true")
public class RehabHepPatientTokenController {
    @Resource private RehabHepTokenService signing;
    @Resource private RehabAppPatientService patientService;
    @PostMapping("/patient-token")
    public CommonResult<Map<String,Object>> patient() {
        Long userId=getLoginUserId();
        patientService.validatePatientSession(userId);
        return CommonResult.success(signing.token(userId,"PATIENT"));
    }
}
