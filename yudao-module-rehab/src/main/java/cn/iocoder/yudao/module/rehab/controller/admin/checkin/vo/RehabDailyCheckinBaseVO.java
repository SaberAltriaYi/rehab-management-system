package cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class RehabDailyCheckinBaseVO {

    @Schema(description = "患者编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "患者编号不能为空")
    private Long patientId;

    @Schema(description = "episode 编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "episode 编号不能为空")
    private Long episodeId;

    @Schema(description = "计划编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "计划编号不能为空")
    private Long planId;

    @Schema(description = "打卡日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "打卡日期不能为空")
    private LocalDate checkinDate;

    @Schema(description = "提交角色 patient/therapist/clerk", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "提交角色不能为空")
    private String submitRoleType;

    private BigDecimal overallCompletionRate;
    private BigDecimal painScoreBefore;
    private BigDecimal painScoreAfter;
    private Integer fatigueLevel;
    private Integer confidenceLevel;
    private String overallComment;

    @Schema(description = "任务执行明细", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "任务执行明细不能为空")
    @Valid
    private List<RehabTaskExecutionItemVO> taskExecutions;

}
