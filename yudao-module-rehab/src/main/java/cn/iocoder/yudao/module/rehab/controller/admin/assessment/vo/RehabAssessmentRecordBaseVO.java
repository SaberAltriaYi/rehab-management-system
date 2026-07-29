package cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RehabAssessmentRecordBaseVO {

    @Schema(description = "患者编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10001")
    @NotNull(message = "患者不能为空")
    private Long patientId;

    @Schema(description = "episode 编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13001")
    @NotNull(message = "episode 不能为空")
    private Long episodeId;

    @Schema(description = "评估类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "static_assessment")
    @NotBlank(message = "评估类型不能为空")
    private String assessmentType;

    @Schema(description = "评估日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "评估日期不能为空")
    private LocalDate assessmentDate;

    @Schema(description = "评估人用户编号", example = "100")
    private Long assessorUserId;

    @Schema(description = "场景类型 clinic / remote / field", example = "clinic")
    private String locationType;

    @Schema(description = "状态 draft / completed / reviewed / archived", example = "draft")
    private String status;

    @Schema(description = "本次重点", example = "下肢稳定性")
    private String chiefFocus;

    @Schema(description = "疼痛评分")
    @DecimalMin(value = "0", message = "疼痛评分不能小于 0")
    @DecimalMax(value = "10", message = "疼痛评分不能大于 10")
    private BigDecimal painScore;

    @Schema(description = "红旗备注")
    private String redFlagNotes;

    @Schema(description = "来源摘要")
    private String sourceSummary;

    @Schema(description = "原始数据状态 complete / partial / missing_items", example = "partial")
    private String rawInputStatus;

    @Schema(description = "质量等级 A/B/C/D", example = "B")
    private String qualityGrade;

    @Schema(description = "置信等级 high / medium / low", example = "medium")
    private String confidenceGrade;

    @Schema(description = "摘要")
    private String summaryText;

    @Schema(description = "备注")
    private String note;

}
