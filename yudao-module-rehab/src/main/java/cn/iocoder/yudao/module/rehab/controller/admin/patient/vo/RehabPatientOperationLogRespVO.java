package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 患者操作日志 Response VO")
@Data
public class RehabPatientOperationLogRespVO {

    private Long id;
    private Long patientId;
    private String operationType;
    private Long operatorUserId;
    private String operatorName;
    private String beforeDataJson;
    private String afterDataJson;
    private String remark;
    private LocalDateTime createTime;

}
