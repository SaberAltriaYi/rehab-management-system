package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RehabPatientExportRespVO {

    @ExcelProperty("患者编号")
    private String patientNo;

    @ExcelProperty("姓名")
    private String name;

    @ExcelProperty("手机号")
    private String phone;

    @ExcelProperty("当前阶段")
    private String currentStage;

    @ExcelProperty("主责治疗师")
    private String currentTherapistName;

    @ExcelProperty("CRM 绑定状态")
    private String crmBindStatus;

    @ExcelProperty("更新时间")
    private LocalDateTime updateTime;

}
