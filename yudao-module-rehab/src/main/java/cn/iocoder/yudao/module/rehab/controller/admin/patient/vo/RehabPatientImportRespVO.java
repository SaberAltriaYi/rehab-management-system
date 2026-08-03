package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 患者批量导入结果")
@Data
@Builder
public class RehabPatientImportRespVO {

    @Schema(description = "总行数")
    private Integer totalCount;

    @Schema(description = "创建成功数")
    private Integer createdCount;

    @Schema(description = "重复跳过数")
    private Integer skippedCount;

    @Schema(description = "失败数")
    private Integer failureCount;

    @Schema(description = "创建成功患者")
    private List<String> createdPatients;

    @Schema(description = "重复跳过患者")
    private List<String> skippedPatients;

    @Schema(description = "失败明细")
    private List<RehabPatientImportFailureVO> failures;

    @Schema(description = "失败明细 Excel 的 Base64；没有失败时为空")
    private String failureExcelBase64;

}
