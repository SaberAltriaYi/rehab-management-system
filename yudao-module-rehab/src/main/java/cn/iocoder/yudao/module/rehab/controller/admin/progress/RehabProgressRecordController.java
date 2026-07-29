package cn.iocoder.yudao.module.rehab.controller.admin.progress;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.progress.vo.*;
import cn.iocoder.yudao.module.rehab.service.progress.RehabProgressRecordService;
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

@Tag(name = "管理后台 - 进度追踪")
@RestController
@RequestMapping("/rehab/progress")
@Validated
public class RehabProgressRecordController {

    @Resource
    private RehabProgressRecordService progressService;

    @GetMapping("/page")
    @Operation(summary = "获得进度分页")
    @PreAuthorize("@ss.hasPermission('rehab:progress:view')")
    public CommonResult<PageResult<RehabProgressRecordRespVO>> getProgressPage(@Valid RehabProgressRecordPageReqVO reqVO) {
        return success(progressService.getProgressPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得进度详情")
    @Parameter(name = "id", description = "进度编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:progress:detail')")
    public CommonResult<RehabProgressRecordRespVO> getProgress(@RequestParam("id") Long id) {
        return success(progressService.getProgress(id, getLoginUserId()));
    }

    @PostMapping("/recalculate")
    @Operation(summary = "重算进度")
    @PreAuthorize("@ss.hasPermission('rehab:progress:detail')")
    public CommonResult<RehabProgressRecordRespVO> recalculate(@Valid @RequestBody RehabProgressRecalculateReqVO reqVO) {
        return success(progressService.recalculate(reqVO, getLoginUserId()));
    }

}
