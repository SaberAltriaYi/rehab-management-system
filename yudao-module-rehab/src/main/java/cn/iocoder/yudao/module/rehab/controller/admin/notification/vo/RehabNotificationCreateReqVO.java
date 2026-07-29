package cn.iocoder.yudao.module.rehab.controller.admin.notification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 创建通知 Request VO")
@Data
public class RehabNotificationCreateReqVO {

    @Schema(description = "目标类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "therapist")
    @NotBlank(message = "目标类型不能为空")
    private String targetType;

    @Schema(description = "目标用户ID", example = "100")
    private Long targetUserId;

    @Schema(description = "患者编号", example = "10001")
    private Long patientId;

    @Schema(description = "episode 编号", example = "13001")
    private Long episodeId;

    @Schema(description = "关联类型", example = "plan")
    private String relatedType;

    @Schema(description = "关联编号", example = "40001")
    private Long relatedId;

    @Schema(description = "通知类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "plan_updated")
    @NotBlank(message = "通知类型不能为空")
    private String notificationType;

    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "标题不能为空")
    private String title;

    @Schema(description = "内容", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "内容不能为空")
    private String content;

    @Schema(description = "严重级别", example = "info")
    private String severity;

    @Schema(description = "投递渠道", example = "web")
    private String deliveryChannel;

    @Schema(description = "可见开始时间")
    private LocalDateTime visibleFrom;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "操作跳转地址")
    private String actionUrl;

    @Schema(description = "操作按钮文本")
    private String actionText;

    @Schema(description = "是否立即发送", example = "true")
    @NotNull(message = "是否立即发送不能为空")
    private Boolean sentNow = Boolean.TRUE;

}
