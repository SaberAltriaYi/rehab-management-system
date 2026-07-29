package cn.iocoder.yudao.module.rehab.controller.admin.progress.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RehabProgressRecordRespVO {

    private Long id;
    private Long patientId;
    private String patientNo;
    private String patientName;
    private Long episodeId;
    private Long planId;
    private String planNo;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer plannedTaskCount;
    private BigDecimal completedTaskCount;
    private BigDecimal completionRate;
    private BigDecimal adherenceScore;
    private BigDecimal averagePainScore;
    private String painTrend;
    private Integer symptomEventsCount;
    private Integer skippedDueToPain;
    private Integer skippedDueToSchedule;
    private String clinicianImpression;
    private String progressStatus;
    private String recommendedAction;
    private LocalDateTime createTime;

}
