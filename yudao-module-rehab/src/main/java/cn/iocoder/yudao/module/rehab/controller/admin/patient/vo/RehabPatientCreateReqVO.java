package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

@Schema(description = "管理后台 - 患者创建 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class RehabPatientCreateReqVO extends RehabPatientBaseVO {

    @Schema(description = "姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "姓名不能为空")
    private String name;

    @Schema(description = "建档后立即创建初始 episode", example = "true")
    private Boolean initEpisode;

    @Schema(description = "初始 episode 类型", example = "initial")
    private String episodeType;

    @Schema(description = "初始 episode 目标")
    private String episodePrimaryGoal;

}
