package cn.iocoder.yudao.module.rehab.dal.dataobject.ai;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * AI 审核日志
 */
@TableName("rehab_ai_review_log")
@KeySequence("rehab_ai_review_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabAiReviewLogDO extends BaseDO {

    @TableId
    private Long id;

    private Long aiOutputId;
    private Long reviewerUserId;
    private String reviewAction;
    private String beforeText;
    private String afterText;
    private String reviewNote;
}
