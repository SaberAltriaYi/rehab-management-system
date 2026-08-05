/*
 * Copyright (c) 2026 杨玺龙. Licensed under the MIT License.
 */
package cn.iocoder.yudao.module.rehab.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 报告中心患者汇总 Response VO")
@Data
public class RehabReportPatientRespVO {

    @Schema(description = "患者编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    @Schema(description = "患者业务编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientNo;

    @Schema(description = "患者姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientName;

    @Schema(description = "报告数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long reportCount;

    @Schema(description = "评估数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long assessmentCount;

    @Schema(description = "最近报告更新时间")
    private LocalDateTime latestReportTime;

}
