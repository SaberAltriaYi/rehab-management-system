package cn.iocoder.yudao.module.rehab.controller.admin.trigger;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.trigger.vo.*;
import cn.iocoder.yudao.module.rehab.service.trigger.RehabReassessmentTriggerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 复评触发")
@RestController
@RequestMapping("/rehab/reassessment-trigger")
@Validated
public class RehabReassessmentTriggerController {

    @Resource
    private RehabReassessmentTriggerService triggerService;

    @GetMapping("/page")
    @Operation(summary = "获得复评触发分页")
    @PreAuthorize("@ss.hasPermission('rehab:reassessment-trigger:view')")
    public CommonResult<PageResult<RehabReassessmentTriggerRespVO>> getTriggerPage(@Valid RehabReassessmentTriggerPageReqVO reqVO) {
        return success(triggerService.getTriggerPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得复评触发详情")
    @Parameter(name = "id", description = "触发编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:reassessment-trigger:view')")
    public CommonResult<RehabReassessmentTriggerRespVO> getTrigger(@RequestParam("id") Long id) {
        return success(triggerService.getTrigger(id, getLoginUserId()));
    }

    @PostMapping("/create")
    @Operation(summary = "创建复评触发")
    @PreAuthorize("@ss.hasPermission('rehab:reassessment-trigger:create')")
    public CommonResult<Long> createTrigger(@Valid @RequestBody RehabReassessmentTriggerCreateReqVO reqVO) {
        return success(triggerService.createTrigger(reqVO, getLoginUserId()));
    }

    @PostMapping("/acknowledge")
    @Operation(summary = "确认复评触发")
    @PreAuthorize("@ss.hasPermission('rehab:reassessment-trigger:handle')")
    public CommonResult<Boolean> acknowledge(@Valid @RequestBody RehabReassessmentTriggerHandleReqVO reqVO) {
        triggerService.acknowledge(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/convert-to-reassessment")
    @Operation(summary = "转为复评")
    @PreAuthorize("@ss.hasPermission('rehab:reassessment-trigger:handle')")
    public CommonResult<RehabTriggerConvertRespVO> convertToReassessment(@Valid @RequestBody RehabReassessmentTriggerHandleReqVO reqVO) {
        return success(triggerService.convertToReassessment(reqVO, getLoginUserId()));
    }

    @PostMapping("/dismiss")
    @Operation(summary = "忽略复评触发")
    @PreAuthorize("@ss.hasPermission('rehab:reassessment-trigger:handle')")
    public CommonResult<Boolean> dismiss(@Valid @RequestBody RehabReassessmentTriggerHandleReqVO reqVO) {
        triggerService.dismiss(reqVO, getLoginUserId());
        return success(true);
    }

}
