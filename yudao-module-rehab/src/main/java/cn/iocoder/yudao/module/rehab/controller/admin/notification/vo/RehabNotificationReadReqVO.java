package cn.iocoder.yudao.module.rehab.controller.admin.notification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 通知已读 Request VO")
@Data
public class RehabNotificationReadReqVO {

    @Schema(description = "通知编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "通知编号不能为空")
    private Long id;

}
