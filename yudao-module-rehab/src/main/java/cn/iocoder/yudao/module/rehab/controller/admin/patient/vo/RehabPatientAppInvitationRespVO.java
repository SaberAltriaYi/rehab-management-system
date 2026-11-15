package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Schema(description = "管理后台 - 患者端一次性邀请 Response VO")
@Data
@AllArgsConstructor
public class RehabPatientAppInvitationRespVO {

    @Schema(description = "一次性邀请原文，只在签发响应中返回")
    private String invitationCode;

    @Schema(description = "使用说明")
    private String instruction;
}
