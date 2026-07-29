package cn.iocoder.yudao.module.rehab.dal.dataobject.plan;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

/**
 * 康复计划主表
 */
@TableName(value = "rehab_care_plan")
@KeySequence("rehab_care_plan_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabCarePlanDO extends BaseDO {

    @TableId
    private Long id;

    private String planNo;
    private Long patientId;
    private Long episodeId;
    private Long sourceAssessmentId;
    private Long primaryTherapistUserId;
    private String planName;
    private String planType;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer cycleDays;
    private String shortTermGoalsJson;
    private String midTermGoalsJson;
    private String longTermGoalsJson;
    private String contraindications;
    private String precautions;
    private Boolean homeProgramEnabled;
    private Boolean clinicProgramEnabled;
    private String intensityLevel;
    private Integer reviewCycleDays;
    private String note;

}
