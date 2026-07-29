package cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 评估附件 Response VO")
@Data
public class RehabAssessmentAttachmentRespVO {

    private Long id;
    private Long assessmentId;
    private String moduleType;
    private String fileName;
    private String fileType;
    private String filePath;
    private Long fileSize;
    private Long uploadUserId;
    private String parseStatus;
    private String parseMessage;
    private LocalDateTime createTime;

}
