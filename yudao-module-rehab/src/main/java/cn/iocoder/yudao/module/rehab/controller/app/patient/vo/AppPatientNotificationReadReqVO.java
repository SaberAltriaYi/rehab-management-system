package cn.iocoder.yudao.module.rehab.controller.app.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "患者端 APP - 通知已读 Request VO")
@Data
public class AppPatientNotificationReadReqVO {

    @Schema(description = "通知编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "通知编号不能为空")
    private Long id;
}
