package cn.iocoder.yudao.module.rehab.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@Schema(description = "管理后台 - AI 输出采纳 Request VO")
public class RehabAiOutputAcceptReqVO {

    @NotNull(message = "outputId 不能为空")
    private Long outputId;

    @Schema(description = "患者可见")
    private Boolean patientVisible;

    @Schema(description = "审核备注")
    private String reviewNote;
}
