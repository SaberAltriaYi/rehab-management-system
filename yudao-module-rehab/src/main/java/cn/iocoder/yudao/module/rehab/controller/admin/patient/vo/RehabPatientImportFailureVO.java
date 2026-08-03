package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - 患者导入失败明细")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RehabPatientImportFailureVO {

    @Schema(description = "Excel 行号")
    @ExcelProperty("Excel 行号")
    private Integer rowNumber;

    @Schema(description = "患者标识")
    @ExcelProperty("患者编号/姓名")
    private String patientIdentity;

    @Schema(description = "失败原因")
    @ExcelProperty("失败原因")
    private String reason;

}
