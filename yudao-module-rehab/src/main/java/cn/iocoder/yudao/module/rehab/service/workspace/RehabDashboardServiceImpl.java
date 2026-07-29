package cn.iocoder.yudao.module.rehab.service.workspace;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.workspace.vo.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.alert.RehabAlertEventDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentRecordDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.checkin.RehabDailyCheckinDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.report.RehabReportDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.trigger.RehabReassessmentTriggerDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.alert.RehabAlertEventMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabDailyCheckinMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.report.RehabReportMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.trigger.RehabReassessmentTriggerMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabAlertConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabPatientStatusConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabPlanConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabReportConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.notification.RehabNotificationService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.PATIENT_NO_PERMISSION;

@Service
@Validated
public class RehabDashboardServiceImpl implements RehabDashboardService {

    @Resource
    private RehabDataPermissionService dataPermissionService;
    @Resource
    private RehabPatientMapper patientMapper;
    @Resource
    private RehabCarePlanMapper planMapper;
    @Resource
    private RehabAssessmentRecordMapper assessmentRecordMapper;
    @Resource
    private RehabReportMapper reportMapper;
    @Resource
    private RehabDailyCheckinMapper checkinMapper;
    @Resource
    private RehabAlertEventMapper alertEventMapper;
    @Resource
    private RehabReassessmentTriggerMapper reassessmentTriggerMapper;
    @Resource
    private RehabNotificationService notificationService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public RehabDashboardSummaryRespVO getTherapistSummary(Long operatorUserId) {
        Set<Long> visiblePatientIds = dataPermissionService.getVisiblePatientIds(operatorUserId);
        RehabDashboardSummaryRespVO vo = new RehabDashboardSummaryRespVO();
        if (visiblePatientIds != null && visiblePatientIds.isEmpty()) {
            vo.setMyPatientCount(0L);
            vo.setActivePlanCount(0L);
            vo.setPendingReassessmentCount(0L);
            vo.setHighRiskPatientCount(0L);
            vo.setLowAdherencePatientCount(0L);
            vo.setWeeklyNewAssessmentCount(0L);
            vo.setUnreadNotificationCount(0L);
            return vo;
        }

        vo.setMyPatientCount(visiblePatientIds == null ? patientMapper.selectCount() : (long) visiblePatientIds.size());
        vo.setActivePlanCount(planMapper.selectCount(new LambdaQueryWrapperX<RehabCarePlanDO>()
                .eq(RehabCarePlanDO::getStatus, RehabPlanConstants.PLAN_STATUS_ACTIVE)
                .inIfPresent(RehabCarePlanDO::getPatientId, visiblePatientIds)));
        vo.setPendingReassessmentCount(reassessmentTriggerMapper.selectCount(
                new LambdaQueryWrapperX<cn.iocoder.yudao.module.rehab.dal.dataobject.trigger.RehabReassessmentTriggerDO>()
                        .eq(cn.iocoder.yudao.module.rehab.dal.dataobject.trigger.RehabReassessmentTriggerDO::getTriggerStatus, RehabPlanConstants.TRIGGER_STATUS_PENDING)
                        .inIfPresent(cn.iocoder.yudao.module.rehab.dal.dataobject.trigger.RehabReassessmentTriggerDO::getPatientId, visiblePatientIds)));

        List<RehabAlertEventDO> activeAlerts = alertEventMapper.selectList(new LambdaQueryWrapperX<RehabAlertEventDO>()
                .eq(RehabAlertEventDO::getStatus, RehabAlertConstants.STATUS_ACTIVE)
                .inIfPresent(RehabAlertEventDO::getPatientId, visiblePatientIds));
        vo.setHighRiskPatientCount(activeAlerts.stream()
                .filter(item -> ObjUtil.equals(item.getSeverity(), RehabAlertConstants.SEVERITY_HIGH))
                .map(RehabAlertEventDO::getPatientId).filter(Objects::nonNull).distinct().count());
        vo.setLowAdherencePatientCount(activeAlerts.stream()
                .filter(item -> ObjUtil.equals(item.getAlertType(), RehabAlertConstants.TYPE_LOW_ADHERENCE))
                .map(RehabAlertEventDO::getPatientId).filter(Objects::nonNull).distinct().count());

        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LambdaQueryWrapperX<RehabAssessmentRecordDO> assessmentQuery = new LambdaQueryWrapperX<>();
        assessmentQuery.between(RehabAssessmentRecordDO::getAssessmentDate, weekStart, weekEnd);
        if (visiblePatientIds != null) {
            assessmentQuery.in(RehabAssessmentRecordDO::getPatientId, visiblePatientIds);
        }
        vo.setWeeklyNewAssessmentCount(assessmentRecordMapper.selectCount(assessmentQuery));
        vo.setUnreadNotificationCount(notificationService.countUnreadForAdminUser(operatorUserId));
        return vo;
    }

    @Override
    public RehabDashboardRecentItemsRespVO getTherapistRecentItems(Long operatorUserId) {
        Set<Long> visiblePatientIds = dataPermissionService.getVisiblePatientIds(operatorUserId);
        RehabDashboardRecentItemsRespVO vo = new RehabDashboardRecentItemsRespVO();
        if (visiblePatientIds != null && visiblePatientIds.isEmpty()) {
            vo.setRecentAlerts(Collections.emptyList());
            vo.setRecentReports(Collections.emptyList());
            vo.setAbnormalCheckins(Collections.emptyList());
            return vo;
        }

        List<RehabPatientDO> patients;
        if (visiblePatientIds == null) {
            patients = patientMapper.selectList();
        } else {
            patients = patientMapper.selectBatchIds(visiblePatientIds);
        }
        Map<Long, RehabPatientDO> patientMap = patients.stream()
                .collect(Collectors.toMap(RehabPatientDO::getId, item -> item, (a, b) -> a));

        List<RehabAlertEventDO> alerts = alertEventMapper.selectList(new LambdaQueryWrapperX<RehabAlertEventDO>()
                .eq(RehabAlertEventDO::getStatus, RehabAlertConstants.STATUS_ACTIVE)
                .inIfPresent(RehabAlertEventDO::getPatientId, visiblePatientIds)
                .orderByDesc(RehabAlertEventDO::getCreateTime)
                .last("LIMIT 10"));
        vo.setRecentAlerts(alerts.stream().map(item -> {
            RehabDashboardRecentItemsRespVO.AlertItem alert = new RehabDashboardRecentItemsRespVO.AlertItem();
            alert.setId(item.getId());
            alert.setPatientId(item.getPatientId());
            alert.setAlertType(item.getAlertType());
            alert.setSeverity(item.getSeverity());
            alert.setStatus(item.getStatus());
            alert.setTriggerMessage(item.getTriggerMessage());
            alert.setCreateTime(item.getCreateTime());
            RehabPatientDO patient = patientMap.get(item.getPatientId());
            if (patient != null) {
                alert.setPatientName(patient.getName());
                alert.setPatientNo(patient.getPatientNo());
            }
            return alert;
        }).collect(Collectors.toList()));

        List<RehabReportDO> reports = reportMapper.selectList(new LambdaQueryWrapperX<RehabReportDO>()
                .inIfPresent(RehabReportDO::getPatientId, visiblePatientIds)
                .orderByDesc(RehabReportDO::getUpdateTime)
                .last("LIMIT 10"));
        vo.setRecentReports(reports.stream().map(item -> {
            RehabDashboardRecentItemsRespVO.ReportItem report = new RehabDashboardRecentItemsRespVO.ReportItem();
            report.setId(item.getId());
            report.setReportNo(item.getReportNo());
            report.setPatientId(item.getPatientId());
            report.setReportStatus(item.getReportStatus());
            report.setReportVersion(item.getReportVersion());
            report.setUpdateTime(item.getUpdateTime());
            RehabPatientDO patient = patientMap.get(item.getPatientId());
            if (patient != null) {
                report.setPatientName(patient.getName());
                report.setPatientNo(patient.getPatientNo());
            }
            return report;
        }).collect(Collectors.toList()));

        List<RehabDailyCheckinDO> checkins = checkinMapper.selectList(new LambdaQueryWrapperX<RehabDailyCheckinDO>()
                .inIfPresent(RehabDailyCheckinDO::getPatientId, visiblePatientIds)
                .orderByDesc(RehabDailyCheckinDO::getCheckinDate)
                .orderByDesc(RehabDailyCheckinDO::getId)
                .last("LIMIT 30"));
        List<RehabDashboardRecentItemsRespVO.CheckinItem> abnormal = new ArrayList<>();
        for (RehabDailyCheckinDO item : checkins) {
            String reason = null;
            if (item.getPainScoreAfter() != null && item.getPainScoreAfter().compareTo(new BigDecimal("6")) >= 0) {
                reason = "疼痛评分偏高";
            } else if (item.getOverallCompletionRate() != null && item.getOverallCompletionRate().compareTo(new BigDecimal("60")) < 0) {
                reason = "完成率偏低";
            }
            if (reason == null) {
                continue;
            }
            RehabDashboardRecentItemsRespVO.CheckinItem voItem = new RehabDashboardRecentItemsRespVO.CheckinItem();
            voItem.setId(item.getId());
            voItem.setPatientId(item.getPatientId());
            voItem.setCheckinDate(item.getCheckinDate());
            voItem.setReason(reason);
            voItem.setCreateTime(item.getCreateTime());
            RehabPatientDO patient = patientMap.get(item.getPatientId());
            if (patient != null) {
                voItem.setPatientName(patient.getName());
                voItem.setPatientNo(patient.getPatientNo());
            }
            abnormal.add(voItem);
            if (abnormal.size() >= 10) {
                break;
            }
        }
        vo.setAbnormalCheckins(abnormal);
        return vo;
    }

    @Override
    public RehabOpsDashboardSummaryRespVO getOpsSummary(Long operatorUserId) {
        validateOpsPermission(operatorUserId);
        RehabOpsDashboardSummaryRespVO vo = new RehabOpsDashboardSummaryRespVO();

        vo.setPatientTotal(patientMapper.selectCount());
        vo.setActivePatientTotal(patientMapper.selectCount(new LambdaQueryWrapperX<RehabPatientDO>()
                .eq(RehabPatientDO::getCurrentStatus, RehabPatientStatusConstants.ACTIVE)));
        vo.setActivePlanTotal(planMapper.selectCount(new LambdaQueryWrapperX<RehabCarePlanDO>()
                .eq(RehabCarePlanDO::getStatus, RehabPlanConstants.PLAN_STATUS_ACTIVE)));

        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        vo.setWeeklyNewAssessmentTotal(assessmentRecordMapper.selectCount(new LambdaQueryWrapperX<RehabAssessmentRecordDO>()
                .between(RehabAssessmentRecordDO::getAssessmentDate, weekStart, weekEnd)));

        vo.setPendingReassessmentTotal(reassessmentTriggerMapper.selectCount(new LambdaQueryWrapperX<RehabReassessmentTriggerDO>()
                .eq(RehabReassessmentTriggerDO::getTriggerStatus, RehabPlanConstants.TRIGGER_STATUS_PENDING)));
        vo.setHighRiskTotal(alertEventMapper.selectCount(new LambdaQueryWrapperX<RehabAlertEventDO>()
                .eq(RehabAlertEventDO::getStatus, RehabAlertConstants.STATUS_ACTIVE)
                .eq(RehabAlertEventDO::getSeverity, RehabAlertConstants.SEVERITY_HIGH)));
        vo.setReportGeneratedTotal(reportMapper.selectCount());
        vo.setReportExportedTotal(reportMapper.selectCount(new LambdaQueryWrapperX<RehabReportDO>()
                .in(RehabReportDO::getReportStatus, Arrays.asList(RehabReportConstants.STATUS_EXPORTED, RehabReportConstants.STATUS_LOCKED))));
        vo.setLowAdherenceTotal(alertEventMapper.selectCount(new LambdaQueryWrapperX<RehabAlertEventDO>()
                .eq(RehabAlertEventDO::getStatus, RehabAlertConstants.STATUS_ACTIVE)
                .eq(RehabAlertEventDO::getAlertType, RehabAlertConstants.TYPE_LOW_ADHERENCE)));

        List<RehabDailyCheckinDO> checkins = checkinMapper.selectList(new LambdaQueryWrapperX<RehabDailyCheckinDO>()
                .between(RehabDailyCheckinDO::getCheckinDate, weekStart, weekEnd));
        if (CollUtil.isEmpty(checkins)) {
            vo.setAvgCheckinCompletionRate(BigDecimal.ZERO);
        } else {
            BigDecimal sum = BigDecimal.ZERO;
            int count = 0;
            for (RehabDailyCheckinDO checkin : checkins) {
                if (checkin.getOverallCompletionRate() != null) {
                    sum = sum.add(checkin.getOverallCompletionRate());
                    count++;
                }
            }
            vo.setAvgCheckinCompletionRate(count <= 0 ? BigDecimal.ZERO : sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
        }
        return vo;
    }

    @Override
    public List<RehabOpsWorkloadRespVO> getOpsWorkload(Long operatorUserId) {
        validateOpsPermission(operatorUserId);
        List<RehabPatientDO> patients = patientMapper.selectList();
        Map<Long, List<RehabPatientDO>> grouped = patients.stream()
                .filter(item -> item.getCurrentTherapistUserId() != null)
                .collect(Collectors.groupingBy(RehabPatientDO::getCurrentTherapistUserId));

        Set<Long> therapistIds = grouped.keySet();
        Map<Long, AdminUserRespDTO> userMap = therapistIds.isEmpty() ? Collections.emptyMap() : adminUserApi.getUserMap(therapistIds);

        List<RehabOpsWorkloadRespVO> list = new ArrayList<>();
        for (Map.Entry<Long, List<RehabPatientDO>> entry : grouped.entrySet()) {
            Long therapistId = entry.getKey();
            List<Long> patientIds = entry.getValue().stream().map(RehabPatientDO::getId).collect(Collectors.toList());
            RehabOpsWorkloadRespVO item = new RehabOpsWorkloadRespVO();
            item.setTherapistUserId(therapistId);
            item.setPatientCount((long) patientIds.size());
            item.setActivePlanCount(planMapper.selectCount(new LambdaQueryWrapperX<RehabCarePlanDO>()
                    .eq(RehabCarePlanDO::getStatus, RehabPlanConstants.PLAN_STATUS_ACTIVE)
                    .in(RehabCarePlanDO::getPatientId, patientIds)));
            AdminUserRespDTO user = userMap.get(therapistId);
            item.setTherapistName(user == null ? ("用户#" + therapistId) : user.getNickname());
            list.add(item);
        }
        list.sort(Comparator.comparing(RehabOpsWorkloadRespVO::getPatientCount, Comparator.nullsLast(Long::compareTo)).reversed());
        return list;
    }

    @Override
    public List<RehabOpsRiskOverviewRespVO> getOpsRiskOverview(Long operatorUserId) {
        validateOpsPermission(operatorUserId);
        List<RehabAlertEventDO> alerts = alertEventMapper.selectList(new LambdaQueryWrapperX<RehabAlertEventDO>()
                .eq(RehabAlertEventDO::getStatus, RehabAlertConstants.STATUS_ACTIVE));
        Map<String, Long> grouped = alerts.stream().collect(Collectors.groupingBy(RehabAlertEventDO::getAlertType, Collectors.counting()));
        return grouped.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(entry -> {
                    RehabOpsRiskOverviewRespVO vo = new RehabOpsRiskOverviewRespVO();
                    vo.setAlertType(entry.getKey());
                    vo.setCount(entry.getValue());
                    return vo;
                }).collect(Collectors.toList());
    }

    private void validateOpsPermission(Long operatorUserId) {
        if (!dataPermissionService.isSuperAdmin(operatorUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
    }
}
