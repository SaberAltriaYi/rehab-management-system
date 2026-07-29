package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 患者 CRM 冲突检查 Request VO")
@Data
public class RehabPatientCheckCrmConflictReqVO {

    @Schema(description = "患者编号", example = "1")
    private Long patientId;

    @Schema(description = "CRM 客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    @NotNull(message = "CRM 客户编号不能为空")
    private Long crmCustomerId;

}
