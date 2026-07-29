package cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;

@Schema(description = "管理后台 - 打卡分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RehabDailyCheckinPageReqVO extends PageParam {

    @Schema(description = "患者编号", example = "10001")
    private Long patientId;

    @Schema(description = "episode 编号", example = "13001")
    private Long episodeId;

    @Schema(description = "计划编号", example = "1")
    private Long planId;

    @Schema(description = "提交角色", example = "patient")
    private String submitRoleType;

    @Schema(description = "打卡日期")
    private LocalDate[] checkinDate;

}
