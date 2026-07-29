package cn.iocoder.yudao.module.rehab.controller.app.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "患者端 APP - 通知 Response VO")
@Data
public class AppPatientNotificationRespVO {

    @Schema(description = "通知编号", example = "1")
    private Long id;

    @Schema(description = "类型", example = "reassessment_due")
    private String notificationType;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "已读状态", example = "unread")
    private String readStatus;

    @Schema(description = "可见起始时间")
    private LocalDateTime visibleFrom;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
