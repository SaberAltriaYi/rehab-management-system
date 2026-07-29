package cn.iocoder.yudao.module.rehab.controller.admin.ai.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - AI 提示词模板分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RehabAiPromptTemplatePageReqVO extends PageParam {

    private String templateCode;
    private String moduleScope;
    private String roleScope;
    private Boolean enabled;
}
