package cn.iocoder.yudao.module.rehab.controller.admin.task.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RehabExerciseTaskRespVO {

    private Long id;
    private Long planId;
    private String planNo;
    private Long patientId;
    private String patientNo;
    private String patientName;
    private Long episodeId;
    private String taskNo;
    private Integer sortOrder;
    private String taskName;
    private String moduleType;
    private String executionType;
    private String targetDeficit;
    private String bodyRegion;
    private String dosageText;
    private Integer repetitions;
    private Integer sets;
    private Integer holdSeconds;
    private Integer frequencyPerWeek;
    private String tempo;
    private String painLimitRule;
    private String stopRule;
    private String progressionRule;
    private String regressionRule;
    private String replacementExercise;
    private String instructionText;
    private String mediaUrl;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime updateTime;

}
