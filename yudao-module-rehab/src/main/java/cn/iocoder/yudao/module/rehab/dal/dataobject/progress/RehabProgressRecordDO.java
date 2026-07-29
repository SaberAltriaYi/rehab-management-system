package cn.iocoder.yudao.module.rehab.dal.dataobject.progress;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 进度汇总
 */
@TableName(value = "rehab_progress_record")
@KeySequence("rehab_progress_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabProgressRecordDO extends BaseDO {

    @TableId
    private Long id;

    private Long patientId;
    private Long episodeId;
    private Long planId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer plannedTaskCount;
    private BigDecimal completedTaskCount;
    private BigDecimal completionRate;
    private BigDecimal adherenceScore;
    private BigDecimal averagePainScore;
    private String painTrend;
    private Integer symptomEventsCount;
    private Integer skippedDueToPain;
    private Integer skippedDueToSchedule;
    private String clinicianImpression;
    private String progressStatus;
    private String recommendedAction;

}
