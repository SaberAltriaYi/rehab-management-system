package cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 归档评估 Request VO")
@Data
public class RehabAssessmentArchiveReqVO {

    @Schema(description = "评估编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "20001")
    @NotNull(message = "评估编号不能为空")
    private Long id;

    @Schema(description = "备注")
    private String remark;

}
