package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 患者端一次性邀请 Request VO")
@Data
public class RehabPatientAppInvitationReqVO {

    @Schema(description = "患者编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Long patientId;

    @Schema(description = "邀请有效分钟数，5-10080", example = "30")
    @Min(5)
    private Integer expiresMinutes = 30;
}
