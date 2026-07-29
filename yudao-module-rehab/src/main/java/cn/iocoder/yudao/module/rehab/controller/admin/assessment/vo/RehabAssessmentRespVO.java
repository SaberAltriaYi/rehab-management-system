package cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 评估记录 Response VO")
@Data
public class RehabAssessmentRespVO {

    private Long id;
    private String assessmentNo;
    private Long patientId;
    private String patientNo;
    private String patientName;
    private Long episodeId;
    private String episodeNo;
    private String assessmentType;
    private LocalDate assessmentDate;
    private Long assessorUserId;
    private String assessorName;
    private String locationType;
    private String status;
    private String chiefFocus;
    private String rawInputStatus;
    private String qualityGrade;
    private String confidenceGrade;
    private LocalDateTime updateTime;

}
