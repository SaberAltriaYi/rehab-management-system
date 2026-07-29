package cn.iocoder.yudao.module.rehab.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 报告解锁 Request VO")
@Data
public class RehabReportUnlockReqVO {

    @Schema(description = "报告编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "30001")
    @NotNull(message = "报告编号不能为空")
    private Long id;

    @Schema(description = "解锁原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "解锁原因不能为空")
    private String reason;

}
