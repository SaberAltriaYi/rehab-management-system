package cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.Valid;
import java.util.List;

@Schema(description = "管理后台 - 创建评估 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class RehabAssessmentCreateReqVO extends RehabAssessmentRecordBaseVO {

    @Schema(description = "模块数据列表")
    @Valid
    private List<RehabAssessmentModuleDataItemVO> moduleDataList;

}
