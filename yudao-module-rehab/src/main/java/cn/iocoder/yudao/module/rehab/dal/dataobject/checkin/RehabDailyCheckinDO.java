package cn.iocoder.yudao.module.rehab.dal.dataobject.checkin;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 每日打卡
 */
@TableName(value = "rehab_daily_checkin")
@KeySequence("rehab_daily_checkin_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabDailyCheckinDO extends BaseDO {

    @TableId
    private Long id;

    private Long patientId;
    private Long episodeId;
    private Long planId;
    private LocalDate checkinDate;
    private Long submittedByUserId;
    private String submitRoleType;
    private BigDecimal overallCompletionRate;
    private BigDecimal painScoreBefore;
    private BigDecimal painScoreAfter;
    private Integer fatigueLevel;
    private Integer confidenceLevel;
    private String overallComment;

}
