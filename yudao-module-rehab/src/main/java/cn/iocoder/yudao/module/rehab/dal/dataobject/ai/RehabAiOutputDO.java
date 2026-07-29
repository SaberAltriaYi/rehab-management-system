package cn.iocoder.yudao.module.rehab.dal.dataobject.ai;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * AI 输出
 */
@TableName("rehab_ai_output")
@KeySequence("rehab_ai_output_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabAiOutputDO extends BaseDO {

    @TableId
    private Long id;

    private Long aiJobId;
    private String outputType;
    private String targetObjectType;
    private Long targetObjectId;
    private String schemaName;
    private String contentJson;
    private String renderedText;
    private String evidenceRefsJson;
    private String safetyStatus;
    private String reviewStatus;
    private Boolean patientVisible;
    private Long reviewedBy;
    private LocalDateTime reviewedTime;
}
