package cn.iocoder.yudao.module.rehab.controller.app.patient;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.rehab.dal.dataobject.binding.RehabPatientUserBindingDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.binding.RehabPatientUserBindingMapper;
import cn.iocoder.yudao.module.rehab.service.app.RehabAppPatientService;
import cn.iocoder.yudao.module.rehab.service.hep.RehabHepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "患者端 APP - HEP 家庭康复训练")
@RestController
@RequestMapping("/app-patient/hep")
public class RehabAppPatientHepController {

    @Resource
    private RehabAppPatientService appPatientService;
    @Resource
    private RehabHepService hepService;
    @Resource
    private RehabPatientUserBindingMapper bindingMapper;

    @GetMapping("/current")
    @Operation(summary = "查看本人当前已签发训练计划")
    public CommonResult<Map<String, Object>> current() {
        appPatientService.validatePatientSession(getLoginUserId());
        RehabPatientUserBindingDO binding = bindingMapper.selectActiveByAppUserId(getLoginUserId());
        return success(hepService.currentForPatient(binding.getPatientId()));
    }

    @PostMapping("/feedback")
    @Operation(summary = "提交本人训练反馈")
    public CommonResult<Long> feedback(@RequestBody Map<String, Object> body) {
        appPatientService.validatePatientSession(getLoginUserId());
        return success(hepService.submitFeedback(body, getLoginUserId()));
    }
}
