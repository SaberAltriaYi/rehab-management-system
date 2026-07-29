package cn.iocoder.yudao.module.rehab.controller.admin.checkin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo.*;
import cn.iocoder.yudao.module.rehab.service.checkin.RehabDailyCheckinService;
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

@Tag(name = "管理后台 - 训练打卡")
@RestController
@RequestMapping("/rehab/checkin")
@Validated
public class RehabDailyCheckinController {

    @Resource
    private RehabDailyCheckinService checkinService;

    @GetMapping("/page")
    @Operation(summary = "获得打卡分页")
    @PreAuthorize("@ss.hasPermission('rehab:checkin:view')")
    public CommonResult<PageResult<RehabDailyCheckinRespVO>> getCheckinPage(@Valid RehabDailyCheckinPageReqVO reqVO) {
        return success(checkinService.getCheckinPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得打卡详情")
    @Parameter(name = "id", description = "打卡编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:checkin:detail')")
    public CommonResult<RehabDailyCheckinRespVO> getCheckin(@RequestParam("id") Long id) {
        return success(checkinService.getCheckin(id, getLoginUserId()));
    }

    @PostMapping("/create")
    @Operation(summary = "创建打卡")
    @PreAuthorize("@ss.hasPermission('rehab:checkin:create-manual')")
    public CommonResult<Long> createCheckin(@Valid @RequestBody RehabDailyCheckinCreateReqVO reqVO) {
        return success(checkinService.createCheckin(reqVO, getLoginUserId(), false));
    }

    @PostMapping("/create-manual")
    @Operation(summary = "后台代录打卡")
    @PreAuthorize("@ss.hasPermission('rehab:checkin:create-manual')")
    public CommonResult<Long> createCheckinManual(@Valid @RequestBody RehabDailyCheckinCreateReqVO reqVO) {
        return success(checkinService.createCheckin(reqVO, getLoginUserId(), true));
    }

    @GetMapping("/task-executions")
    @Operation(summary = "获得打卡任务执行明细")
    @Parameter(name = "checkinId", description = "打卡编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:checkin:detail')")
    public CommonResult<List<RehabTaskExecutionRespVO>> getTaskExecutions(@RequestParam("checkinId") Long checkinId) {
        return success(checkinService.getTaskExecutionList(checkinId, getLoginUserId()));
    }

}
