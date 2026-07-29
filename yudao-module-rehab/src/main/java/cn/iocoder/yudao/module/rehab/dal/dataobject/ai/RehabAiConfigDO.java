package cn.iocoder.yudao.module.rehab.dal.dataobject.ai;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * AI 配置
 */
@TableName("rehab_ai_config")
@KeySequence("rehab_ai_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabAiConfigDO extends BaseDO {

    @TableId
    private Long id;

    private String configScope;
    private Long scopeId;
    private Boolean aiEnabled;
    private Boolean enableAssessmentInterpretation;
    private Boolean enableReportSummary;
    private Boolean enablePatientSummary;
    private Boolean enablePlanDraft;
    private Boolean enableFollowupWriter;
    private Boolean requireHumanReviewBeforeVisible;
    private Boolean visibleToPatientAfterReviewOnly;
    private String preferredModelName;
    private String promptStyle;
    private String safetyMode;
    private String note;
}
