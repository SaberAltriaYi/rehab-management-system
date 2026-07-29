package cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 评估模块数据 Response VO")
@Data
public class RehabAssessmentModuleDataRespVO {

    private Long id;
    private Long assessmentId;
    private String moduleType;
    private String moduleStatus;
    private String dataJson;
    private String sourceType;
    private String version;
    private String note;
    private LocalDateTime updateTime;

}
