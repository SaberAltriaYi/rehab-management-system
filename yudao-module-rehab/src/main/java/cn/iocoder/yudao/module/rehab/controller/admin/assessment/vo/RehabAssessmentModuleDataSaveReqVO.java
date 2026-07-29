package cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 保存评估模块数据 Request VO")
@Data
public class RehabAssessmentModuleDataSaveReqVO {

    @Schema(description = "评估编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "20001")
    @NotNull(message = "评估编号不能为空")
    private Long assessmentId;

    @Schema(description = "模块类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "ybt")
    @NotBlank(message = "模块类型不能为空")
    private String moduleType;

    @Schema(description = "模块状态", example = "completed")
    private String moduleStatus;

    @Schema(description = "模块数据 JSON（字符串或对象）")
    private Object dataJson;

    @Schema(description = "来源类型", example = "manual")
    private String sourceType;

    @Schema(description = "版本", example = "v1")
    private String version;

    @Schema(description = "备注")
    private String note;

}
