package cn.iocoder.yudao.module.rehab.service.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.rehab.controller.admin.task.vo.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.log.RehabPlanOperationLogDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.task.RehabExerciseTaskDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.log.RehabPlanOperationLogMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.task.RehabExerciseTaskMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabOperationTypeConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabPlanConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
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
public class RehabExerciseTaskServiceImpl implements RehabExerciseTaskService {

    private static final DateTimeFormatter TASK_NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

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
    private AdminUserApi adminUserApi;

    @Override
    public PageResult<RehabExerciseTaskRespVO> getTaskPage(RehabExerciseTaskPageReqVO reqVO, Long operatorUserId) {
        Set<Long> visiblePatientIds = dataPermissionService.getVisiblePatientIds(operatorUserId);
        if (visiblePatientIds != null && visiblePatientIds.isEmpty()) {
            return PageResult.empty();
        }
        if (reqVO.getPatientId() != null) {
            validatePatientReadable(reqVO.getPatientId(), operatorUserId);
            visiblePatientIds = Collections.singleton(reqVO.getPatientId());
        }
        if (reqVO.getPlanId() != null) {
            RehabCarePlanDO plan = validatePlanExists(reqVO.getPlanId());
            validatePatientReadable(plan.getPatientId(), operatorUserId);
        }

        PageResult<RehabExerciseTaskDO> pageResult = taskMapper.selectPage(reqVO, visiblePatientIds);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        return new PageResult<>(toRespVOList(pageResult.getList()), pageResult.getTotal());
    }

    @Override
    public RehabExerciseTaskRespVO getTask(Long id, Long operatorUserId) {
        RehabExerciseTaskDO task = validateTaskReadable(id, operatorUserId);
        return toRespVOList(Collections.singletonList(task)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(RehabExerciseTaskCreateReqVO reqVO, Long operatorUserId) {
        validateClerkWriteForbidden(operatorUserId);
        RehabCarePlanDO plan = validatePlanReadable(reqVO.getPlanId(), operatorUserId);
        validateTaskBelongToPlan(reqVO.getPatientId(), reqVO.getEpisodeId(), plan);

        RehabExerciseTaskDO task = BeanUtils.toBean(reqVO, RehabExerciseTaskDO.class);
        if (task.getSortOrder() == null) {
            task.setSortOrder(taskMapper.selectListByPlanId(reqVO.getPlanId()).size() + 1);
        }
        if (StrUtil.isBlank(task.getStatus())) {
            task.setStatus(RehabPlanConstants.TASK_STATUS_ACTIVE);
        }
        if (task.getStartDate() == null) {
            task.setStartDate(ObjUtil.defaultIfNull(plan.getStartDate(), LocalDate.now()));
        }
        if (task.getEndDate() == null) {
            task.setEndDate(plan.getEndDate());
        }
        taskMapper.insert(task);
        taskMapper.updateById(new RehabExerciseTaskDO().setId(task.getId()).setTaskNo(generateTaskNo(task.getId())));

        RehabExerciseTaskDO latest = taskMapper.selectById(task.getId());
        createPlanLog(plan.getId(), RehabOperationTypeConstants.PLAN_TASK_ADD, operatorUserId,
                null, latest, "新增任务");
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTask(RehabExerciseTaskUpdateReqVO reqVO, Long operatorUserId) {
        validateClerkWriteForbidden(operatorUserId);
        RehabExerciseTaskDO oldTask = validateTaskReadable(reqVO.getId(), operatorUserId);
        RehabCarePlanDO plan = validatePlanReadable(reqVO.getPlanId(), operatorUserId);
        validateTaskBelongToPlan(reqVO.getPatientId(), reqVO.getEpisodeId(), plan);
        if (!ObjUtil.equals(oldTask.getPlanId(), plan.getId())) {
            throw exception(TASK_PLAN_MISMATCH);
        }

        RehabExerciseTaskDO updateObj = BeanUtils.toBean(reqVO, RehabExerciseTaskDO.class);
        if (StrUtil.isBlank(updateObj.getStatus())) {
            updateObj.setStatus(oldTask.getStatus());
        }
        updateObj.clean();
        taskMapper.updateById(updateObj);

        RehabExerciseTaskDO newTask = taskMapper.selectById(reqVO.getId());
        createPlanLog(plan.getId(), RehabOperationTypeConstants.PLAN_TASK_EDIT, operatorUserId,
                oldTask, newTask, "更新任务");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sortTasks(RehabExerciseTaskSortReqVO reqVO, Long operatorUserId) {
        validateClerkWriteForbidden(operatorUserId);
        RehabCarePlanDO plan = validatePlanReadable(reqVO.getPlanId(), operatorUserId);
        for (RehabExerciseTaskSortReqVO.Item item : reqVO.getItems()) {
            RehabExerciseTaskDO task = validateTaskReadable(item.getId(), operatorUserId);
            if (!ObjUtil.equals(task.getPlanId(), plan.getId())) {
                throw exception(TASK_PLAN_MISMATCH);
            }
            taskMapper.updateById(new RehabExerciseTaskDO().setId(task.getId()).setSortOrder(item.getSortOrder()));
        }
        createPlanLog(plan.getId(), RehabOperationTypeConstants.PLAN_TASK_EDIT, operatorUserId,
                null, reqVO.getItems(), "调整任务排序");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableTask(RehabExerciseTaskToggleReqVO reqVO, Long operatorUserId) {
        toggleTaskStatus(reqVO, operatorUserId, RehabPlanConstants.TASK_STATUS_DISABLED, "停用任务");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableTask(RehabExerciseTaskToggleReqVO reqVO, Long operatorUserId) {
        toggleTaskStatus(reqVO, operatorUserId, RehabPlanConstants.TASK_STATUS_ACTIVE, "启用任务");
    }

    @Override
    public List<RehabExerciseTaskRespVO> getTaskListByPlan(Long planId, Long operatorUserId) {
        RehabCarePlanDO plan = validatePlanReadable(planId, operatorUserId);
        return toRespVOList(taskMapper.selectListByPlanId(plan.getId()));
    }

    @Override
    public RehabExerciseTaskDO validateTaskReadable(Long taskId, Long operatorUserId) {
        RehabExerciseTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw exception(TASK_NOT_EXISTS);
        }
        validatePatientReadable(task.getPatientId(), operatorUserId);
        return task;
    }

    private void toggleTaskStatus(RehabExerciseTaskToggleReqVO reqVO, Long operatorUserId, String targetStatus, String action) {
        validateClerkWriteForbidden(operatorUserId);
        RehabExerciseTaskDO oldTask = validateTaskReadable(reqVO.getId(), operatorUserId);
        taskMapper.updateById(new RehabExerciseTaskDO().setId(reqVO.getId()).setStatus(targetStatus));
        RehabExerciseTaskDO newTask = taskMapper.selectById(reqVO.getId());
        createPlanLog(oldTask.getPlanId(), RehabOperationTypeConstants.PLAN_TASK_EDIT, operatorUserId,
                oldTask, newTask, StrUtil.blankToDefault(reqVO.getRemark(), action));
    }

    private RehabCarePlanDO validatePlanReadable(Long planId, Long operatorUserId) {
        RehabCarePlanDO plan = validatePlanExists(planId);
        validatePatientReadable(plan.getPatientId(), operatorUserId);
        return plan;
    }

    private RehabCarePlanDO validatePlanExists(Long planId) {
        RehabCarePlanDO plan = planMapper.selectById(planId);
        if (plan == null) {
            throw exception(PLAN_NOT_EXISTS);
        }
        return plan;
    }

    private void validateTaskBelongToPlan(Long patientId, Long episodeId, RehabCarePlanDO plan) {
        if (!ObjUtil.equals(plan.getPatientId(), patientId) || !ObjUtil.equals(plan.getEpisodeId(), episodeId)) {
            throw exception(TASK_PLAN_MISMATCH);
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

    private String generateTaskNo(Long id) {
        String datePart = TASK_NO_DATE_FORMATTER.format(LocalDateTime.now());
        return "TSK" + datePart + String.format("%04d", id % 10000);
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

    private List<RehabExerciseTaskRespVO> toRespVOList(List<RehabExerciseTaskDO> tasks) {
        if (CollUtil.isEmpty(tasks)) {
            return Collections.emptyList();
        }
        Set<Long> planIds = tasks.stream().map(RehabExerciseTaskDO::getPlanId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> patientIds = tasks.stream().map(RehabExerciseTaskDO::getPatientId).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, RehabCarePlanDO> planMap = planIds.isEmpty() ? Collections.emptyMap() : planMapper.selectBatchIds(planIds)
                .stream().collect(Collectors.toMap(RehabCarePlanDO::getId, item -> item, (a, b) -> a));
        Map<Long, RehabPatientDO> patientMap = patientIds.isEmpty() ? Collections.emptyMap() : patientMapper.selectBatchIds(patientIds)
                .stream().collect(Collectors.toMap(RehabPatientDO::getId, item -> item, (a, b) -> a));

        return tasks.stream().map(task -> {
            RehabExerciseTaskRespVO vo = BeanUtils.toBean(task, RehabExerciseTaskRespVO.class);
            RehabCarePlanDO plan = planMap.get(task.getPlanId());
            if (plan != null) {
                vo.setPlanNo(plan.getPlanNo());
            }
            RehabPatientDO patient = patientMap.get(task.getPatientId());
            if (patient != null) {
                vo.setPatientNo(patient.getPatientNo());
                vo.setPatientName(patient.getName());
            }
            return vo;
        }).collect(Collectors.toList());
    }

}
