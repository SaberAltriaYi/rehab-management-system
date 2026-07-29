package cn.iocoder.yudao.module.rehab.service.app;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.app.vo.*;
import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.RehabAlertEventPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.RehabAlertEventRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.ai.vo.RehabAiOutputRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo.RehabAssessmentPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo.RehabAssessmentRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo.RehabDailyCheckinPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo.RehabDailyCheckinRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.notification.vo.RehabNotificationPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.notification.vo.RehabNotificationRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.plan.vo.RehabCarePlanPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.plan.vo.RehabCarePlanRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.progress.vo.RehabProgressRecordPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.progress.vo.RehabProgressRecordRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.RehabReportPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.RehabReportRespVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.alert.RehabAlertEventDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.checkin.RehabDailyCheckinDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.followup.RehabFollowupNoteDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.trigger.RehabReassessmentTriggerDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.alert.RehabAlertEventMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabDailyCheckinMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.followup.RehabFollowupNoteMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.trigger.RehabReassessmentTriggerMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabAlertConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabAppConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabPlanConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabRoleCodeConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.alert.RehabAlertService;
import cn.iocoder.yudao.module.rehab.service.ai.RehabAiService;
import cn.iocoder.yudao.module.rehab.service.assessment.RehabAssessmentService;
import cn.iocoder.yudao.module.rehab.service.checkin.RehabDailyCheckinService;
import cn.iocoder.yudao.module.rehab.service.notification.RehabNotificationService;
import cn.iocoder.yudao.module.rehab.service.patient.RehabPatientService;
import cn.iocoder.yudao.module.rehab.service.plan.RehabCarePlanService;
import cn.iocoder.yudao.module.rehab.service.progress.RehabProgressRecordService;
import cn.iocoder.yudao.module.rehab.service.report.RehabReportService;
import cn.iocoder.yudao.module.rehab.service.trigger.RehabReassessmentTriggerService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.enums.logger.LoginLogTypeEnum;
import cn.iocoder.yudao.module.system.service.auth.AdminAuthService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.APP_ADMIN_LOGIN_FORBIDDEN;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.PATIENT_NO_PERMISSION;

@Service
@Validated
public class RehabAppAdminServiceImpl implements RehabAppAdminService {

    @Resource
    private AdminAuthService adminAuthService;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private RehabDataPermissionService dataPermissionService;
    @Resource
    private RehabPatientService patientService;
    @Resource
    private RehabAssessmentService assessmentService;
    @Resource
    private RehabReportService reportService;
    @Resource
    private RehabCarePlanService planService;
    @Resource
    private RehabProgressRecordService progressService;
    @Resource
    private RehabReassessmentTriggerService triggerService;
    @Resource
    private RehabDailyCheckinService checkinService;
    @Resource
    private RehabReassessmentTriggerMapper triggerMapper;
    @Resource
    private RehabAlertEventMapper alertEventMapper;
    @Resource
    private RehabDailyCheckinMapper checkinMapper;
    @Resource
    private RehabFollowupNoteMapper followupNoteMapper;
    @Resource
    private RehabNotificationService notificationService;
    @Resource
    private RehabAlertService alertService;
    @Resource
    private RehabAiService aiService;
    @Resource
    private RehabPatientMapper patientMapper;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public AuthLoginRespVO login(AuthLoginReqVO reqVO) {
        AuthLoginRespVO respVO = adminAuthService.login(reqVO);
        if (!isAppAdminRole(respVO.getUserId())) {
            adminAuthService.logout(respVO.getAccessToken(), LoginLogTypeEnum.LOGOUT_SELF.getType());
            throw exception(APP_ADMIN_LOGIN_FORBIDDEN);
        }
        return respVO;
    }

    @Override
    public AppAdminDashboardSummaryRespVO getDashboardSummary(Long loginUserId) {
        validateAppAdminUser(loginUserId);
        Set<Long> visiblePatientIds = dataPermissionService.getVisiblePatientIds(loginUserId);

        AppAdminDashboardSummaryRespVO respVO = new AppAdminDashboardSummaryRespVO();
        respVO.setMyPatientCount(visiblePatientIds == null ? patientMapper.selectCount() : Long.valueOf(visiblePatientIds.size()));

        LambdaQueryWrapperX<RehabReassessmentTriggerDO> pendingQuery = new LambdaQueryWrapperX<RehabReassessmentTriggerDO>()
                .eq(RehabReassessmentTriggerDO::getTriggerStatus, RehabPlanConstants.TRIGGER_STATUS_PENDING);
        if (visiblePatientIds != null) {
            if (visiblePatientIds.isEmpty()) {
                respVO.setPendingReassessmentCount(0L);
                respVO.setHighRiskCount(0L);
                respVO.setTodayNeedFocusCount(0L);
                respVO.setAbnormalCheckinCount(0L);
                return respVO;
            }
            pendingQuery.in(RehabReassessmentTriggerDO::getPatientId, visiblePatientIds);
        }
        List<RehabReassessmentTriggerDO> pendingTriggers = triggerMapper.selectList(pendingQuery);
        respVO.setPendingReassessmentCount((long) pendingTriggers.size());
        respVO.setHighRiskCount(pendingTriggers.stream()
                .filter(item -> ObjUtil.equals(item.getTriggerLevel(), RehabPlanConstants.TRIGGER_LEVEL_HIGH))
                .count());
        long pendingAlertCount = alertEventMapper.selectCount(new LambdaQueryWrapperX<RehabAlertEventDO>()
                .eq(RehabAlertEventDO::getStatus, RehabAlertConstants.STATUS_ACTIVE)
                .inIfPresent(RehabAlertEventDO::getPatientId, visiblePatientIds));
        respVO.setPendingAlertCount(pendingAlertCount);
        respVO.setTodayNeedFocusCount(Math.max((long) pendingTriggers.stream().map(RehabReassessmentTriggerDO::getPatientId).distinct().count(), pendingAlertCount));

        LambdaQueryWrapperX<RehabDailyCheckinDO> todayQuery = new LambdaQueryWrapperX<RehabDailyCheckinDO>()
                .eq(RehabDailyCheckinDO::getCheckinDate, LocalDate.now());
        if (visiblePatientIds != null) {
            todayQuery.in(RehabDailyCheckinDO::getPatientId, visiblePatientIds);
        }
        List<RehabDailyCheckinDO> todayCheckins = checkinMapper.selectList(todayQuery);
        long abnormalCount = todayCheckins.stream().filter(item ->
                (item.getPainScoreAfter() != null && item.getPainScoreAfter().compareTo(new BigDecimal("6")) >= 0)
                        || (item.getOverallCompletionRate() != null && item.getOverallCompletionRate().compareTo(new BigDecimal("60")) < 0)
        ).count();
        respVO.setAbnormalCheckinCount(abnormalCount);
        respVO.setUnreadNotificationCount(notificationService.countUnreadForAdminUser(loginUserId));
        return respVO;
    }

    @Override
    public PageResult<AppAdminPatientMiniRespVO> getMyPatientPage(RehabPatientPageReqVO reqVO, Long loginUserId) {
        validateAppAdminUser(loginUserId);
        PageResult<RehabPatientRespVO> pageResult = patientService.getPatientPage(reqVO, loginUserId);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        List<AppAdminPatientMiniRespVO> list = pageResult.getList().stream().map(item -> {
            AppAdminPatientMiniRespVO vo = new AppAdminPatientMiniRespVO();
            vo.setId(item.getId());
            vo.setPatientNo(item.getPatientNo());
            vo.setName(item.getName());
            vo.setCurrentStage(item.getCurrentStage());
            vo.setUpdateTime(item.getUpdateTime());

            RehabCarePlanPageReqVO planReq = new RehabCarePlanPageReqVO();
            planReq.setPatientId(item.getId());
            planReq.setStatus(RehabPlanConstants.PLAN_STATUS_ACTIVE);
            planReq.setPageNo(1);
            planReq.setPageSize(1);
            PageResult<RehabCarePlanRespVO> planPage = planService.getPlanPage(planReq, loginUserId);
            if (CollUtil.isNotEmpty(planPage.getList())) {
                vo.setActivePlanStatus(planPage.getList().get(0).getStatus());
            }

            RehabDailyCheckinPageReqVO checkinReq = new RehabDailyCheckinPageReqVO();
            checkinReq.setPatientId(item.getId());
            checkinReq.setPageNo(1);
            checkinReq.setPageSize(1);
            PageResult<RehabDailyCheckinRespVO> checkinPage = checkinService.getCheckinPage(checkinReq, loginUserId);
            if (CollUtil.isNotEmpty(checkinPage.getList())) {
                vo.setLatestCheckinDate(checkinPage.getList().get(0).getCheckinDate());
            }

            vo.setHasHighRiskTrigger(triggerMapper.selectCount(new LambdaQueryWrapperX<RehabReassessmentTriggerDO>()
                    .eq(RehabReassessmentTriggerDO::getPatientId, item.getId())
                    .eq(RehabReassessmentTriggerDO::getTriggerStatus, RehabPlanConstants.TRIGGER_STATUS_PENDING)
                    .eq(RehabReassessmentTriggerDO::getTriggerLevel, RehabPlanConstants.TRIGGER_LEVEL_HIGH)) > 0);
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    public AppAdminPatientSummaryRespVO getPatientSummary(Long patientId, Long loginUserId) {
        validateAppAdminUser(loginUserId);
        if (!dataPermissionService.canReadPatient(patientId, loginUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }

        AppAdminPatientSummaryRespVO respVO = new AppAdminPatientSummaryRespVO();
        RehabPatientDO patient = patientMapper.selectById(patientId);
        if (patient != null) {
            RehabPatientRespVO patientRespVO = new RehabPatientRespVO();
            patientRespVO.setId(patient.getId());
            patientRespVO.setPatientNo(patient.getPatientNo());
            patientRespVO.setName(patient.getName());
            patientRespVO.setGender(patient.getGender());
            patientRespVO.setPhone(patient.getPhone());
            patientRespVO.setCurrentStage(patient.getCurrentStage());
            patientRespVO.setCurrentTherapistUserId(patient.getCurrentTherapistUserId());
            patientRespVO.setUpdateTime(patient.getUpdateTime());
            respVO.setPatient(patientRespVO);
        }

        RehabAssessmentPageReqVO assessmentReq = new RehabAssessmentPageReqVO();
        assessmentReq.setPatientId(patientId);
        assessmentReq.setPageNo(1);
        assessmentReq.setPageSize(1);
        PageResult<RehabAssessmentRespVO> assessmentPage = assessmentService.getAssessmentPage(assessmentReq, loginUserId);
        if (CollUtil.isNotEmpty(assessmentPage.getList())) {
            RehabAssessmentRespVO assessment = assessmentPage.getList().get(0);
            respVO.setLatestAssessmentNo(assessment.getAssessmentNo());
            respVO.setLatestAssessmentDate(assessment.getAssessmentDate());
            respVO.setLatestAssessmentSummary(StrUtil.blankToDefault(assessment.getChiefFocus(), "暂无摘要"));
        }

        RehabReportPageReqVO reportReq = new RehabReportPageReqVO();
        reportReq.setPatientId(patientId);
        reportReq.setPageNo(1);
        reportReq.setPageSize(1);
        PageResult<RehabReportRespVO> reportPage = reportService.getReportPage(reportReq, loginUserId);
        if (CollUtil.isNotEmpty(reportPage.getList())) {
            RehabReportRespVO report = reportPage.getList().get(0);
            respVO.setLatestReportNo(report.getReportNo());
            respVO.setLatestReportSummary(StrUtil.blankToDefault(report.getNote(), "暂无摘要"));
        }

        RehabCarePlanPageReqVO planReq = new RehabCarePlanPageReqVO();
        planReq.setPatientId(patientId);
        planReq.setStatus(RehabPlanConstants.PLAN_STATUS_ACTIVE);
        planReq.setPageNo(1);
        planReq.setPageSize(1);
        PageResult<RehabCarePlanRespVO> planPage = planService.getPlanPage(planReq, loginUserId);
        if (CollUtil.isNotEmpty(planPage.getList())) {
            RehabCarePlanRespVO plan = planPage.getList().get(0);
            respVO.setActivePlanNo(plan.getPlanNo());
            respVO.setActivePlanStatus(plan.getStatus());

            RehabProgressRecordPageReqVO progressReq = new RehabProgressRecordPageReqVO();
            progressReq.setPlanId(plan.getId());
            progressReq.setPageNo(1);
            progressReq.setPageSize(1);
            PageResult<RehabProgressRecordRespVO> progressPage = progressService.getProgressPage(progressReq, loginUserId);
            if (CollUtil.isNotEmpty(progressPage.getList())) {
                RehabProgressRecordRespVO progress = progressPage.getList().get(0);
                respVO.setLatestProgressSummary(StrUtil.format("完成率:{}%，疼痛趋势:{}，状态:{}",
                        progress.getCompletionRate(), progress.getPainTrend(), progress.getProgressStatus()));
            }
        }

        respVO.setPendingTriggerCount(triggerMapper.selectPendingCountByPatientId(patientId));
        List<RehabFollowupNoteDO> notes = followupNoteMapper.selectRecentByPatientId(patientId, 5);
        respVO.setRecentFollowupNotes(toFollowupRespList(notes));
        return respVO;
    }

    @Override
    public PageResult<RehabDailyCheckinRespVO> getPatientCheckins(Long patientId, Integer pageNo, Integer pageSize, Long loginUserId) {
        validateAppAdminUser(loginUserId);
        if (!dataPermissionService.canReadPatient(patientId, loginUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
        RehabDailyCheckinPageReqVO reqVO = new RehabDailyCheckinPageReqVO();
        reqVO.setPatientId(patientId);
        reqVO.setPageNo(pageNo);
        reqVO.setPageSize(pageSize);
        return checkinService.getCheckinPage(reqVO, loginUserId);
    }

    @Override
    public PageResult<RehabAlertEventRespVO> getAlertPage(RehabAlertEventPageReqVO reqVO, Long loginUserId) {
        validateAppAdminUser(loginUserId);
        return alertService.getAlertPage(reqVO, loginUserId);
    }

    @Override
    public PageResult<AppAdminNotificationRespVO> getNotificationPage(Integer pageNo, Integer pageSize, Long loginUserId) {
        validateAppAdminUser(loginUserId);
        RehabNotificationPageReqVO reqVO = new RehabNotificationPageReqVO();
        reqVO.setPageNo(pageNo);
        reqVO.setPageSize(pageSize);
        reqVO.setOnlyMine(true);
        PageResult<RehabNotificationRespVO> pageResult = notificationService.getNotificationPage(reqVO, loginUserId);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        List<AppAdminNotificationRespVO> list = pageResult.getList().stream().map(item -> {
            AppAdminNotificationRespVO vo = new AppAdminNotificationRespVO();
            vo.setId(item.getId());
            vo.setNotificationType(item.getNotificationType());
            vo.setTitle(item.getTitle());
            vo.setContent(item.getContent());
            vo.setSeverity(item.getSeverity());
            vo.setReadStatus(item.getReadStatus());
            vo.setActionUrl(item.getActionUrl());
            vo.setActionText(item.getActionText());
            vo.setCreateTime(item.getCreateTime());
            vo.setPatientId(item.getPatientId());
            vo.setPatientName(item.getPatientName());
            vo.setPatientNo(item.getPatientNo());
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    public void readNotification(Long id, Long loginUserId) {
        validateAppAdminUser(loginUserId);
        notificationService.readNotification(id, loginUserId);
    }

    @Override
    public Long createFollowupNote(AppAdminFollowupNoteCreateReqVO reqVO, Long loginUserId) {
        validateAppAdminUser(loginUserId);
        if (!dataPermissionService.canReadPatient(reqVO.getPatientId(), loginUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
        RehabFollowupNoteDO note = RehabFollowupNoteDO.builder()
                .patientId(reqVO.getPatientId())
                .episodeId(reqVO.getEpisodeId())
                .therapistUserId(loginUserId)
                .noteType(reqVO.getNoteType())
                .visibilityType(reqVO.getVisibilityType())
                .content(reqVO.getContent())
                .build();
        followupNoteMapper.insert(note);

        if (ObjUtil.equals(reqVO.getVisibilityType(), RehabAppConstants.FOLLOWUP_VISIBILITY_PATIENT)) {
            notificationService.createSystemNotification("patient", null,
                    reqVO.getPatientId(), reqVO.getEpisodeId(), "system", note.getId(),
                    "system_notice", "info", "治疗师随访提醒", StrUtil.maxLength(reqVO.getContent(), 200),
                    "multi", "/pages/notification/index", "查看");
        }
        return note.getId();
    }

    @Override
    public PageResult<AppAdminFollowupNoteRespVO> getFollowupNotePage(AppAdminFollowupNotePageReqVO reqVO, Long loginUserId) {
        validateAppAdminUser(loginUserId);
        if (!dataPermissionService.canReadPatient(reqVO.getPatientId(), loginUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
        Set<Long> visiblePatientIds = dataPermissionService.getVisiblePatientIds(loginUserId);
        PageResult<RehabFollowupNoteDO> pageResult = followupNoteMapper.selectPage(
                reqVO.getPageNo(), reqVO.getPageSize(), reqVO.getPatientId(), visiblePatientIds);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        return new PageResult<>(toFollowupRespList(pageResult.getList()), pageResult.getTotal());
    }

    @Override
    public AppAdminAiOutputRespVO getLatestAiSummary(Long patientId, Long loginUserId) {
        validateAppAdminUser(loginUserId);
        if (!dataPermissionService.canReadPatient(patientId, loginUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
        RehabAiOutputRespVO output = aiService.getLatestSummaryForAdminPatient(patientId, loginUserId);
        if (output == null) {
            return null;
        }
        AppAdminAiOutputRespVO vo = new AppAdminAiOutputRespVO();
        vo.setId(output.getId());
        vo.setOutputType(output.getOutputType());
        vo.setReviewStatus(output.getReviewStatus());
        vo.setSafetyStatus(output.getSafetyStatus());
        vo.setRenderedText(output.getRenderedText());
        vo.setEvidenceRefsJson(output.getEvidenceRefsJson());
        vo.setCreateTime(output.getCreateTime());
        return vo;
    }

    @Override
    public AppAdminAiOutputRespVO getLatestAiFollowup(Long patientId, Long loginUserId) {
        validateAppAdminUser(loginUserId);
        if (!dataPermissionService.canReadPatient(patientId, loginUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
        RehabAiOutputRespVO output = aiService.getLatestFollowupForAdminPatient(patientId, loginUserId);
        if (output == null) {
            return null;
        }
        AppAdminAiOutputRespVO vo = new AppAdminAiOutputRespVO();
        vo.setId(output.getId());
        vo.setOutputType(output.getOutputType());
        vo.setReviewStatus(output.getReviewStatus());
        vo.setSafetyStatus(output.getSafetyStatus());
        vo.setRenderedText(output.getRenderedText());
        vo.setEvidenceRefsJson(output.getEvidenceRefsJson());
        vo.setCreateTime(output.getCreateTime());
        return vo;
    }

    private List<AppAdminFollowupNoteRespVO> toFollowupRespList(List<RehabFollowupNoteDO> notes) {
        Set<Long> userIds = notes.stream().map(RehabFollowupNoteDO::getTherapistUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> userMap = userIds.isEmpty() ? Collections.emptyMap() : adminUserApi.getUserMap(userIds);
        return notes.stream().map(item -> {
            AppAdminFollowupNoteRespVO vo = new AppAdminFollowupNoteRespVO();
            vo.setId(item.getId());
            vo.setPatientId(item.getPatientId());
            vo.setEpisodeId(item.getEpisodeId());
            vo.setTherapistUserId(item.getTherapistUserId());
            vo.setNoteType(item.getNoteType());
            vo.setVisibilityType(item.getVisibilityType());
            vo.setContent(item.getContent());
            vo.setCreateTime(item.getCreateTime());
            AdminUserRespDTO user = userMap.get(item.getTherapistUserId());
            vo.setTherapistName(user == null ? "" : user.getNickname());
            return vo;
        }).collect(Collectors.toList());
    }

    private void validateAppAdminUser(Long userId) {
        if (!isAppAdminRole(userId)) {
            throw exception(APP_ADMIN_LOGIN_FORBIDDEN);
        }
    }

    private boolean isAppAdminRole(Long userId) {
        return permissionApi.hasAnyRoles(userId, RehabRoleCodeConstants.SUPER_ADMIN,
                RehabRoleCodeConstants.REHAB_THERAPIST, RehabRoleCodeConstants.REHAB_CLERK);
    }
}
