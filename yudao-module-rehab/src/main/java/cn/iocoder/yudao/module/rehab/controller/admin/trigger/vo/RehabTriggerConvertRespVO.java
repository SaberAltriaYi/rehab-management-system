package cn.iocoder.yudao.module.rehab.controller.admin.trigger.vo;

import lombok.Data;

@Data
public class RehabTriggerConvertRespVO {

    private Long triggerId;
    private Long patientId;
    private Long episodeId;
    private Long planId;
    private String reassessmentEntry;
    private String message;

}
