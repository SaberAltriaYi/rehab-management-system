package cn.iocoder.yudao.module.rehab.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 生成报告 Response VO")
@Data
public class RehabReportGenerateRespVO {

    private Long id;
    private String reportNo;
    private Integer reportVersion;
    private Boolean fallbackUsed;
    private String fallbackMessage;

}
