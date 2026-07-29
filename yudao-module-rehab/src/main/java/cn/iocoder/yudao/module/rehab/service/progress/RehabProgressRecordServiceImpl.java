package cn.iocoder.yudao.module.rehab.service.progress;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.rehab.controller.admin.progress.vo.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.checkin.RehabDailyCheckinDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.checkin.RehabTaskExecutionDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.log.RehabPlanOperationLogDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.progress.RehabProgressRecordDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.task.RehabExerciseTaskDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.task.RehabTaskScheduleDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabDailyCheckinMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabTaskExecutionMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.log.RehabPlanOperationLogMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.progress.RehabProgressRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.task.RehabExerciseTaskMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.task.RehabTaskScheduleMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabOperationTypeConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabPlanConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

@Service
@Validated
public class RehabProgressRecordServiceImpl implements RehabProgressRecordService {

    @Resource
    private RehabProgressRecordMapper progressRecordMapper;
    @Resource
    private RehabCarePlanMapper planMapper;
    @Resource
    private RehabPatientMapper patientMapper;
    @Resource
    private RehabExerciseTaskMapper taskMapper;
    @Resource
    private RehabTaskScheduleMapper taskScheduleMapper;
    @Resource
    private RehabDailyCheckinMapper checkinMapper;
    @Resource
    private RehabTaskExecutionMapper taskExecutionMapper;
    @Resource
    private RehabPlanOperationLogMapper planOperationLogMapper;
    @Resource
    private RehabDataPermissionService dataPermissionService;

    @Override
    public PageResult<RehabProgressRecordRespVO> getProgressPage(RehabProgressRecordPageReqVO reqVO, Long operatorUserId) {
        Set<Long> visiblePatientIds = dataPermissionService.getVisiblePatientIds(operatorUserId);
        if (visiblePatientIds != null && visiblePatientIds.isEmpty()) {
            return PageResult.empty();
        }
        if (reqVO.getPatientId() != null) {
            validatePatientReadable(reqVO.getPatientId(), operatorUserId);
            visiblePatientIds = Collections.singleton(reqVO.getPatientId());
        }
        if (reqVO.getPlanId() != null) {
            RehabCarePlanDO plan = validatePlanReadable(reqVO.getPlanId(), operatorUserId);
            visiblePatientIds = Collections.singleton(plan.getPatientId());
        }

        PageResult<RehabProgressRecordDO> pageResult = progressRecordMapper.selectPage(reqVO, visiblePatientIds);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        return new PageResult<>(toRespVOList(pageResult.getList()), pageResult.getTotal());
    }

    @Override
    public RehabProgressRecordRespVO getProgress(Long id, Long operatorUserId) {
        RehabProgressRecordDO progress = progressRecordMapper.selectById(id);
        if (progress == null) {
            throw exception(PROGRESS_NOT_EXISTS);
        }
        validatePatientReadable(progress.getPatientId(), operatorUserId);
        return toRespVOList(Collections.singletonList(progress)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RehabProgressRecordRespVO recalculate(RehabProgressRecalculateReqVO reqVO, Long operatorUserId) {
        validateClerkWriteForbidden(operatorUserId);
        RehabProgressRecordDO record = recalculateByPlan(reqVO.getPlanId(),
                ObjUtil.defaultIfNull(reqVO.getPeriodEnd(), LocalDate.now()),
                operatorUserId,
                StrUtil.blankToDefault(reqVO.getRemark(), "手动重算进度"));
        return toRespVOList(Collections.singletonList(record)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RehabProgressRecordDO recalculateByPlan(Long planId, LocalDate anchorDate, Long operatorUserId, String remark) {
        RehabCarePlanDO plan = validatePlanReadable(planId, operatorUserId);
        LocalDate targetDate = ObjUtil.defaultIfNull(anchorDate, LocalDate.now());
        LocalDate periodStart = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate periodEnd = targetDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        List<RehabExerciseTaskDO> tasks = taskMapper.selectListByPlanId(planId).stream()
                .filter(item -> !ObjUtil.equals(item.getStatus(), RehabPlanConstants.TASK_STATUS_DISABLED))
                .collect(Collectors.toList());
        List<RehabTaskScheduleDO> schedules = taskScheduleMapper.selectListByPlanId(planId);

        int plannedTaskCount = calcPlannedTaskCount(tasks, schedules, periodStart, periodEnd);

        List<RehabDailyCheckinDO> checkins = checkinMapper.selectListByPlanIdAndDateRange(planId, periodStart, periodEnd);
        BigDecimal completedTaskCount = BigDecimal.ZERO;
        int skippedDueToPain = 0;
        int skippedDueToSchedule = 0;
        int symptomEventsCount = 0;

        List<Long> checkinIds = checkins.stream().map(RehabDailyCheckinDO::getId).collect(Collectors.toList());
        List<RehabTaskExecutionDO> executions = taskExecutionMapper.selectListByCheckinIds(checkinIds);
        List<BigDecimal> painSamples = new ArrayList<>();

        Map<Long, List<RehabTaskExecutionDO>> executionMap = executions.stream()
                .collect(Collectors.groupingBy(RehabTaskExecutionDO::getCheckinId));

        // 同日多打卡自动聚合：先按日期分组后再计算
        Map<LocalDate, List<RehabDailyCheckinDO>> dayCheckinsMap = checkins.stream()
                .collect(Collectors.groupingBy(RehabDailyCheckinDO::getCheckinDate));
        for (Map.Entry<LocalDate, List<RehabDailyCheckinDO>> entry : dayCheckinsMap.entrySet()) {
            BigDecimal dayCompleted = BigDecimal.ZERO;
            for (RehabDailyCheckinDO dayCheckin : entry.getValue()) {
                List<RehabTaskExecutionDO> dayExecutions = executionMap.getOrDefault(dayCheckin.getId(), Collections.emptyList());
                for (RehabTaskExecutionDO execution : dayExecutions) {
                    dayCompleted = dayCompleted.add(scoreCompletion(execution.getCompletionStatus()));
                    if (ObjUtil.equals(execution.getCompletionStatus(), RehabPlanConstants.COMPLETION_PAIN_STOP)) {
                        skippedDueToPain++;
                    }
                    if (ObjUtil.equals(execution.getCompletionStatus(), RehabPlanConstants.COMPLETION_SKIPPED)) {
                        skippedDueToSchedule++;
                    }
                    if (Boolean.TRUE.equals(execution.getSymptomFlag())
                            || ObjUtil.equals(execution.getCompletionStatus(), RehabPlanConstants.COMPLETION_PAIN_STOP)) {
                        symptomEventsCount++;
                    }
                    if (execution.getPainScore() != null) {
                        painSamples.add(execution.getPainScore());
                    }
                }
                if (dayCheckin.getPainScoreAfter() != null) {
                    painSamples.add(dayCheckin.getPainScoreAfter());
                } else if (dayCheckin.getPainScoreBefore() != null) {
                    painSamples.add(dayCheckin.getPainScoreBefore());
                }
            }
            completedTaskCount = completedTaskCount.add(dayCompleted);
        }

        BigDecimal completionRate = plannedTaskCount <= 0 ? BigDecimal.ZERO
                : completedTaskCount.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(plannedTaskCount), 2, RoundingMode.HALF_UP);
        BigDecimal adherenceScore = completionRate;
        BigDecimal averagePainScore = calcAverage(painSamples);

        PainTrendResult painTrendResult = calcPainTrend(planId, periodStart, periodEnd, averagePainScore, painSamples.size());
        String progressStatus = calcProgressStatus(completionRate, painTrendResult.painTrend, symptomEventsCount, plannedTaskCount);
        String recommendedAction = calcRecommendedAction(progressStatus);

        RehabProgressRecordDO existing = progressRecordMapper.selectByPlanAndPeriod(planId, periodStart, periodEnd);
        RehabProgressRecordDO record = existing == null ? new RehabProgressRecordDO() : existing;
        record.setPatientId(plan.getPatientId());
        record.setEpisodeId(plan.getEpisodeId());
        record.setPlanId(planId);
        record.setPeriodStart(periodStart);
        record.setPeriodEnd(periodEnd);
        record.setPlannedTaskCount(plannedTaskCount);
        record.setCompletedTaskCount(completedTaskCount.setScale(2, RoundingMode.HALF_UP));
        record.setCompletionRate(completionRate);
        record.setAdherenceScore(adherenceScore);
        record.setAveragePainScore(averagePainScore);
        record.setPainTrend(painTrendResult.painTrend);
        record.setSymptomEventsCount(symptomEventsCount);
        record.setSkippedDueToPain(skippedDueToPain);
        record.setSkippedDueToSchedule(skippedDueToSchedule);
        record.setProgressStatus(progressStatus);
        record.setRecommendedAction(recommendedAction);

        if (record.getId() == null) {
            progressRecordMapper.insert(record);
        } else {
            record.clean();
            progressRecordMapper.updateById(record);
        }
        RehabProgressRecordDO latest = progressRecordMapper.selectById(record.getId());
        createPlanLog(planId, RehabOperationTypeConstants.PROGRESS_RECALCULATE, operatorUserId,
                null, latest, StrUtil.blankToDefault(remark, "重算进度"));
        return latest;
    }

    @Override
    public RehabProgressRecordDO getLatestByPlanId(Long planId, Long operatorUserId) {
        RehabCarePlanDO plan = validatePlanReadable(planId, operatorUserId);
        return progressRecordMapper.selectLatestByPlanId(plan.getId());
    }

    private BigDecimal scoreCompletion(String completionStatus) {
        if (ObjUtil.equals(completionStatus, RehabPlanConstants.COMPLETION_COMPLETED)) {
            return BigDecimal.ONE;
        }
        if (ObjUtil.equals(completionStatus, RehabPlanConstants.COMPLETION_PARTIAL)) {
            return new BigDecimal("0.5");
        }
        return BigDecimal.ZERO;
    }

    private int calcPlannedTaskCount(List<RehabExerciseTaskDO> tasks, List<RehabTaskScheduleDO> schedules,
                                     LocalDate periodStart, LocalDate periodEnd) {
        if (CollUtil.isNotEmpty(schedules)) {
            int total = 0;
            long days = periodEnd.toEpochDay() - periodStart.toEpochDay() + 1;
            for (RehabTaskScheduleDO schedule : schedules) {
                int sessions = ObjUtil.defaultIfNull(schedule.getTargetSessions(), 1);
                if (ObjUtil.equals(schedule.getScheduleType(), RehabPlanConstants.SCHEDULE_DAILY)) {
                    total += (int) days * sessions;
                } else if (ObjUtil.equals(schedule.getScheduleType(), RehabPlanConstants.SCHEDULE_WEEKLY)) {
                    total += sessions;
                } else {
                    if (schedule.getScheduledDate() != null
                            && !schedule.getScheduledDate().isBefore(periodStart)
                            && !schedule.getScheduledDate().isAfter(periodEnd)) {
                        total += sessions;
                    }
                }
            }
            return Math.max(total, tasks.size());
        }
        int fallback = 0;
        for (RehabExerciseTaskDO task : tasks) {
            fallback += ObjUtil.defaultIfNull(task.getFrequencyPerWeek(), 0);
        }
        return fallback > 0 ? fallback : tasks.size();
    }

    private BigDecimal calcAverage(List<BigDecimal> values) {
        if (CollUtil.isEmpty(values)) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (value != null) {
                sum = sum.add(value);
            }
        }
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private PainTrendResult calcPainTrend(Long planId, LocalDate periodStart, LocalDate periodEnd,
                                          BigDecimal currentAvg, int currentSampleSize) {
        LocalDate prevStart = periodStart.minusDays(7);
        LocalDate prevEnd = periodEnd.minusDays(7);

        List<RehabDailyCheckinDO> prevCheckins = checkinMapper.selectListByPlanIdAndDateRange(planId, prevStart, prevEnd);
        List<Long> prevIds = prevCheckins.stream().map(RehabDailyCheckinDO::getId).collect(Collectors.toList());
        List<RehabTaskExecutionDO> prevExecutions = taskExecutionMapper.selectListByCheckinIds(prevIds);

        List<BigDecimal> prevPainSamples = new ArrayList<>();
        for (RehabTaskExecutionDO execution : prevExecutions) {
            if (execution.getPainScore() != null) {
                prevPainSamples.add(execution.getPainScore());
            }
        }
        for (RehabDailyCheckinDO checkin : prevCheckins) {
            if (checkin.getPainScoreAfter() != null) {
                prevPainSamples.add(checkin.getPainScoreAfter());
            } else if (checkin.getPainScoreBefore() != null) {
                prevPainSamples.add(checkin.getPainScoreBefore());
            }
        }
        if (currentSampleSize < 2 || prevPainSamples.size() < 2) {
            return new PainTrendResult(RehabPlanConstants.PAIN_TREND_INSUFFICIENT, BigDecimal.ZERO);
        }
        BigDecimal prevAvg = calcAverage(prevPainSamples);
        BigDecimal diff = currentAvg.subtract(prevAvg);
        if (diff.compareTo(BigDecimal.ONE) >= 0) {
            return new PainTrendResult(RehabPlanConstants.PAIN_TREND_WORSENED, diff);
        }
        if (diff.compareTo(BigDecimal.ONE.negate()) <= 0) {
            return new PainTrendResult(RehabPlanConstants.PAIN_TREND_IMPROVED, diff);
        }
        return new PainTrendResult(RehabPlanConstants.PAIN_TREND_STABLE, diff);
    }

    private String calcProgressStatus(BigDecimal completionRate, String painTrend, int symptomEventsCount, int plannedTaskCount) {
        if (plannedTaskCount <= 0) {
            return RehabPlanConstants.PROGRESS_INSUFFICIENT;
        }
        if (completionRate.compareTo(new BigDecimal("80")) >= 0
                && !ObjUtil.equals(painTrend, RehabPlanConstants.PAIN_TREND_WORSENED)
                && symptomEventsCount <= 1) {
            return RehabPlanConstants.PROGRESS_IMPROVED;
        }
        if (completionRate.compareTo(new BigDecimal("60")) >= 0) {
            return RehabPlanConstants.PROGRESS_SLIGHTLY_IMPROVED;
        }
        if (completionRate.compareTo(new BigDecimal("40")) >= 0) {
            return RehabPlanConstants.PROGRESS_STABLE;
        }
        return RehabPlanConstants.PROGRESS_WORSENED;
    }

    private String calcRecommendedAction(String progressStatus) {
        if (ObjUtil.equals(progressStatus, RehabPlanConstants.PROGRESS_IMPROVED)
                || ObjUtil.equals(progressStatus, RehabPlanConstants.PROGRESS_SLIGHTLY_IMPROVED)) {
            return "继续执行当前计划并按复评周期跟进";
        }
        if (ObjUtil.equals(progressStatus, RehabPlanConstants.PROGRESS_STABLE)) {
            return "建议复核任务剂量与执行质量，必要时微调计划";
        }
        if (ObjUtil.equals(progressStatus, RehabPlanConstants.PROGRESS_WORSENED)) {
            return "建议优先人工复核并考虑提前复评";
        }
        return "数据不足，建议补充执行记录";
    }

    private RehabCarePlanDO validatePlanReadable(Long planId, Long operatorUserId) {
        RehabCarePlanDO plan = planMapper.selectById(planId);
        if (plan == null) {
            throw exception(PLAN_NOT_EXISTS);
        }
        validatePatientReadable(plan.getPatientId(), operatorUserId);
        return plan;
    }

    private void validatePatientReadable(Long patientId, Long operatorUserId) {
        if (!dataPermissionService.canReadPatient(patientId, operatorUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
    }

    private void validateClerkWriteForbidden(Long operatorUserId) {
        if (dataPermissionService.isClerk(operatorUserId)) {
            throw exception(CLERK_WRITE_FORBIDDEN);
        }
    }

    private void createPlanLog(Long planId, String operationType, Long operatorUserId,
                               Object beforeData, Object afterData, String remark) {
        RehabPlanOperationLogDO log = RehabPlanOperationLogDO.builder()
                .planId(planId)
                .operationType(operationType)
                .operatorUserId(operatorUserId)
                .beforeDataJson(beforeData == null ? null : JsonUtils.toJsonString(beforeData))
                .afterDataJson(afterData == null ? null : JsonUtils.toJsonString(afterData))
                .remark(remark)
                .build();
        planOperationLogMapper.insert(log);
    }

    private List<RehabProgressRecordRespVO> toRespVOList(List<RehabProgressRecordDO> list) {
        Set<Long> patientIds = list.stream().map(RehabProgressRecordDO::getPatientId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> planIds = list.stream().map(RehabProgressRecordDO::getPlanId).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, RehabPatientDO> patientMap = patientIds.isEmpty() ? Collections.emptyMap() : patientMapper.selectBatchIds(patientIds).stream()
                .collect(Collectors.toMap(RehabPatientDO::getId, item -> item, (a, b) -> a));
        Map<Long, RehabCarePlanDO> planMap = planIds.isEmpty() ? Collections.emptyMap() : planMapper.selectBatchIds(planIds).stream()
                .collect(Collectors.toMap(RehabCarePlanDO::getId, item -> item, (a, b) -> a));

        return list.stream().map(item -> {
            RehabProgressRecordRespVO vo = BeanUtils.toBean(item, RehabProgressRecordRespVO.class);
            RehabPatientDO patient = patientMap.get(item.getPatientId());
            if (patient != null) {
                vo.setPatientNo(patient.getPatientNo());
                vo.setPatientName(patient.getName());
            }
            RehabCarePlanDO plan = planMap.get(item.getPlanId());
            if (plan != null) {
                vo.setPlanNo(plan.getPlanNo());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private static class PainTrendResult {
        private final String painTrend;
        @SuppressWarnings("unused")
        private final BigDecimal diff;

        private PainTrendResult(String painTrend, BigDecimal diff) {
            this.painTrend = painTrend;
            this.diff = diff;
        }
    }

}
