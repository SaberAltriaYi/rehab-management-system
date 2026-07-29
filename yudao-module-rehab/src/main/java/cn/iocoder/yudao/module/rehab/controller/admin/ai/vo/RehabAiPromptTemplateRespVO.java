package cn.iocoder.yudao.module.rehab.controller.admin.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RehabAiPromptTemplateRespVO {

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
    private LocalDateTime createTime;
}
