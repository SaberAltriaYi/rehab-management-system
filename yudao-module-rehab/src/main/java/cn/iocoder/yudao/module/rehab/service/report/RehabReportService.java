package cn.iocoder.yudao.module.rehab.service.report;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.*;
import cn.iocoder.yudao.module.rehab.controller.admin.audit.vo.RehabAuditLogRespVO;

import java.util.List;

/**
 * 报告管理 Service
 */
public interface RehabReportService {

    PageResult<RehabReportRespVO> getReportPage(RehabReportPageReqVO reqVO, Long operatorUserId);

    RehabReportRespVO getReport(Long id, Long operatorUserId);

    RehabReportGenerateRespVO generateReport(RehabReportGenerateReqVO reqVO, Long operatorUserId);

    RehabReportPreviewRespVO previewReport(Long id, Long operatorUserId);

    void reviewReport(RehabReportReviewReqVO reqVO, Long operatorUserId);

    void approveReport(RehabReportApproveReqVO reqVO, Long operatorUserId);

    void lockReport(RehabReportLockReqVO reqVO, Long operatorUserId);

    void unlockReport(RehabReportUnlockReqVO reqVO, Long operatorUserId);

    byte[] exportDocx(Long id, Long operatorUserId);

    byte[] exportPdf(Long id, Long operatorUserId);

    List<RehabReportRespVO> getReportListByAssessment(Long assessmentId, Long operatorUserId);

    PageResult<RehabReportVersionRespVO> getReportVersionPage(RehabReportVersionPageReqVO reqVO, Long operatorUserId);

    List<RehabAuditLogRespVO> getReportAuditLogs(Long reportId, Long operatorUserId);

}
