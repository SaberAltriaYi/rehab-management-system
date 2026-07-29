package cn.iocoder.yudao.module.rehab.controller.admin.episode.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RehabEpisodeBaseVO {

    @Schema(description = "患者编号", example = "1")
    private Long patientId;

    @Schema(description = "主责治疗师", example = "105")
    private Long primaryTherapistUserId;

    @Schema(description = "episode 类型", example = "initial")
    private String episodeType;

    @Schema(description = "当前阶段", example = "待评估")
    private String currentStage;

    @Schema(description = "开始日期")
    private LocalDate startDate;

    @Schema(description = "结束日期")
    private LocalDate endDate;

    @Schema(description = "主要目标")
    private String primaryGoal;

    @Schema(description = "状态", example = "active")
    private String status;

    @Schema(description = "结案原因")
    private String closeReason;

    @Schema(description = "转诊原因")
    private String referralReason;

    @Schema(description = "备注")
    private String note;

}
