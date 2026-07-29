package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 患者解绑 CRM Request VO")
@Data
public class RehabPatientUnbindCrmReqVO {

    @Schema(description = "患者编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "患者编号不能为空")
    private Long patientId;

    @Schema(description = "解绑备注")
    private String remark;

}
