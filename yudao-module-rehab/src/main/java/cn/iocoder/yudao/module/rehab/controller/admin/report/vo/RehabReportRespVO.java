package cn.iocoder.yudao.module.rehab.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 报告 Response VO")
@Data
public class RehabReportRespVO {

    private Long id;
    private String reportNo;
    private Long patientId;
    private String patientNo;
    private String patientName;
    private Long episodeId;
    private String episodeNo;
    private Long assessmentId;
    private String assessmentNo;
    private String reportType;
    private String reportStatus;
    private Integer reportVersion;
    private Long generatedBy;
    private String generatedByName;
    private Long reviewedBy;
    private String reviewedByName;
    private Long approvedBy;
    private String approvedByName;
    private Long lockedBy;
    private String lockedByName;
    private LocalDateTime lockedTime;
    private String generationMode;
    private String docxPath;
    private String pdfPath;
    private String htmlSnapshotPath;
    private LocalDateTime lastGeneratedAt;
    private LocalDateTime exportedAt;
    private LocalDateTime updateTime;
    private String note;

}
