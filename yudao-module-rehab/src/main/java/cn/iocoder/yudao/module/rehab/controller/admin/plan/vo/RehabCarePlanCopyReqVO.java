package cn.iocoder.yudao.module.rehab.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RehabCarePlanCopyReqVO {

    @Schema(description = "来源计划编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "来源计划不能为空")
    private Long id;

    @Schema(description = "新计划名称")
    private String planName;

    @Schema(description = "是否自动激活", example = "false")
    private Boolean activate;

    @Schema(description = "备注")
    private String remark;

}
