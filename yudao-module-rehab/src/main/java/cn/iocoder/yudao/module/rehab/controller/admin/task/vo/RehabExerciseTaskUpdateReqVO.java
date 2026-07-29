package cn.iocoder.yudao.module.rehab.controller.admin.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 训练任务更新 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class RehabExerciseTaskUpdateReqVO extends RehabExerciseTaskBaseVO {

    @Schema(description = "任务编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "任务编号不能为空")
    private Long id;

}
