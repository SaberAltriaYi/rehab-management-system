package cn.iocoder.yudao.module.rehab.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@Schema(description = "管理后台 - 生成进展总结 Request VO")
public class RehabAiGenerateProgressSummaryReqVO {

    @NotNull(message = "progressId 不能为空")
    private Long progressId;

    @Schema(description = "是否异步", example = "false")
    private Boolean asyncMode;
}
