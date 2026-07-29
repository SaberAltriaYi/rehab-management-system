package cn.iocoder.yudao.module.rehab.dal.dataobject.task;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

/**
 * 任务排程
 */
@TableName(value = "rehab_task_schedule")
@KeySequence("rehab_task_schedule_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabTaskScheduleDO extends BaseDO {

    @TableId
    private Long id;

    private Long taskId;
    private Long planId;
    private Long patientId;
    private String scheduleType;
    private Integer weekdayMask;
    private LocalDate scheduledDate;
    private Integer targetSessions;

}
