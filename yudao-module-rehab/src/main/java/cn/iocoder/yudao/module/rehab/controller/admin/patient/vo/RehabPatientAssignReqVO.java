package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 患者分配治疗师 Request VO")
@Data
public class RehabPatientAssignReqVO {

    @NotNull(message = "患者编号不能为空")
    private Long patientId;

    @NotNull(message = "治疗师编号不能为空")
    private Long therapistUserId;

    @NotBlank(message = "角色类型不能为空")
    @Schema(description = "角色类型：primary / collaborator", requiredMode = Schema.RequiredMode.REQUIRED)
    private String roleType;

    @Schema(description = "分配原因")
    private String assignReason;

    @Schema(description = "备注")
    private String remark;

}
