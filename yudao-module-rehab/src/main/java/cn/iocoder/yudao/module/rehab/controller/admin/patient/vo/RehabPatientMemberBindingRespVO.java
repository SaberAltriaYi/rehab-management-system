package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 患者会员绑定 Response VO")
@Data
public class RehabPatientMemberBindingRespVO {

    private Long id;
    private Long patientId;
    private Long appUserId;
    private String bindType;
    private String bindStatus;
    private String phone;
    private String nickname;
    private LocalDateTime lastLoginTime;
    private LocalDateTime updateTime;

    private String memberNickname;
    private String memberMobile;
    private Integer memberStatus;

}
