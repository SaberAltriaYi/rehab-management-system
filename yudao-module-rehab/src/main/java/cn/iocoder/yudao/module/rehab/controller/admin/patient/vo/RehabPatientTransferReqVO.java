package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 患者转交治疗师 Request VO")
@Data
public class RehabPatientTransferReqVO {

    @NotNull(message = "患者编号不能为空")
    private Long patientId;

    @Schema(description = "原主责治疗师编号")
    private Long fromTherapistUserId;

    @NotNull(message = "新主责治疗师编号不能为空")
    private Long toTherapistUserId;

    @Schema(description = "转交原因")
    private String reason;

    @Schema(description = "备注")
    private String remark;

}
