package cn.iocoder.yudao.module.rehab.dal.dataobject.motion;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 动作评估异步任务（租户隔离：继承 TenantBaseDO，由多租户拦截器自动过滤 tenant_id）
 */
@TableName("rehab_motion_task")
@KeySequence("rehab_motion_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabMotionTaskDO extends TenantBaseDO {

    /** 任务编号 */
    @TableId
    private Long id;
    /** 动作评估编号 */
    private Long assessmentId;
    /** PIPELINE/RESCORE/AI/PDF */
    private String taskType;
    /** 任务状态 */
    private String state;
    /** 进度 0-100 */
    private Integer progress;
    /** 当前步骤说明 */
    private String stepMessage;
    /** 当前步骤已重试次数 */
    private Integer attempts;
    /** 最大自动重试次数 */
    private Integer maxAttempts;
    /** 下次执行时间 */
    private LocalDateTime nextRunTime;
    /** 执行节点 */
    private String lockOwner;
    /** 锁过期时间 */
    private LocalDateTime lockUntil;
    /** 幂等键 */
    private String idempotencyKey;
    /** 已请求取消 */
    private Boolean cancelRequested;
    /** 错误码 */
    private String errorCode;
    /** 已脱敏错误信息 */
    private String errorMessage;
    /** 失败时所在步骤 */
    private String failedState;
    /** 开始时间 */
    private LocalDateTime startedTime;
    /** 结束时间 */
    private LocalDateTime finishedTime;
    /** 等待外部处理的截止时间 */
    private LocalDateTime deadlineTime;
    /** 发起人 */
    private Long requestedBy;
    /** 发起时的输入修订号 */
    private Integer inputRevision;
    /** 任务参数（不含患者身份） */
    private String payloadJson;

}
