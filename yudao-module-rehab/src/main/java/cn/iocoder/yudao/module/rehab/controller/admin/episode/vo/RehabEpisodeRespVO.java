package cn.iocoder.yudao.module.rehab.controller.admin.episode.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - episode Response VO")
@Data
public class RehabEpisodeRespVO {

    private Long id;
    private String episodeNo;
    private Long patientId;
    private Long primaryTherapistUserId;
    private String primaryTherapistName;
    private String episodeType;
    private String currentStage;
    private LocalDate startDate;
    private LocalDate endDate;
    private String primaryGoal;
    private String status;
    private String closeReason;
    private String referralReason;
    private String note;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
