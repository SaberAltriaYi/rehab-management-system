package cn.iocoder.yudao.module.rehab.controller.admin.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理端小程序 - 工作台摘要 Response VO")
@Data
public class AppAdminDashboardSummaryRespVO {

    @Schema(description = "负责患者数", example = "12")
    private Long myPatientCount;

    @Schema(description = "待复评数", example = "3")
    private Long pendingReassessmentCount;

    @Schema(description = "高风险提醒数", example = "2")
    private Long highRiskCount;

    @Schema(description = "今日关注数", example = "4")
    private Long todayNeedFocusCount;

    @Schema(description = "最近打卡异常数", example = "1")
    private Long abnormalCheckinCount;

    @Schema(description = "未读通知数", example = "6")
    private Long unreadNotificationCount;

    @Schema(description = "待处理提醒数", example = "5")
    private Long pendingAlertCount;
}
