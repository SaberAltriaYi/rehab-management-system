package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 患者 Response VO")
@Data
public class RehabPatientRespVO {

    @Schema(description = "患者编号", example = "1")
    private Long id;
    @Schema(description = "患者编号字符串", example = "PT202603080001")
    private String patientNo;
    @Schema(description = "姓名")
    private String name;
    @Schema(description = "性别（1男 2女）")
    private Integer gender;
    @Schema(description = "生日")
    private LocalDate birthday;
    @Schema(description = "年龄")
    private Integer age;
    @Schema(description = "手机号")
    private String phone;
    @Schema(description = "当前阶段")
    private String currentStage;
    @Schema(description = "当前状态")
    private String currentStatus;
    @Schema(description = "当前主责治疗师")
    private Long currentTherapistUserId;
    @Schema(description = "当前主责治疗师姓名")
    private String currentTherapistName;
    @Schema(description = "CRM 绑定状态")
    private String crmBindStatus;
    @Schema(description = "最近更新时间")
    private LocalDateTime updateTime;

}
