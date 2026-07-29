package cn.iocoder.yudao.module.rehab.controller.app.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "患者端 APP - 报告摘要 Response VO")
@Data
public class AppPatientReportRespVO {

    @Schema(description = "报告编号", example = "1")
    private Long id;

    @Schema(description = "报告号", example = "RPT202603100001")
    private String reportNo;

    @Schema(description = "报告类型", example = "综合评估")
    private String reportType;

    @Schema(description = "报告状态", example = "reviewed")
    private String reportStatus;

    @Schema(description = "主要问题摘要")
    private String issueSummary;

    @Schema(description = "建议摘要")
    private String recommendationSummary;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
