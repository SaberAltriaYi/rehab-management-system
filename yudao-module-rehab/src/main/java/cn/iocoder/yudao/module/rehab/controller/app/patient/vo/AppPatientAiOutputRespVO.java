package cn.iocoder.yudao.module.rehab.controller.app.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "患者端 - AI 摘要 Response VO")
@Data
public class AppPatientAiOutputRespVO {

    @Schema(description = "输出编号")
    private Long id;
    @Schema(description = "输出类型")
    private String outputType;
    @Schema(description = "渲染文本")
    private String renderedText;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
