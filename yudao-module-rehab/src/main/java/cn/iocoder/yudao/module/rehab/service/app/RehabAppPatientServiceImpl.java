package cn.iocoder.yudao.module.rehab.service.app;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo.RehabDailyCheckinCreateReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo.RehabDailyCheckinPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo.RehabDailyCheckinRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo.RehabTaskExecutionItemVO;
import cn.iocoder.yudao.module.rehab.controller.admin.ai.vo.RehabAiOutputRespVO;
import cn.iocoder.yudao.module.rehab.controller.app.patient.vo.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assignment.RehabTherapistAssignmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.binding.RehabPatientUserBindingDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.checkin.RehabDailyCheckinDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.checkin.RehabTaskExecutionDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.followup.RehabFollowupNoteDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.progress.RehabProgressRecordDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.report.RehabReportDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.task.RehabExerciseTaskDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.trigger.RehabReassessmentTriggerDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.assignment.RehabTherapistAssignmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.binding.RehabPatientUserBindingMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabDailyCheckinMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.checkin.RehabTaskExecutionMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.followup.RehabFollowupNoteMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.progress.RehabProgressRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.report.RehabReportMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.task.RehabExerciseTaskMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.trigger.RehabReassessmentTriggerMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabAppConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabPlanConstants;
import cn.iocoder.yudao.module.rehab.service.notification.RehabNotificationService;
import cn.iocoder.yudao.module.rehab.service.checkin.RehabDailyCheckinService;
import cn.iocoder.yudao.module.rehab.service.ai.RehabAiService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

@Service
@Validated
public class RehabAppPatientServiceImpl implements RehabAppPatientService {

    @Resource
    private OAuth2TokenCommonApi oauth2TokenApi;
    @Resource
    private RehabPatientUserBindingMapper patientUserBindingMapper;
    @Resource
    private RehabPatientMapper patientMapper;
    @Resource
    private RehabCarePlanMapper planMapper;
    @Resource
    private RehabExerciseTaskMapper taskMapper;
    @Resource
    private RehabDailyCheckinMapper checkinMapper;
    @Resource
    private RehabTaskExecutionMapper taskExecutionMapper;
    @Resource
    private RehabProgressRecordMapper progressRecordMapper;
    @Resource
    private RehabReassessmentTriggerMapper triggerMapper;
    @Resource
    private RehabReportMapper reportMapper;
    @Resource
    private RehabTherapistAssignmentMapper assignmentMapper;
    @Resource
    private RehabDailyCheckinService checkinService;
    @Resource
    private RehabNotificationService notificationService;
    @Resource
    private RehabFollowupNoteMapper followupNoteMapper;
    @Resource
    private RehabAiService aiService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public AppPatientLoginRespVO login(AppPatientLoginReqVO reqVO) {
        RehabPatientUserBindingDO binding = resolveBindingByLogin(reqVO);
        binding.setLastLoginTime(LocalDateTime.now());
        binding.clean();
        patientUserBindingMapper.updateById(binding);

        OAuth2AccessTokenCreateReqDTO tokenCreateReqDTO = new OAuth2AccessTokenCreateReqDTO();
        tokenCreateReqDTO.setUserId(binding.getAppUserId());
        tokenCreateReqDTO.setUserType(UserTypeEnum.MEMBER.getValue());
        tokenCreateReqDTO.setClientId(OAuth2ClientConstants.CLIENT_ID_DEFAULT);
        OAuth2AccessTokenRespDTO token = oauth2TokenApi.createAccessToken(tokenCreateReqDTO);

        AppPatientLoginRespVO respVO = new AppPatientLoginRespVO();
        respVO.setUserId(token.getUserId());
        respVO.setAccessToken(token.getAccessToken());
        respVO.setRefreshToken(token.getRefreshToken());
        respVO.setExpiresTime(token.getExpiresTime());
        return respVO;
    }

    @Override
    public Long bindPatient(AppPatientAuthBindReqVO reqVO, Long appUserId) {
        RehabPatientDO patient = resolvePatient(reqVO);
        if (patient == null) {
            throw exception(PATIENT_NOT_EXISTS);
        }
        if (StrUtil.isNotBlank(patient.getPhone()) && !ObjUtil.equals(patient.getPhone(), reqVO.getPhone())) {
            throw exception(APP_PATIENT_BIND_PHONE_MISMATCH);
        }

        Long finalAppUserId = appUserId != null ? appUserId : generateAppUserId(patient.getId());

        RehabPatientUserBindingDO currentBinding = patientUserBindingMapper.selectActiveByAppUserId(finalAppUserId);
        if (currentBinding != null && !ObjUtil.equals(currentBinding.getPatientId(), patient.getId())) {
            throw exception(APP_PATIENT_BINDING_CONFLICT);
        }
        RehabPatientUserBindingDO patientBinding = patientUserBindingMapper.selectActiveByPatientId(patient.getId());
        if (patientBinding != null && !ObjUtil.equals(patientBinding.getAppUserId(), finalAppUserId)) {
            throw exception(APP_PATIENT_BINDING_CONFLICT);
        }

        RehabPatientUserBindingDO binding = currentBinding;
        if (binding == null) {
            binding = patientUserBindingMapper.selectActiveByPatientIdAndAppUserId(patient.getId(), finalAppUserId);
        }
        if (binding == null) {
            binding = RehabPatientUserBindingDO.builder().build();
        }
        binding.setPatientId(patient.getId());
        binding.setAppUserId(finalAppUserId);
        binding.setBindType(StrUtil.blankToDefault(reqVO.getBindType(), RehabAppConstants.BIND_TYPE_SELF));
        binding.setBindStatus(RehabAppConstants.BIND_STATUS_ACTIVE);
        binding.setPhone(reqVO.getPhone());
        binding.setNickname(reqVO.getNickname());
        binding.setLastLoginTime(LocalDateTime.now());
        if (binding.getId() == null) {
            patientUserBindingMapper.insert(binding);
        } else {
            binding.clean();
            patientUserBindingMapper.updateById(binding);
        }
        return binding.getId();
    }

    @Override
    public AppPatientHomeSummaryRespVO getHomeSummary(Long appUserId) {
        RehabPatientUserBindingDO binding = requireActiveBinding(appUserId);
        RehabPatientDO patient = patientMapper.selectById(binding.getPatientId());
        if (patient == null) {
            throw exception(PATIENT_NOT_EXISTS);
        }

        AppPatientHomeSummaryRespVO respVO = new AppPatientHomeSummaryRespVO();
        respVO.setPatientId(patient.getId());
        respVO.setPatientName(patient.getName());
        respVO.setCurrentStage(patient.getCurrentStage());

        RehabReportDO latestReport = reportMapper.selectLatestByPatientId(patient.getId());
        if (latestReport != null) {
            respVO.setLatestReportSummary(StrUtil.blankToDefault(latestReport.getNote(), "已生成最新评估报告，请查看摘要。"));
        } else {
            respVO.setLatestReportSummary("暂无报告");
        }

        RehabCarePlanDO activePlan = planMapper.selectActiveByPatientId(patient.getId());
        if (activePlan != null) {
            respVO.setCurrentPlanSummary(StrUtil.format("{}（{} ~ {}）",
                    StrUtil.blankToDefault(activePlan.getPlanName(), "当前训练计划"),
                    activePlan.getStartDate(), activePlan.getEndDate()));
            respVO.setPrecautions(StrUtil.blankToDefault(activePlan.getPrecautions(), "按计划执行，注意动作质量与疼痛反馈。"));
            List<RehabExerciseTaskDO> tasks = taskMapper.selectActiveListByPlanId(activePlan.getId());
            respVO.setTodayTaskCount(tasks.size());
        } else {
            respVO.setCurrentPlanSummary("暂无当前训练计划");
            respVO.setPrecautions("请等待治疗师分配计划后开始执行。");
            respVO.setTodayTaskCount(0);
        }

        RehabDailyCheckinDO latestCheckin = checkinMapper.selectLatestByPatientId(patient.getId());
        if (latestCheckin == null) {
            respVO.setLatestCheckinSummary("暂无打卡记录");
        } else {
            respVO.setLatestCheckinSummary(StrUtil.format("{} 打卡，完成率 {}%",
                    latestCheckin.getCheckinDate(),
                    latestCheckin.getOverallCompletionRate() == null ? "-" : latestCheckin.getOverallCompletionRate()));
        }

        List<RehabReassessmentTriggerDO> triggers = triggerMapper.selectListByPatientId(patient.getId());
        RehabReassessmentTriggerDO pending = triggers.stream()
                .filter(item -> ObjUtil.equals(item.getTriggerStatus(), RehabPlanConstants.TRIGGER_STATUS_PENDING))
                .findFirst().orElse(null);
        if (pending != null) {
            String due = pending.getDueDate() == null ? "请尽快联系治疗师" : ("建议在 " + pending.getDueDate() + " 前复评");
            respVO.setNextReassessmentReminder(StrUtil.format("{}，{}", pending.getTriggerMessage(), due));
        } else {
            respVO.setNextReassessmentReminder("暂无复评提醒");
        }
        respVO.setUnreadNotificationCount(notificationService.countUnreadForPatient(patient.getId(), appUserId));

        return respVO;
    }

    @Override
    public PageResult<AppPatientReportRespVO> getReportPage(Integer pageNo, Integer pageSize, Long appUserId) {
        RehabPatientUserBindingDO binding = requireActiveBinding(appUserId);
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(pageNo);
        pageParam.setPageSize(pageSize);
        PageResult<RehabReportDO> pageResult = reportMapper.selectPage(pageParam,
                new LambdaQueryWrapperX<RehabReportDO>()
                        .eq(RehabReportDO::getPatientId, binding.getPatientId())
                        .orderByDesc(RehabReportDO::getCreateTime)
                        .orderByDesc(RehabReportDO::getId));
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        List<AppPatientReportRespVO> list = pageResult.getList().stream().map(this::toReportRespVO).collect(Collectors.toList());
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    public AppPatientReportRespVO getReport(Long reportId, Long appUserId) {
        RehabPatientUserBindingDO binding = requireActiveBinding(appUserId);
        RehabReportDO report = reportMapper.selectById(reportId);
        if (report == null || !ObjUtil.equals(report.getPatientId(), binding.getPatientId())) {
            throw exception(REPORT_NOT_EXISTS);
        }
        return toReportRespVO(report);
    }

    @Override
    public AppPatientCurrentPlanRespVO getCurrentPlan(Long appUserId) {
        RehabPatientUserBindingDO binding = requireActiveBinding(appUserId);
        RehabCarePlanDO plan = planMapper.selectActiveByPatientId(binding.getPatientId());
        if (plan == null) {
            return null;
        }
        AppPatientCurrentPlanRespVO respVO = new AppPatientCurrentPlanRespVO();
        respVO.setId(plan.getId());
        respVO.setPlanNo(plan.getPlanNo());
        respVO.setPlanName(plan.getPlanName());
        respVO.setPlanType(plan.getPlanType());
        respVO.setStatus(plan.getStatus());
        respVO.setShortTermGoalsJson(plan.getShortTermGoalsJson());
        respVO.setPrecautions(plan.getPrecautions());
        respVO.setContraindications(plan.getContraindications());
        respVO.setStartDate(plan.getStartDate());
        respVO.setEndDate(plan.getEndDate());
        respVO.setTaskCount(taskMapper.selectActiveListByPlanId(plan.getId()).size());
        return respVO;
    }

    @Override
    public List<AppPatientTaskRespVO> getTodayTasks(Long appUserId) {
        RehabPatientUserBindingDO binding = requireActiveBinding(appUserId);
        RehabCarePlanDO plan = planMapper.selectActiveByPatientId(binding.getPatientId());
        if (plan == null) {
            return Collections.emptyList();
        }
        List<RehabExerciseTaskDO> tasks = taskMapper.selectActiveListByPlanId(plan.getId());
        return tasks.stream().map(task -> {
            AppPatientTaskRespVO vo = new AppPatientTaskRespVO();
            vo.setId(task.getId());
            vo.setTaskNo(task.getTaskNo());
            vo.setTaskName(task.getTaskName());
            vo.setModuleType(task.getModuleType());
            vo.setDosageText(task.getDosageText());
            vo.setSets(task.getSets());
            vo.setRepetitions(task.getRepetitions());
            vo.setFrequencyPerWeek(task.getFrequencyPerWeek());
            vo.setTempo(task.getTempo());
            vo.setPainLimitRule(task.getPainLimitRule());
            vo.setInstructionText(task.getInstructionText());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public Long createCheckin(AppPatientCheckinCreateReqVO reqVO, Long appUserId) {
        RehabPatientUserBindingDO binding = requireActiveBinding(appUserId);
        RehabCarePlanDO plan = planMapper.selectById(reqVO.getPlanId());
        if (plan == null || !ObjUtil.equals(plan.getPatientId(), binding.getPatientId())) {
            throw exception(PLAN_NOT_EXISTS);
        }
        LocalDate checkinDate = reqVO.getCheckinDate() == null ? LocalDate.now() : reqVO.getCheckinDate();
        RehabDailyCheckinDO existed = checkinMapper.selectByPatientPlanAndDate(binding.getPatientId(), plan.getId(), checkinDate);
        if (existed != null) {
            throw exception(APP_PATIENT_DAILY_CHECKIN_EXISTS);
        }

        RehabDailyCheckinCreateReqVO createReqVO = new RehabDailyCheckinCreateReqVO();
        createReqVO.setPatientId(binding.getPatientId());
        createReqVO.setEpisodeId(plan.getEpisodeId());
        createReqVO.setPlanId(plan.getId());
        createReqVO.setCheckinDate(checkinDate);
        createReqVO.setSubmitRoleType(RehabPlanConstants.CHECKIN_ROLE_PATIENT);
        createReqVO.setPainScoreBefore(reqVO.getPainScoreBefore());
        createReqVO.setPainScoreAfter(reqVO.getPainScoreAfter());
        createReqVO.setFatigueLevel(reqVO.getFatigueLevel());
        createReqVO.setConfidenceLevel(reqVO.getConfidenceLevel());
        createReqVO.setOverallComment(reqVO.getOverallComment());
        createReqVO.setTaskExecutions(reqVO.getTaskExecutions().stream().map(item -> {
            RehabTaskExecutionItemVO vo = new RehabTaskExecutionItemVO();
            vo.setTaskId(item.getTaskId());
            vo.setCompletionStatus(item.getCompletionStatus());
            vo.setCompletedSets(item.getCompletedSets());
            vo.setCompletedReps(item.getCompletedReps());
            vo.setPainScore(item.getPainScore());
            vo.setDifficultyLevel(item.getDifficultyLevel());
            vo.setSymptomFlag(item.getSymptomFlag());
            vo.setSymptomNote(item.getSymptomNote());
            vo.setTaskComment(item.getTaskComment());
            return vo;
        }).collect(Collectors.toList()));
        return checkinService.createCheckin(createReqVO, appUserId, false);
    }

    @Override
    public PageResult<AppPatientCheckinHistoryRespVO> getCheckinHistory(Integer pageNo, Integer pageSize, Long appUserId) {
        RehabPatientUserBindingDO binding = requireActiveBinding(appUserId);
        RehabDailyCheckinPageReqVO pageReqVO = new RehabDailyCheckinPageReqVO();
        pageReqVO.setPatientId(binding.getPatientId());
        pageReqVO.setPageNo(pageNo);
        pageReqVO.setPageSize(pageSize);
        PageResult<RehabDailyCheckinRespVO> pageResult = checkinService.getCheckinPage(pageReqVO, appUserId);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        List<AppPatientCheckinHistoryRespVO> list = pageResult.getList().stream().map(item -> {
            AppPatientCheckinHistoryRespVO vo = new AppPatientCheckinHistoryRespVO();
            vo.setId(item.getId());
            vo.setCheckinDate(item.getCheckinDate());
            vo.setOverallCompletionRate(item.getOverallCompletionRate());
            vo.setPainScoreBefore(item.getPainScoreBefore());
            vo.setPainScoreAfter(item.getPainScoreAfter());
            vo.setOverallComment(item.getOverallComment());

            List<RehabTaskExecutionDO> executions = taskExecutionMapper.selectListByCheckinId(item.getId());
            List<String> summaries = executions.stream().map(exec -> {
                RehabExerciseTaskDO task = taskMapper.selectById(exec.getTaskId());
                String taskName = task == null ? ("任务#" + exec.getTaskId()) : task.getTaskName();
                return taskName + "：" + exec.getCompletionStatus();
            }).collect(Collectors.toList());
            vo.setTaskExecutionSummary(summaries);
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    public AppPatientProfileRespVO getProfile(Long appUserId) {
        RehabPatientUserBindingDO binding = requireActiveBinding(appUserId);
        RehabPatientDO patient = patientMapper.selectById(binding.getPatientId());
        if (patient == null) {
            throw exception(PATIENT_NOT_EXISTS);
        }
        AppPatientProfileRespVO respVO = new AppPatientProfileRespVO();
        respVO.setPatientId(patient.getId());
        respVO.setPatientNo(patient.getPatientNo());
        respVO.setName(patient.getName());
        respVO.setPhone(maskPhone(patient.getPhone()));
        respVO.setCurrentStage(patient.getCurrentStage());
        RehabTherapistAssignmentDO assignment = assignmentMapper.selectActivePrimaryByPatientId(patient.getId());
        if (assignment != null) {
            AdminUserRespDTO user = adminUserApi.getUser(assignment.getTherapistUserId());
            respVO.setTherapistName(user == null ? "" : user.getNickname());
        }
        return respVO;
    }

    @Override
    public PageResult<AppPatientNotificationRespVO> getNotificationPage(Integer pageNo, Integer pageSize, Long appUserId) {
        RehabPatientUserBindingDO binding = requireActiveBinding(appUserId);
        PageResult<AppPatientNotificationRespVO> pageResult = notificationService
                .getPatientNotificationPage(pageNo, pageSize, binding.getPatientId(), appUserId);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return pageResult;
        }
        return pageResult;
    }

    @Override
    public void markNotificationRead(Long notificationId, Long appUserId) {
        RehabPatientUserBindingDO binding = requireActiveBinding(appUserId);
        notificationService.readPatientNotification(notificationId, binding.getPatientId(), appUserId);
    }

    @Override
    public AppPatientAiOutputRespVO getLatestAiSummary(Long appUserId) {
        RehabPatientUserBindingDO binding = requireActiveBinding(appUserId);
        RehabAiOutputRespVO output = aiService.getLatestPatientVisibleSummary(binding.getPatientId(), appUserId);
        if (output == null) {
            return null;
        }
        AppPatientAiOutputRespVO respVO = new AppPatientAiOutputRespVO();
        respVO.setId(output.getId());
        respVO.setOutputType(output.getOutputType());
        respVO.setRenderedText(output.getRenderedText());
        respVO.setCreateTime(output.getCreateTime());
        return respVO;
    }

    @Override
    public AppPatientAiOutputRespVO getLatestAiFollowup(Long appUserId) {
        RehabPatientUserBindingDO binding = requireActiveBinding(appUserId);
        RehabAiOutputRespVO output = aiService.getLatestPatientVisibleFollowup(binding.getPatientId(), appUserId);
        if (output == null) {
            return null;
        }
        AppPatientAiOutputRespVO respVO = new AppPatientAiOutputRespVO();
        respVO.setId(output.getId());
        respVO.setOutputType(output.getOutputType());
        respVO.setRenderedText(output.getRenderedText());
        respVO.setCreateTime(output.getCreateTime());
        return respVO;
    }

    private AppPatientReportRespVO toReportRespVO(RehabReportDO report) {
        AppPatientReportRespVO vo = new AppPatientReportRespVO();
        vo.setId(report.getId());
        vo.setReportNo(report.getReportNo());
        vo.setReportType(report.getReportType());
        vo.setReportStatus(report.getReportStatus());
        vo.setIssueSummary("提示存在功能问题，建议结合训练计划持续跟进。");
        vo.setRecommendationSummary(StrUtil.blankToDefault(report.getNote(), "请按治疗师计划执行训练并按时复评。"));
        vo.setCreateTime(report.getCreateTime());
        return vo;
    }

    private RehabPatientUserBindingDO requireActiveBinding(Long appUserId) {
        RehabPatientUserBindingDO binding = patientUserBindingMapper.selectActiveByAppUserId(appUserId);
        if (binding == null) {
            throw exception(APP_PATIENT_BINDING_REQUIRED);
        }
        return binding;
    }

    private RehabPatientDO resolvePatient(AppPatientAuthBindReqVO reqVO) {
        if (reqVO.getPatientId() != null) {
            return patientMapper.selectById(reqVO.getPatientId());
        }
        if (StrUtil.isNotBlank(reqVO.getPatientNo())) {
            return patientMapper.selectByPatientNo(reqVO.getPatientNo());
        }
        return null;
    }

    private RehabPatientUserBindingDO resolveBindingByLogin(AppPatientLoginReqVO reqVO) {
        List<RehabPatientUserBindingDO> bindings = patientUserBindingMapper.selectActiveListByPhone(reqVO.getPhone());
        if (CollUtil.isEmpty(bindings)) {
            throw exception(APP_PATIENT_BINDING_REQUIRED);
        }
        RehabPatientUserBindingDO matched = null;
        for (RehabPatientUserBindingDO binding : bindings) {
            RehabPatientDO patient = patientMapper.selectById(binding.getPatientId());
            if (patient == null) {
                continue;
            }
            if (ObjUtil.equals(patient.getPatientNo(), reqVO.getBindCode())) {
                matched = binding;
                break;
            }
        }
        if (matched == null) {
            throw exception(APP_PATIENT_BINDING_REQUIRED);
        }
        return matched;
    }

    private String maskPhone(String phone) {
        if (StrUtil.isBlank(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private Long generateAppUserId(Long patientId) {
        return 90_000_000L + patientId;
    }
}
