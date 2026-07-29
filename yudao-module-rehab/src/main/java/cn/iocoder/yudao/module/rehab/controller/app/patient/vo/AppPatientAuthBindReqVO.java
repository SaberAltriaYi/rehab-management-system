package cn.iocoder.yudao.module.rehab.controller.app.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "患者端 APP - 身份绑定 Request VO")
@Data
public class AppPatientAuthBindReqVO {

    @Schema(description = "患者编号", example = "10001")
    private Long patientId;

    @Schema(description = "患者编码 patient_no", example = "PAT202603100001")
    private String patientNo;

    @Schema(description = "绑定手机号（用于与患者档案校验）", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
    @NotBlank(message = "手机号不能为空")
    private String phone;

    @Schema(description = "绑定类型 self/caregiver/imported", example = "self")
    private String bindType;

    @Schema(description = "昵称", example = "小明")
    private String nickname;
}
