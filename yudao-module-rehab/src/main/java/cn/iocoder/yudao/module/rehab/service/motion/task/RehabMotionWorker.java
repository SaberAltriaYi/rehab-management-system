package cn.iocoder.yudao.module.rehab.service.motion.task;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTaskDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionAssessmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionTaskMapper;
import cn.iocoder.yudao.module.rehab.service.motion.engine.MotionEngineException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants.*;

/**
 * 动作评估任务 worker：数据库队列 + 条件更新认领（多实例安全），任务状态全部落库，关闭页面/重启服务不丢失。
 * <ul>
 *   <li>跨租户轮询（executeIgnore），执行时切换到任务所属租户（execute）。</li>
 *   <li>锁超时（lock-seconds）后其它节点可接管，步骤须可重入。</li>
 *   <li>可重试错误指数退避（30s·2^n，上限 30 分钟），超过 max-attempts 进入 FAILED；不可重试错误直接 FAILED。</li>
 *   <li>日志只含任务编号、状态、错误码，不含患者信息与数据内容。</li>
 * </ul>
 */
@Component
@Slf4j
public class RehabMotionWorker {

    private final String owner = StrUtil.maxLength(hostName(), 40) + "-" + IdUtil.fastSimpleUUID().substring(0, 8);

    @Value("${yudao.rehab.motion.worker-enabled:true}")
    private boolean enabled = true;
    @Value("${yudao.rehab.motion.worker-batch:5}")
    private int batch = 5;
    @Value("${yudao.rehab.motion.lock-seconds:300}")
    private int lockSeconds = 300;

    @Resource
    private RehabMotionTaskMapper taskMapper;
    @Resource
    private RehabMotionAssessmentMapper assessmentMapper;
    @Resource
    private RehabMotionPipelineSteps steps;

    @Scheduled(fixedDelayString = "${yudao.rehab.motion.worker-delay-ms:3000}",
            initialDelayString = "${yudao.rehab.motion.worker-initial-delay-ms:20000}")
    public void poll() {
        if (!enabled) {
            return;
        }
        final LocalDateTime now = LocalDateTime.now();
        List<RehabMotionTaskDO> due;
        try {
            due = TenantUtils.executeIgnore(() -> taskMapper.selectDue(RUNNABLE_STATES, now, batch));
        } catch (RuntimeException ex) {
            log.warn("[motion-worker] poll failed: {}", ex.getClass().getSimpleName());
            return;
        }
        for (RehabMotionTaskDO task : due) {
            Integer claimed = TenantUtils.executeIgnore(() -> taskMapper.claim(task.getId(), task.getState(), owner,
                    LocalDateTime.now(), LocalDateTime.now().plusSeconds(lockSeconds)));
            if (claimed == null || claimed != 1) {
                continue;
            }
            try {
                TenantUtils.execute(task.getTenantId(), () -> runClaimed(task.getId()));
            } catch (RuntimeException ex) {
                log.error("[motion-worker] task={} unexpected {}", task.getId(), ex.getClass().getSimpleName(), ex);
            }
        }
    }

    /** 执行已认领任务，直到完成、失败、等待或丢失锁。 */
    void runClaimed(Long taskId) {
        for (int guard = 0; guard < 12; guard++) {
            RehabMotionTaskDO task = taskMapper.selectById(taskId);
            if (task == null || !owner.equals(task.getLockOwner())) {
                return;
            }
            RehabMotionAssessmentDO a = assessmentMapper.selectById(task.getAssessmentId());
            if (Boolean.TRUE.equals(task.getCancelRequested())) {
                finish(task, a, STATE_CANCELLED, null, null);
                return;
            }
            if (a == null) {
                finish(task, null, STATE_FAILED, "ASSESSMENT_DELETED", "评估已删除");
                return;
            }
            if (task.getStartedTime() == null) {
                RehabMotionTaskDO upd = new RehabMotionTaskDO();
                upd.setId(task.getId());
                upd.setStartedTime(LocalDateTime.now());
                taskMapper.updateById(upd);
            }
            if (MotionTaskStates.isAnalysis(task.getTaskType()) || TASK_AI.equals(task.getTaskType())) {
                steps.syncAssessmentStatus(a.getId(), task.getState());
            }
            RehabMotionPipelineSteps.Outcome outcome;
            try {
                outcome = steps.execute(task, a);
            } catch (MotionTaskException ex) {
                fail(task, a, ex.getCode(), ex.getMessage(), ex.isRetryable());
                return;
            } catch (MotionEngineException ex) {
                fail(task, a, ex.getCode(), ex.getMessage(), ex.isRetryable());
                return;
            } catch (Exception ex) {
                log.error("[motion-worker] task={} state={} error {}", task.getId(), task.getState(),
                        ex.getClass().getSimpleName(), ex);
                fail(task, a, "INTERNAL_ERROR", "内部错误：" + ex.getClass().getSimpleName(), true);
                return;
            }
            if (outcome.waitUntil != null) {
                RehabMotionTaskDO upd = new RehabMotionTaskDO();
                upd.setId(task.getId());
                upd.setNextRunTime(outcome.waitUntil);
                upd.setStepMessage(StrUtil.maxLength(outcome.message, 250));
                taskMapper.release(upd, owner);
                return;
            }
            if (STATE_COMPLETED.equals(outcome.nextState)) {
                finish(task, a, STATE_COMPLETED, null, null);
                return;
            }
            RehabMotionTaskDO upd = new RehabMotionTaskDO();
            upd.setId(task.getId());
            upd.setState(outcome.nextState);
            upd.setProgress(MotionTaskStates.progress(task.getTaskType(), a.getDataSource(), outcome.nextState));
            upd.setStepMessage(stateLabel(outcome.nextState));
            upd.setAttempts(0);
            upd.setLockUntil(LocalDateTime.now().plusSeconds(lockSeconds));
            int n = taskMapper.update(upd, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RehabMotionTaskDO>()
                    .eq(RehabMotionTaskDO::getId, task.getId())
                    .eq(RehabMotionTaskDO::getLockOwner, owner));
            if (n != 1) {
                return;
            }
            log.info("[motion-worker] task={} {} -> {}", task.getId(), task.getState(), outcome.nextState);
        }
        // 防御：步骤过多时释放锁，下轮继续
        RehabMotionTaskDO upd = new RehabMotionTaskDO();
        upd.setId(taskId);
        taskMapper.release(upd, owner);
    }

    void fail(RehabMotionTaskDO task, RehabMotionAssessmentDO a, String code, String message, boolean retryable) {
        int attempts = (task.getAttempts() == null ? 0 : task.getAttempts()) + 1;
        int max = task.getMaxAttempts() == null ? 5 : task.getMaxAttempts();
        String safeCode = StrUtil.maxLength(StrUtil.blankToDefault(code, "ERROR"), 60);
        String safeMsg = StrUtil.maxLength(StrUtil.blankToDefault(message, safeCode), 480);
        if (retryable && attempts < max) {
            RehabMotionTaskDO upd = new RehabMotionTaskDO();
            upd.setId(task.getId());
            upd.setAttempts(attempts);
            upd.setErrorCode(safeCode);
            upd.setErrorMessage(safeMsg);
            upd.setNextRunTime(LocalDateTime.now().plusSeconds(MotionTaskStates.backoffSeconds(attempts)));
            upd.setStepMessage(StrUtil.maxLength(stateLabel(task.getState()) + "：第 " + attempts + " 次失败，稍后自动重试", 250));
            taskMapper.release(upd, owner);
            log.warn("[motion-worker] task={} state={} retry {}/{} code={}", task.getId(), task.getState(), attempts, max,
                    safeCode);
            return;
        }
        RehabMotionTaskDO upd = new RehabMotionTaskDO();
        upd.setId(task.getId());
        upd.setAttempts(attempts);
        upd.setErrorCode(safeCode);
        upd.setErrorMessage(safeMsg);
        upd.setFailedState(task.getState());
        finishWith(task, a, STATE_FAILED, upd);
        log.warn("[motion-worker] task={} failed at {} code={}", task.getId(), task.getState(), safeCode);
    }

    void finish(RehabMotionTaskDO task, RehabMotionAssessmentDO a, String state, String code, String message) {
        RehabMotionTaskDO upd = new RehabMotionTaskDO();
        upd.setId(task.getId());
        if (code != null) {
            upd.setErrorCode(code);
            upd.setErrorMessage(message);
            upd.setFailedState(task.getState());
        }
        finishWith(task, a, state, upd);
    }

    private void finishWith(RehabMotionTaskDO task, RehabMotionAssessmentDO a, String state, RehabMotionTaskDO upd) {
        upd.setState(state);
        upd.setStepMessage(stateLabel(state));
        upd.setFinishedTime(LocalDateTime.now());
        if (STATE_COMPLETED.equals(state)) {
            upd.setProgress(100);
        }
        if (taskMapper.release(upd, owner) != 1 || a == null) {
            return;
        }
        if (TASK_PDF.equals(task.getTaskType())) {
            return;
        }
        RehabMotionAssessmentDO fresh = assessmentMapper.selectById(a.getId());
        boolean analyzed = fresh != null && fresh.getAnalyzedRevision() != null;
        String status;
        if (STATE_COMPLETED.equals(state)) {
            status = STATE_PENDING_REVIEW;
        } else if (TASK_AI.equals(task.getTaskType())) {
            status = analyzed ? STATE_PENDING_REVIEW : STATE_UPLOADING;
        } else if (STATE_FAILED.equals(state)) {
            status = STATE_FAILED;
        } else {
            status = analyzed ? STATE_PENDING_REVIEW : STATE_UPLOADING;
        }
        steps.syncAssessmentStatus(a.getId(), status);
    }

    String owner() {
        return owner;
    }

    private static String hostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "node";
        }
    }

}
