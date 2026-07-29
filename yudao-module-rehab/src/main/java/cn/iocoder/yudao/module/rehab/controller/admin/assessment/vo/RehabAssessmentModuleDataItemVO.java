package cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "管理后台 - 评估模块数据项")
@Data
public class RehabAssessmentModuleDataItemVO {

    @Schema(description = "模块类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "nasm")
    @NotBlank(message = "模块类型不能为空")
    private String moduleType;

    @Schema(description = "模块状态", example = "completed")
    private String moduleStatus;

    @Schema(description = "模块结构化数据 JSON（字符串或对象）")
    private Object dataJson;

    @Schema(description = "来源类型", example = "manual")
    private String sourceType;

    @Schema(description = "版本", example = "v1")
    private String version;

    @Schema(description = "备注")
    private String note;

}
