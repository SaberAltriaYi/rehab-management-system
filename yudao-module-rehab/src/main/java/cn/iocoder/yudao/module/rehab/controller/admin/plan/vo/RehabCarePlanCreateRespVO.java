package cn.iocoder.yudao.module.rehab.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class RehabCarePlanCreateRespVO {

    @Schema(description = "计划编号", example = "1")
    private Long id;

    @Schema(description = "计划单号", example = "PLN202603100001")
    private String planNo;

}
