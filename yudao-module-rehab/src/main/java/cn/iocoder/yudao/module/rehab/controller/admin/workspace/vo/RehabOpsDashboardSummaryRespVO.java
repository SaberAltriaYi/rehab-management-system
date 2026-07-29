package cn.iocoder.yudao.module.rehab.controller.admin.workspace.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 机构运营看板摘要 Response VO")
@Data
public class RehabOpsDashboardSummaryRespVO {

    private Long patientTotal;
    private Long activePatientTotal;
    private Long activePlanTotal;
    private Long weeklyNewAssessmentTotal;
    private Long pendingReassessmentTotal;
    private Long highRiskTotal;
    private Long reportGeneratedTotal;
    private Long reportExportedTotal;
    private Long lowAdherenceTotal;

    @Schema(description = "平均打卡完成率")
    private BigDecimal avgCheckinCompletionRate;

}
