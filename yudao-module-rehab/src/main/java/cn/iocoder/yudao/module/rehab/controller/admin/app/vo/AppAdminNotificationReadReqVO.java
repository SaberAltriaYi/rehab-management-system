package cn.iocoder.yudao.module.rehab.controller.admin.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "管理端小程序 - 通知已读 Request VO")
@Data
public class AppAdminNotificationReadReqVO {

    @Schema(description = "通知编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "通知编号不能为空")
    private Long id;

}
