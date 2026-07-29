package cn.iocoder.yudao.module.rehab.controller.admin.notification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 通知 Response VO")
@Data
public class RehabNotificationRespVO {

    private Long id;
    private String notificationNo;
    private String targetType;
    private Long targetUserId;
    private Long patientId;
    private Long episodeId;
    private String relatedType;
    private Long relatedId;
    private String notificationType;
    private String title;
    private String content;
    private String severity;
    private String deliveryChannel;
    private String readStatus;
    private LocalDateTime readTime;
    private String sendStatus;
    private LocalDateTime visibleFrom;
    private LocalDateTime expireTime;
    private String actionUrl;
    private String actionText;
    private LocalDateTime createTime;

    @Schema(description = "患者姓名")
    private String patientName;

    @Schema(description = "患者编号")
    private String patientNo;

}
