package cn.iocoder.yudao.module.rehab.dal.dataobject.checkin;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * 打卡-任务执行明细
 */
@TableName(value = "rehab_task_execution")
@KeySequence("rehab_task_execution_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabTaskExecutionDO extends BaseDO {

    @TableId
    private Long id;

    private Long checkinId;
    private Long taskId;
    private String completionStatus;
    private Integer completedSets;
    private Integer completedReps;
    private BigDecimal perceivedExertion;
    private BigDecimal painScore;
    private Integer difficultyLevel;
    private Boolean symptomFlag;
    private String symptomNote;
    private String taskComment;

}
