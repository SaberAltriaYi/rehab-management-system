package cn.iocoder.yudao.module.rehab.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@Schema(description = "管理后台 - AI 输出驳回 Request VO")
public class RehabAiOutputRejectReqVO {

    @NotNull(message = "outputId 不能为空")
    private Long outputId;

    @Schema(description = "驳回原因")
    private String reviewNote;
}
