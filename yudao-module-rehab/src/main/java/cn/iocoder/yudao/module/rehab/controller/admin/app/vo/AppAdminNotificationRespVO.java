package cn.iocoder.yudao.module.rehab.controller.admin.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理端小程序 - 通知 Response VO")
@Data
public class AppAdminNotificationRespVO {

    private Long id;
    private String notificationType;
    private String title;
    private String content;
    private String severity;
    private String readStatus;
    private String actionUrl;
    private String actionText;
    private LocalDateTime createTime;

    private Long patientId;
    private String patientName;
    private String patientNo;

}
