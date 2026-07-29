package cn.iocoder.yudao.module.rehab.controller.admin.plan.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RehabPlanOperationLogRespVO {

    private Long id;
    private Long planId;
    private String operationType;
    private Long operatorUserId;
    private String operatorName;
    private String beforeDataJson;
    private String afterDataJson;
    private String remark;
    private LocalDateTime createTime;

}
