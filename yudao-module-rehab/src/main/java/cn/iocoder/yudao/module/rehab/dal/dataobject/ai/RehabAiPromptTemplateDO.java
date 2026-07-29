package cn.iocoder.yudao.module.rehab.dal.dataobject.ai;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * AI 提示词模板
 */
@TableName("rehab_ai_prompt_template")
@KeySequence("rehab_ai_prompt_template_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabAiPromptTemplateDO extends BaseDO {

    @TableId
    private Long id;

    private String templateCode;
    private String templateName;
    private String moduleScope;
    private String roleScope;
    private String language;
    private Integer versionNo;
    private String systemPrompt;
    private String userPromptTemplate;
    private String outputSchemaName;
    private Boolean enabled;
    private Boolean isDefault;
    private String note;
}
