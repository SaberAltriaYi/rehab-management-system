package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 患者绑定 CRM Request VO")
@Data
public class RehabPatientBindCrmReqVO {

    @Schema(description = "患者编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "患者编号不能为空")
    private Long patientId;

    @Schema(description = "CRM 客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    @NotNull(message = "CRM 客户编号不能为空")
    private Long crmCustomerId;

    @Schema(description = "绑定来源", example = "manual")
    private String bindSource;

    @Schema(description = "同步状态", example = "success")
    private String syncStatus;

    @Schema(description = "同步消息")
    private String syncMessage;

}
