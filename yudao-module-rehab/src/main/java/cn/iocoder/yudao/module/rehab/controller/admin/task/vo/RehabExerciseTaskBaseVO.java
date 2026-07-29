package cn.iocoder.yudao.module.rehab.controller.admin.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class RehabExerciseTaskBaseVO {

    @Schema(description = "计划编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "计划编号不能为空")
    private Long planId;

    @Schema(description = "患者编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10001")
    @NotNull(message = "患者编号不能为空")
    private Long patientId;

    @Schema(description = "episode 编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13001")
    @NotNull(message = "episode 编号不能为空")
    private Long episodeId;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    @Schema(description = "模块类型", example = "stability")
    private String moduleType;

    @Schema(description = "执行类型 home/clinic/both", example = "both")
    private String executionType;

    @Schema(description = "目标缺陷")
    private String targetDeficit;

    @Schema(description = "身体区域")
    private String bodyRegion;

    @Schema(description = "剂量文本")
    private String dosageText;

    @Schema(description = "次数")
    private Integer repetitions;

    @Schema(description = "组数")
    private Integer sets;

    @Schema(description = "保持秒数")
    private Integer holdSeconds;

    @Schema(description = "周频次")
    private Integer frequencyPerWeek;

    @Schema(description = "节奏")
    private String tempo;

    @Schema(description = "疼痛限制规则")
    private String painLimitRule;

    @Schema(description = "终止条件")
    private String stopRule;

    @Schema(description = "进阶规则")
    private String progressionRule;

    @Schema(description = "退阶规则")
    private String regressionRule;

    @Schema(description = "替代动作")
    private String replacementExercise;

    @Schema(description = "动作说明")
    private String instructionText;

    @Schema(description = "示例媒体地址")
    private String mediaUrl;

    @Schema(description = "状态", example = "active")
    private String status;

    @Schema(description = "开始日期")
    private LocalDate startDate;

    @Schema(description = "结束日期")
    private LocalDate endDate;

}
