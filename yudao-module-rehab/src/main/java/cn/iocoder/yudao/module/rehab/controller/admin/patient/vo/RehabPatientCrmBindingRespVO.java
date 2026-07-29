package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 患者 CRM 绑定 Response VO")
@Data
public class RehabPatientCrmBindingRespVO {

    private Long id;
    private Long patientId;
    private Long crmCustomerId;
    private String crmCustomerName;
    private String crmCustomerMobile;
    private String bindStatus;
    private String bindSource;
    private String syncStatus;
    private String syncMessage;
    private LocalDateTime bindTime;
    private LocalDateTime lastSyncTime;
    private LocalDateTime updateTime;

}
