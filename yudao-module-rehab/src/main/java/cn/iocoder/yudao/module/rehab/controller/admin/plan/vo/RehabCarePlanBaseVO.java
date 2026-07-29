package cn.iocoder.yudao.module.rehab.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class RehabCarePlanBaseVO {

    @Schema(description = "患者编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10001")
    @NotNull(message = "患者编号不能为空")
    private Long patientId;

    @Schema(description = "episode 编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13001")
    @NotNull(message = "episode 编号不能为空")
    private Long episodeId;

    @Schema(description = "来源评估编号", example = "20001")
    private Long sourceAssessmentId;

    @Schema(description = "主责治疗师用户编号", example = "100")
    private Long primaryTherapistUserId;

    @Schema(description = "计划名称", example = "膝关节稳定性四周计划")
    private String planName;

    @Schema(description = "计划类型", example = "rehab")
    private String planType;

    @Schema(description = "状态", example = "draft")
    private String status;

    @Schema(description = "开始日期")
    private LocalDate startDate;

    @Schema(description = "结束日期")
    private LocalDate endDate;

    @Schema(description = "周期天数", example = "28")
    private Integer cycleDays;

    @Schema(description = "短期目标 JSON")
    private String shortTermGoalsJson;

    @Schema(description = "中期目标 JSON")
    private String midTermGoalsJson;

    @Schema(description = "长期目标 JSON")
    private String longTermGoalsJson;

    @Schema(description = "禁忌")
    private String contraindications;

    @Schema(description = "注意事项")
    private String precautions;

    @Schema(description = "启用家庭训练")
    private Boolean homeProgramEnabled;

    @Schema(description = "启用院内训练")
    private Boolean clinicProgramEnabled;

    @Schema(description = "强度等级", example = "medium")
    private String intensityLevel;

    @Schema(description = "复评周期天数", example = "14")
    private Integer reviewCycleDays;

    @Schema(description = "备注")
    private String note;

}
