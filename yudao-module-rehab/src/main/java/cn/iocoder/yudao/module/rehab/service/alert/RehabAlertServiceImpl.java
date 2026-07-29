package cn.iocoder.yudao.module.rehab.service.alert;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.RehabAlertEventPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.RehabAlertEventRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.RehabAlertHandleReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.RehabAlertRefreshReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.alert.RehabAlertEventDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.alert.RehabAlertRuleDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.checkin.RehabDailyCheckinDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.progress.RehabProgressRecordDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.report.RehabReportDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.trigger.RehabReassessmentTriggerDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.alert.RehabAlertEventMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.alert.RehabAlertRuleMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabDailyCheckinMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.progress.RehabProgressRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.report.RehabReportMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.trigger.RehabReassessmentTriggerMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabAlertConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabNotificationConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabOperationTypeConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabPlanConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabReportConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.log.RehabAuditLogService;
import cn.iocoder.yudao.module.rehab.service.notification.RehabNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

@Service
@Validated
public class RehabAlertServiceImpl implements RehabAlertService {

    @Resource
    private RehabAlertEventMapper alertEventMapper;
    @Resource
    private RehabAlertRuleMapper alertRuleMapper;
    @Resource
    private RehabCarePlanMapper planMapper;
    @Resource
    private RehabProgressRecordMapper progressRecordMapper;
    @Resource
    private RehabDailyCheckinMapper checkinMapper;
    @Resource
    private RehabReassessmentTriggerMapper triggerMapper;
    @Resource
    private RehabReportMapper reportMapper;
    @Resource
    private RehabPatientMapper patientMapper;
    @Resource
    private RehabDataPermissionService dataPermissionService;
    @Resource
    private RehabNotificationService notificationService;
    @Resource
    private RehabAuditLogService auditLogService;

    @Override
    public PageResult<RehabAlertEventRespVO> getAlertPage(RehabAlertEventPageReqVO reqVO, Long operatorUserId) {
        Set<Long> visiblePatientIds = dataPermissionService.getVisiblePatientIds(operatorUserId);
        if (visiblePatientIds != null && visiblePatientIds.isEmpty()) {
            return PageResult.empty();
        }
        if (reqVO.getPatientId() != null) {
            validatePatientReadable(reqVO.getPatientId(), operatorUserId);
            visiblePatientIds = Collections.singleton(reqVO.getPatientId());
        }
        PageResult<RehabAlertEventDO> pageResult = alertEventMapper.selectPage(reqVO, visiblePatientIds);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        return new PageResult<>(toRespList(pageResult.getList()), pageResult.getTotal());
    }

    @Override
    public RehabAlertEventRespVO getAlert(Long id, Long operatorUserId) {
        RehabAlertEventDO event = validateReadable(id, operatorUserId);
        return toRespList(Collections.singletonList(event)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> refreshAlerts(RehabAlertRefreshReqVO reqVO, Long operatorUserId) {
        List<RehabCarePlanDO> plans = resolveTargetPlans(reqVO, operatorUserId);
        if (CollUtil.isEmpty(plans)) {
            return Collections.emptyList();
        }
        List<Long> touchedIds = new ArrayList<>();
        for (RehabCarePlanDO plan : plans) {
            touchedIds.addAll(refreshPlanAlerts(plan));
        }
        auditLogService.createAuditLog("alert", 0L, RehabOperationTypeConstants.ALERT_REFRESH,
                operatorUserId, resolveRole(operatorUserId), reqVO, touchedIds, "success",
                "刷新提醒事件");
        return touchedIds;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acknowledgeAlert(RehabAlertHandleReqVO reqVO, Long operatorUserId) {
        RehabAlertEventDO event = validateReadable(reqVO.getId(), operatorUserId);
        if (!ObjUtil.equals(event.getStatus(), RehabAlertConstants.STATUS_ACTIVE)) {
            throw exception(ALERT_EVENT_CAN_NOT_HANDLE);
        }
        RehabAlertEventDO after = new RehabAlertEventDO().setId(event.getId())
                .setStatus(RehabAlertConstants.STATUS_ACKNOWLEDGED)
                .setAcknowledgedBy(operatorUserId)
                .setAcknowledgedTime(LocalDateTime.now());
        alertEventMapper.updateById(after);
        auditLogService.createAuditLog("alert", event.getId(), RehabOperationTypeConstants.ALERT_ACKNOWLEDGE,
                operatorUserId, resolveRole(operatorUserId), event, alertEventMapper.selectById(event.getId()),
                "success", StrUtil.blankToDefault(reqVO.getRemark(), "提醒已确认"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveAlert(RehabAlertHandleReqVO reqVO, Long operatorUserId) {
        RehabAlertEventDO event = validateReadable(reqVO.getId(), operatorUserId);
        if (!ObjUtil.equals(event.getStatus(), RehabAlertConstants.STATUS_ACTIVE)
                && !ObjUtil.equals(event.getStatus(), RehabAlertConstants.STATUS_ACKNOWLEDGED)) {
            throw exception(ALERT_EVENT_CAN_NOT_HANDLE);
        }
        RehabAlertEventDO after = new RehabAlertEventDO().setId(event.getId())
                .setStatus(RehabAlertConstants.STATUS_RESOLVED)
                .setResolvedBy(operatorUserId)
                .setResolvedTime(LocalDateTime.now());
        alertEventMapper.updateById(after);
        auditLogService.createAuditLog("alert", event.getId(), RehabOperationTypeConstants.ALERT_RESOLVE,
                operatorUserId, resolveRole(operatorUserId), event, alertEventMapper.selectById(event.getId()),
                "success", StrUtil.blankToDefault(reqVO.getRemark(), "提醒已解决"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ignoreAlert(RehabAlertHandleReqVO reqVO, Long operatorUserId) {
        RehabAlertEventDO event = validateReadable(reqVO.getId(), operatorUserId);
        if (!ObjUtil.equals(event.getStatus(), RehabAlertConstants.STATUS_ACTIVE)
                && !ObjUtil.equals(event.getStatus(), RehabAlertConstants.STATUS_ACKNOWLEDGED)) {
            throw exception(ALERT_EVENT_CAN_NOT_HANDLE);
        }
        RehabAlertEventDO after = new RehabAlertEventDO().setId(event.getId())
                .setStatus(RehabAlertConstants.STATUS_IGNORED)
                .setResolvedBy(operatorUserId)
                .setResolvedTime(LocalDateTime.now());
        alertEventMapper.updateById(after);
        auditLogService.createAuditLog("alert", event.getId(), RehabOperationTypeConstants.ALERT_IGNORE,
                operatorUserId, resolveRole(operatorUserId), event, alertEventMapper.selectById(event.getId()),
                "success", StrUtil.blankToDefault(reqVO.getRemark(), "提醒已忽略"));
    }

    @Override
    public long countActiveHighRiskByVisiblePatients(Long operatorUserId) {
        Set<Long> visiblePatientIds = dataPermissionService.getVisiblePatientIds(operatorUserId);
        if (visiblePatientIds != null && visiblePatientIds.isEmpty()) {
            return 0;
        }
        return alertEventMapper.selectCountActiveHighRiskByPatientIds(visiblePatientIds);
    }

    private List<Long> refreshPlanAlerts(RehabCarePlanDO plan) {
        List<Long> touched = new ArrayList<>();
        RehabProgressRecordDO latestProgress = progressRecordMapper.selectLatestByPlanId(plan.getId());
        RehabDailyCheckinDO latestCheckin = checkinMapper.selectListByPlanId(plan.getId()).stream().findFirst().orElse(null);
        List<RehabReassessmentTriggerDO> pendingTriggers = triggerMapper.selectList(new LambdaQueryWrapperX<RehabReassessmentTriggerDO>()
                .eq(RehabReassessmentTriggerDO::getPlanId, plan.getId())
                .eq(RehabReassessmentTriggerDO::getTriggerStatus, RehabPlanConstants.TRIGGER_STATUS_PENDING));
        RehabReportDO latestReport = reportMapper.selectLatestByPatientId(plan.getPatientId());

        LocalDate today = LocalDate.now();
        if (hasReassessmentDue(pendingTriggers, today, plan)) {
            touched.add(upsertActiveAlert(plan, RehabAlertConstants.TYPE_REASSESSMENT_DUE, RehabAlertConstants.SEVERITY_WARNING,
                    "存在待复评事项，建议尽快安排复测", "trigger_due", "pending", "due",
                    "请在 48 小时内联系治疗师安排复评"));
        }
        if (latestProgress != null && latestProgress.getCompletionRate() != null
                && latestProgress.getCompletionRate().compareTo(new BigDecimal("60")) < 0) {
            touched.add(upsertActiveAlert(plan, RehabAlertConstants.TYPE_LOW_ADHERENCE, RehabAlertConstants.SEVERITY_HIGH,
                    "依从性偏低，建议优先人工复核执行障碍", "completion_rate",
                    latestProgress.getCompletionRate().toPlainString(), ">=60",
                    "近期训练完成率较低，请与治疗师沟通并调整训练节奏"));
        }
        if (isPainUpgrade(latestProgress, latestCheckin)) {
            touched.add(upsertActiveAlert(plan, RehabAlertConstants.TYPE_PAIN_UPGRADE, RehabAlertConstants.SEVERITY_HIGH,
                    "疼痛或不适风险上升，建议及时复核", "pain",
                    latestProgress == null ? null : String.valueOf(latestProgress.getAveragePainScore()), "stable",
                    "近期疼痛反馈升高，请减少高负荷动作并联系治疗师"));
        }
        if (plan.getEndDate() != null && !plan.getEndDate().isAfter(today)
                && ObjUtil.equals(plan.getStatus(), RehabPlanConstants.PLAN_STATUS_ACTIVE)) {
            touched.add(upsertActiveAlert(plan, RehabAlertConstants.TYPE_PLAN_DUE, RehabAlertConstants.SEVERITY_WARNING,
                    "计划已到期但尚未关闭", "plan_end_date", String.valueOf(plan.getEndDate()), ">today",
                    "当前计划已到期，请联系治疗师确认下一阶段安排"));
        }
        if (latestReport != null && (ObjUtil.equals(latestReport.getReportStatus(), RehabReportConstants.STATUS_APPROVED)
                || ObjUtil.equals(latestReport.getReportStatus(), RehabReportConstants.STATUS_EXPORTED)
                || ObjUtil.equals(latestReport.getReportStatus(), RehabReportConstants.STATUS_LOCKED))) {
            touched.add(upsertActiveAlert(plan, RehabAlertConstants.TYPE_REPORT_READY, RehabAlertConstants.SEVERITY_INFO,
                    "有新报告可查看", "report_status", latestReport.getReportStatus(), "approved/exported",
                    "您的最新评估报告摘要已更新，可在小程序查看"));
        }

        LocalDateTime unresolvedThreshold = LocalDateTime.now().minusHours(72);
        if (alertEventMapper.selectCount(new LambdaQueryWrapperX<RehabAlertEventDO>()
                .eq(RehabAlertEventDO::getPlanId, plan.getId())
                .eq(RehabAlertEventDO::getStatus, RehabAlertConstants.STATUS_ACTIVE)
                .eq(RehabAlertEventDO::getSeverity, RehabAlertConstants.SEVERITY_HIGH)
                .le(RehabAlertEventDO::getCreateTime, unresolvedThreshold)) > 0) {
            touched.add(upsertActiveAlert(plan, RehabAlertConstants.TYPE_HIGH_RISK_UNRESOLVED, RehabAlertConstants.SEVERITY_WARNING,
                    "高风险提醒持续未处理", "active_high_risk_overdue", ">=72h", "<72h",
                    "建议尽快与治疗师沟通近期训练与复评安排"));
        }
        return touched.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    private Long upsertActiveAlert(RehabCarePlanDO plan, String alertType, String severity, String triggerMessage,
                                   String metric, String value, String threshold, String patientMessage) {
        RehabAlertEventDO existing = alertEventMapper.selectActiveByPlanAndType(plan.getId(), alertType);
        RehabAlertRuleDO rule = alertRuleMapper.selectByRuleCode(alertType.toUpperCase(Locale.ROOT));
        RehabAlertEventDO target = existing == null ? new RehabAlertEventDO() : existing;
        target.setRuleId(rule == null ? null : rule.getId());
        target.setPatientId(plan.getPatientId());
        target.setEpisodeId(plan.getEpisodeId());
        target.setPlanId(plan.getId());
        target.setRelatedType("plan");
        target.setRelatedId(plan.getId());
        target.setAlertType(alertType);
        target.setSeverity(severity);
        target.setTriggerMessage(triggerMessage);
        target.setTriggerMetric(metric);
        target.setTriggerValue(value);
        target.setThresholdValue(threshold);
        target.setStatus(RehabAlertConstants.STATUS_ACTIVE);
        target.setCreatedFrom(RehabAlertConstants.CREATED_FROM_AUTO);
        if (target.getId() == null) {
            alertEventMapper.insert(target);
        } else {
            target.clean();
            alertEventMapper.updateById(target);
        }
        Long id = target.getId();
        notifyByAlert(plan, id, alertType, severity, triggerMessage, patientMessage);
        return id;
    }

    private void notifyByAlert(RehabCarePlanDO plan, Long alertId, String alertType, String severity,
                               String triggerMessage, String patientMessage) {
        Long therapistUserId = plan.getPrimaryTherapistUserId();
        if (therapistUserId == null) {
            RehabPatientDO patient = patientMapper.selectById(plan.getPatientId());
            therapistUserId = patient == null ? null : patient.getCurrentTherapistUserId();
        }
        if (therapistUserId != null) {
            notificationService.createSystemNotification(RehabNotificationConstants.TARGET_THERAPIST, therapistUserId,
                    plan.getPatientId(), plan.getEpisodeId(), RehabNotificationConstants.RELATED_ALERT, alertId,
                    toNotificationType(alertType), severity,
                    "风险提醒", triggerMessage, RehabNotificationConstants.DELIVERY_MULTI,
                    "/rehab/alert", "查看提醒");
        }
        // 患者端仅下发可读提醒
        if (ObjUtil.equals(alertType, RehabAlertConstants.TYPE_REASSESSMENT_DUE)
                || ObjUtil.equals(alertType, RehabAlertConstants.TYPE_PLAN_DUE)
                || ObjUtil.equals(alertType, RehabAlertConstants.TYPE_REPORT_READY)) {
            notificationService.createSystemNotification(RehabNotificationConstants.TARGET_PATIENT, null,
                    plan.getPatientId(), plan.getEpisodeId(), RehabNotificationConstants.RELATED_ALERT, alertId,
                    toNotificationType(alertType), severity,
                    "训练提醒", patientMessage, RehabNotificationConstants.DELIVERY_APP_PATIENT,
                    "/pages/home/index", "查看详情");
        }
    }

    private String toNotificationType(String alertType) {
        if (ObjUtil.equals(alertType, RehabAlertConstants.TYPE_REASSESSMENT_DUE)) {
            return RehabNotificationConstants.TYPE_REASSESSMENT_DUE;
        }
        if (ObjUtil.equals(alertType, RehabAlertConstants.TYPE_LOW_ADHERENCE)) {
            return RehabNotificationConstants.TYPE_LOW_ADHERENCE;
        }
        if (ObjUtil.equals(alertType, RehabAlertConstants.TYPE_PAIN_UPGRADE)) {
            return RehabNotificationConstants.TYPE_PAIN_ALERT;
        }
        if (ObjUtil.equals(alertType, RehabAlertConstants.TYPE_PLAN_DUE)) {
            return RehabNotificationConstants.TYPE_PLAN_UPDATED;
        }
        if (ObjUtil.equals(alertType, RehabAlertConstants.TYPE_REPORT_READY)) {
            return RehabNotificationConstants.TYPE_REPORT_READY;
        }
        return RehabNotificationConstants.TYPE_TRIGGER_CREATED;
    }

    private boolean hasReassessmentDue(List<RehabReassessmentTriggerDO> pendingTriggers, LocalDate today, RehabCarePlanDO plan) {
        if (CollUtil.isNotEmpty(pendingTriggers)) {
            return pendingTriggers.stream().anyMatch(item -> item.getDueDate() == null || !item.getDueDate().isAfter(today));
        }
        if (plan.getReviewCycleDays() == null || plan.getStartDate() == null) {
            return false;
        }
        return !plan.getStartDate().plusDays(plan.getReviewCycleDays()).isAfter(today);
    }

    private boolean isPainUpgrade(RehabProgressRecordDO latestProgress, RehabDailyCheckinDO latestCheckin) {
        if (latestProgress != null) {
            if (ObjUtil.equals(latestProgress.getPainTrend(), RehabPlanConstants.PAIN_TREND_WORSENED)) {
                return true;
            }
            if (latestProgress.getAveragePainScore() != null
                    && latestProgress.getAveragePainScore().compareTo(new BigDecimal("6")) >= 0) {
                return true;
            }
        }
        if (latestCheckin != null && latestCheckin.getPainScoreAfter() != null && latestCheckin.getPainScoreBefore() != null) {
            return latestCheckin.getPainScoreAfter().subtract(latestCheckin.getPainScoreBefore())
                    .compareTo(new BigDecimal("2")) >= 0;
        }
        return false;
    }

    private List<RehabCarePlanDO> resolveTargetPlans(RehabAlertRefreshReqVO reqVO, Long operatorUserId) {
        if (reqVO.getPlanId() != null) {
            RehabCarePlanDO plan = planMapper.selectById(reqVO.getPlanId());
            if (plan == null) {
                throw exception(PLAN_NOT_EXISTS);
            }
            validatePatientReadable(plan.getPatientId(), operatorUserId);
            return Collections.singletonList(plan);
        }
        Set<Long> visiblePatientIds = dataPermissionService.getVisiblePatientIds(operatorUserId);
        LambdaQueryWrapperX<RehabCarePlanDO> query = new LambdaQueryWrapperX<RehabCarePlanDO>()
                .eq(RehabCarePlanDO::getStatus, RehabPlanConstants.PLAN_STATUS_ACTIVE)
                .orderByDesc(RehabCarePlanDO::getUpdateTime);
        if (reqVO.getPatientId() != null) {
            validatePatientReadable(reqVO.getPatientId(), operatorUserId);
            query.eq(RehabCarePlanDO::getPatientId, reqVO.getPatientId());
        } else if (visiblePatientIds != null) {
            if (visiblePatientIds.isEmpty()) {
                return Collections.emptyList();
            }
            query.in(RehabCarePlanDO::getPatientId, visiblePatientIds);
        }
        return planMapper.selectList(query);
    }

    private RehabAlertEventDO validateReadable(Long alertId, Long operatorUserId) {
        RehabAlertEventDO event = alertEventMapper.selectById(alertId);
        if (event == null) {
            throw exception(ALERT_EVENT_NOT_EXISTS);
        }
        validatePatientReadable(event.getPatientId(), operatorUserId);
        return event;
    }

    private void validatePatientReadable(Long patientId, Long operatorUserId) {
        if (!dataPermissionService.canReadPatient(patientId, operatorUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
    }

    private String resolveRole(Long userId) {
        if (dataPermissionService.isSuperAdmin(userId)) {
            return "admin";
        }
        if (dataPermissionService.isTherapist(userId)) {
            return "therapist";
        }
        if (dataPermissionService.isClerk(userId)) {
            return "clerk";
        }
        return "unknown";
    }

    private List<RehabAlertEventRespVO> toRespList(List<RehabAlertEventDO> list) {
        if (CollUtil.isEmpty(list)) {
            return Collections.emptyList();
        }
        Set<Long> patientIds = list.stream().map(RehabAlertEventDO::getPatientId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> planIds = list.stream().map(RehabAlertEventDO::getPlanId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> ruleIds = list.stream().map(RehabAlertEventDO::getRuleId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, RehabPatientDO> patientMap = patientIds.isEmpty() ? Collections.emptyMap() : patientMapper.selectBatchIds(patientIds).stream()
                .collect(Collectors.toMap(RehabPatientDO::getId, item -> item, (a, b) -> a));
        Map<Long, RehabCarePlanDO> planMap = planIds.isEmpty() ? Collections.emptyMap() : planMapper.selectBatchIds(planIds).stream()
                .collect(Collectors.toMap(RehabCarePlanDO::getId, item -> item, (a, b) -> a));
        Map<Long, RehabAlertRuleDO> ruleMap = ruleIds.isEmpty() ? Collections.emptyMap() : alertRuleMapper.selectBatchIds(ruleIds).stream()
                .collect(Collectors.toMap(RehabAlertRuleDO::getId, item -> item, (a, b) -> a));

        return list.stream().map(item -> {
            RehabAlertEventRespVO vo = BeanUtils.toBean(item, RehabAlertEventRespVO.class);
            RehabPatientDO patient = patientMap.get(item.getPatientId());
            if (patient != null) {
                vo.setPatientName(patient.getName());
                vo.setPatientNo(patient.getPatientNo());
            }
            RehabCarePlanDO plan = planMap.get(item.getPlanId());
            if (plan != null) {
                vo.setPlanNo(plan.getPlanNo());
            }
            RehabAlertRuleDO rule = ruleMap.get(item.getRuleId());
            if (rule != null) {
                vo.setRuleName(rule.getRuleName());
            }
            return vo;
        }).collect(Collectors.toList());
    }
}
