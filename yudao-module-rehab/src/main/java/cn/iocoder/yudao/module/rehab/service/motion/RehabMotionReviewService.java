package cn.iocoder.yudao.module.rehab.service.motion;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.rehab.controller.admin.motion.vo.RehabMotionAiReviewReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.motion.vo.RehabMotionAmendReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.motion.vo.RehabMotionScoreReviewReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.motion.vo.RehabMotionSignReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAiDraftDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionFileDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionManualEditDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionMetricDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionProtocolVersionDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionReportDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionRuleResultDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionScoreDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTaskDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTrialDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionAiDraftMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionAssessmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionFileMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionManualEditMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionMetricMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionProtocolVersionMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionReportMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionRuleResultMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionScoreMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionTrialMapper;
import cn.iocoder.yudao.module.rehab.service.motion.engine.MotionEngineClient;
import cn.iocoder.yudao.module.rehab.service.motion.engine.MotionEngineException;
import cn.iocoder.yudao.module.rehab.service.motion.report.MotionComparator;
import cn.iocoder.yudao.module.rehab.service.motion.report.RehabMotionReportService;
import cn.iocoder.yudao.module.rehab.service.motion.task.RehabMotionPipelineSteps;
import cn.iocoder.yudao.module.rehab.service.motion.task.RehabMotionTaskService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants.*;
import static cn.iocoder.yudao.module.rehab.enums.RehabMotionErrorCodeConstants.*;

/**
 * 治疗师审核、签署、修订、报告、对比与趋势。
 *
 * <p>硬性约束：</p>
 * <ul>
 *   <li>系统分只由引擎写入；治疗师只能写最终分，且“修改”必须填写原因并留痕；</li>
 *   <li>FMS 疼痛（动作或清除测试）= 0 分为官方规则：系统判定疼痛时最终分只能为 0；
 *       若疼痛记录有误，须修正 Trial/清除测试后重新计算；疼痛信息缺失时不允许审核；</li>
 *   <li>AI 草稿只能被接受/驳回，不影响任何分数；过期（结果已重算）的草稿不可接受；</li>
 *   <li>签署前置：结果为最新修订、无运行中分析任务、全部评分已审核、AI 草稿已处理；</li>
 *   <li>签署后锁定；修订须填写原因，旧报告标记为 superseded（保留，不删除）。</li>
 * </ul>
 */
@Service
public class RehabMotionReviewService {

    static final int MAX_TREND_POINTS = 50;

    @Resource
    private RehabMotionAssessmentMapper assessmentMapper;
    @Resource
    private RehabMotionTrialMapper trialMapper;
    @Resource
    private RehabMotionScoreMapper scoreMapper;
    @Resource
    private RehabMotionRuleResultMapper ruleResultMapper;
    @Resource
    private RehabMotionMetricMapper metricMapper;
    @Resource
    private RehabMotionAiDraftMapper aiDraftMapper;
    @Resource
    private RehabMotionReportMapper reportMapper;
    @Resource
    private RehabMotionFileMapper motionFileMapper;
    @Resource
    private RehabMotionManualEditMapper manualEditMapper;
    @Resource
    private RehabMotionProtocolVersionMapper protocolVersionMapper;
    @Resource
    private RehabMotionAccess access;
    @Resource
    private RehabMotionTaskService taskService;
    @Resource
    private RehabMotionPipelineSteps steps;
    @Resource
    private RehabMotionReportService reportService;
    @Resource
    private RehabMotionResultWriter resultWriter;
    @Resource
    private MotionEngineClient engineClient;
    @Resource
    private MotionStorage storage;
    @Resource
    private AdminUserApi adminUserApi;

    // ========== 结果聚合 ==========

    public Map<String, Object> result(Long assessmentId, Long userId) {
        RehabMotionAssessmentDO a = access.readable(assessmentId, userId);
        List<RehabMotionScoreDO> scores = scoreMapper.selectListByAssessmentId(a.getId());
        List<RehabMotionAiDraftDO> drafts = aiDraftMapper.selectListByAssessmentId(a.getId());
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("assessmentId", a.getId());
        out.put("status", a.getStatus());
        out.put("statusLabel", stateLabel(a.getStatus()));
        out.put("inputRevision", a.getInputRevision());
        out.put("analyzedRevision", a.getAnalyzedRevision());
        out.put("stale", isStale(a));
        out.put("engineVersion", a.getEngineVersion());
        out.put("ruleVersion", a.getRuleVersion());
        out.put("resultSchemaVersion", a.getResultSchemaVersion());
        out.put("protocolVersions", parse(a.getProtocolVersionsJson()));
        out.put("sessionQualityStatus", a.getSessionQualityStatus());
        out.put("analyzedTime", a.getAnalyzedTime());
        out.put("trials", trialMapper.selectListByAssessmentId(a.getId()));
        out.put("scores", scoreViews(scores));
        out.put("fmsTotal", MotionScoreRules.fmsTotal(scores));
        out.put("fmsTotalNote", "FMS 总分仅在 7 项均经治疗师审核后显示，且仅作描述性参考（P9）");
        out.put("rules", ruleResultMapper.selectListByAssessmentId(a.getId()));
        out.put("metrics", metricMapper.selectListByAssessmentId(a.getId()));
        out.put("aiDrafts", drafts.size() > 5 ? drafts.subList(0, 5) : drafts);
        out.put("reports", reportViews(reportMapper.selectListByAssessmentId(a.getId())));
        out.put("limitations", RehabMotionResultWriter.limitations(resultWriter.readResult(a)));
        out.put("signBlockers", signBlockers(a, scores, drafts));
        out.put("manualInputs", MotionEngineRequestBuilder.parseManualInputs(a.getManualInputsJson()));
        out.put("videoConsent", Boolean.TRUE.equals(a.getVideoConsent()));
        return out;
    }

    /** 曲线：显示用降采样序列（计算均基于原始帧），按 OpenCap trial 名取。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> series(Long assessmentId, String trialName, Long userId) {
        RehabMotionAssessmentDO a = access.readable(assessmentId, userId);
        Object all = resultWriter.readResult(a).get("series");
        if (!(all instanceof Map)) {
            return Collections.emptyMap();
        }
        if (StrUtil.isBlank(trialName)) {
            Map<String, Object> names = new LinkedHashMap<String, Object>();
            names.put("trials", new ArrayList<String>(((Map<String, Object>) all).keySet()));
            return names;
        }
        Object one = ((Map<String, Object>) all).get(trialName);
        return one instanceof Map ? (Map<String, Object>) one : Collections.<String, Object>emptyMap();
    }

    static List<Map<String, Object>> scoreViews(List<RehabMotionScoreDO> scores) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (RehabMotionScoreDO s : scores) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", s.getId());
            m.put("family", s.getFamily());
            m.put("testCode", s.getTestCode());
            m.put("testLabel", MotionLabels.test(s.getTestCode()));
            m.put("side", s.getSide());
            m.put("scoringScheme", s.getScoringScheme());
            m.put("systemScore", s.getSystemScore());
            m.put("provisionalScore", s.getProvisionalScore());
            m.put("systemStatus", s.getSystemStatus());
            m.put("finalScore", s.getFinalScore());
            m.put("finalStatus", s.getFinalStatus());
            m.put("changeReason", s.getChangeReason());
            m.put("reviewedUserId", s.getReviewedUserId());
            m.put("reviewedTime", s.getReviewedTime());
            m.put("systemScoreChanged", s.getSystemScoreChanged());
            m.put("analysisRevision", s.getAnalysisRevision());
            m.put("detail", parse(s.getDetailJson()));
            m.put("evidence", parse(s.getEvidenceJson()));
            out.add(m);
        }
        return out;
    }

    static List<Map<String, Object>> reportViews(List<RehabMotionReportDO> reports) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (RehabMotionReportDO r : reports) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", r.getId());
            m.put("reportType", r.getReportType());
            m.put("versionNo", r.getVersionNo());
            m.put("status", r.getStatus());
            m.put("pdfStatus", r.getPdfStatus());
            m.put("signerName", r.getSignerName());
            m.put("signedTime", r.getSignedTime());
            m.put("contentSha256", r.getContentSha256());
            m.put("engineVersion", r.getEngineVersion());
            m.put("ruleVersion", r.getRuleVersion());
            m.put("promptVersion", r.getPromptVersion());
            m.put("aiModel", r.getAiModel());
            out.add(m);
        }
        return out;
    }

    // ========== 评分审核 ==========

    @Transactional(rollbackFor = Exception.class)
    public void reviewScore(RehabMotionScoreReviewReqVO req, Long userId) {
        RehabMotionScoreDO score = scoreMapper.selectById(req.getScoreId());
        if (score == null) {
            throw exception(MOTION_SCORE_NOT_EXISTS);
        }
        RehabMotionAssessmentDO a = access.editable(score.getAssessmentId(), userId);
        access.requireClinician(userId);
        assessmentMapper.lockById(a.getId());
        a = assessmentMapper.selectById(a.getId());
        requireReviewable(a);
        if (!Objects.equals(score.getAnalysisRevision(), a.getAnalyzedRevision())) {
            throw exception(MOTION_STATE_INVALID, "评分来自旧的分析结果，请刷新");
        }
        String status = req.getFinalStatus();
        BigDecimal value = req.getFinalScore();
        String reason = StrUtil.trimToNull(req.getReason());
        String err = checkFinal(score, status, value, reason);
        if (err != null) {
            if ("REASON".equals(err)) {
                throw exception(MOTION_REASON_REQUIRED);
            }
            throw exception(MOTION_STATE_INVALID, err);
        }
        BigDecimal finalValue = FINAL_CONFIRMED.equals(status) ? score.getSystemScore() : value;
        String rangeErr = MotionScoreRules.validateFinal(score, finalValue, status);
        if (rangeErr != null) {
            throw exception(MOTION_SCORE_OUT_OF_RANGE);
        }
        Map<String, Object> before = scoreAudit(score);
        scoreMapper.update(null, new LambdaUpdateWrapper<RehabMotionScoreDO>()
                .set(RehabMotionScoreDO::getFinalScore, finalValue)
                .set(RehabMotionScoreDO::getFinalStatus, status)
                .set(RehabMotionScoreDO::getChangeReason, reason == null ? null : StrUtil.maxLength(reason, 497))
                .set(RehabMotionScoreDO::getReviewedUserId, userId)
                .set(RehabMotionScoreDO::getReviewedTime, LocalDateTime.now())
                .set(RehabMotionScoreDO::getSystemScoreChanged, false)
                .eq(RehabMotionScoreDO::getId, score.getId()));
        String key = score.getFamily() + ":" + score.getTestCode() + ":" + score.getSide();
        access.manualEdit(a.getId(), "score", score.getId(), key, "final_score",
                score.getFinalScore(), finalValue, reason, userId);
        if (!Objects.equals(score.getFinalStatus(), status)) {
            access.manualEdit(a.getId(), "score", score.getId(), key, "final_status",
                    score.getFinalStatus(), status, reason, userId);
        }
        markInReview(a.getId());
        RehabMotionScoreDO after = scoreMapper.selectById(score.getId());
        access.audit(a.getId(), "score_review", userId, before, scoreAudit(after), reason);
    }

    /**
     * 业务规则校验（与取值范围校验分开）。
     *
     * @return null = 通过；"REASON" = 缺原因；其他 = 错误说明
     */
    static String checkFinal(RehabMotionScoreDO score, String status, BigDecimal value, String reason) {
        String sys = score.getSystemStatus();
        if ("FMS_0_3".equals(score.getScoringScheme())) {
            if ("pending_pain".equals(sys)) {
                return "疼痛信息（含清除测试）未记录：请先补录后重新计算，再审核（QC-06）";
            }
            if ("pain_zero".equals(sys) && !FINAL_NOT_APPLICABLE.equals(status)) {
                BigDecimal v = FINAL_CONFIRMED.equals(status) ? score.getSystemScore() : value;
                if (v == null || v.compareTo(BigDecimal.ZERO) != 0) {
                    return "FMS 官方规则：动作或清除测试出现疼痛即为 0 分；如疼痛记录有误，请修正后重新计算";
                }
            }
        }
        if (FINAL_CONFIRMED.equals(status)) {
            if (value != null && !MotionScoreRules.sameScore(value, score.getSystemScore())) {
                return "确认系统分时不应填写不同的分数；如需改分请选择“修改”并填写原因";
            }
            if (score.getSystemScore() == null && !"NASM_EVIDENCE".equals(score.getScoringScheme())) {
                return "系统未给出建议分（质控未通过/疼痛缺失/需人工判定），请选择“修改”并按现场观察填写分数与原因";
            }
            return null;
        }
        if (FINAL_MODIFIED.equals(status) || FINAL_NOT_APPLICABLE.equals(status)) {
            return reason == null ? "REASON" : null;
        }
        return "未知的审核状态";
    }

    // ========== AI 草稿 ==========

    @Transactional(rollbackFor = Exception.class)
    public void reviewAiDraft(RehabMotionAiReviewReqVO req, Long userId) {
        RehabMotionAiDraftDO draft = aiDraftMapper.selectById(req.getDraftId());
        if (draft == null) {
            throw exception(MOTION_AI_DRAFT_NOT_EXISTS);
        }
        RehabMotionAssessmentDO a = access.editable(draft.getAssessmentId(), userId);
        access.requireClinician(userId);
        assessmentMapper.lockById(a.getId());
        a = assessmentMapper.selectById(a.getId());
        requireReviewable(a);
        if (!AI_GENERATED.equals(draft.getStatus()) && !AI_FALLBACK.equals(draft.getStatus())) {
            throw exception(MOTION_STATE_INVALID, "该草稿已处理或已过期");
        }
        if (!Objects.equals(draft.getAnalysisRevision(), a.getAnalyzedRevision())) {
            throw exception(MOTION_STATE_INVALID, "草稿基于旧的分析结果，已过期");
        }
        boolean accept = "accept".equals(req.getAction());
        String edited = StrUtil.trimToNull(req.getEditedText());
        if (accept) {
            // 同一评估只保留一个已接受草稿
            aiDraftMapper.update(null, new LambdaUpdateWrapper<RehabMotionAiDraftDO>()
                    .set(RehabMotionAiDraftDO::getStatus, AI_STALE)
                    .eq(RehabMotionAiDraftDO::getAssessmentId, a.getId())
                    .eq(RehabMotionAiDraftDO::getStatus, AI_ACCEPTED));
        }
        aiDraftMapper.update(null, new LambdaUpdateWrapper<RehabMotionAiDraftDO>()
                .set(RehabMotionAiDraftDO::getStatus, accept ? AI_ACCEPTED : AI_REJECTED)
                .set(RehabMotionAiDraftDO::getReviewedUserId, userId)
                .set(RehabMotionAiDraftDO::getReviewedTime, LocalDateTime.now())
                .set(RehabMotionAiDraftDO::getEditedText, accept ? edited : null)
                .eq(RehabMotionAiDraftDO::getId, draft.getId()));
        if (accept && edited != null && !edited.equals(draft.getRenderedText())) {
            access.manualEdit(a.getId(), "ai_draft", draft.getId(), null, "edited_text",
                    "[AI 原文 " + (draft.getRenderedText() == null ? 0 : draft.getRenderedText().length()) + " 字]",
                    "[治疗师修改后 " + edited.length() + " 字]", "治疗师编辑 AI 草稿", userId);
        }
        markInReview(a.getId());
        Map<String, Object> after = new LinkedHashMap<String, Object>();
        after.put("draftId", draft.getId());
        after.put("action", req.getAction());
        after.put("edited", edited != null);
        access.audit(a.getId(), accept ? "ai_draft_accept" : "ai_draft_reject", userId, null, after, null);
    }

    /** 重新生成 AI 草稿（异步任务；AI 关闭/失败时生成模板草稿）。 */
    public RehabMotionTaskDO regenerateAi(Long assessmentId, String idempotencyKey, Long userId) {
        RehabMotionAssessmentDO a = access.editable(assessmentId, userId);
        access.requireClinician(userId);
        requireReviewable(a);
        RehabMotionTaskDO task = taskService.enqueue(a, TASK_AI, STATE_AI_GENERATING, idempotencyKey, userId, null);
        steps.syncAssessmentStatus(a.getId(), task.getState());
        access.audit(a.getId(), "ai_regenerate", userId, null, RehabMotionAssessmentService.taskAudit(task), null);
        return task;
    }

    // ========== 签署 / 修订 ==========

    @Transactional(rollbackFor = Exception.class)
    public void sign(RehabMotionSignReqVO req, Long userId) {
        RehabMotionAssessmentDO a = access.editable(req.getAssessmentId(), userId);
        access.requireClinician(userId);
        assessmentMapper.lockById(a.getId());
        a = assessmentMapper.selectById(a.getId());
        List<RehabMotionScoreDO> scores = scoreMapper.selectListByAssessmentId(a.getId());
        List<RehabMotionAiDraftDO> drafts = aiDraftMapper.selectListByAssessmentId(a.getId());
        List<String> blockers = signBlockers(a, scores, drafts);
        if (!blockers.isEmpty()) {
            throw exception(MOTION_SIGN_PRECONDITION, StrUtil.join("；", blockers));
        }
        RehabMotionAiDraftDO accepted = null;
        for (RehabMotionAiDraftDO d : drafts) {
            if (AI_ACCEPTED.equals(d.getStatus()) && Objects.equals(d.getAnalysisRevision(), a.getAnalyzedRevision())) {
                accepted = d;
                break;
            }
        }
        Map<String, Object> comparison = null;
        if (a.getBaselineId() != null) {
            comparison = compareInternal(assessmentMapper.selectById(a.getBaselineId()), a);
        }
        LocalDateTime now = LocalDateTime.now();
        String signerName = signerName(userId);
        a.setSignedUserId(userId);
        a.setSignedTime(now);
        reportService.createSignedReports(a, accepted, comparison, userId, signerName, now);
        assessmentMapper.update(null, new LambdaUpdateWrapper<RehabMotionAssessmentDO>()
                .set(RehabMotionAssessmentDO::getStatus, STATE_COMPLETED)
                .set(RehabMotionAssessmentDO::getReviewStatus, "reviewed")
                .set(RehabMotionAssessmentDO::getSignedUserId, userId)
                .set(RehabMotionAssessmentDO::getSignedTime, now)
                .eq(RehabMotionAssessmentDO::getId, a.getId()));
        RehabMotionTaskDO pdf = taskService.enqueue(a, TASK_PDF, STATE_PDF_RENDERING, null, userId, null);
        Map<String, Object> after = new LinkedHashMap<String, Object>();
        after.put("analyzedRevision", a.getAnalyzedRevision());
        after.put("aiDraftId", accepted == null ? null : accepted.getId());
        after.put("fmsTotal", MotionScoreRules.fmsTotal(scores));
        after.put("pdfTaskId", pdf.getId());
        access.audit(a.getId(), "sign", userId, null, after, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void amend(RehabMotionAmendReqVO req, Long userId) {
        RehabMotionAssessmentDO a = access.readable(req.getAssessmentId(), userId);
        access.requireClinician(userId);
        assessmentMapper.lockById(a.getId());
        a = assessmentMapper.selectById(a.getId());
        if (!STATE_COMPLETED.equals(a.getStatus())) {
            throw exception(MOTION_STATE_INVALID, "仅已签署的评估可发起修订");
        }
        String reason = StrUtil.trimToNull(req.getReason());
        if (reason == null) {
            throw exception(MOTION_REASON_REQUIRED);
        }
        reportService.supersede(a.getId());
        assessmentMapper.update(null, new LambdaUpdateWrapper<RehabMotionAssessmentDO>()
                .set(RehabMotionAssessmentDO::getStatus, STATE_PENDING_REVIEW)
                .set(RehabMotionAssessmentDO::getReviewStatus, "in_review")
                .set(RehabMotionAssessmentDO::getSignedUserId, null)
                .set(RehabMotionAssessmentDO::getSignedTime, null)
                .eq(RehabMotionAssessmentDO::getId, a.getId()));
        access.manualEdit(a.getId(), "assessment", a.getId(), null, "status", STATE_COMPLETED, STATE_PENDING_REVIEW,
                reason, userId);
        access.audit(a.getId(), "amend", userId, a.getSignedUserId(), null, reason);
    }

    /** 签署前置条件（空列表 = 可签署）。 */
    List<String> signBlockers(RehabMotionAssessmentDO a, List<RehabMotionScoreDO> scores,
                              List<RehabMotionAiDraftDO> drafts) {
        List<String> out = new ArrayList<String>();
        if (!STATE_PENDING_REVIEW.equals(a.getStatus())) {
            out.add("当前状态为“" + stateLabel(a.getStatus()) + "”，需为“待治疗师审核”");
        }
        if (a.getAnalyzedRevision() == null) {
            out.add("尚未完成分析");
        } else if (isStale(a)) {
            out.add("输入已修改，分析结果已过期，请重新计算");
        }
        if (taskService.hasActive(a.getId())) {
            out.add("存在进行中的任务");
        }
        if (scores.isEmpty()) {
            out.add("没有可审核的评分");
        }
        List<String> pending = MotionScoreRules.unreviewed(scores);
        if (!pending.isEmpty()) {
            out.add("以下评分未审核：" + StrUtil.join("、", pending.size() > 10 ? pending.subList(0, 10) : pending)
                    + (pending.size() > 10 ? " 等 " + pending.size() + " 项" : ""));
        }
        for (RehabMotionAiDraftDO d : drafts) {
            if ((AI_GENERATED.equals(d.getStatus()) || AI_FALLBACK.equals(d.getStatus()))
                    && Objects.equals(d.getAnalysisRevision(), a.getAnalyzedRevision())) {
                out.add("AI 报告草稿尚未接受或驳回");
                break;
            }
        }
        return out;
    }

    private void requireReviewable(RehabMotionAssessmentDO a) {
        if (!STATE_PENDING_REVIEW.equals(a.getStatus())) {
            throw exception(MOTION_STATE_INVALID, "需为“待治疗师审核”状态，当前为“" + stateLabel(a.getStatus()) + "”");
        }
        if (a.getAnalyzedRevision() == null || isStale(a)) {
            throw exception(MOTION_STATE_INVALID, "输入已修改，请先重新计算");
        }
    }

    private void markInReview(Long assessmentId) {
        assessmentMapper.update(null, new LambdaUpdateWrapper<RehabMotionAssessmentDO>()
                .set(RehabMotionAssessmentDO::getReviewStatus, "in_review")
                .eq(RehabMotionAssessmentDO::getId, assessmentId)
                .and(w -> w.isNull(RehabMotionAssessmentDO::getReviewStatus)
                        .or().ne(RehabMotionAssessmentDO::getReviewStatus, "in_review")));
    }

    static boolean isStale(RehabMotionAssessmentDO a) {
        return a.getAnalyzedRevision() != null && !Objects.equals(a.getAnalyzedRevision(), a.getInputRevision());
    }

    private String signerName(Long userId) {
        try {
            Map<Long, AdminUserRespDTO> users = adminUserApi.getUserMap(Collections.singleton(userId));
            AdminUserRespDTO u = users == null ? null : users.get(userId);
            return u == null ? String.valueOf(userId) : StrUtil.blankToDefault(u.getNickname(), String.valueOf(userId));
        } catch (RuntimeException ex) {
            return String.valueOf(userId);
        }
    }

    static Map<String, Object> scoreAudit(RehabMotionScoreDO s) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("scoreId", s.getId());
        m.put("test", s.getTestCode());
        m.put("side", s.getSide());
        m.put("systemScore", s.getSystemScore());
        m.put("finalScore", s.getFinalScore());
        m.put("finalStatus", s.getFinalStatus());
        return m;
    }

    // ========== 报告 ==========

    public List<Map<String, Object>> reports(Long assessmentId, Long userId) {
        access.readable(assessmentId, userId);
        return reportViews(reportMapper.selectListByAssessmentId(assessmentId));
    }

    public Map<String, Object> reportContent(Long reportId, Long userId) {
        RehabMotionReportDO r = requireReport(reportId, userId);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("report", reportViews(Collections.singletonList(r)).get(0));
        out.put("content", parse(r.getContentJson()));
        Map<String, Object> audit = new LinkedHashMap<String, Object>();
        audit.put("reportId", r.getId());
        audit.put("type", r.getReportType());
        audit.put("version", r.getVersionNo());
        access.audit(r.getAssessmentId(), "report_view", userId, null, audit, null);
        return out;
    }

    public RehabMotionAssessmentService.Download reportPdf(Long reportId, Long userId) {
        RehabMotionReportDO r = requireReport(reportId, userId);
        if (!"ready".equals(r.getPdfStatus()) || r.getPdfFileId() == null) {
            throw exception(MOTION_PDF_NOT_READY);
        }
        RehabMotionFileDO f = motionFileMapper.selectById(r.getPdfFileId());
        if (f == null || !Objects.equals(f.getAssessmentId(), r.getAssessmentId())) {
            throw exception(MOTION_FILE_NOT_EXISTS);
        }
        java.io.File file;
        try {
            file = storage.file(f.getStoragePath());
        } catch (IOException ex) {
            throw exception(MOTION_FILE_NOT_EXISTS);
        }
        if (!file.isFile()) {
            throw exception(MOTION_FILE_NOT_EXISTS);
        }
        Map<String, Object> audit = new LinkedHashMap<String, Object>();
        audit.put("reportId", r.getId());
        audit.put("type", r.getReportType());
        audit.put("version", r.getVersionNo());
        audit.put("sha256", f.getSha256());
        access.audit(r.getAssessmentId(), "report_pdf_export", userId, null, audit, null);
        String name = "motion-" + r.getAssessmentId() + "-" + r.getReportType() + "-v" + r.getVersionNo() + ".pdf";
        return new RehabMotionAssessmentService.Download(name, "application/pdf", file);
    }

    private RehabMotionReportDO requireReport(Long reportId, Long userId) {
        RehabMotionReportDO r = reportId == null ? null : reportMapper.selectById(reportId);
        if (r == null) {
            throw exception(MOTION_REPORT_NOT_EXISTS);
        }
        access.readable(r.getAssessmentId(), userId);
        return r;
    }

    // ========== 对比 / 趋势 ==========

    public Map<String, Object> compare(Long baseId, Long currentId, Long userId) {
        RehabMotionAssessmentDO base = access.readable(baseId, userId);
        RehabMotionAssessmentDO cur = access.readable(currentId, userId);
        return compareInternal(base, cur);
    }

    Map<String, Object> compareInternal(RehabMotionAssessmentDO base, RehabMotionAssessmentDO cur) {
        if (base == null || cur == null || !Objects.equals(base.getPatientId(), cur.getPatientId())
                || Objects.equals(base.getId(), cur.getId())) {
            throw exception(MOTION_COMPARE_INVALID);
        }
        Map<String, Object> out = MotionComparator.compare(
                scoreMapper.selectListByAssessmentId(base.getId()), scoreMapper.selectListByAssessmentId(cur.getId()),
                trialMapper.selectListByAssessmentId(base.getId()), metricMapper.selectListByAssessmentId(base.getId()),
                trialMapper.selectListByAssessmentId(cur.getId()), metricMapper.selectListByAssessmentId(cur.getId()));
        out.put("baseId", base.getId());
        out.put("currentId", cur.getId());
        out.put("baseCaptureTime", base.getCaptureTime());
        out.put("currentCaptureTime", cur.getCaptureTime());
        out.put("baseRuleVersion", base.getRuleVersion());
        out.put("currentRuleVersion", cur.getRuleVersion());
        if (!Objects.equals(base.getRuleVersion(), cur.getRuleVersion())) {
            out.put("versionWarning", "两次评估的规则版本不同，系统分对比需谨慎解读");
        }
        return out;
    }

    /** 患者趋势：每次评估的项目最终/系统分（YBT 逐侧）与 FMS 总分（未审核的分数明确标注）。 */
    public Map<String, Object> trend(Long patientId, Long userId) {
        access.requirePatient(patientId, userId);
        List<RehabMotionAssessmentDO> list = assessmentMapper.selectListByPatientId(patientId);
        if (list.size() > MAX_TREND_POINTS) {
            list = list.subList(list.size() - MAX_TREND_POINTS, list.size());
        }
        List<Map<String, Object>> points = new ArrayList<Map<String, Object>>();
        List<Long> ids = new ArrayList<Long>();
        for (RehabMotionAssessmentDO a : list) {
            ids.add(a.getId());
        }
        Map<Long, List<RehabMotionScoreDO>> byAssessment = new LinkedHashMap<Long, List<RehabMotionScoreDO>>();
        if (!ids.isEmpty()) {
            for (RehabMotionScoreDO s : scoreMapper.selectListByAssessmentIds(ids)) {
                List<RehabMotionScoreDO> l = byAssessment.get(s.getAssessmentId());
                if (l == null) {
                    l = new ArrayList<RehabMotionScoreDO>();
                    byAssessment.put(s.getAssessmentId(), l);
                }
                l.add(s);
            }
        }
        for (RehabMotionAssessmentDO a : list) {
            List<RehabMotionScoreDO> scores = byAssessment.containsKey(a.getId()) ? byAssessment.get(a.getId())
                    : Collections.<RehabMotionScoreDO>emptyList();
            Map<String, Object> p = new LinkedHashMap<String, Object>();
            p.put("assessmentId", a.getId());
            p.put("captureTime", a.getCaptureTime());
            p.put("visitType", a.getVisitType());
            p.put("status", a.getStatus());
            p.put("signed", STATE_COMPLETED.equals(a.getStatus()));
            p.put("ruleVersion", a.getRuleVersion());
            p.put("fmsTotal", MotionScoreRules.fmsTotal(scores));
            List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
            for (RehabMotionScoreDO s : scores) {
                // FMS/TJA/LESS 趋势取项目总分；YBT 复合分按左右侧分别存储，逐侧展示（前端以 testCode|side 为键）
                boolean ybtSide = FAMILY_YBT.equals(s.getFamily());
                if (!"overall".equals(s.getSide()) && !ybtSide) {
                    continue;
                }
                Map<String, Object> it = new LinkedHashMap<String, Object>();
                it.put("family", s.getFamily());
                it.put("testCode", s.getTestCode());
                it.put("side", s.getSide());
                boolean reviewed = FINAL_REVIEWED.contains(s.getFinalStatus());
                it.put("value", reviewed ? s.getFinalScore() : s.getSystemScore());
                it.put("source", reviewed ? "final" : "system_unreviewed");
                items.add(it);
            }
            p.put("scores", items);
            points.add(p);
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("patientId", patientId);
        out.put("points", points);
        out.put("note", "未审核的系统分仅供参考；差值需结合测量误差（旋转约 4.5°、平移约 12.3 mm）与临床判断");
        return out;
    }

    // ========== 留痕 / 协议 ==========

    public List<RehabMotionManualEditDO> manualEdits(Long assessmentId, Long userId) {
        access.readable(assessmentId, userId);
        return manualEditMapper.selectListByAssessmentId(assessmentId);
    }

    public List<?> auditLogs(Long assessmentId, Long userId) {
        access.readable(assessmentId, userId);
        return access.auditLogs(assessmentId, userId);
    }

    /** 协议版本：优先实时读取引擎；引擎不可用时返回已登记的版本。 */
    public Map<String, Object> protocols() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (engineClient.isConfigured()) {
            try {
                out.put("source", "engine");
                out.put("protocols", engineClient.protocols());
                return out;
            } catch (MotionEngineException ex) {
                out.put("engineError", ex.getCode());
            }
        }
        List<RehabMotionProtocolVersionDO> registered = protocolVersionMapper.selectList();
        out.put("source", "registered");
        out.put("protocols", registered);
        return out;
    }

    static Object parse(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(json, new TypeReference<Object>() {
            });
        } catch (RuntimeException ex) {
            return null;
        }
    }

}
