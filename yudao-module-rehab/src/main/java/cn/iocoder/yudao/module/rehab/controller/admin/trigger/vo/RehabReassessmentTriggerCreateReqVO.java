package cn.iocoder.yudao.module.rehab.controller.admin.trigger.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class RehabReassessmentTriggerCreateReqVO {

    @Schema(description = "患者编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "患者编号不能为空")
    private Long patientId;

    @Schema(description = "episode 编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "episode 编号不能为空")
    private Long episodeId;

    @Schema(description = "计划编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "计划编号不能为空")
    private Long planId;

    @Schema(description = "触发类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "触发类型不能为空")
    private String triggerType;

    @Schema(description = "触发等级", example = "medium")
    private String triggerLevel;

    @Schema(description = "触发说明")
    private String triggerMessage;

    @Schema(description = "建议动作")
    private String suggestedAction;

    @Schema(description = "建议处理日期")
    private LocalDate dueDate;

}
