package cn.iocoder.yudao.module.rehab.controller.admin.notification.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 通知分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RehabNotificationPageReqVO extends PageParam {

    @Schema(description = "患者编号", example = "10001")
    private Long patientId;

    @Schema(description = "通知类型", example = "reassessment_due")
    private String notificationType;

    @Schema(description = "严重级别", example = "warning")
    private String severity;

    @Schema(description = "已读状态", example = "unread")
    private String readStatus;

    @Schema(description = "发送状态", example = "sent")
    private String sendStatus;

    @Schema(description = "目标类型", example = "therapist")
    private String targetType;

    @Schema(description = "目标用户", example = "100")
    private Long targetUserId;

    @Schema(description = "仅查询当前登录用户相关通知", example = "true")
    private Boolean onlyMine;

    @Schema(description = "创建时间")
    private LocalDateTime[] createTime;

}
