package cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class RehabTaskExecutionItemVO {

    @Schema(description = "任务编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "任务编号不能为空")
    private Long taskId;

    @Schema(description = "完成状态 completed/partial/skipped/pain_stop", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "完成状态不能为空")
    private String completionStatus;

    private Integer completedSets;
    private Integer completedReps;
    private BigDecimal perceivedExertion;
    private BigDecimal painScore;
    private Integer difficultyLevel;
    private Boolean symptomFlag;
    private String symptomNote;
    private String taskComment;

}
