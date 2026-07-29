package cn.iocoder.yudao.module.rehab.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理后台 - AI 配置更新 Request VO")
public class RehabAiConfigUpdateReqVO {

    private Boolean aiEnabled;
    private Boolean enableAssessmentInterpretation;
    private Boolean enableReportSummary;
    private Boolean enablePatientSummary;
    private Boolean enablePlanDraft;
    private Boolean enableFollowupWriter;
    private Boolean requireHumanReviewBeforeVisible;
    private Boolean visibleToPatientAfterReviewOnly;
    private String preferredModelName;
    private String promptStyle;
    private String safetyMode;
    private String note;
}
