package cn.iocoder.yudao.module.rehab.controller.admin.ai;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.ai.vo.*;
import cn.iocoder.yudao.module.rehab.service.ai.RehabAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 康复 AI 内容中心")
@RestController
@RequestMapping("/rehab/ai")
@Validated
public class RehabAiController {

    @Resource
    private RehabAiService aiService;

    @GetMapping("/job/page")
    @Operation(summary = "AI 任务分页")
    @PreAuthorize("@ss.hasPermission('rehab:ai:job:view')")
    public CommonResult<PageResult<RehabAiJobRespVO>> getJobPage(@Valid RehabAiJobPageReqVO reqVO) {
        return success(aiService.getJobPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/job/get")
    @Operation(summary = "AI 任务详情")
    @PreAuthorize("@ss.hasPermission('rehab:ai:job:view')")
    public CommonResult<RehabAiJobRespVO> getJob(@RequestParam("id") @Parameter(description = "任务编号") Long id) {
        return success(aiService.getJob(id, getLoginUserId()));
    }

    @GetMapping("/output/page")
    @Operation(summary = "AI 输出分页")
    @PreAuthorize("@ss.hasPermission('rehab:ai:output:view')")
    public CommonResult<PageResult<RehabAiOutputRespVO>> getOutputPage(@Valid RehabAiOutputPageReqVO reqVO) {
        return success(aiService.getOutputPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/output/get")
    @Operation(summary = "AI 输出详情")
    @PreAuthorize("@ss.hasPermission('rehab:ai:output:view')")
    public CommonResult<RehabAiOutputRespVO> getOutput(@RequestParam("id") @Parameter(description = "输出编号") Long id) {
        return success(aiService.getOutput(id, getLoginUserId()));
    }

    @PostMapping("/generate/assessment-interpretation")
    @Operation(summary = "生成评估解读")
    @PreAuthorize("@ss.hasPermission('rehab:ai:generate')")
    public CommonResult<RehabAiGenerateRespVO> generateAssessmentInterpretation(@Valid @RequestBody RehabAiGenerateAssessmentInterpretReqVO reqVO) {
        return success(aiService.generateAssessmentInterpretation(reqVO, getLoginUserId()));
    }

    @PostMapping("/generate/report-summary")
    @Operation(summary = "生成报告摘要")
    @PreAuthorize("@ss.hasPermission('rehab:ai:generate')")
    public CommonResult<RehabAiGenerateRespVO> generateReportSummary(@Valid @RequestBody RehabAiGenerateReportSummaryReqVO reqVO) {
        return success(aiService.generateReportSummary(reqVO, getLoginUserId()));
    }

    @PostMapping("/generate/risk-explanation")
    @Operation(summary = "生成风险解释")
    @PreAuthorize("@ss.hasPermission('rehab:ai:generate')")
    public CommonResult<RehabAiGenerateRespVO> generateRiskExplanation(@Valid @RequestBody RehabAiGenerateRiskExplanationReqVO reqVO) {
        return success(aiService.generateRiskExplanation(reqVO, getLoginUserId()));
    }

    @PostMapping("/generate/plan-draft")
    @Operation(summary = "生成计划草案")
    @PreAuthorize("@ss.hasPermission('rehab:ai:generate')")
    public CommonResult<RehabAiGenerateRespVO> generatePlanDraft(@Valid @RequestBody RehabAiGeneratePlanDraftReqVO reqVO) {
        return success(aiService.generatePlanDraft(reqVO, getLoginUserId()));
    }

    @PostMapping("/generate/followup-message")
    @Operation(summary = "生成随访文案")
    @PreAuthorize("@ss.hasPermission('rehab:ai:generate')")
    public CommonResult<RehabAiGenerateRespVO> generateFollowupMessage(@Valid @RequestBody RehabAiGenerateFollowupMessageReqVO reqVO) {
        return success(aiService.generateFollowupMessage(reqVO, getLoginUserId()));
    }

    @PostMapping("/generate/progress-summary")
    @Operation(summary = "生成进展总结")
    @PreAuthorize("@ss.hasPermission('rehab:ai:generate')")
    public CommonResult<RehabAiGenerateRespVO> generateProgressSummary(@Valid @RequestBody RehabAiGenerateProgressSummaryReqVO reqVO) {
        return success(aiService.generateProgressSummary(reqVO, getLoginUserId()));
    }

    @PostMapping("/output/accept")
    @Operation(summary = "采纳 AI 输出")
    @PreAuthorize("@ss.hasPermission('rehab:ai:accept')")
    public CommonResult<Boolean> acceptOutput(@Valid @RequestBody RehabAiOutputAcceptReqVO reqVO) {
        aiService.acceptOutput(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/output/edit")
    @Operation(summary = "编辑 AI 输出")
    @PreAuthorize("@ss.hasPermission('rehab:ai:edit')")
    public CommonResult<Boolean> editOutput(@Valid @RequestBody RehabAiOutputEditReqVO reqVO) {
        aiService.editOutput(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/output/reject")
    @Operation(summary = "驳回 AI 输出")
    @PreAuthorize("@ss.hasPermission('rehab:ai:reject')")
    public CommonResult<Boolean> rejectOutput(@Valid @RequestBody RehabAiOutputRejectReqVO reqVO) {
        aiService.rejectOutput(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/output/regenerate")
    @Operation(summary = "重生成 AI 输出")
    @PreAuthorize("@ss.hasPermission('rehab:ai:regenerate')")
    public CommonResult<RehabAiGenerateRespVO> regenerateOutput(@Valid @RequestBody RehabAiOutputRegenerateReqVO reqVO) {
        return success(aiService.regenerateOutput(reqVO, getLoginUserId()));
    }

    @GetMapping("/config/get")
    @Operation(summary = "获取 AI 配置")
    @PreAuthorize("@ss.hasPermission('rehab:ai:config:view')")
    public CommonResult<RehabAiConfigRespVO> getConfig() {
        return success(aiService.getConfig(getLoginUserId()));
    }

    @PutMapping("/config/update")
    @Operation(summary = "更新 AI 配置")
    @PreAuthorize("@ss.hasPermission('rehab:ai:config:update')")
    public CommonResult<Boolean> updateConfig(@Valid @RequestBody RehabAiConfigUpdateReqVO reqVO) {
        aiService.updateConfig(reqVO, getLoginUserId());
        return success(true);
    }

    @GetMapping("/prompt-template/page")
    @Operation(summary = "提示词模板分页")
    @PreAuthorize("@ss.hasPermission('rehab:ai:prompt-template:view')")
    public CommonResult<PageResult<RehabAiPromptTemplateRespVO>> getPromptTemplatePage(@Valid RehabAiPromptTemplatePageReqVO reqVO) {
        return success(aiService.getPromptTemplatePage(reqVO, getLoginUserId()));
    }

    @GetMapping("/prompt-template/get")
    @Operation(summary = "提示词模板详情")
    @PreAuthorize("@ss.hasPermission('rehab:ai:prompt-template:view')")
    public CommonResult<RehabAiPromptTemplateRespVO> getPromptTemplate(@RequestParam("id") Long id) {
        return success(aiService.getPromptTemplate(id, getLoginUserId()));
    }

    @PostMapping("/prompt-template/create")
    @Operation(summary = "创建提示词模板")
    @PreAuthorize("@ss.hasPermission('rehab:ai:prompt-template:create')")
    public CommonResult<Long> createPromptTemplate(@Valid @RequestBody RehabAiPromptTemplateCreateReqVO reqVO) {
        return success(aiService.createPromptTemplate(reqVO, getLoginUserId()));
    }

    @PutMapping("/prompt-template/update")
    @Operation(summary = "更新提示词模板")
    @PreAuthorize("@ss.hasPermission('rehab:ai:prompt-template:update')")
    public CommonResult<Boolean> updatePromptTemplate(@Valid @RequestBody RehabAiPromptTemplateUpdateReqVO reqVO) {
        aiService.updatePromptTemplate(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/prompt-template/enable")
    @Operation(summary = "启停提示词模板")
    @PreAuthorize("@ss.hasPermission('rehab:ai:prompt-template:enable')")
    public CommonResult<Boolean> enablePromptTemplate(@Valid @RequestBody RehabAiPromptTemplateEnableReqVO reqVO) {
        aiService.enablePromptTemplate(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/prompt-template/set-default")
    @Operation(summary = "设置默认模板")
    @PreAuthorize("@ss.hasPermission('rehab:ai:prompt-template:enable')")
    public CommonResult<Boolean> setDefaultPromptTemplate(@Valid @RequestBody RehabAiPromptTemplateSetDefaultReqVO reqVO) {
        aiService.setDefaultPromptTemplate(reqVO, getLoginUserId());
        return success(true);
    }
}
