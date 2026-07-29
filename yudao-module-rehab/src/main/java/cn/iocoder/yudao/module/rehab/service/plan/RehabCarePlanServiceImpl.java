package cn.iocoder.yudao.module.rehab.service.plan;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.rehab.controller.admin.plan.vo.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentRecordDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.checkin.RehabDailyCheckinDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.episode.RehabEpisodeDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.log.RehabPlanOperationLogDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.progress.RehabProgressRecordDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.task.RehabExerciseTaskDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.task.RehabTaskScheduleDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabDailyCheckinMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.episode.RehabEpisodeMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.log.RehabPlanOperationLogMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.progress.RehabProgressRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.task.RehabExerciseTaskMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.task.RehabTaskScheduleMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.trigger.RehabReassessmentTriggerMapper;
import cn.iocoder.yudao.module.rehab.dal.dataobject.trigger.RehabReassessmentTriggerDO;
import cn.iocoder.yudao.module.rehab.enums.RehabOperationTypeConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabPlanConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.log.RehabAuditLogService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

@Service
@Validated
public class RehabCarePlanServiceImpl implements RehabCarePlanService {

    private static final DateTimeFormatter PLAN_NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Resource
    private RehabCarePlanMapper planMapper;
    @Resource
    private RehabPlanOperationLogMapper planOperationLogMapper;
    @Resource
    private RehabPatientMapper patientMapper;
    @Resource
    private RehabEpisodeMapper episodeMapper;
    @Resource
    private RehabAssessmentRecordMapper assessmentRecordMapper;
    @Resource
    private RehabExerciseTaskMapper taskMapper;
    @Resource
    private RehabTaskScheduleMapper taskScheduleMapper;
    @Resource
    private RehabDailyCheckinMapper checkinMapper;
    @Resource
    private RehabProgressRecordMapper progressRecordMapper;
    @Resource
    private RehabReassessmentTriggerMapper triggerMapper;
    @Resource
    private RehabDataPermissionService dataPermissionService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private RehabAuditLogService auditLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RehabCarePlanCreateRespVO createPlan(RehabCarePlanCreateReqVO reqVO, Long operatorUserId) {
        validateClerkWriteForbidden(operatorUserId);
        RehabPatientDO patient = validatePatientExists(reqVO.getPatientId());
        validatePatientReadable(patient.getId(), operatorUserId);
        RehabEpisodeDO episode = validateEpisodeExists(reqVO.getEpisodeId());
        validateEpisodeBelongsToPatient(episode, patient.getId());
        if (reqVO.getSourceAssessmentId() != null) {
            validateAssessmentBelongs(reqVO.getSourceAssessmentId(), patient.getId(), episode.getId());
        }

        RehabCarePlanDO plan = BeanUtils.toBean(reqVO, RehabCarePlanDO.class);
        fillPlanDefaults(plan, operatorUserId);
        validateActiveUniqueness(plan.getPatientId(), plan.getEpisodeId(), plan.getStatus(), null);
        planMapper.insert(plan);

        String planNo = generatePlanNo(plan.getId());
        planMapper.updateById(new RehabCarePlanDO().setId(plan.getId()).setPlanNo(planNo));

        RehabCarePlanDO latest = planMapper.selectById(plan.getId());
        createOperationLog(plan.getId(), RehabOperationTypeConstants.PLAN_CREATE, operatorUserId,
                null, latest, "创建训练计划");

        RehabCarePlanCreateRespVO respVO = new RehabCarePlanCreateRespVO();
        respVO.setId(plan.getId());
        respVO.setPlanNo(planNo);
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePlan(RehabCarePlanUpdateReqVO reqVO, Long operatorUserId) {
        validateClerkWriteForbidden(operatorUserId);
        RehabCarePlanDO oldPlan = validatePlanReadable(reqVO.getId(), operatorUserId);
        RehabEpisodeDO episode = validateEpisodeExists(reqVO.getEpisodeId());
        validateEpisodeBelongsToPatient(episode, reqVO.getPatientId());

        String targetStatus = StrUtil.blankToDefault(reqVO.getStatus(), oldPlan.getStatus());
        validateActiveUniqueness(reqVO.getPatientId(), reqVO.getEpisodeId(), targetStatus, reqVO.getId());

        RehabCarePlanDO updateObj = BeanUtils.toBean(reqVO, RehabCarePlanDO.class);
        fillPlanDateFields(updateObj);
        updateObj.clean();
        planMapper.updateById(updateObj);

        RehabCarePlanDO newPlan = planMapper.selectById(reqVO.getId());
        createOperationLog(reqVO.getId(), RehabOperationTypeConstants.PLAN_UPDATE, operatorUserId,
                oldPlan, newPlan, "更新训练计划");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activatePlan(RehabCarePlanChangeStatusReqVO reqVO, Long operatorUserId) {
        changeStatus(reqVO, RehabPlanConstants.PLAN_STATUS_ACTIVE, RehabOperationTypeConstants.PLAN_ACTIVATE, operatorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pausePlan(RehabCarePlanChangeStatusReqVO reqVO, Long operatorUserId) {
        changeStatus(reqVO, RehabPlanConstants.PLAN_STATUS_PAUSED, RehabOperationTypeConstants.PLAN_PAUSE, operatorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completePlan(RehabCarePlanChangeStatusReqVO reqVO, Long operatorUserId) {
        changeStatus(reqVO, RehabPlanConstants.PLAN_STATUS_COMPLETED, RehabOperationTypeConstants.PLAN_COMPLETE, operatorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RehabCarePlanCreateRespVO copyPlan(RehabCarePlanCopyReqVO reqVO, Long operatorUserId) {
        validateClerkWriteForbidden(operatorUserId);
        RehabCarePlanDO sourcePlan = validatePlanReadable(reqVO.getId(), operatorUserId);

        RehabCarePlanDO copied = BeanUtils.toBean(sourcePlan, RehabCarePlanDO.class);
        copied.setId(null);
        copied.setPlanNo(null);
        copied.setPlanName(StrUtil.blankToDefault(reqVO.getPlanName(), sourcePlan.getPlanName() + "-复制"));
        copied.setStatus(Boolean.TRUE.equals(reqVO.getActivate()) ? RehabPlanConstants.PLAN_STATUS_ACTIVE : RehabPlanConstants.PLAN_STATUS_DRAFT);
        copied.setStartDate(LocalDate.now());
        copied.setEndDate(resolveEndDate(copied.getStartDate(), copied.getEndDate(), copied.getCycleDays()));

        validateActiveUniqueness(copied.getPatientId(), copied.getEpisodeId(), copied.getStatus(), null);
        planMapper.insert(copied);

        String planNo = generatePlanNo(copied.getId());
        planMapper.updateById(new RehabCarePlanDO().setId(copied.getId()).setPlanNo(planNo));

        List<RehabExerciseTaskDO> sourceTasks = taskMapper.selectListByPlanId(sourcePlan.getId());
        Map<Long, Long> taskIdMap = new HashMap<>();
        for (RehabExerciseTaskDO sourceTask : sourceTasks) {
            RehabExerciseTaskDO task = BeanUtils.toBean(sourceTask, RehabExerciseTaskDO.class);
            task.setId(null);
            task.setTaskNo(null);
            task.setPlanId(copied.getId());
            task.setStartDate(copied.getStartDate());
            task.setEndDate(copied.getEndDate());
            taskMapper.insert(task);
            taskMapper.updateById(new RehabExerciseTaskDO().setId(task.getId()).setTaskNo(generateTaskNo(task.getId())));
            taskIdMap.put(sourceTask.getId(), task.getId());
        }

        List<RehabTaskScheduleDO> schedules = taskScheduleMapper.selectListByPlanId(sourcePlan.getId());
        for (RehabTaskScheduleDO sourceSchedule : schedules) {
            Long newTaskId = taskIdMap.get(sourceSchedule.getTaskId());
            if (newTaskId == null) {
                continue;
            }
            RehabTaskScheduleDO schedule = BeanUtils.toBean(sourceSchedule, RehabTaskScheduleDO.class);
            schedule.setId(null);
            schedule.setPlanId(copied.getId());
            schedule.setTaskId(newTaskId);
            taskScheduleMapper.insert(schedule);
        }

        RehabCarePlanDO latest = planMapper.selectById(copied.getId());
        createOperationLog(copied.getId(), RehabOperationTypeConstants.PLAN_COPY, operatorUserId,
                sourcePlan, latest, StrUtil.blankToDefault(reqVO.getRemark(), "复制训练计划"));

        RehabCarePlanCreateRespVO respVO = new RehabCarePlanCreateRespVO();
        respVO.setId(copied.getId());
        respVO.setPlanNo(planNo);
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePlan(Long id, Long operatorUserId) {
        validateClerkWriteForbidden(operatorUserId);
        RehabCarePlanDO plan = validatePlanReadable(id, operatorUserId);
        if (CollUtil.isNotEmpty(checkinMapper.selectListByPlanId(id))
                || progressRecordMapper.selectLatestByPlanId(id) != null
                || triggerMapper.selectCount(RehabReassessmentTriggerDO::getPlanId, id) > 0) {
            throw exception(PLAN_CAN_NOT_DELETE);
        }
        planMapper.deleteById(id);
        taskMapper.delete(RehabExerciseTaskDO::getPlanId, id);
        taskScheduleMapper.delete(RehabTaskScheduleDO::getPlanId, id);
        createOperationLog(id, RehabOperationTypeConstants.ARCHIVE, operatorUserId, plan, null, "删除训练计划（逻辑删除）");
    }

    @Override
    public RehabCarePlanRespVO getPlan(Long id, Long operatorUserId) {
        RehabCarePlanDO plan = validatePlanReadable(id, operatorUserId);
        return toRespVOList(Collections.singletonList(plan)).get(0);
    }

    @Override
    public PageResult<RehabCarePlanRespVO> getPlanPage(RehabCarePlanPageReqVO reqVO, Long operatorUserId) {
        Set<Long> visiblePatientIds = dataPermissionService.getVisiblePatientIds(operatorUserId);
        if (visiblePatientIds != null && visiblePatientIds.isEmpty()) {
            return PageResult.empty();
        }
        if (reqVO.getPatientId() != null) {
            validatePatientReadable(reqVO.getPatientId(), operatorUserId);
            visiblePatientIds = Collections.singleton(reqVO.getPatientId());
        }

        PageResult<RehabCarePlanDO> pageResult = planMapper.selectPage(reqVO, visiblePatientIds);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        return new PageResult<>(toRespVOList(pageResult.getList()), pageResult.getTotal());
    }

    @Override
    public List<RehabPlanOperationLogRespVO> getOperationLogList(Long planId, Long operatorUserId) {
        RehabCarePlanDO plan = validatePlanReadable(planId, operatorUserId);
        List<RehabPlanOperationLogDO> logs = planOperationLogMapper.selectListByPlanId(plan.getId());
        if (CollUtil.isEmpty(logs)) {
            return Collections.emptyList();
        }
        Set<Long> userIds = logs.stream().map(RehabPlanOperationLogDO::getOperatorUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> userMap = userIds.isEmpty() ? Collections.emptyMap() : adminUserApi.getUserMap(userIds);
        return logs.stream().map(item -> {
            RehabPlanOperationLogRespVO vo = BeanUtils.toBean(item, RehabPlanOperationLogRespVO.class);
            AdminUserRespDTO user = userMap.get(item.getOperatorUserId());
            vo.setOperatorName(user == null ? "" : user.getNickname());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public RehabCarePlanDO validatePlanReadable(Long id, Long operatorUserId) {
        RehabCarePlanDO plan = planMapper.selectById(id);
        if (plan == null) {
            throw exception(PLAN_NOT_EXISTS);
        }
        validatePatientReadable(plan.getPatientId(), operatorUserId);
        return plan;
    }

    private void changeStatus(RehabCarePlanChangeStatusReqVO reqVO, String targetStatus, String opType, Long operatorUserId) {
        validateClerkWriteForbidden(operatorUserId);
        RehabCarePlanDO oldPlan = validatePlanReadable(reqVO.getId(), operatorUserId);
        if (!RehabPlanConstants.PLAN_STATUS_LIST.contains(targetStatus)) {
            throw exception(PLAN_STATUS_INVALID);
        }
        validateActiveUniqueness(oldPlan.getPatientId(), oldPlan.getEpisodeId(), targetStatus, oldPlan.getId());

        RehabCarePlanDO updateObj = new RehabCarePlanDO().setId(reqVO.getId()).setStatus(targetStatus);
        if (ObjUtil.equals(targetStatus, RehabPlanConstants.PLAN_STATUS_COMPLETED)) {
            updateObj.setEndDate(LocalDate.now());
        }
        planMapper.updateById(updateObj);

        RehabCarePlanDO newPlan = planMapper.selectById(reqVO.getId());
        createOperationLog(reqVO.getId(), opType, operatorUserId, oldPlan, newPlan,
                StrUtil.blankToDefault(reqVO.getRemark(), "更新计划状态为 " + targetStatus));
    }

    private void fillPlanDefaults(RehabCarePlanDO plan, Long operatorUserId) {
        if (plan.getPrimaryTherapistUserId() == null) {
            plan.setPrimaryTherapistUserId(operatorUserId);
        }
        if (StrUtil.isBlank(plan.getPlanType())) {
            plan.setPlanType(RehabPlanConstants.PLAN_TYPE_REHAB);
        }
        if (StrUtil.isBlank(plan.getStatus())) {
            plan.setStatus(RehabPlanConstants.PLAN_STATUS_DRAFT);
        }
        if (plan.getStartDate() == null) {
            plan.setStartDate(LocalDate.now());
        }
        if (plan.getCycleDays() == null || plan.getCycleDays() <= 0) {
            plan.setCycleDays(28);
        }
        if (plan.getReviewCycleDays() == null || plan.getReviewCycleDays() <= 0) {
            plan.setReviewCycleDays(14);
        }
        if (plan.getHomeProgramEnabled() == null) {
            plan.setHomeProgramEnabled(Boolean.TRUE);
        }
        if (plan.getClinicProgramEnabled() == null) {
            plan.setClinicProgramEnabled(Boolean.TRUE);
        }
        if (StrUtil.isBlank(plan.getIntensityLevel())) {
            plan.setIntensityLevel(RehabPlanConstants.INTENSITY_MEDIUM);
        }
        plan.setEndDate(resolveEndDate(plan.getStartDate(), plan.getEndDate(), plan.getCycleDays()));
    }

    private void fillPlanDateFields(RehabCarePlanDO plan) {
        if (plan.getStartDate() != null || plan.getEndDate() != null || plan.getCycleDays() != null) {
            LocalDate start = plan.getStartDate();
            LocalDate end = plan.getEndDate();
            Integer cycleDays = plan.getCycleDays();
            if (start != null || cycleDays != null) {
                plan.setEndDate(resolveEndDate(start == null ? LocalDate.now() : start, end, cycleDays));
            }
        }
    }

    private LocalDate resolveEndDate(LocalDate startDate, LocalDate endDate, Integer cycleDays) {
        if (endDate != null) {
            return endDate;
        }
        if (startDate == null || cycleDays == null || cycleDays <= 0) {
            return endDate;
        }
        return startDate.plusDays(cycleDays - 1L);
    }

    private void validateActiveUniqueness(Long patientId, Long episodeId, String status, Long selfId) {
        if (!ObjUtil.equals(status, RehabPlanConstants.PLAN_STATUS_ACTIVE)) {
            return;
        }
        RehabCarePlanDO active = planMapper.selectActiveByPatientEpisode(patientId, episodeId);
        if (active != null && !ObjUtil.equals(active.getId(), selfId)) {
            throw exception(PLAN_ACTIVE_ALREADY_EXISTS);
        }
    }

    private RehabPatientDO validatePatientExists(Long patientId) {
        RehabPatientDO patient = patientMapper.selectById(patientId);
        if (patient == null) {
            throw exception(PATIENT_NOT_EXISTS);
        }
        return patient;
    }

    private RehabEpisodeDO validateEpisodeExists(Long episodeId) {
        RehabEpisodeDO episode = episodeMapper.selectById(episodeId);
        if (episode == null) {
            throw exception(EPISODE_NOT_EXISTS);
        }
        return episode;
    }

    private void validateEpisodeBelongsToPatient(RehabEpisodeDO episode, Long patientId) {
        if (!ObjUtil.equals(episode.getPatientId(), patientId)) {
            throw exception(PLAN_PATIENT_EPISODE_MISMATCH);
        }
    }

    private void validateAssessmentBelongs(Long assessmentId, Long patientId, Long episodeId) {
        RehabAssessmentRecordDO assessment = assessmentRecordMapper.selectById(assessmentId);
        if (assessment == null) {
            throw exception(ASSESSMENT_NOT_EXISTS);
        }
        if (!ObjUtil.equals(assessment.getPatientId(), patientId) || !ObjUtil.equals(assessment.getEpisodeId(), episodeId)) {
            throw exception(ASSESSMENT_PATIENT_EPISODE_MISMATCH);
        }
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

    private String generatePlanNo(Long id) {
        String datePart = PLAN_NO_DATE_FORMATTER.format(LocalDateTime.now());
        return "PLN" + datePart + String.format("%04d", id % 10000);
    }

    private String generateTaskNo(Long id) {
        String datePart = PLAN_NO_DATE_FORMATTER.format(LocalDateTime.now());
        return "TSK" + datePart + String.format("%04d", id % 10000);
    }

    private void createOperationLog(Long planId, String operationType, Long operatorUserId,
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
        if (auditLogService != null) {
            auditLogService.createAuditLog("plan", planId, operationType, operatorUserId,
                    resolveRole(operatorUserId), beforeData, afterData, "success", remark);
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

    private List<RehabCarePlanRespVO> toRespVOList(List<RehabCarePlanDO> plans) {
        Set<Long> patientIds = plans.stream().map(RehabCarePlanDO::getPatientId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> episodeIds = plans.stream().map(RehabCarePlanDO::getEpisodeId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> assessmentIds = plans.stream().map(RehabCarePlanDO::getSourceAssessmentId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> therapistIds = plans.stream().map(RehabCarePlanDO::getPrimaryTherapistUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, RehabPatientDO> patientMap = patientIds.isEmpty() ? Collections.emptyMap() : patientMapper.selectBatchIds(patientIds)
                .stream().collect(Collectors.toMap(RehabPatientDO::getId, item -> item, (a, b) -> a));
        Map<Long, RehabEpisodeDO> episodeMap = episodeIds.isEmpty() ? Collections.emptyMap() : episodeMapper.selectBatchIds(episodeIds)
                .stream().collect(Collectors.toMap(RehabEpisodeDO::getId, item -> item, (a, b) -> a));
        Map<Long, RehabAssessmentRecordDO> assessmentMap = assessmentIds.isEmpty() ? Collections.emptyMap() : assessmentRecordMapper.selectBatchIds(assessmentIds)
                .stream().collect(Collectors.toMap(RehabAssessmentRecordDO::getId, item -> item, (a, b) -> a));
        Map<Long, AdminUserRespDTO> therapistMap = therapistIds.isEmpty() ? Collections.emptyMap() : adminUserApi.getUserMap(therapistIds);

        Map<Long, RehabProgressRecordDO> latestProgressMap = new HashMap<>();
        for (RehabCarePlanDO plan : plans) {
            if (plan.getId() == null) {
                continue;
            }
            RehabProgressRecordDO progress = progressRecordMapper.selectLatestByPlanId(plan.getId());
            if (progress != null) {
                latestProgressMap.put(plan.getId(), progress);
            }
        }

        return plans.stream().map(plan -> {
            RehabCarePlanRespVO vo = BeanUtils.toBean(plan, RehabCarePlanRespVO.class);
            RehabPatientDO patient = patientMap.get(plan.getPatientId());
            if (patient != null) {
                vo.setPatientNo(patient.getPatientNo());
                vo.setPatientName(patient.getName());
            }
            RehabEpisodeDO episode = episodeMap.get(plan.getEpisodeId());
            if (episode != null) {
                vo.setEpisodeNo(episode.getEpisodeNo());
            }
            RehabAssessmentRecordDO assessment = assessmentMap.get(plan.getSourceAssessmentId());
            if (assessment != null) {
                vo.setAssessmentNo(assessment.getAssessmentNo());
            }
            AdminUserRespDTO therapist = therapistMap.get(plan.getPrimaryTherapistUserId());
            vo.setPrimaryTherapistName(therapist == null ? "" : therapist.getNickname());

            RehabProgressRecordDO latest = latestProgressMap.get(plan.getId());
            if (latest != null) {
                vo.setLatestProgressSummary(StrUtil.format("完成率:{}%，疼痛趋势:{}，状态:{}",
                        latest.getCompletionRate() == null ? "-" : latest.getCompletionRate(),
                        StrUtil.blankToDefault(latest.getPainTrend(), "-"),
                        StrUtil.blankToDefault(latest.getProgressStatus(), "-")));
            }
            return vo;
        }).collect(Collectors.toList());
    }

}
