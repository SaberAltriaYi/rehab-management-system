package cn.iocoder.yudao.module.rehab.controller.admin.workspace;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.rehab.controller.admin.workspace.vo.RehabOpsDashboardSummaryRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.workspace.vo.RehabOpsRiskOverviewRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.workspace.vo.RehabOpsWorkloadRespVO;
import cn.iocoder.yudao.module.rehab.service.workspace.RehabDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 机构运营看板")
@RestController
@RequestMapping("/rehab/ops-dashboard")
@Validated
public class RehabOpsDashboardController {

    @Resource
    private RehabDashboardService dashboardService;

    @GetMapping("/summary")
    @Operation(summary = "获得运营看板摘要")
    @PreAuthorize("@ss.hasPermission('rehab:ops-dashboard:view')")
    public CommonResult<RehabOpsDashboardSummaryRespVO> getSummary() {
        return success(dashboardService.getOpsSummary(getLoginUserId()));
    }

    @GetMapping("/workload")
    @Operation(summary = "获得治疗师负载分布")
    @PreAuthorize("@ss.hasPermission('rehab:ops-dashboard:view')")
    public CommonResult<List<RehabOpsWorkloadRespVO>> getWorkload() {
        return success(dashboardService.getOpsWorkload(getLoginUserId()));
    }

    @GetMapping("/risk-overview")
    @Operation(summary = "获得风险分布")
    @PreAuthorize("@ss.hasPermission('rehab:ops-dashboard:view')")
    public CommonResult<List<RehabOpsRiskOverviewRespVO>> getRiskOverview() {
        return success(dashboardService.getOpsRiskOverview(getLoginUserId()));
    }

}
