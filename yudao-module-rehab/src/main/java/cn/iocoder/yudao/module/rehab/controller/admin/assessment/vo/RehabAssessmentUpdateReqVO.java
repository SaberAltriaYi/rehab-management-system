package cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "管理后台 - 更新评估 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class RehabAssessmentUpdateReqVO extends RehabAssessmentRecordBaseVO {

    @Schema(description = "评估编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "20001")
    @NotNull(message = "评估编号不能为空")
    private Long id;

    @Schema(description = "模块数据列表")
    @Valid
    private List<RehabAssessmentModuleDataItemVO> moduleDataList;

}
