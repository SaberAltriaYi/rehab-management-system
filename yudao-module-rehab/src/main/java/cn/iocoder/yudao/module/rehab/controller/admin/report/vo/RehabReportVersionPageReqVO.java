package cn.iocoder.yudao.module.rehab.controller.admin.report.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 报告版本分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RehabReportVersionPageReqVO extends PageParam {

    @Schema(description = "报告编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "30001")
    @NotNull(message = "报告编号不能为空")
    private Long reportId;

}
