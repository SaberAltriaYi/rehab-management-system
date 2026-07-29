package cn.iocoder.yudao.module.rehab.controller.app.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "患者端 APP - 当前计划 Response VO")
@Data
public class AppPatientCurrentPlanRespVO {

    @Schema(description = "计划编号", example = "30001")
    private Long id;

    @Schema(description = "计划号", example = "PLN202603100001")
    private String planNo;

    @Schema(description = "计划名称")
    private String planName;

    @Schema(description = "计划类型")
    private String planType;

    @Schema(description = "计划状态")
    private String status;

    @Schema(description = "短期目标")
    private String shortTermGoalsJson;

    @Schema(description = "注意事项")
    private String precautions;

    @Schema(description = "禁忌")
    private String contraindications;

    @Schema(description = "开始日期")
    private LocalDate startDate;

    @Schema(description = "结束日期")
    private LocalDate endDate;

    @Schema(description = "任务数量", example = "4")
    private Integer taskCount;
}
