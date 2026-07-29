package cn.iocoder.yudao.module.rehab.controller.admin.trigger.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 复评触发分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RehabReassessmentTriggerPageReqVO extends PageParam {

    @Schema(description = "计划编号", example = "1")
    private Long planId;

    @Schema(description = "患者编号", example = "10001")
    private Long patientId;

    @Schema(description = "episode 编号", example = "13001")
    private Long episodeId;

    @Schema(description = "触发类型", example = "low_adherence")
    private String triggerType;

    @Schema(description = "触发等级", example = "medium")
    private String triggerLevel;

    @Schema(description = "触发状态", example = "pending")
    private String triggerStatus;

    @Schema(description = "创建时间")
    private LocalDateTime[] createTime;

}
