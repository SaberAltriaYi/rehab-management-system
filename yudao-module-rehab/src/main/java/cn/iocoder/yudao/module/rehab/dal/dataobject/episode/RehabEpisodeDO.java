package cn.iocoder.yudao.module.rehab.dal.dataobject.episode;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

/**
 * 康复 episode DO
 */
@TableName(value = "rehab_episode")
@KeySequence("rehab_episode_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabEpisodeDO extends BaseDO {

    @TableId
    private Long id;

    private String episodeNo;
    private Long patientId;
    private Long primaryTherapistUserId;
    /**
     * initial / followup / maintenance / return_to_sport
     */
    private String episodeType;
    private String currentStage;
    private LocalDate startDate;
    private LocalDate endDate;
    private String primaryGoal;
    /**
     * active / paused / closed / referred_out
     */
    private String status;
    private String closeReason;
    private String referralReason;
    private String note;

}
