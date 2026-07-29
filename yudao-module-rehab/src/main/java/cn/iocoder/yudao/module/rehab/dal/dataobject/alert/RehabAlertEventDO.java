package cn.iocoder.yudao.module.rehab.dal.dataobject.alert;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 提醒事件
 */
@TableName("rehab_alert_event")
@KeySequence("rehab_alert_event_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabAlertEventDO extends BaseDO {

    @TableId
    private Long id;

    private Long ruleId;
    private Long patientId;
    private Long episodeId;
    private Long planId;
    private String relatedType;
    private Long relatedId;
    private String alertType;
    private String severity;
    private String triggerMessage;
    private String triggerMetric;
    private String triggerValue;
    private String thresholdValue;
    private String status;
    private String createdFrom;
    private Long acknowledgedBy;
    private LocalDateTime acknowledgedTime;
    private Long resolvedBy;
    private LocalDateTime resolvedTime;
}
