package cn.iocoder.yudao.module.rehab.controller.admin.trigger.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RehabReassessmentTriggerHandleReqVO {

    @Schema(description = "触发编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "触发编号不能为空")
    private Long id;

    @Schema(description = "备注")
    private String remark;

}
