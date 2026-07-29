package cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 评估操作日志 Response VO")
@Data
public class RehabAssessmentOperationLogRespVO {

    private Long id;
    private Long assessmentId;
    private String operationType;
    private Long operatorUserId;
    private String operatorName;
    private String beforeDataJson;
    private String afterDataJson;
    private String remark;
    private LocalDateTime createTime;

}
