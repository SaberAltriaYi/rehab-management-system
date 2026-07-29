package cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RehabTaskExecutionRespVO {

    private Long id;
    private Long checkinId;
    private Long taskId;
    private String taskNo;
    private String taskName;
    private String completionStatus;
    private Integer completedSets;
    private Integer completedReps;
    private BigDecimal perceivedExertion;
    private BigDecimal painScore;
    private Integer difficultyLevel;
    private Boolean symptomFlag;
    private String symptomNote;
    private String taskComment;
    private LocalDateTime createTime;

}
