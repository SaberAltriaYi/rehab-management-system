package cn.iocoder.yudao.module.rehab.service.trigger;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.rehab.controller.admin.trigger.vo.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.log.RehabPlanOperationLogDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.progress.RehabProgressRecordDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.trigger.RehabReassessmentTriggerDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.log.RehabPlanOperationLogMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.progress.RehabProgressRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.trigger.RehabReassessmentTriggerMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabOperationTypeConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabPlanConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

@Service
@Validated
public class RehabReassessmentTriggerServiceImpl implements RehabReassessmentTriggerService {

    @Resource
    private RehabReassessmentTriggerMapper triggerMapper;
    @Resource
    private RehabCarePlanMapper planMapper;
    @Resource
    private RehabPatientMapper patientMapper;
    @Resource
    private RehabProgressRecordMapper progressRecordMapper;
    @Resource
    private RehabPlanOperationLogMapper planOperationLogMapper;
    @Resource
    private RehabDataPermissionService dataPermissionService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public PageResult<RehabReassessmentTriggerRespVO> getTriggerPage(RehabReassessmentTriggerPageReqVO reqVO, Long operatorUserId) {
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

        PageResult<RehabReassessmentTriggerDO> pageResult = triggerMapper.selectPage(reqVO, visiblePatientIds);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        return new PageResult<>(toRespVOList(pageResult.getList()), pageResult.getTotal());
    }

    @Override
    public RehabReassessmentTriggerRespVO getTrigger(Long id, Long operatorUserId) {
        RehabReassessmentTriggerDO trigger = validateTriggerReadable(id, operatorUserId);
        return toRespVOList(Collections.singletonList(trigger)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTrigger(RehabReassessmentTriggerCreateReqVO reqVO, Long operatorUserId) {
        validateClerkWriteForbidden(operatorUserId);
        RehabCarePlanDO plan = validatePlanReadable(reqVO.getPlanId(), operatorUserId);
        if (!ObjUtil.equals(plan.getPatientId(), reqVO.getPatientId()) || !ObjUtil.equals(plan.getEpisodeId(), reqVO.getEpisodeId())) {
            throw exception(TASK_PLAN_MISMATCH);
        }
        RehabReassessmentTriggerDO trigger = upsertPendingTrigger(plan,
                reqVO.getTriggerType(),
                StrUtil.blankToDefault(reqVO.getTriggerLevel(), RehabPlanConstants.TRIGGER_LEVEL_MEDIUM),
                StrUtil.blankToDefault(reqVO.getTriggerMessage(), "人工创建复评触发"),
                StrUtil.blankToDefault(reqVO.getSuggestedAction(), "建议评估复核"),
                reqVO.getDueDate());
        createPlanLog(plan.getId(), RehabOperationTypeConstants.PLAN_TRIGGER_REASSESSMENT, operatorUserId,
                null, trigger, "创建复评触发");
        return trigger.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acknowledge(RehabReassessmentTriggerHandleReqVO reqVO, Long operatorUserId) {
        validateClerkWriteForbidden(operatorUserId);
        RehabReassessmentTriggerDO trigger = validateTriggerReadable(reqVO.getId(), operatorUserId);
        if (!ObjUtil.equals(trigger.getTriggerStatus(), RehabPlanConstants.TRIGGER_STATUS_PENDING)) {
            throw exception(TRIGGER_CAN_NOT_HANDLE);
        }
        RehabReassessmentTriggerDO updateObj = new RehabReassessmentTriggerDO().setId(trigger.getId())
                .setTriggerStatus(RehabPlanConstants.TRIGGER_STATUS_ACKNOWLEDGED)
                .setAcknowledgedBy(operatorUserId)
                .setAcknowledgedTime(LocalDateTime.now());
        triggerMapper.updateById(updateObj);
        createPlanLog(trigger.getPlanId(), RehabOperationTypeConstants.TRIGGER_HANDLE, operatorUserId,
                trigger, triggerMapper.selectById(trigger.getId()), StrUtil.blankToDefault(reqVO.getRemark(), "已确认触发"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RehabTriggerConvertRespVO convertToReassessment(RehabReassessmentTriggerHandleReqVO reqVO, Long operatorUserId) {
        validateClerkWriteForbidden(operatorUserId);
        RehabReassessmentTriggerDO trigger = validateTriggerReadable(reqVO.getId(), operatorUserId);
        if (!ObjUtil.equals(trigger.getTriggerStatus(), RehabPlanConstants.TRIGGER_STATUS_PENDING)
                && !ObjUtil.equals(trigger.getTriggerStatus(), RehabPlanConstants.TRIGGER_STATUS_ACKNOWLEDGED)) {
            throw exception(TRIGGER_CAN_NOT_HANDLE);
        }
        RehabReassessmentTriggerDO updateObj = new RehabReassessmentTriggerDO().setId(trigger.getId())
                .setTriggerStatus(RehabPlanConstants.TRIGGER_STATUS_CONVERTED)
                .setAcknowledgedBy(operatorUserId)
                .setAcknowledgedTime(LocalDateTime.now());
        triggerMapper.updateById(updateObj);

        createPlanLog(trigger.getPlanId(), RehabOperationTypeConstants.PLAN_TRIGGER_REASSESSMENT, operatorUserId,
                trigger, triggerMapper.selectById(trigger.getId()), StrUtil.blankToDefault(reqVO.getRemark(), "转为复评任务"));

        RehabTriggerConvertRespVO respVO = new RehabTriggerConvertRespVO();
        respVO.setTriggerId(trigger.getId());
        respVO.setPatientId(trigger.getPatientId());
        respVO.setEpisodeId(trigger.getEpisodeId());
        respVO.setPlanId(trigger.getPlanId());
        respVO.setReassessmentEntry(StrUtil.format("/rehab/assessment/create?patientId={}&episodeId={}", trigger.getPatientId(), trigger.getEpisodeId()));
        respVO.setMessage("已转为复评入口，请由治疗师发起正式复评");
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dismiss(RehabReassessmentTriggerHandleReqVO reqVO, Long operatorUserId) {
        validateClerkWriteForbidden(operatorUserId);
        RehabReassessmentTriggerDO trigger = validateTriggerReadable(reqVO.getId(), operatorUserId);
        if (ObjUtil.equals(trigger.getTriggerStatus(), RehabPlanConstants.TRIGGER_STATUS_CONVERTED)
                || ObjUtil.equals(trigger.getTriggerStatus(), RehabPlanConstants.TRIGGER_STATUS_DISMISSED)) {
            throw exception(TRIGGER_CAN_NOT_HANDLE);
        }
        RehabReassessmentTriggerDO updateObj = new RehabReassessmentTriggerDO().setId(trigger.getId())
                .setTriggerStatus(RehabPlanConstants.TRIGGER_STATUS_DISMISSED)
                .setAcknowledgedBy(operatorUserId)
                .setAcknowledgedTime(LocalDateTime.now());
        triggerMapper.updateById(updateObj);

        createPlanLog(trigger.getPlanId(), RehabOperationTypeConstants.TRIGGER_HANDLE, operatorUserId,
                trigger, triggerMapper.selectById(trigger.getId()), StrUtil.blankToDefault(reqVO.getRemark(), "忽略触发"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void evaluateByPlan(Long planId, RehabProgressRecordDO latestProgress, Long operatorUserId) {
        RehabCarePlanDO plan = validatePlanReadable(planId, operatorUserId);

        List<RehabProgressRecordDO> progresses = progressRecordMapper.selectListByPlanId(planId);
        if (latestProgress == null && CollUtil.isNotEmpty(progresses)) {
            latestProgress = progresses.get(0);
        }
        RehabProgressRecordDO prevProgress = progresses.size() > 1 ? progresses.get(1) : null;

        LocalDate today = LocalDate.now();
        Integer reviewCycle = ObjUtil.defaultIfNull(plan.getReviewCycleDays(), 14);

        if (plan.getStartDate() != null && ChronoUnit.DAYS.between(plan.getStartDate(), today) >= reviewCycle) {
            upsertPendingTrigger(plan,
                    RehabPlanConstants.TRIGGER_TIME_DUE,
                    RehabPlanConstants.TRIGGER_LEVEL_MEDIUM,
                    "计划已达到复评周期，建议安排复测",
                    "建议发起复评并更新干预策略",
                    today.plusDays(2));
        }

        if (plan.getEndDate() != null && (today.isEqual(plan.getEndDate()) || today.isAfter(plan.getEndDate()))) {
            upsertPendingTrigger(plan,
                    RehabPlanConstants.TRIGGER_STAGE_END,
                    RehabPlanConstants.TRIGGER_LEVEL_MEDIUM,
                    "当前计划周期已结束",
                    "建议进行阶段复评",
                    today.plusDays(1));
        }

        if (latestProgress != null && prevProgress != null
                && latestProgress.getCompletionRate() != null && prevProgress.getCompletionRate() != null
                && latestProgress.getCompletionRate().compareTo(new BigDecimal("60")) < 0
                && prevProgress.getCompletionRate().compareTo(new BigDecimal("60")) < 0) {
            upsertPendingTrigger(plan,
                    RehabPlanConstants.TRIGGER_LOW_ADHERENCE,
                    RehabPlanConstants.TRIGGER_LEVEL_HIGH,
                    "依从性持续偏低（连续两周期 < 60%）",
                    "建议优先人工复核执行障碍并调整任务剂量",
                    today.plusDays(1));
        }

        if (latestProgress != null && prevProgress != null
                && latestProgress.getAveragePainScore() != null && prevProgress.getAveragePainScore() != null
                && latestProgress.getAveragePainScore().subtract(prevProgress.getAveragePainScore())
                .compareTo(new BigDecimal("2")) >= 0) {
            upsertPendingTrigger(plan,
                    RehabPlanConstants.TRIGGER_PAIN_UPGRADE,
                    RehabPlanConstants.TRIGGER_LEVEL_HIGH,
                    "疼痛评分较上一周期上升 >= 2",
                    "建议降阶并优先人工复核，必要时考虑进一步医学评估",
                    today.plusDays(1));
        }

        if (latestProgress != null
                && plan.getEndDate() != null
                && (today.isEqual(plan.getEndDate()) || today.isAfter(plan.getEndDate()))) {
            if ((latestProgress.getCompletionRate() != null && latestProgress.getCompletionRate().compareTo(new BigDecimal("60")) < 0)
                    || ObjUtil.equals(latestProgress.getProgressStatus(), RehabPlanConstants.PROGRESS_WORSENED)) {
                upsertPendingTrigger(plan,
                        RehabPlanConstants.TRIGGER_TARGET_NOT_MET,
                        RehabPlanConstants.TRIGGER_LEVEL_HIGH,
                        "计划目标未达成或表现恶化",
                        "建议立即复评并调整方案",
                        today.plusDays(1));
            }
            if (latestProgress.getCompletionRate() != null
                    && latestProgress.getCompletionRate().compareTo(new BigDecimal("80")) >= 0
                    && (ObjUtil.equals(latestProgress.getProgressStatus(), RehabPlanConstants.PROGRESS_IMPROVED)
                    || ObjUtil.equals(latestProgress.getProgressStatus(), RehabPlanConstants.PROGRESS_SLIGHTLY_IMPROVED))) {
                upsertPendingTrigger(plan,
                        RehabPlanConstants.TRIGGER_TARGET_MET,
                        RehabPlanConstants.TRIGGER_LEVEL_LOW,
                        "阶段目标基本达成",
                        "建议发起复评并考虑进入下一阶段",
                        today.plusDays(3));
            }
        }
    }

    private RehabReassessmentTriggerDO upsertPendingTrigger(RehabCarePlanDO plan, String triggerType,
                                                            String triggerLevel, String triggerMessage,
                                                            String suggestedAction, LocalDate dueDate) {
        if (!RehabPlanConstants.TRIGGER_TYPES.contains(triggerType)) {
            throw exception(TRIGGER_STATUS_INVALID);
        }
        RehabReassessmentTriggerDO pending = triggerMapper.selectPendingByPlanAndType(plan.getId(), triggerType);
        RehabReassessmentTriggerDO trigger = pending == null ? new RehabReassessmentTriggerDO() : pending;
        trigger.setPatientId(plan.getPatientId());
        trigger.setEpisodeId(plan.getEpisodeId());
        trigger.setPlanId(plan.getId());
        trigger.setTriggerType(triggerType);
        trigger.setTriggerLevel(StrUtil.blankToDefault(triggerLevel, RehabPlanConstants.TRIGGER_LEVEL_MEDIUM));
        trigger.setTriggerStatus(RehabPlanConstants.TRIGGER_STATUS_PENDING);
        trigger.setTriggerMessage(triggerMessage);
        trigger.setSuggestedAction(suggestedAction);
        trigger.setDueDate(dueDate);

        if (trigger.getId() == null) {
            triggerMapper.insert(trigger);
        } else {
            trigger.clean();
            triggerMapper.updateById(trigger);
        }
        return triggerMapper.selectById(trigger.getId());
    }

    private RehabReassessmentTriggerDO validateTriggerReadable(Long triggerId, Long operatorUserId) {
        RehabReassessmentTriggerDO trigger = triggerMapper.selectById(triggerId);
        if (trigger == null) {
            throw exception(TRIGGER_NOT_EXISTS);
        }
        validatePatientReadable(trigger.getPatientId(), operatorUserId);
        return trigger;
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

    private List<RehabReassessmentTriggerRespVO> toRespVOList(List<RehabReassessmentTriggerDO> list) {
        Set<Long> patientIds = list.stream().map(RehabReassessmentTriggerDO::getPatientId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> planIds = list.stream().map(RehabReassessmentTriggerDO::getPlanId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> ackIds = list.stream().map(RehabReassessmentTriggerDO::getAcknowledgedBy).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, RehabPatientDO> patientMap = patientIds.isEmpty() ? Collections.emptyMap() : patientMapper.selectBatchIds(patientIds).stream()
                .collect(Collectors.toMap(RehabPatientDO::getId, item -> item, (a, b) -> a));
        Map<Long, RehabCarePlanDO> planMap = planIds.isEmpty() ? Collections.emptyMap() : planMapper.selectBatchIds(planIds).stream()
                .collect(Collectors.toMap(RehabCarePlanDO::getId, item -> item, (a, b) -> a));
        Map<Long, AdminUserRespDTO> ackMap = ackIds.isEmpty() ? Collections.emptyMap() : adminUserApi.getUserMap(ackIds);

        return list.stream().map(item -> {
            RehabReassessmentTriggerRespVO vo = BeanUtils.toBean(item, RehabReassessmentTriggerRespVO.class);
            RehabPatientDO patient = patientMap.get(item.getPatientId());
            if (patient != null) {
                vo.setPatientNo(patient.getPatientNo());
                vo.setPatientName(patient.getName());
            }
            RehabCarePlanDO plan = planMap.get(item.getPlanId());
            if (plan != null) {
                vo.setPlanNo(plan.getPlanNo());
            }
            AdminUserRespDTO ackUser = ackMap.get(item.getAcknowledgedBy());
            vo.setAcknowledgedByName(ackUser == null ? "" : ackUser.getNickname());
            return vo;
        }).collect(Collectors.toList());
    }

}
