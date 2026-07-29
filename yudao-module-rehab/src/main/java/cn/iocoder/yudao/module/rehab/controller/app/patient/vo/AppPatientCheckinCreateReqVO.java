package cn.iocoder.yudao.module.rehab.controller.app.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "患者端 APP - 打卡提交 Request VO")
@Data
public class AppPatientCheckinCreateReqVO {

    @Schema(description = "计划编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "30001")
    @NotNull(message = "计划编号不能为空")
    private Long planId;

    @Schema(description = "打卡日期", example = "2026-03-10")
    private LocalDate checkinDate;

    @Schema(description = "训练前疼痛", example = "2")
    private BigDecimal painScoreBefore;

    @Schema(description = "训练后疼痛", example = "3")
    private BigDecimal painScoreAfter;

    @Schema(description = "疲劳等级 1~10", example = "6")
    private Integer fatigueLevel;

    @Schema(description = "信心等级 1~10", example = "7")
    private Integer confidenceLevel;

    @Schema(description = "总体备注")
    private String overallComment;

    @Schema(description = "任务执行列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @Valid
    @NotNull(message = "任务执行列表不能为空")
    private List<AppPatientTaskExecutionItemVO> taskExecutions;
}
