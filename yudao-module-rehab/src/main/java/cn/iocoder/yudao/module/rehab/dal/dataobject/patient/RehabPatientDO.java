package cn.iocoder.yudao.module.rehab.dal.dataobject.patient;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 康复患者主档案 DO
 */
@TableName(value = "rehab_patient")
@KeySequence("rehab_patient_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabPatientDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 患者编号（系统自动生成）
     */
    private String patientNo;
    private String name;
    private Integer gender;
    private LocalDate birthday;
    private Integer age;
    private String phone;
    private String idCardMasked;
    private String contactPerson;
    private String contactPhone;
    private String emergencyContact;
    private String emergencyPhone;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private BigDecimal bmi;
    private String dominantSide;
    private String sportType;
    private String schoolOrCompany;
    private String chiefComplaint;
    private String painArea;
    private BigDecimal painScore;
    private String medicalHistory;
    private String injuryHistory;
    private String trainingHistory;
    private String sourceChannel;
    private String remark;

    /**
     * active / inactive / archived
     */
    private String currentStatus;
    /**
     * 当前康复阶段
     */
    private String currentStage;
    private Long currentTherapistUserId;

}
