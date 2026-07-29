package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RehabPatientBaseVO {

    @Schema(description = "姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    @Size(min = 1, max = 64, message = "姓名长度需在 1-64 之间")
    private String name;

    @Schema(description = "性别（1男 2女）", example = "1")
    private Integer gender;

    @Schema(description = "出生日期")
    private LocalDate birthday;

    @Schema(description = "年龄", example = "14")
    private Integer age;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "身份证脱敏号", example = "330*********1234")
    private String idCardMasked;

    @Schema(description = "联系人")
    private String contactPerson;

    @Schema(description = "联系人手机号")
    private String contactPhone;

    @Schema(description = "紧急联系人")
    private String emergencyContact;

    @Schema(description = "紧急联系人手机号")
    private String emergencyPhone;

    @Schema(description = "身高 cm")
    private BigDecimal heightCm;

    @Schema(description = "体重 kg")
    private BigDecimal weightKg;

    @Schema(description = "BMI")
    private BigDecimal bmi;

    @Schema(description = "惯用侧", example = "right")
    private String dominantSide;

    @Schema(description = "运动专项", example = "篮球")
    private String sportType;

    @Schema(description = "学校/公司")
    private String schoolOrCompany;

    @Schema(description = "主诉")
    private String chiefComplaint;

    @Schema(description = "疼痛部位")
    private String painArea;

    @Schema(description = "疼痛评分")
    @DecimalMin(value = "0", message = "疼痛评分不能低于 0")
    @DecimalMax(value = "10", message = "疼痛评分不能高于 10")
    private BigDecimal painScore;

    @Schema(description = "基础病史")
    private String medicalHistory;

    @Schema(description = "既往伤病")
    private String injuryHistory;

    @Schema(description = "训练史")
    private String trainingHistory;

    @Schema(description = "来源渠道", example = "门诊")
    private String sourceChannel;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "当前状态", example = "active")
    private String currentStatus;

    @Schema(description = "当前阶段", example = "待评估")
    private String currentStage;

    @Schema(description = "当前主责治疗师用户编号", example = "105")
    private Long currentTherapistUserId;

}
