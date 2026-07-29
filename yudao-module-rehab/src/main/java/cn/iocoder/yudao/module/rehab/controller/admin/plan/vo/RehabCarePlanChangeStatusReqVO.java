package cn.iocoder.yudao.module.rehab.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RehabCarePlanChangeStatusReqVO {

    @Schema(description = "计划编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "计划编号不能为空")
    private Long id;

    @Schema(description = "备注")
    private String remark;

}
