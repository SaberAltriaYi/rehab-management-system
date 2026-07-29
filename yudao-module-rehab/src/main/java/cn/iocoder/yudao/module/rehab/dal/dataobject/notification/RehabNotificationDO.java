package cn.iocoder.yudao.module.rehab.dal.dataobject.notification;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 统一通知中心
 */
@TableName("rehab_notification")
@KeySequence("rehab_notification_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabNotificationDO extends BaseDO {

    @TableId
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
}
