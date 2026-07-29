package cn.iocoder.yudao.module.rehab.controller.app.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "患者端 APP - 打卡历史 Response VO")
@Data
public class AppPatientCheckinHistoryRespVO {

    @Schema(description = "打卡编号", example = "1")
    private Long id;

    @Schema(description = "打卡日期")
    private LocalDate checkinDate;

    @Schema(description = "完成率", example = "75.50")
    private BigDecimal overallCompletionRate;

    @Schema(description = "训练前疼痛", example = "2")
    private BigDecimal painScoreBefore;

    @Schema(description = "训练后疼痛", example = "3")
    private BigDecimal painScoreAfter;

    @Schema(description = "总体备注")
    private String overallComment;

    @Schema(description = "任务执行摘要")
    private List<String> taskExecutionSummary;
}
