package cn.iocoder.yudao.module.rehab.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 生成报告 Request VO")
@Data
public class RehabReportGenerateReqVO {

    @Schema(description = "评估编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "20001")
    @NotNull(message = "评估编号不能为空")
    private Long assessmentId;

    @Schema(description = "报告类型", example = "comprehensive")
    private String reportType;

    @Schema(description = "生成模式 auto / ai_assisted / manual_adjusted", example = "auto")
    private String generationMode;

    @Schema(description = "备注")
    private String note;

}
