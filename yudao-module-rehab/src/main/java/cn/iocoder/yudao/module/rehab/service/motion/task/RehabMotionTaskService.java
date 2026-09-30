package cn.iocoder.yudao.module.rehab.service.motion.task;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTaskDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionAssessmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionTaskMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants.*;
import static cn.iocoder.yudao.module.rehab.enums.RehabMotionErrorCodeConstants.*;

/**
 * 动作评估异步任务：创建（幂等 + 同组互斥）、取消、重试。执行见 {@link RehabMotionWorker}。
 */
@Service
public class RehabMotionTaskService {

    public static final Pattern CLIENT_KEY = Pattern.compile("^[A-Za-z0-9_\\-]{8,64}$");

    @Resource
    private RehabMotionTaskMapper taskMapper;
    @Resource
    private RehabMotionAssessmentMapper assessmentMapper;

    @Value("${yudao.rehab.motion.max-attempts:5}")
    private int maxAttempts = 5;
    @Value("${yudao.rehab.motion.opencap-deadline-hours:12}")
    private int opencapDeadlineHours = 12;

    /**
     * 创建任务。同一幂等键重复提交返回已有任务；同组已有运行中任务时拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public RehabMotionTaskDO enqueue(RehabMotionAssessmentDO a, String taskType, String initialState, String clientKey,
                                     Long userId, String payloadJson) {
        if (StrUtil.isNotBlank(clientKey) && !CLIENT_KEY.matcher(clientKey).matches()) {
            throw exception(MOTION_IDEMPOTENCY_KEY_INVALID);
        }
        String key = StrUtil.isBlank(clientKey)
                ? taskType + ":" + a.getId() + ":" + IdUtil.fastSimpleUUID()
                : a.getId() + ":" + taskType + ":" + clientKey;
        if (StrUtil.isNotBlank(clientKey)) {
            RehabMotionTaskDO existing = taskMapper.selectByIdempotencyKey(key);
            if (existing != null) {
                return existing;
            }
        }
        assessmentMapper.lockById(a.getId());
        List<RehabMotionTaskDO> active = taskMapper.selectActiveByAssessment(a.getId(), RUNNABLE_STATES);
        for (RehabMotionTaskDO t : active) {
            if (MotionTaskStates.group(t.getTaskType()).equals(MotionTaskStates.group(taskType))) {
                throw exception(MOTION_TASK_ACTIVE);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        RehabMotionTaskDO task = RehabMotionTaskDO.builder()
                .assessmentId(a.getId())
                .taskType(taskType)
                .state(initialState)
                .progress(MotionTaskStates.progress(taskType, a.getDataSource(), initialState))
                .stepMessage(stateLabel(initialState))
                .attempts(0)
                .maxAttempts(maxAttempts)
                .nextRunTime(now)
                .idempotencyKey(key)
                .cancelRequested(false)
                .requestedBy(userId)
                .inputRevision(a.getInputRevision())
                .payloadJson(payloadJson)
                .deadlineTime(STATE_OPENCAP_PROCESSING.equals(initialState) ? now.plusHours(opencapDeadlineHours) : null)
                .build();
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException ex) {
            RehabMotionTaskDO existing = taskMapper.selectByIdempotencyKey(key);
            if (existing != null) {
                return existing;
            }
            throw ex;
        }
        return task;
    }

    /**
     * 请求取消。未被 worker 持有时立即取消；否则设置取消标记，worker 在步骤之间检查并停止。
     *
     * @return true = 已立即取消
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(RehabMotionTaskDO task) {
        if (MotionTaskStates.isTerminal(task.getState())) {
            throw exception(MOTION_TASK_STATE_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        int immediate = taskMapper.update(null, new LambdaUpdateWrapper<RehabMotionTaskDO>()
                .set(RehabMotionTaskDO::getCancelRequested, true)
                .set(RehabMotionTaskDO::getState, STATE_CANCELLED)
                .set(RehabMotionTaskDO::getStepMessage, stateLabel(STATE_CANCELLED))
                .set(RehabMotionTaskDO::getFinishedTime, now)
                .eq(RehabMotionTaskDO::getId, task.getId())
                .eq(RehabMotionTaskDO::getState, task.getState())
                .and(w -> w.isNull(RehabMotionTaskDO::getLockUntil).or().lt(RehabMotionTaskDO::getLockUntil, now)));
        if (immediate == 1) {
            return true;
        }
        taskMapper.update(null, new LambdaUpdateWrapper<RehabMotionTaskDO>()
                .set(RehabMotionTaskDO::getCancelRequested, true)
                .eq(RehabMotionTaskDO::getId, task.getId()));
        return false;
    }

    /** 重试失败/已取消任务：新建任务，从失败步骤继续。 */
    @Transactional(rollbackFor = Exception.class)
    public RehabMotionTaskDO retry(RehabMotionAssessmentDO a, RehabMotionTaskDO failed, String clientKey, Long userId) {
        if (!STATE_FAILED.equals(failed.getState()) && !STATE_CANCELLED.equals(failed.getState())) {
            throw exception(MOTION_TASK_STATE_INVALID);
        }
        String state = MotionTaskStates.resumeState(failed.getTaskType(), a.getDataSource(), failed.getFailedState());
        return enqueue(a, failed.getTaskType(), state, clientKey, userId, failed.getPayloadJson());
    }

    public boolean hasActive(Long assessmentId) {
        return !taskMapper.selectActiveByAssessment(assessmentId, RUNNABLE_STATES).isEmpty();
    }

    public List<RehabMotionTaskDO> list(Long assessmentId) {
        return taskMapper.selectListByAssessmentId(assessmentId);
    }

    public RehabMotionTaskDO get(Long id) {
        RehabMotionTaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw exception(MOTION_TASK_NOT_EXISTS);
        }
        return task;
    }

}
