package cn.iocoder.yudao.module.rehab.service.checkin;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.checkin.RehabDailyCheckinDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.checkin.RehabTaskExecutionDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.log.RehabPlanOperationLogDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.progress.RehabProgressRecordDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.task.RehabExerciseTaskDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabDailyCheckinMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabTaskExecutionMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.log.RehabPlanOperationLogMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.task.RehabExerciseTaskMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabOperationTypeConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabPlanConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.progress.RehabProgressRecordService;
import cn.iocoder.yudao.module.rehab.service.trigger.RehabReassessmentTriggerService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

@Service
@Validated
public class RehabDailyCheckinServiceImpl implements RehabDailyCheckinService {

    @Resource
    private RehabDailyCheckinMapper checkinMapper;
    @Resource
    private RehabTaskExecutionMapper taskExecutionMapper;
    @Resource
    private RehabExerciseTaskMapper taskMapper;
    @Resource
    private RehabCarePlanMapper planMapper;
    @Resource
    private RehabPatientMapper patientMapper;
    @Resource
    private RehabPlanOperationLogMapper planOperationLogMapper;
    @Resource
    private RehabDataPermissionService dataPermissionService;
    @Resource
    private RehabProgressRecordService progressRecordService;
    @Resource
    private RehabReassessmentTriggerService triggerService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public PageResult<RehabDailyCheckinRespVO> getCheckinPage(RehabDailyCheckinPageReqVO reqVO, Long operatorUserId) {
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

        PageResult<RehabDailyCheckinDO> pageResult = checkinMapper.selectPage(reqVO, visiblePatientIds);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        return new PageResult<>(toRespVOList(pageResult.getList()), pageResult.getTotal());
    }

    @Override
    public RehabDailyCheckinRespVO getCheckin(Long id, Long operatorUserId) {
        RehabDailyCheckinDO checkin = validateCheckinReadable(id, operatorUserId);
        return toRespVOList(Collections.singletonList(checkin)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCheckin(RehabDailyCheckinCreateReqVO reqVO, Long operatorUserId, boolean manual) {
        if (!manual && dataPermissionService.isClerk(operatorUserId)) {
            throw exception(CLERK_WRITE_FORBIDDEN);
        }
        RehabCarePlanDO plan = validatePlanReadable(reqVO.getPlanId(), operatorUserId);
        if (!ObjUtil.equals(plan.getPatientId(), reqVO.getPatientId()) || !ObjUtil.equals(plan.getEpisodeId(), reqVO.getEpisodeId())) {
            throw exception(TASK_PLAN_MISMATCH);
        }
        if (CollUtil.isEmpty(reqVO.getTaskExecutions())) {
            throw exception(CHECKIN_TASK_EXECUTION_EMPTY);
        }

        Set<Long> taskIds = reqVO.getTaskExecutions().stream().map(RehabTaskExecutionItemVO::getTaskId).collect(Collectors.toSet());
        List<RehabExerciseTaskDO> tasks = taskMapper.selectListByIds(taskIds);
        Map<Long, RehabExerciseTaskDO> taskMap = tasks.stream().collect(Collectors.toMap(RehabExerciseTaskDO::getId, item -> item, (a, b) -> a));
        for (RehabTaskExecutionItemVO item : reqVO.getTaskExecutions()) {
            RehabExerciseTaskDO task = taskMap.get(item.getTaskId());
            if (task == null || !ObjUtil.equals(task.getPlanId(), plan.getId())) {
                throw exception(CHECKIN_TASK_NOT_BELONG_TO_PLAN);
            }
            validatePatientReadable(task.getPatientId(), operatorUserId);
        }

        RehabDailyCheckinDO checkin = BeanUtils.toBean(reqVO, RehabDailyCheckinDO.class);
        checkin.setSubmittedByUserId(operatorUserId);
        if (StrUtil.isBlank(checkin.getSubmitRoleType())) {
            checkin.setSubmitRoleType(manual ? RehabPlanConstants.CHECKIN_ROLE_THERAPIST : RehabPlanConstants.CHECKIN_ROLE_PATIENT);
        }
        if (dataPermissionService.isClerk(operatorUserId)) {
            checkin.setSubmitRoleType(RehabPlanConstants.CHECKIN_ROLE_CLERK);
        }
        if (checkin.getCheckinDate() == null) {
            checkin.setCheckinDate(LocalDate.now());
        }
        checkinMapper.insert(checkin);

        BigDecimal completedScore = BigDecimal.ZERO;
        for (RehabTaskExecutionItemVO item : reqVO.getTaskExecutions()) {
            RehabTaskExecutionDO execution = BeanUtils.toBean(item, RehabTaskExecutionDO.class);
            execution.setCheckinId(checkin.getId());
            taskExecutionMapper.insert(execution);
            completedScore = completedScore.add(scoreCompletion(item.getCompletionStatus()));
        }

        if (checkin.getOverallCompletionRate() == null) {
            BigDecimal completion = BigDecimal.ZERO;
            if (!reqVO.getTaskExecutions().isEmpty()) {
                completion = completedScore.multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(reqVO.getTaskExecutions().size()), 2, RoundingMode.HALF_UP);
            }
            checkinMapper.updateById(new RehabDailyCheckinDO().setId(checkin.getId()).setOverallCompletionRate(completion));
        }

        createPlanLog(plan.getId(), RehabOperationTypeConstants.CHECKIN_CREATE, operatorUserId,
                null, checkinMapper.selectById(checkin.getId()), manual ? "后台代录打卡" : "提交打卡");

        RehabProgressRecordDO latestProgress = progressRecordService.recalculateByPlan(plan.getId(), checkin.getCheckinDate(), operatorUserId,
                "打卡后自动重算进度");
        triggerService.evaluateByPlan(plan.getId(), latestProgress, operatorUserId);

        return checkin.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAttendance(RehabTrainingAttendanceCreateReqVO reqVO, Long operatorUserId) {
        RehabCarePlanDO plan = validatePlanReadable(reqVO.getPlanId(), operatorUserId);
        if (!ObjUtil.equals(plan.getPatientId(), reqVO.getPatientId())) {
            throw exception(TASK_PLAN_MISMATCH);
        }

        RehabDailyCheckinDO attendance = RehabDailyCheckinDO.builder()
                .patientId(plan.getPatientId())
                .episodeId(plan.getEpisodeId())
                .planId(plan.getId())
                .checkinDate(reqVO.getTrainingDate())
                .submittedByUserId(operatorUserId)
                .submitRoleType(dataPermissionService.isClerk(operatorUserId)
                        ? RehabPlanConstants.CHECKIN_ROLE_CLERK : RehabPlanConstants.CHECKIN_ROLE_THERAPIST)
                .overallComment(StrUtil.trim(reqVO.getNote()))
                .build();
        checkinMapper.insert(attendance);

        createPlanLog(plan.getId(), RehabOperationTypeConstants.CHECKIN_CREATE, operatorUserId,
                null, attendance, "登记患者课程签到（不计入训练任务完成率）");
        return attendance.getId();
    }

    @Override
    public List<RehabTaskExecutionRespVO> getTaskExecutionList(Long checkinId, Long operatorUserId) {
        RehabDailyCheckinDO checkin = validateCheckinReadable(checkinId, operatorUserId);
        List<RehabTaskExecutionDO> executions = taskExecutionMapper.selectListByCheckinId(checkin.getId());
        if (CollUtil.isEmpty(executions)) {
            return Collections.emptyList();
        }
        Set<Long> taskIds = executions.stream().map(RehabTaskExecutionDO::getTaskId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, RehabExerciseTaskDO> taskMap = taskIds.isEmpty() ? Collections.emptyMap() : taskMapper.selectBatchIds(taskIds).stream()
                .collect(Collectors.toMap(RehabExerciseTaskDO::getId, item -> item, (a, b) -> a));
        return executions.stream().map(item -> {
            RehabTaskExecutionRespVO vo = BeanUtils.toBean(item, RehabTaskExecutionRespVO.class);
            RehabExerciseTaskDO task = taskMap.get(item.getTaskId());
            if (task != null) {
                vo.setTaskNo(task.getTaskNo());
                vo.setTaskName(task.getTaskName());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private RehabDailyCheckinDO validateCheckinReadable(Long checkinId, Long operatorUserId) {
        RehabDailyCheckinDO checkin = checkinMapper.selectById(checkinId);
        if (checkin == null) {
            throw exception(CHECKIN_NOT_EXISTS);
        }
        validatePatientReadable(checkin.getPatientId(), operatorUserId);
        return checkin;
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

    private BigDecimal scoreCompletion(String completionStatus) {
        if (ObjUtil.equals(completionStatus, RehabPlanConstants.COMPLETION_COMPLETED)) {
            return BigDecimal.ONE;
        }
        if (ObjUtil.equals(completionStatus, RehabPlanConstants.COMPLETION_PARTIAL)) {
            return new BigDecimal("0.5");
        }
        return BigDecimal.ZERO;
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

    private List<RehabDailyCheckinRespVO> toRespVOList(List<RehabDailyCheckinDO> checkins) {
        Set<Long> patientIds = checkins.stream().map(RehabDailyCheckinDO::getPatientId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> planIds = checkins.stream().map(RehabDailyCheckinDO::getPlanId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> userIds = checkins.stream().map(RehabDailyCheckinDO::getSubmittedByUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> checkinIdsWithTaskExecutions = taskExecutionMapper.selectListByCheckinIds(
                        checkins.stream().map(RehabDailyCheckinDO::getId).collect(Collectors.toSet()))
                .stream().map(RehabTaskExecutionDO::getCheckinId).collect(Collectors.toSet());

        Map<Long, RehabPatientDO> patientMap = patientIds.isEmpty() ? Collections.emptyMap() : patientMapper.selectBatchIds(patientIds)
                .stream().collect(Collectors.toMap(RehabPatientDO::getId, item -> item, (a, b) -> a));
        Map<Long, RehabCarePlanDO> planMap = planIds.isEmpty() ? Collections.emptyMap() : planMapper.selectBatchIds(planIds)
                .stream().collect(Collectors.toMap(RehabCarePlanDO::getId, item -> item, (a, b) -> a));
        Map<Long, AdminUserRespDTO> userMap = userIds.isEmpty() ? Collections.emptyMap() : adminUserApi.getUserMap(userIds);

        return checkins.stream().map(item -> {
            RehabDailyCheckinRespVO vo = BeanUtils.toBean(item, RehabDailyCheckinRespVO.class);
            RehabPatientDO patient = patientMap.get(item.getPatientId());
            if (patient != null) {
                vo.setPatientNo(patient.getPatientNo());
                vo.setPatientName(patient.getName());
            }
            RehabCarePlanDO plan = planMap.get(item.getPlanId());
            if (plan != null) {
                vo.setPlanNo(plan.getPlanNo());
            }
            AdminUserRespDTO submitter = userMap.get(item.getSubmittedByUserId());
            vo.setSubmitterName(submitter == null ? "" : submitter.getNickname());
            vo.setCourseAttendance(!checkinIdsWithTaskExecutions.contains(item.getId()));
            return vo;
        }).collect(Collectors.toList());
    }

}
