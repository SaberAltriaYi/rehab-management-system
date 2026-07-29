package cn.iocoder.yudao.module.rehab.controller.admin.plan;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.plan.vo.*;
import cn.iocoder.yudao.module.rehab.service.plan.RehabCarePlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 康复计划")
@RestController
@RequestMapping("/rehab/plan")
@Validated
public class RehabCarePlanController {

    @Resource
    private RehabCarePlanService carePlanService;

    @GetMapping("/page")
    @Operation(summary = "获得计划分页")
    @PreAuthorize("@ss.hasPermission('rehab:plan:view')")
    public CommonResult<PageResult<RehabCarePlanRespVO>> getPlanPage(@Valid RehabCarePlanPageReqVO reqVO) {
        return success(carePlanService.getPlanPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得计划详情")
    @Parameter(name = "id", description = "计划编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:plan:detail')")
    public CommonResult<RehabCarePlanRespVO> getPlan(@RequestParam("id") Long id) {
        return success(carePlanService.getPlan(id, getLoginUserId()));
    }

    @PostMapping("/create")
    @Operation(summary = "创建计划")
    @PreAuthorize("@ss.hasPermission('rehab:plan:create')")
    public CommonResult<RehabCarePlanCreateRespVO> createPlan(@Valid @RequestBody RehabCarePlanCreateReqVO reqVO) {
        return success(carePlanService.createPlan(reqVO, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新计划")
    @PreAuthorize("@ss.hasPermission('rehab:plan:update')")
    public CommonResult<Boolean> updatePlan(@Valid @RequestBody RehabCarePlanUpdateReqVO reqVO) {
        carePlanService.updatePlan(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/activate")
    @Operation(summary = "激活计划")
    @PreAuthorize("@ss.hasPermission('rehab:plan:activate')")
    public CommonResult<Boolean> activatePlan(@Valid @RequestBody RehabCarePlanChangeStatusReqVO reqVO) {
        carePlanService.activatePlan(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/pause")
    @Operation(summary = "暂停计划")
    @PreAuthorize("@ss.hasPermission('rehab:plan:pause')")
    public CommonResult<Boolean> pausePlan(@Valid @RequestBody RehabCarePlanChangeStatusReqVO reqVO) {
        carePlanService.pausePlan(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/complete")
    @Operation(summary = "完成计划")
    @PreAuthorize("@ss.hasPermission('rehab:plan:complete')")
    public CommonResult<Boolean> completePlan(@Valid @RequestBody RehabCarePlanChangeStatusReqVO reqVO) {
        carePlanService.completePlan(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/copy")
    @Operation(summary = "复制计划")
    @PreAuthorize("@ss.hasPermission('rehab:plan:copy')")
    public CommonResult<RehabCarePlanCreateRespVO> copyPlan(@Valid @RequestBody RehabCarePlanCopyReqVO reqVO) {
        return success(carePlanService.copyPlan(reqVO, getLoginUserId()));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除计划")
    @Parameter(name = "id", description = "计划编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:plan:update')")
    public CommonResult<Boolean> deletePlan(@RequestParam("id") Long id) {
        carePlanService.deletePlan(id, getLoginUserId());
        return success(true);
    }

    @GetMapping("/operation-log")
    @Operation(summary = "获得计划操作日志")
    @Parameter(name = "planId", description = "计划编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:plan:detail')")
    public CommonResult<List<RehabPlanOperationLogRespVO>> getOperationLog(@RequestParam("planId") Long planId) {
        return success(carePlanService.getOperationLogList(planId, getLoginUserId()));
    }

}
