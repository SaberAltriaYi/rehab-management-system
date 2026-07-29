package cn.iocoder.yudao.module.rehab.dal.dataobject.ai;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * AI 建议包
 */
@TableName("rehab_ai_suggestion_bundle")
@KeySequence("rehab_ai_suggestion_bundle_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabAiSuggestionBundleDO extends BaseDO {

    @TableId
    private Long id;

    private Long patientId;
    private Long episodeId;
    private Long sourceAssessmentId;
    private Long sourceProgressId;
    private String bundleType;
    private String summaryJson;
    private String status;
}
