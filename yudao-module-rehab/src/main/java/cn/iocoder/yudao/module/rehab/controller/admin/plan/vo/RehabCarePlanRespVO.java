package cn.iocoder.yudao.module.rehab.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RehabCarePlanRespVO {

    private Long id;
    private String planNo;
    private Long patientId;
    private String patientNo;
    private String patientName;
    private Long episodeId;
    private String episodeNo;
    private Long sourceAssessmentId;
    private String assessmentNo;
    private Long primaryTherapistUserId;
    private String primaryTherapistName;
    private String planName;
    private String planType;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer cycleDays;
    private String shortTermGoalsJson;
    private String midTermGoalsJson;
    private String longTermGoalsJson;
    private String contraindications;
    private String precautions;
    private Boolean homeProgramEnabled;
    private Boolean clinicProgramEnabled;
    private String intensityLevel;
    private Integer reviewCycleDays;
    private String note;
    @Schema(description = "最近进度摘要")
    private String latestProgressSummary;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
