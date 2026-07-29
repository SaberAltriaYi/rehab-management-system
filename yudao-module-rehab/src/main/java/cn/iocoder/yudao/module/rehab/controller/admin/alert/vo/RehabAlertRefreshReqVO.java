package cn.iocoder.yudao.module.rehab.controller.admin.alert.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 刷新提醒 Request VO")
@Data
public class RehabAlertRefreshReqVO {

    @Schema(description = "患者编号", example = "10001")
    private Long patientId;

    @Schema(description = "计划编号", example = "40001")
    private Long planId;

}
