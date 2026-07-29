package cn.iocoder.yudao.module.rehab.controller.admin.alert;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.*;
import cn.iocoder.yudao.module.rehab.service.alert.RehabAlertService;
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

@Tag(name = "管理后台 - 风险提醒")
@RestController
@RequestMapping("/rehab/alert")
@Validated
public class RehabAlertController {

    @Resource
    private RehabAlertService alertService;

    @GetMapping("/page")
    @Operation(summary = "获得提醒事件分页")
    @PreAuthorize("@ss.hasPermission('rehab:alert:view')")
    public CommonResult<PageResult<RehabAlertEventRespVO>> getPage(@Valid RehabAlertEventPageReqVO reqVO) {
        return success(alertService.getAlertPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得提醒事件详情")
    @Parameter(name = "id", description = "提醒编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:alert:view')")
    public CommonResult<RehabAlertEventRespVO> get(@RequestParam("id") Long id) {
        return success(alertService.getAlert(id, getLoginUserId()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新提醒")
    @PreAuthorize("@ss.hasPermission('rehab:alert:view')")
    public CommonResult<List<Long>> refresh(@RequestBody(required = false) RehabAlertRefreshReqVO reqVO) {
        return success(alertService.refreshAlerts(reqVO == null ? new RehabAlertRefreshReqVO() : reqVO, getLoginUserId()));
    }

    @PostMapping("/acknowledge")
    @Operation(summary = "确认提醒")
    @PreAuthorize("@ss.hasPermission('rehab:alert:acknowledge')")
    public CommonResult<Boolean> acknowledge(@Valid @RequestBody RehabAlertHandleReqVO reqVO) {
        alertService.acknowledgeAlert(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/resolve")
    @Operation(summary = "解决提醒")
    @PreAuthorize("@ss.hasPermission('rehab:alert:resolve')")
    public CommonResult<Boolean> resolve(@Valid @RequestBody RehabAlertHandleReqVO reqVO) {
        alertService.resolveAlert(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/ignore")
    @Operation(summary = "忽略提醒")
    @PreAuthorize("@ss.hasPermission('rehab:alert:ignore')")
    public CommonResult<Boolean> ignore(@Valid @RequestBody RehabAlertHandleReqVO reqVO) {
        alertService.ignoreAlert(reqVO, getLoginUserId());
        return success(true);
    }

}
