package cn.iocoder.yudao.module.rehab.controller.admin.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Schema(description = "管理端小程序 - 新增随访备注 Request VO")
@Data
public class AppAdminFollowupNoteCreateReqVO {

    @Schema(description = "患者编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10001")
    @NotNull(message = "患者编号不能为空")
    private Long patientId;

    @Schema(description = "episode 编号", example = "13001")
    private Long episodeId;

    @Schema(description = "备注类型 followup/reminder/pain_feedback/adherence_comment", requiredMode = Schema.RequiredMode.REQUIRED, example = "followup")
    @NotBlank(message = "备注类型不能为空")
    private String noteType;

    @Schema(description = "可见性 internal/patient_visible", requiredMode = Schema.RequiredMode.REQUIRED, example = "internal")
    @NotBlank(message = "可见性不能为空")
    private String visibilityType;

    @Schema(description = "备注内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "本周重点控制膝外翻，减少跳跃训练")
    @NotBlank(message = "备注内容不能为空")
    private String content;
}
