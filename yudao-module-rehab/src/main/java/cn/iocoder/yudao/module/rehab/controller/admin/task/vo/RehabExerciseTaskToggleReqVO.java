package cn.iocoder.yudao.module.rehab.controller.admin.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RehabExerciseTaskToggleReqVO {

    @Schema(description = "任务编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "任务编号不能为空")
    private Long id;

    @Schema(description = "备注")
    private String remark;

}
