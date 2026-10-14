package cn.iocoder.yudao.module.rehab.service.motion;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.module.rehab.controller.admin.motion.vo.RehabMotionAiReviewReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.motion.vo.RehabMotionAmendReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.motion.vo.RehabMotionScoreReviewReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.motion.vo.RehabMotionSignReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAiDraftDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionScoreDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTaskDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionAiDraftMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionAssessmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionMetricMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionReportMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionRuleResultMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionScoreMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionTrialMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;
import cn.iocoder.yudao.module.rehab.service.motion.report.RehabMotionReportService;
import cn.iocoder.yudao.module.rehab.service.motion.task.RehabMotionPipelineSteps;
import cn.iocoder.yudao.module.rehab.service.motion.task.RehabMotionTaskService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.PATIENT_NO_PERMISSION;
import static cn.iocoder.yudao.module.rehab.enums.RehabMotionErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RehabMotionReviewServiceTest {

    private static final Long UID = 7L;

    private RehabMotionReviewService service;
    @Mock
    private RehabMotionAssessmentMapper assessmentMapper;
    @Mock
    private RehabMotionScoreMapper scoreMapper;
    @Mock
    private RehabMotionAiDraftMapper aiDraftMapper;
    @Mock
    private RehabMotionReportMapper reportMapper;
    @Mock
    private RehabMotionTrialMapper trialMapper;
    @Mock
    private RehabMotionMetricMapper metricMapper;
    @Mock
    private RehabMotionRuleResultMapper ruleResultMapper;
    @Mock
    private RehabMotionAccess access;
    @Mock
    private RehabMotionTaskService taskService;
    @Mock
    private RehabMotionPipelineSteps steps;
    @Mock
    private RehabMotionReportService reportService;
    @Mock
    private AdminUserApi adminUserApi;

    private RehabMotionAssessmentDO assessment;

    @BeforeAll
    static void initLambdaCache() {
        // LambdaUpdateWrapper 需要实体元数据（无数据库的单元测试中手动初始化）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : new Class<?>[]{RehabMotionScoreDO.class, RehabMotionAssessmentDO.class,
                RehabMotionAiDraftDO.class}) {
            if (TableInfoHelper.getTableInfo(c) == null) {
                TableInfoHelper.initTableInfo(assistant, c);
            }
        }
    }

    @BeforeEach
    void setUp() {
        service = new RehabMotionReviewService();
        ReflectionTestUtils.setField(service, "assessmentMapper", assessmentMapper);
        ReflectionTestUtils.setField(service, "scoreMapper", scoreMapper);
        ReflectionTestUtils.setField(service, "aiDraftMapper", aiDraftMapper);
        ReflectionTestUtils.setField(service, "reportMapper", reportMapper);
        ReflectionTestUtils.setField(service, "trialMapper", trialMapper);
        ReflectionTestUtils.setField(service, "metricMapper", metricMapper);
        ReflectionTestUtils.setField(service, "ruleResultMapper", ruleResultMapper);
        ReflectionTestUtils.setField(service, "access", access);
        ReflectionTestUtils.setField(service, "taskService", taskService);
        ReflectionTestUtils.setField(service, "steps", steps);
        ReflectionTestUtils.setField(service, "reportService", reportService);
        ReflectionTestUtils.setField(service, "adminUserApi", adminUserApi);
        assessment = RehabMotionAssessmentDO.builder().id(100L).patientId(9L)
                .status(RehabMotionConstants.STATE_PENDING_REVIEW).inputRevision(3).analyzedRevision(3).build();
        when(access.editable(eq(100L), eq(UID))).thenReturn(assessment);
        when(access.readable(eq(100L), eq(UID))).thenReturn(assessment);
        when(assessmentMapper.selectById(100L)).thenReturn(assessment);
        when(adminUserApi.getUserMap(any())).thenReturn(Collections.emptyMap());
        RehabMotionTaskDO pdf = RehabMotionTaskDO.builder().id(55L).build();
        when(taskService.enqueue(any(), eq(RehabMotionConstants.TASK_PDF), any(), any(), any(), any())).thenReturn(pdf);
    }

    private static RehabMotionScoreDO score(Long id, String status, Integer system, String finalStatus) {
        return RehabMotionScoreDO.builder().id(id).assessmentId(100L).family("FMS").testCode("FMS_HURDLE_STEP")
                .side("overall").scoringScheme("FMS_0_3").systemStatus(status)
                .systemScore(system == null ? null : BigDecimal.valueOf(system)).finalStatus(finalStatus)
                .analysisRevision(3).build();
    }

    private static RehabMotionScoreReviewReqVO review(Long id, String status, Integer value, String reason) {
        RehabMotionScoreReviewReqVO r = new RehabMotionScoreReviewReqVO();
        r.setScoreId(id);
        r.setFinalStatus(status);
        r.setFinalScore(value == null ? null : BigDecimal.valueOf(value));
        r.setReason(reason);
        return r;
    }

    private static int code(Runnable r) {
        ServiceException ex = assertThrows(ServiceException.class, r::run);
        return ex.getCode();
    }

    // ---------------------------------------------------------------- 评分审核

    @Test
    void painZeroCannotBeOverriddenToNonZero() {
        when(scoreMapper.selectById(1L)).thenReturn(score(1L, "pain_zero", 0, "pending"));
        assertEquals(MOTION_STATE_INVALID.getCode(),
                code(() -> service.reviewScore(review(1L, "modified", 2, "看起来没痛"), UID)));
        verify(scoreMapper, never()).update(any(), any());
    }

    @Test
    void missingPainBlocksReview() {
        when(scoreMapper.selectById(1L)).thenReturn(score(1L, "pending_pain", null, "pending"));
        assertEquals(MOTION_STATE_INVALID.getCode(),
                code(() -> service.reviewScore(review(1L, "modified", 3, "原因"), UID)));
    }

    @Test
    void modifyRequiresReasonAndConfirmNeedsSystemScore() {
        when(scoreMapper.selectById(1L)).thenReturn(score(1L, "suggested", 2, "pending"));
        assertEquals(MOTION_REASON_REQUIRED.getCode(), code(() -> service.reviewScore(review(1L, "modified", 1, " "), UID)));
        when(scoreMapper.selectById(2L)).thenReturn(score(2L, "pending_manual", null, "pending"));
        assertEquals(MOTION_STATE_INVALID.getCode(), code(() -> service.reviewScore(review(2L, "confirmed", null, null), UID)));
        when(scoreMapper.selectById(3L)).thenReturn(score(3L, "suggested", 2, "pending"));
        assertEquals(MOTION_SCORE_OUT_OF_RANGE.getCode(),
                code(() -> service.reviewScore(review(3L, "modified", 5, "现场观察"), UID)));
    }

    @Test
    void confirmWritesSystemScoreAsFinalAndLeavesTrail() {
        RehabMotionScoreDO s = score(1L, "suggested", 2, "pending");
        when(scoreMapper.selectById(1L)).thenReturn(s);
        service.reviewScore(review(1L, "confirmed", null, null), UID);
        verify(scoreMapper).update(isNull(), any());
        verify(access).manualEdit(eq(100L), eq("score"), eq(1L), anyString(), eq("final_score"), isNull(),
                eq(BigDecimal.valueOf(2)), isNull(), eq(UID));
        verify(access).audit(eq(100L), eq("score_review"), eq(UID), any(), any(), isNull());
        verify(scoreMapper, times(1)).update(isNull(), any());
    }

    @Test
    void staleAnalysisOrWrongStateRejectsReview() {
        when(scoreMapper.selectById(1L)).thenReturn(score(1L, "suggested", 2, "pending"));
        assessment.setInputRevision(4);
        assertEquals(MOTION_STATE_INVALID.getCode(), code(() -> service.reviewScore(review(1L, "confirmed", null, null), UID)));
        assessment.setInputRevision(3);
        assessment.setStatus(RehabMotionConstants.STATE_RULES);
        assertEquals(MOTION_STATE_INVALID.getCode(), code(() -> service.reviewScore(review(1L, "confirmed", null, null), UID)));
    }

    @Test
    void clerkAndOtherTenantUsersCannotReview() {
        when(scoreMapper.selectById(1L)).thenReturn(score(1L, "suggested", 2, "pending"));
        doThrow(ServiceExceptionUtil.exception(PATIENT_NO_PERMISSION)).when(access).editable(eq(100L), eq(8L));
        assertEquals(PATIENT_NO_PERMISSION.getCode(), code(() -> service.reviewScore(review(1L, "confirmed", null, null), 8L)));
        doThrow(ServiceExceptionUtil.exception(PATIENT_NO_PERMISSION)).when(access).requireClinician(UID);
        assertThrows(ServiceException.class, () -> service.reviewScore(review(1L, "confirmed", null, null), UID));
        verify(scoreMapper, never()).update(any(), any());
    }

    // ---------------------------------------------------------------- AI 草稿

    @Test
    void aiDraftReviewNeverTouchesScores() {
        RehabMotionAiDraftDO d = RehabMotionAiDraftDO.builder().id(11L).assessmentId(100L)
                .status(RehabMotionConstants.AI_GENERATED).analysisRevision(3).renderedText("原文").build();
        when(aiDraftMapper.selectById(11L)).thenReturn(d);
        RehabMotionAiReviewReqVO req = new RehabMotionAiReviewReqVO();
        req.setDraftId(11L);
        req.setAction("accept");
        req.setEditedText("治疗师修改后的解释");
        service.reviewAiDraft(req, UID);
        verify(aiDraftMapper, times(2)).update(isNull(), any());
        verifyNoInteractions(scoreMapper);
        verify(access).manualEdit(eq(100L), eq("ai_draft"), eq(11L), isNull(), eq("edited_text"), any(), any(), any(), eq(UID));
    }

    @Test
    void staleAiDraftCannotBeAccepted() {
        RehabMotionAiDraftDO d = RehabMotionAiDraftDO.builder().id(11L).assessmentId(100L)
                .status(RehabMotionConstants.AI_GENERATED).analysisRevision(2).build();
        when(aiDraftMapper.selectById(11L)).thenReturn(d);
        RehabMotionAiReviewReqVO req = new RehabMotionAiReviewReqVO();
        req.setDraftId(11L);
        req.setAction("accept");
        assertEquals(MOTION_STATE_INVALID.getCode(), code(() -> service.reviewAiDraft(req, UID)));
    }

    // ---------------------------------------------------------------- 签署

    private void allReviewed() {
        List<RehabMotionScoreDO> scores = new ArrayList<RehabMotionScoreDO>();
        scores.add(score(1L, "suggested", 2, RehabMotionConstants.FINAL_CONFIRMED));
        when(scoreMapper.selectListByAssessmentId(100L)).thenReturn(scores);
        when(aiDraftMapper.selectListByAssessmentId(100L)).thenReturn(new ArrayList<RehabMotionAiDraftDO>());
        when(taskService.hasActive(100L)).thenReturn(false);
    }

    private static RehabMotionSignReqVO sign() {
        RehabMotionSignReqVO req = new RehabMotionSignReqVO();
        req.setAssessmentId(100L);
        req.setConfirmed(true);
        return req;
    }

    @Test
    void signBlockersCoverStaleActiveUnreviewedAndUnhandledDraft() {
        allReviewed();
        assertTrue(service.signBlockers(assessment, scoreMapper.selectListByAssessmentId(100L),
                new ArrayList<RehabMotionAiDraftDO>()).isEmpty());
        assessment.setInputRevision(4);
        when(taskService.hasActive(100L)).thenReturn(true);
        List<RehabMotionScoreDO> pending = Collections.singletonList(score(1L, "suggested", 2, "needs_recheck"));
        List<RehabMotionAiDraftDO> drafts = Collections.singletonList(RehabMotionAiDraftDO.builder()
                .status(RehabMotionConstants.AI_FALLBACK).analysisRevision(3).build());
        List<String> blockers = service.signBlockers(assessment, pending, drafts);
        assertEquals(4, blockers.size(), blockers.toString());
    }

    @Test
    void signCreatesReportsLocksAndQueuesPdf() {
        allReviewed();
        service.sign(sign(), UID);
        verify(reportService).createSignedReports(eq(assessment), isNull(), isNull(), eq(UID), eq("7"), any());
        verify(assessmentMapper).update(isNull(), any());
        verify(taskService).enqueue(eq(assessment), eq(RehabMotionConstants.TASK_PDF),
                eq(RehabMotionConstants.STATE_PDF_RENDERING), isNull(), eq(UID), isNull());
        verify(access).audit(eq(100L), eq("sign"), eq(UID), isNull(), any(), isNull());
    }

    @Test
    void signRefusedWhenPreconditionsFail() {
        allReviewed();
        when(scoreMapper.selectListByAssessmentId(100L))
                .thenReturn(Collections.singletonList(score(1L, "suggested", 2, "pending")));
        assertEquals(MOTION_SIGN_PRECONDITION.getCode(), code(() -> service.sign(sign(), UID)));
        verifyNoInteractions(reportService);
    }

    @Test
    void amendRequiresSignedStateAndReasonAndSupersedes() {
        RehabMotionAmendReqVO req = new RehabMotionAmendReqVO();
        req.setAssessmentId(100L);
        req.setReason("补录清除测试");
        assertEquals(MOTION_STATE_INVALID.getCode(), code(() -> service.amend(req, UID)));
        assessment.setStatus(RehabMotionConstants.STATE_COMPLETED);
        service.amend(req, UID);
        verify(reportService).supersede(100L);
        verify(access).manualEdit(eq(100L), eq("assessment"), eq(100L), isNull(), eq("status"),
                eq(RehabMotionConstants.STATE_COMPLETED), eq(RehabMotionConstants.STATE_PENDING_REVIEW),
                eq("补录清除测试"), eq(UID));
    }

    // ---------------------------------------------------------------- 对比

    @Test
    void compareRejectsDifferentPatients() {
        RehabMotionAssessmentDO other = RehabMotionAssessmentDO.builder().id(200L).patientId(10L).build();
        when(access.readable(eq(200L), eq(UID))).thenReturn(other);
        assertEquals(MOTION_COMPARE_INVALID.getCode(), code(() -> service.compare(100L, 200L, UID)));
        assertEquals(MOTION_COMPARE_INVALID.getCode(), code(() -> service.compare(100L, 100L, UID)));
    }

    @Test
    void compareUsesFinalWhenReviewedOtherwiseMarksUnreviewed() {
        RehabMotionAssessmentDO base = RehabMotionAssessmentDO.builder().id(200L).patientId(9L).ruleVersion("motion-rules/1.0.0").build();
        assessment.setRuleVersion("motion-rules/1.0.0");
        when(access.readable(eq(200L), eq(UID))).thenReturn(base);
        RehabMotionScoreDO b = score(1L, "suggested", 1, RehabMotionConstants.FINAL_MODIFIED);
        b.setFinalScore(BigDecimal.valueOf(2));
        b.setAssessmentId(200L);
        when(scoreMapper.selectListByAssessmentId(200L)).thenReturn(Collections.singletonList(b));
        when(scoreMapper.selectListByAssessmentId(100L))
                .thenReturn(Collections.singletonList(score(2L, "suggested", 3, "pending")));
        java.util.Map<String, Object> out = service.compare(200L, 100L, UID);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> row = ((List<java.util.Map<String, Object>>) out.get("scores")).get(0);
        assertEquals(BigDecimal.valueOf(2), row.get("baseline"));
        assertEquals("final", row.get("baseline_source"));
        assertEquals("system_unreviewed", row.get("current_source"));
        assertFalse(out.containsKey("versionWarning"));
        assertFalse(out.toString().contains("改善") || out.toString().contains("恶化"));
    }

    @Test
    void trendKeepsYbtSidesApartAndSkipsPerSideFmsRows() {
        when(assessmentMapper.selectListByPatientId(9L)).thenReturn(Collections.singletonList(assessment));
        RehabMotionScoreDO fms = score(1L, "suggested", 2, RehabMotionConstants.FINAL_CONFIRMED);
        fms.setFinalScore(BigDecimal.valueOf(2));
        RehabMotionScoreDO fmsLeft = score(2L, "suggested", 2, "pending");
        fmsLeft.setSide("left");
        RehabMotionScoreDO ybtLeft = RehabMotionScoreDO.builder().id(3L).assessmentId(100L)
                .family(RehabMotionConstants.FAMILY_YBT).testCode("YBT_LQ").side("left")
                .systemScore(BigDecimal.valueOf(95)).finalStatus("pending").build();
        RehabMotionScoreDO ybtRight = RehabMotionScoreDO.builder().id(4L).assessmentId(100L)
                .family(RehabMotionConstants.FAMILY_YBT).testCode("YBT_LQ").side("right")
                .systemScore(BigDecimal.valueOf(88)).finalStatus("pending").build();
        when(scoreMapper.selectListByAssessmentIds(anyList()))
                .thenReturn(java.util.Arrays.asList(fms, fmsLeft, ybtLeft, ybtRight));
        java.util.Map<String, Object> out = service.trend(9L, UID);
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> points = (List<java.util.Map<String, Object>>) out.get("points");
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> items = (List<java.util.Map<String, Object>>) points.get(0).get("scores");
        assertEquals(3, items.size());
        assertEquals("overall", items.get(0).get("side"));
        assertEquals("final", items.get(0).get("source"));
        assertEquals("left", items.get(1).get("side"));
        assertEquals(BigDecimal.valueOf(95), items.get(1).get("value"));
        assertEquals("right", items.get(2).get("side"));
        assertEquals("system_unreviewed", items.get(2).get("source"));
    }

}
