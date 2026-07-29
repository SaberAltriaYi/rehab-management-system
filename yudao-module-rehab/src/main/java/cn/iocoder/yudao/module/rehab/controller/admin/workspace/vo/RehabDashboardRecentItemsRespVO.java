package cn.iocoder.yudao.module.rehab.controller.admin.workspace.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 治疗师工作台最近事项 Response VO")
@Data
public class RehabDashboardRecentItemsRespVO {

    @Schema(description = "最近待处理提醒")
    private List<AlertItem> recentAlerts;

    @Schema(description = "最近报告")
    private List<ReportItem> recentReports;

    @Schema(description = "最近打卡异常")
    private List<CheckinItem> abnormalCheckins;

    @Data
    public static class AlertItem {
        private Long id;
        private Long patientId;
        private String patientName;
        private String patientNo;
        private String alertType;
        private String severity;
        private String status;
        private String triggerMessage;
        private LocalDateTime createTime;
    }

    @Data
    public static class ReportItem {
        private Long id;
        private String reportNo;
        private Long patientId;
        private String patientName;
        private String patientNo;
        private String reportStatus;
        private Integer reportVersion;
        private LocalDateTime updateTime;
    }

    @Data
    public static class CheckinItem {
        private Long id;
        private Long patientId;
        private String patientName;
        private String patientNo;
        private LocalDate checkinDate;
        private String reason;
        private LocalDateTime createTime;
    }

}
