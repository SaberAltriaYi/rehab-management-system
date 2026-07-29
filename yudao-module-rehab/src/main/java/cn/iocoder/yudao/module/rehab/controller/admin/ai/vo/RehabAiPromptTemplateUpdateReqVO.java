package cn.iocoder.yudao.module.rehab.controller.admin.ai.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class RehabAiPromptTemplateUpdateReqVO {

    @NotNull(message = "id 不能为空")
    private Long id;
    @NotBlank(message = "templateName 不能为空")
    private String templateName;
    @NotBlank(message = "moduleScope 不能为空")
    private String moduleScope;
    @NotBlank(message = "roleScope 不能为空")
    private String roleScope;
    private String language;
    @NotNull(message = "versionNo 不能为空")
    private Integer versionNo;
    @NotBlank(message = "systemPrompt 不能为空")
    private String systemPrompt;
    @NotBlank(message = "userPromptTemplate 不能为空")
    private String userPromptTemplate;
    @NotBlank(message = "outputSchemaName 不能为空")
    private String outputSchemaName;
    private Boolean enabled;
    private Boolean isDefault;
    private String note;
}
