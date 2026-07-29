package cn.iocoder.yudao.module.rehab.dal.dataobject.assessment;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 康复评估主表
 */
@TableName(value = "rehab_assessment_record")
@KeySequence("rehab_assessment_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabAssessmentRecordDO extends BaseDO {

    @TableId
    private Long id;

    private String assessmentNo;
    private Long patientId;
    private Long episodeId;
    private String assessmentType;
    private LocalDate assessmentDate;
    private Long assessorUserId;
    private String locationType;
    private String status;
    private String chiefFocus;
    private BigDecimal painScore;
    private String redFlagNotes;
    private String sourceSummary;
    private String rawInputStatus;
    private String qualityGrade;
    private String confidenceGrade;
    private String summaryText;
    private String note;

}
