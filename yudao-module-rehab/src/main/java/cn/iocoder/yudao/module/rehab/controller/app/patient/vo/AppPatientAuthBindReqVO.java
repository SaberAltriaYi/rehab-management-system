package cn.iocoder.yudao.module.rehab.controller.app.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Schema(description = "患者端 APP - 身份绑定 Request VO")
@Data
public class AppPatientAuthBindReqVO {

    @Schema(description = "患者编号", example = "10001")
    private Long patientId;

    @Schema(description = "患者编码 patient_no", example = "PAT202603100001")
    @Size(max = 64)
    private String patientNo;

    @Schema(description = "绑定手机号（用于与患者档案校验）", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
    @NotBlank(message = "手机号不能为空")
    @Size(max = 32)
    private String phone;

    @Schema(description = "机构签发的一次性邀请码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "邀请码不能为空")
    @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "邀请码格式无效")
    private String invitationCode;

    @Schema(description = "仅支持 self；监护人须通过独立授权流程，不能自行声明", example = "self")
    @Pattern(regexp = "self", message = "当前入口仅支持患者本人绑定")
    private String bindType;

    @Schema(description = "昵称", example = "小明")
    @Size(max = 64)
    private String nickname;
}
