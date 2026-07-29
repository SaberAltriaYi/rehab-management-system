package cn.iocoder.yudao.module.rehab.dal.dataobject.notification;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 患者通知
 */
@TableName("rehab_patient_notification")
@KeySequence("rehab_patient_notification_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabPatientNotificationDO extends BaseDO {

    @TableId
    private Long id;

    private Long patientId;
    private Long episodeId;
    /**
     * task_reminder / reassessment_due / progress_update / risk_notice
     */
    private String notificationType;
    private String title;
    private String content;
    /**
     * unread / read
     */
    private String readStatus;
    /**
     * sent / pending
     */
    private String sentStatus;
    private LocalDateTime visibleFrom;
    private LocalDateTime expireTime;
}
