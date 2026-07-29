package cn.iocoder.yudao.module.rehab.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 报告预览 Response VO")
@Data
public class RehabReportPreviewRespVO {

    private Long id;
    private String reportNo;
    private String reportStatus;
    private Integer reportVersion;
    private String html;
    private String reportJson;

}
