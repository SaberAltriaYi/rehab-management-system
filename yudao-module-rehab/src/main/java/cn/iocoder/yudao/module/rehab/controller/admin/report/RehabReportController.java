package cn.iocoder.yudao.module.rehab.controller.admin.report;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.rehab.controller.admin.audit.vo.RehabAuditLogRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.*;
import cn.iocoder.yudao.module.rehab.service.report.RehabReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 康复报告")
@RestController
@RequestMapping("/rehab/report")
@Validated
public class RehabReportController {

    @Resource
    private RehabReportService reportService;

    @GetMapping("/page")
    @Operation(summary = "获得报告分页")
    @PreAuthorize("@ss.hasPermission('rehab:report:view')")
    public CommonResult<PageResult<RehabReportRespVO>> getReportPage(@Valid RehabReportPageReqVO reqVO) {
        return success(reportService.getReportPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得报告详情")
    @Parameter(name = "id", description = "报告编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:report:detail')")
    public CommonResult<RehabReportRespVO> getReport(@RequestParam("id") Long id) {
        return success(reportService.getReport(id, getLoginUserId()));
    }

    @PostMapping("/generate")
    @Operation(summary = "生成报告")
    @PreAuthorize("@ss.hasPermission('rehab:assessment:generate-report')")
    public CommonResult<RehabReportGenerateRespVO> generateReport(@Valid @RequestBody RehabReportGenerateReqVO reqVO) {
        return success(reportService.generateReport(reqVO, getLoginUserId()));
    }

    @GetMapping("/preview")
    @Operation(summary = "预览报告")
    @Parameter(name = "id", description = "报告编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:report:preview')")
    public CommonResult<RehabReportPreviewRespVO> previewReport(@RequestParam("id") Long id) {
        return success(reportService.previewReport(id, getLoginUserId()));
    }

    @PostMapping("/review")
    @Operation(summary = "复核报告")
    @PreAuthorize("@ss.hasPermission('rehab:report:review')")
    public CommonResult<Boolean> reviewReport(@Valid @RequestBody RehabReportReviewReqVO reqVO) {
        reportService.reviewReport(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/approve")
    @Operation(summary = "审批报告")
    @PreAuthorize("@ss.hasPermission('rehab:report:approve')")
    public CommonResult<Boolean> approveReport(@Valid @RequestBody RehabReportApproveReqVO reqVO) {
        reportService.approveReport(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/lock")
    @Operation(summary = "锁版报告")
    @PreAuthorize("@ss.hasPermission('rehab:report:lock')")
    public CommonResult<Boolean> lockReport(@Valid @RequestBody RehabReportLockReqVO reqVO) {
        reportService.lockReport(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/unlock")
    @Operation(summary = "解锁报告")
    @PreAuthorize("@ss.hasPermission('rehab:report:unlock')")
    public CommonResult<Boolean> unlockReport(@Valid @RequestBody RehabReportUnlockReqVO reqVO) {
        reportService.unlockReport(reqVO, getLoginUserId());
        return success(true);
    }

    @GetMapping("/export-docx")
    @Operation(summary = "导出 DOCX")
    @Parameter(name = "id", description = "报告编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:report:export')")
    public void exportDocx(@RequestParam("id") Long id, HttpServletResponse response) throws IOException {
        RehabReportRespVO report = reportService.getReport(id, getLoginUserId());
        byte[] bytes = reportService.exportDocx(id, getLoginUserId());
        ServletUtils.writeAttachment(response, report.getReportNo() + ".docx", bytes);
    }

    @GetMapping("/export-pdf")
    @Operation(summary = "导出 PDF")
    @Parameter(name = "id", description = "报告编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:report:export')")
    public void exportPdf(@RequestParam("id") Long id, HttpServletResponse response) throws IOException {
        RehabReportRespVO report = reportService.getReport(id, getLoginUserId());
        byte[] bytes = reportService.exportPdf(id, getLoginUserId());
        ServletUtils.writeAttachment(response, report.getReportNo() + ".pdf", bytes);
    }

    @GetMapping("/by-assessment")
    @Operation(summary = "按评估查询报告")
    @Parameter(name = "assessmentId", description = "评估编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:report:view')")
    public CommonResult<List<RehabReportRespVO>> getReportByAssessment(@RequestParam("assessmentId") Long assessmentId) {
        return success(reportService.getReportListByAssessment(assessmentId, getLoginUserId()));
    }

    @GetMapping("/version/page")
    @Operation(summary = "获得报告版本分页")
    @PreAuthorize("@ss.hasPermission('rehab:report:version:view')")
    public CommonResult<PageResult<RehabReportVersionRespVO>> getReportVersionPage(@Valid RehabReportVersionPageReqVO reqVO) {
        return success(reportService.getReportVersionPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/audit-log")
    @Operation(summary = "获得报告审计日志")
    @Parameter(name = "reportId", description = "报告编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:report:version:view')")
    public CommonResult<List<RehabAuditLogRespVO>> getAuditLog(@RequestParam("reportId") Long reportId) {
        return success(reportService.getReportAuditLogs(reportId, getLoginUserId()));
    }

}
