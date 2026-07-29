package cn.iocoder.yudao.module.rehab.controller.app.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(description = "患者端 APP - 任务执行项 VO")
@Data
public class AppPatientTaskExecutionItemVO {

    @Schema(description = "任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "50001")
    @NotNull(message = "任务编号不能为空")
    private Long taskId;

    @Schema(description = "完成状态 completed/partial/skipped/pain_stop", requiredMode = Schema.RequiredMode.REQUIRED, example = "completed")
    @NotBlank(message = "完成状态不能为空")
    private String completionStatus;

    @Schema(description = "完成组数", example = "3")
    private Integer completedSets;

    @Schema(description = "完成次数", example = "10")
    private Integer completedReps;

    @Schema(description = "任务疼痛评分", example = "2")
    private BigDecimal painScore;

    @Schema(description = "主观难度 1~10", example = "5")
    private Integer difficultyLevel;

    @Schema(description = "是否出现不适", example = "false")
    private Boolean symptomFlag;

    @Schema(description = "不适描述")
    private String symptomNote;

    @Schema(description = "任务备注")
    private String taskComment;
}
