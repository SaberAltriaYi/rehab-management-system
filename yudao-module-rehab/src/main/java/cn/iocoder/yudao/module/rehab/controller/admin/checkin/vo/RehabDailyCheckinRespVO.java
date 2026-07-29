package cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RehabDailyCheckinRespVO {

    private Long id;
    private Long patientId;
    private String patientNo;
    private String patientName;
    private Long episodeId;
    private Long planId;
    private String planNo;
    private LocalDate checkinDate;
    private Long submittedByUserId;
    private String submitterName;
    private String submitRoleType;
    private BigDecimal overallCompletionRate;
    private BigDecimal painScoreBefore;
    private BigDecimal painScoreAfter;
    private Integer fatigueLevel;
    private Integer confidenceLevel;
    private String overallComment;
    private LocalDateTime createTime;

}
