package cn.iocoder.yudao.module.rehab.controller.admin.progress.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class RehabProgressRecalculateReqVO {

    @Schema(description = "计划编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "计划编号不能为空")
    private Long planId;

    @Schema(description = "周期开始日期")
    private LocalDate periodStart;

    @Schema(description = "周期结束日期")
    private LocalDate periodEnd;

    @Schema(description = "备注")
    private String remark;

}
