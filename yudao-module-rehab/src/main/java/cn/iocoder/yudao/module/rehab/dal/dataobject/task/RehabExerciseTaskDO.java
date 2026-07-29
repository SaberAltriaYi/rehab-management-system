package cn.iocoder.yudao.module.rehab.dal.dataobject.task;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

/**
 * 训练任务
 */
@TableName(value = "rehab_exercise_task")
@KeySequence("rehab_exercise_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabExerciseTaskDO extends BaseDO {

    @TableId
    private Long id;

    private Long planId;
    private Long patientId;
    private Long episodeId;
    private String taskNo;
    private Integer sortOrder;
    private String taskName;
    private String moduleType;
    private String executionType;
    private String targetDeficit;
    private String bodyRegion;
    private String dosageText;
    private Integer repetitions;
    private Integer sets;
    private Integer holdSeconds;
    private Integer frequencyPerWeek;
    private String tempo;
    private String painLimitRule;
    private String stopRule;
    private String progressionRule;
    private String regressionRule;
    private String replacementExercise;
    private String instructionText;
    private String mediaUrl;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;

}
