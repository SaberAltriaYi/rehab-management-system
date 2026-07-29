package cn.iocoder.yudao.module.rehab.dal.dataobject.trigger;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 复评触发记录
 */
@TableName(value = "rehab_reassessment_trigger")
@KeySequence("rehab_reassessment_trigger_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabReassessmentTriggerDO extends BaseDO {

    @TableId
    private Long id;

    private Long patientId;
    private Long episodeId;
    private Long planId;
    private String triggerType;
    private String triggerLevel;
    private String triggerStatus;
    private String triggerMessage;
    private String suggestedAction;
    private LocalDate dueDate;
    private Long acknowledgedBy;
    private LocalDateTime acknowledgedTime;

}
