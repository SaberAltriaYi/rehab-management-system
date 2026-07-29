package cn.iocoder.yudao.module.rehab.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - AI 任务 Response VO")
@Data
public class RehabAiJobRespVO {

    private Long id;
    private String jobNo;
    private Long patientId;
    private String patientName;
    private Long episodeId;
    private Long assessmentId;
    private Long reportId;
    private Long planId;
    private Long progressId;
    private Long alertId;
    private Long triggerId;
    private String jobType;
    private String modelName;
    private String promptName;
    private String inputHash;
    private String outputHash;
    private String status;
    private Boolean fallbackUsed;
    private Long latencyMs;
    private String tokenUsageJson;
    private Long triggeredByUserId;
    private String triggeredByName;
    private LocalDateTime createTime;
}
