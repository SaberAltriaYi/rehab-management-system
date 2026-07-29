package cn.iocoder.yudao.module.rehab.controller.app.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "患者端 APP - 登录 Request VO")
@Data
public class AppPatientLoginReqVO {

    @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
    @NotBlank(message = "手机号不能为空")
    private String phone;

    @Schema(description = "绑定码（v1 使用 patient_no）", requiredMode = Schema.RequiredMode.REQUIRED, example = "PAT202603100001")
    @NotBlank(message = "绑定码不能为空")
    private String bindCode;
}
