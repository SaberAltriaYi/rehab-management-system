package cn.iocoder.yudao.module.rehab.controller.admin.ai.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RehabAiPromptTemplateEnableReqVO {

    @NotNull(message = "id 不能为空")
    private Long id;
    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;
}
