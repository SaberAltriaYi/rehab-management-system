package cn.iocoder.yudao.module.rehab.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - AI 生成 Response VO")
@Data
public class RehabAiGenerateRespVO {

    @Schema(description = "任务编号")
    private Long jobId;

    @Schema(description = "任务状态")
    private String jobStatus;

    @Schema(description = "输出编号")
    private Long outputId;

    @Schema(description = "审核状态")
    private String reviewStatus;

    @Schema(description = "是否 fallback")
    private Boolean fallbackUsed;

    @Schema(description = "渲染文本")
    private String renderedText;

    @Schema(description = "结构化内容")
    private String contentJson;

    @Schema(description = "是否异步")
    private Boolean asyncMode;
}
