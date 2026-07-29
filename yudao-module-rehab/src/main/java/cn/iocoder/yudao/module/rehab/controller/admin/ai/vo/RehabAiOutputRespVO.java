package cn.iocoder.yudao.module.rehab.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - AI 输出 Response VO")
@Data
public class RehabAiOutputRespVO {

    private Long id;
    private Long aiJobId;
    private String jobNo;
    private Long patientId;
    private String patientName;
    private String outputType;
    private String targetObjectType;
    private Long targetObjectId;
    private String schemaName;
    private String contentJson;
    private String renderedText;
    private String evidenceRefsJson;
    private String safetyStatus;
    private String reviewStatus;
    private Boolean patientVisible;
    private Long reviewedBy;
    private String reviewedByName;
    private LocalDateTime reviewedTime;
    private LocalDateTime createTime;
}
