package cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "管理后台 - 患者课程签到创建 Request VO")
@Data
public class RehabTrainingAttendanceCreateReqVO {

    @Schema(description = "患者编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10001")
    @NotNull(message = "患者不能为空")
    private Long patientId;

    @Schema(description = "训练计划编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "40001")
    @NotNull(message = "训练计划不能为空")
    private Long planId;

    @Schema(description = "训练日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-08-03")
    @NotNull(message = "训练日期不能为空")
    private LocalDate trainingDate;

    @Schema(description = "签到备注")
    @Size(max = 1000, message = "签到备注不能超过 1000 个字符")
    private String note;

}
