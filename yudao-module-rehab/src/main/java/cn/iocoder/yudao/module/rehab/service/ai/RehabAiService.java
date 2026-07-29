package cn.iocoder.yudao.module.rehab.service.ai;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.ai.vo.*;

/**
 * AI 增强层服务
 */
public interface RehabAiService {

    PageResult<RehabAiJobRespVO> getJobPage(RehabAiJobPageReqVO reqVO, Long operatorUserId);

    RehabAiJobRespVO getJob(Long id, Long operatorUserId);

    PageResult<RehabAiOutputRespVO> getOutputPage(RehabAiOutputPageReqVO reqVO, Long operatorUserId);

    RehabAiOutputRespVO getOutput(Long id, Long operatorUserId);

    RehabAiGenerateRespVO generateAssessmentInterpretation(RehabAiGenerateAssessmentInterpretReqVO reqVO, Long operatorUserId);

    RehabAiGenerateRespVO generateReportSummary(RehabAiGenerateReportSummaryReqVO reqVO, Long operatorUserId);

    RehabAiGenerateRespVO generateRiskExplanation(RehabAiGenerateRiskExplanationReqVO reqVO, Long operatorUserId);

    RehabAiGenerateRespVO generatePlanDraft(RehabAiGeneratePlanDraftReqVO reqVO, Long operatorUserId);

    RehabAiGenerateRespVO generateFollowupMessage(RehabAiGenerateFollowupMessageReqVO reqVO, Long operatorUserId);

    RehabAiGenerateRespVO generateProgressSummary(RehabAiGenerateProgressSummaryReqVO reqVO, Long operatorUserId);

    void acceptOutput(RehabAiOutputAcceptReqVO reqVO, Long operatorUserId);

    void editOutput(RehabAiOutputEditReqVO reqVO, Long operatorUserId);

    void rejectOutput(RehabAiOutputRejectReqVO reqVO, Long operatorUserId);

    RehabAiGenerateRespVO regenerateOutput(RehabAiOutputRegenerateReqVO reqVO, Long operatorUserId);

    RehabAiConfigRespVO getConfig(Long operatorUserId);

    void updateConfig(RehabAiConfigUpdateReqVO reqVO, Long operatorUserId);

    PageResult<RehabAiPromptTemplateRespVO> getPromptTemplatePage(RehabAiPromptTemplatePageReqVO reqVO, Long operatorUserId);

    RehabAiPromptTemplateRespVO getPromptTemplate(Long id, Long operatorUserId);

    Long createPromptTemplate(RehabAiPromptTemplateCreateReqVO reqVO, Long operatorUserId);

    void updatePromptTemplate(RehabAiPromptTemplateUpdateReqVO reqVO, Long operatorUserId);

    void enablePromptTemplate(RehabAiPromptTemplateEnableReqVO reqVO, Long operatorUserId);

    void setDefaultPromptTemplate(RehabAiPromptTemplateSetDefaultReqVO reqVO, Long operatorUserId);

    RehabAiOutputRespVO getLatestSummaryForAdminPatient(Long patientId, Long operatorUserId);

    RehabAiOutputRespVO getLatestFollowupForAdminPatient(Long patientId, Long operatorUserId);

    RehabAiOutputRespVO getLatestPatientVisibleSummary(Long patientId, Long appUserId);

    RehabAiOutputRespVO getLatestPatientVisibleFollowup(Long patientId, Long appUserId);

}
