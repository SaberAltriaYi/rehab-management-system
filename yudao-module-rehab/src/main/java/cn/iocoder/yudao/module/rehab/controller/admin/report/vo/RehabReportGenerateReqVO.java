package cn.iocoder.yudao.module.rehab.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;

@Schema(description = "管理后台 - 生成报告 Request VO")
@Data
public class RehabReportGenerateReqVO {

    @Schema(description = "评估编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "20001")
    @NotNull(message = "评估编号不能为空")
    @Positive
    private Long assessmentId;

    @Schema(description = "可选UUID请求编号；同一操作重试复用，主动生成新修订使用新编号")
    @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
    private String requestKey;

    @Schema(description = "报告类型", example = "comprehensive")
    private String reportType;

    @Schema(description = "生成模式 auto / ai_assisted / manual_adjusted", example = "auto")
    private String generationMode;

    @Schema(description = "备注")
    private String note;

}
