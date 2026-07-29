package cn.iocoder.yudao.module.rehab.controller.admin.alert.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 提醒事件 Response VO")
@Data
public class RehabAlertEventRespVO {

    private Long id;
    private Long ruleId;
    private Long patientId;
    private Long episodeId;
    private Long planId;
    private String relatedType;
    private Long relatedId;
    private String alertType;
    private String severity;
    private String triggerMessage;
    private String triggerMetric;
    private String triggerValue;
    private String thresholdValue;
    private String status;
    private String createdFrom;
    private Long acknowledgedBy;
    private LocalDateTime acknowledgedTime;
    private Long resolvedBy;
    private LocalDateTime resolvedTime;
    private LocalDateTime createTime;

    @Schema(description = "规则名称")
    private String ruleName;

    @Schema(description = "患者姓名")
    private String patientName;

    @Schema(description = "患者编号")
    private String patientNo;

    @Schema(description = "计划编号")
    private String planNo;

}
