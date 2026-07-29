package cn.iocoder.yudao.module.rehab.controller.admin.alert.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 提醒事件分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RehabAlertEventPageReqVO extends PageParam {

    @Schema(description = "患者编号", example = "10001")
    private Long patientId;

    @Schema(description = "episode 编号", example = "13001")
    private Long episodeId;

    @Schema(description = "计划编号", example = "40001")
    private Long planId;

    @Schema(description = "提醒类型", example = "low_adherence")
    private String alertType;

    @Schema(description = "严重级别", example = "high")
    private String severity;

    @Schema(description = "状态", example = "active")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime[] createTime;

}
