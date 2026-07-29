package cn.iocoder.yudao.module.rehab.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理后台 - 生成风险解释 Request VO")
public class RehabAiGenerateRiskExplanationReqVO {

    private Long patientId;
    private Long alertId;
    private Long triggerId;
    private Long progressId;

    @Schema(description = "是否异步", example = "false")
    private Boolean asyncMode;
}
