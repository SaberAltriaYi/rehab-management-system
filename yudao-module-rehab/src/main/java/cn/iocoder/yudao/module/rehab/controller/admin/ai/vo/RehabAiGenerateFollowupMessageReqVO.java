package cn.iocoder.yudao.module.rehab.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理后台 - 生成随访文案 Request VO")
public class RehabAiGenerateFollowupMessageReqVO {

    private Long patientId;
    private Long episodeId;
    private Long progressId;
    private Long triggerId;

    @Schema(description = "是否异步", example = "false")
    private Boolean asyncMode;
}
