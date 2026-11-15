package cn.iocoder.yudao.module.rehab.controller.admin.hep;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.rehab.service.hep.RehabHepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - HEP 家庭康复训练")
@RestController
@RequestMapping("/rehab/hep")
public class RehabHepController {

    @Resource
    private RehabHepService hepService;

    @GetMapping("/exercises")
    @Operation(summary = "获得经审核动作库")
    @PreAuthorize("@ss.hasPermission('rehab:hep:exercise:view')")
    public CommonResult<List<Map<String, Object>>> listExercises() {
        return success(hepService.listReviewedExercises());
    }

    @PostMapping("/exercises")
    @Operation(summary = "创建动作库条目")
    @PreAuthorize("@ss.hasPermission('rehab:hep:exercise:create')")
    public CommonResult<Long> createExercise(@RequestBody Map<String, Object> body) {
        return success(hepService.createExercise(body, getLoginUserId()));
    }

    @PostMapping("/exercises/{id}/review")
    @Operation(summary = "审核动作库条目")
    @PreAuthorize("@ss.hasPermission('rehab:hep:exercise:review')")
    public CommonResult<Boolean> reviewExercise(@PathVariable("id") Long id) {
        hepService.reviewExercise(id, getLoginUserId());
        return success(true);
    }

    @GetMapping("/templates")
    @Operation(summary = "获得 HEP 训练模板")
    @PreAuthorize("@ss.hasPermission('rehab:hep:template:view')")
    public CommonResult<List<Map<String, Object>>> listTemplates() {
        return success(hepService.listTemplates());
    }

    @PostMapping("/templates")
    @Operation(summary = "创建 HEP 训练模板")
    @PreAuthorize("@ss.hasPermission('rehab:hep:template:create')")
    public CommonResult<Long> createTemplate(@RequestBody Map<String, Object> body) {
        return success(hepService.createTemplate(body, getLoginUserId()));
    }

    @PostMapping("/prescriptions")
    @Operation(summary = "创建 HEP 处方草稿")
    @PreAuthorize("@ss.hasPermission('rehab:hep:prescription:create')")
    public CommonResult<Long> createPrescription(@RequestBody Map<String, Object> body) {
        return success(hepService.createPrescription(body, getLoginUserId()));
    }

    @PostMapping("/prescriptions/{id}/issue")
    @Operation(summary = "审核并签发 HEP 处方")
    @PreAuthorize("@ss.hasPermission('rehab:hep:prescription:issue')")
    public CommonResult<Boolean> issuePrescription(@PathVariable("id") Long id) {
        hepService.issuePrescription(id, getLoginUserId());
        return success(true);
    }

    @PutMapping("/feedback/{id}/review")
    @Operation(summary = "审核患者执行反馈并回传 Core")
    @PreAuthorize("@ss.hasPermission('rehab:hep:feedback:review')")
    public CommonResult<Boolean> reviewFeedback(@PathVariable("id") Long id,
                                                 @RequestBody(required = false) Map<String, Object> body) {
        String status = body == null ? "REVIEWED" : String.valueOf(body.getOrDefault("status", "REVIEWED"));
        hepService.reviewFeedback(id, getLoginUserId(), status);
        return success(true);
    }
}
