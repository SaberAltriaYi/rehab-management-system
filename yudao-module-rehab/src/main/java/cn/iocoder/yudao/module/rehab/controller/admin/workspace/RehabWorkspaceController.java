package cn.iocoder.yudao.module.rehab.controller.admin.workspace;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.rehab.controller.admin.workspace.vo.RehabDashboardRecentItemsRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.workspace.vo.RehabDashboardSummaryRespVO;
import cn.iocoder.yudao.module.rehab.service.workspace.RehabDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 康复工作台")
@RestController
@RequestMapping("/rehab")
@Validated
public class RehabWorkspaceController {

    @Resource
    private RehabDashboardService dashboardService;

    @GetMapping("/dashboard/summary")
    @Operation(summary = "获得康复工作台摘要")
    @PreAuthorize("@ss.hasPermission('rehab:dashboard:view')")
    public CommonResult<RehabDashboardSummaryRespVO> getDashboardSummary() {
        return success(dashboardService.getTherapistSummary(getLoginUserId()));
    }

    @GetMapping("/dashboard/recent-items")
    @Operation(summary = "获得康复工作台最近事项")
    @PreAuthorize("@ss.hasPermission('rehab:dashboard:view')")
    public CommonResult<RehabDashboardRecentItemsRespVO> getDashboardRecentItems() {
        return success(dashboardService.getTherapistRecentItems(getLoginUserId()));
    }

}
