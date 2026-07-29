package cn.iocoder.yudao.module.rehab.controller.admin.alert.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 提醒处理 Request VO")
@Data
public class RehabAlertHandleReqVO {

    @Schema(description = "提醒编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "提醒编号不能为空")
    private Long id;

    @Schema(description = "备注")
    private String remark;

}
