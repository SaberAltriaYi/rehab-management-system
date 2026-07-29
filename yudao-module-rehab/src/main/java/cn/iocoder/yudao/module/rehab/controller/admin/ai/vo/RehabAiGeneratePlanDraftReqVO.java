package cn.iocoder.yudao.module.rehab.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理后台 - 生成计划草案 Request VO")
public class RehabAiGeneratePlanDraftReqVO {

    private Long patientId;
    private Long episodeId;
    private Long assessmentId;
    private Long reportId;
    private Long progressId;

    @Schema(description = "是否异步", example = "false")
    private Boolean asyncMode;
}
