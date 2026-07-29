package cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 创建评估 Response VO")
@Data
public class RehabAssessmentCreateRespVO {

    @Schema(description = "评估编号", example = "20001")
    private Long id;

    @Schema(description = "评估单号", example = "ASM202603080001")
    private String assessmentNo;

}
