package cn.iocoder.yudao.module.rehab.controller.admin.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理端小程序 - 我的患者列表项 Response VO")
@Data
public class AppAdminPatientMiniRespVO {

    @Schema(description = "患者编号", example = "10001")
    private Long id;

    @Schema(description = "患者编号字符串", example = "PAT202603100001")
    private String patientNo;

    @Schema(description = "姓名", example = "张三")
    private String name;

    @Schema(description = "当前阶段", example = "执行中")
    private String currentStage;

    @Schema(description = "当前计划状态", example = "active")
    private String activePlanStatus;

    @Schema(description = "最近打卡日期")
    private LocalDate latestCheckinDate;

    @Schema(description = "是否存在高风险提醒", example = "true")
    private Boolean hasHighRiskTrigger;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
