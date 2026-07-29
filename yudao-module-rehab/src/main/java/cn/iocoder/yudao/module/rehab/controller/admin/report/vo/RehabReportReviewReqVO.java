package cn.iocoder.yudao.module.rehab.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 报告复核 Request VO")
@Data
public class RehabReportReviewReqVO {

    @Schema(description = "报告编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "30001")
    @NotNull(message = "报告编号不能为空")
    private Long id;

    @Schema(description = "备注")
    private String remark;

}
