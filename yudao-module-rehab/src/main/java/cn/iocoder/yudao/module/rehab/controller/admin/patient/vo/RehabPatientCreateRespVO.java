package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 患者创建 Response VO")
@Data
public class RehabPatientCreateRespVO {

    @Schema(description = "患者编号", example = "1")
    private Long id;

    @Schema(description = "患者号", example = "PT202603080001")
    private String patientNo;

    @Schema(description = "是否存在疑似重复")
    private Boolean suspectedDuplicate;

    @Schema(description = "疑似重复患者编号")
    private List<Long> duplicatePatientIds;

}
