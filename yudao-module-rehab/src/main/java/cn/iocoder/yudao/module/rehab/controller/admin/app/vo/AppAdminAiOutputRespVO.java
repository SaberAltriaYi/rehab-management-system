package cn.iocoder.yudao.module.rehab.controller.admin.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理端小程序 - 最新 AI 输出 Response VO")
@Data
public class AppAdminAiOutputRespVO {

    @Schema(description = "输出编号")
    private Long id;
    @Schema(description = "输出类型")
    private String outputType;
    @Schema(description = "审核状态")
    private String reviewStatus;
    @Schema(description = "安全状态")
    private String safetyStatus;
    @Schema(description = "渲染文本")
    private String renderedText;
    @Schema(description = "证据引用 JSON")
    private String evidenceRefsJson;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
