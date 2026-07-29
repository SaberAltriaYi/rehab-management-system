package cn.iocoder.yudao.module.rehab.controller.app.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "患者端 APP - 首页摘要 Response VO")
@Data
public class AppPatientHomeSummaryRespVO {

    @Schema(description = "患者编号", example = "10001")
    private Long patientId;

    @Schema(description = "患者姓名", example = "张三")
    private String patientName;

    @Schema(description = "当前阶段", example = "执行中")
    private String currentStage;

    @Schema(description = "最新报告摘要")
    private String latestReportSummary;

    @Schema(description = "当前计划摘要")
    private String currentPlanSummary;

    @Schema(description = "今日任务数", example = "4")
    private Integer todayTaskCount;

    @Schema(description = "最近打卡状态摘要")
    private String latestCheckinSummary;

    @Schema(description = "下次复评提醒")
    private String nextReassessmentReminder;

    @Schema(description = "近期注意事项")
    private String precautions;

    @Schema(description = "未读通知数", example = "2")
    private Long unreadNotificationCount;
}
