package cn.iocoder.yudao.module.rehab.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 报告版本 Response VO")
@Data
public class RehabReportVersionRespVO {

    private Long id;
    private Long reportId;
    private Integer versionNo;
    private String reportStatus;
    private String generationMode;
    private String docxPath;
    private String pdfPath;
    private String htmlSnapshotPath;
    private Long basedOnAssessmentId;
    private String changeSummary;
    private Long generatedBy;
    private Long reviewedBy;
    private Long approvedBy;
    private Long lockedBy;
    private LocalDateTime lockedTime;
    private LocalDateTime createTime;

    private String generatedByName;
    private String reviewedByName;
    private String approvedByName;
    private String lockedByName;

}
