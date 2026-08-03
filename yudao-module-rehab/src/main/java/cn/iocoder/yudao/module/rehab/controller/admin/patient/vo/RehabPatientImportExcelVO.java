package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 患者批量导入 Excel 行。
 *
 * <p>患者编号仅用于重复识别；新建成功后仍由系统生成本机构患者编号。</p>
 */
@Data
public class RehabPatientImportExcelVO {

    @ExcelProperty("患者编号（可空，仅用于查重）")
    private String patientNo;

    @ExcelProperty("姓名（必填）")
    private String name;

    @ExcelProperty("性别（1男2女）")
    private Integer gender;

    @ExcelProperty("出生日期")
    private LocalDate birthday;

    @ExcelProperty("年龄")
    private Integer age;

    @ExcelProperty("手机号")
    private String phone;

    @ExcelProperty("身份证脱敏号")
    private String idCardMasked;

    @ExcelProperty("联系人")
    private String contactPerson;

    @ExcelProperty("联系人手机号")
    private String contactPhone;

    @ExcelProperty("紧急联系人")
    private String emergencyContact;

    @ExcelProperty("紧急联系人手机号")
    private String emergencyPhone;

    @ExcelProperty("身高(cm)")
    private BigDecimal heightCm;

    @ExcelProperty("体重(kg)")
    private BigDecimal weightKg;

    @ExcelProperty("惯用侧")
    private String dominantSide;

    @ExcelProperty("运动专项")
    private String sportType;

    @ExcelProperty("学校/公司")
    private String schoolOrCompany;

    @ExcelProperty("主诉")
    private String chiefComplaint;

    @ExcelProperty("疼痛部位")
    private String painArea;

    @ExcelProperty("疼痛评分(0-10)")
    private BigDecimal painScore;

    @ExcelProperty("基础病史")
    private String medicalHistory;

    @ExcelProperty("既往伤病")
    private String injuryHistory;

    @ExcelProperty("训练史")
    private String trainingHistory;

    @ExcelProperty("来源渠道")
    private String sourceChannel;

    @ExcelProperty("当前阶段")
    private String currentStage;

    @ExcelProperty("备注")
    private String remark;

}
