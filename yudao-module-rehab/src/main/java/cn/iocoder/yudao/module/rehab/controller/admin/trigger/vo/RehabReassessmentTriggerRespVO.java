package cn.iocoder.yudao.module.rehab.controller.admin.trigger.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RehabReassessmentTriggerRespVO {

    private Long id;
    private Long patientId;
    private String patientNo;
    private String patientName;
    private Long episodeId;
    private Long planId;
    private String planNo;
    private String triggerType;
    private String triggerLevel;
    private String triggerStatus;
    private String triggerMessage;
    private String suggestedAction;
    private LocalDate dueDate;
    private Long acknowledgedBy;
    private String acknowledgedByName;
    private LocalDateTime acknowledgedTime;
    private LocalDateTime createTime;

}
