package cn.iocoder.yudao.module.rehab.controller.app.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "患者端 APP - 今日任务 Response VO")
@Data
public class AppPatientTaskRespVO {

    @Schema(description = "任务编号", example = "50001")
    private Long id;

    @Schema(description = "任务号", example = "TSK202603100001")
    private String taskNo;

    @Schema(description = "动作名称")
    private String taskName;

    @Schema(description = "模块类型", example = "control")
    private String moduleType;

    @Schema(description = "剂量描述")
    private String dosageText;

    @Schema(description = "组数", example = "3")
    private Integer sets;

    @Schema(description = "次数", example = "10")
    private Integer repetitions;

    @Schema(description = "频率（每周）", example = "5")
    private Integer frequencyPerWeek;

    @Schema(description = "节奏")
    private String tempo;

    @Schema(description = "疼痛限制规则")
    private String painLimitRule;

    @Schema(description = "说明")
    private String instructionText;
}
