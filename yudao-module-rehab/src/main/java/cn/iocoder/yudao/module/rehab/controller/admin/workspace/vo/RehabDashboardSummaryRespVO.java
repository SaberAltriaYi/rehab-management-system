package cn.iocoder.yudao.module.rehab.controller.admin.workspace.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 治疗师工作台摘要 Response VO")
@Data
public class RehabDashboardSummaryRespVO {

    @Schema(description = "我的患者数")
    private Long myPatientCount;

    @Schema(description = "执行中计划数")
    private Long activePlanCount;

    @Schema(description = "待复评患者数")
    private Long pendingReassessmentCount;

    @Schema(description = "高风险患者数")
    private Long highRiskPatientCount;

    @Schema(description = "低依从性患者数")
    private Long lowAdherencePatientCount;

    @Schema(description = "本周新增评估数")
    private Long weeklyNewAssessmentCount;

    @Schema(description = "未读通知数")
    private Long unreadNotificationCount;

}
