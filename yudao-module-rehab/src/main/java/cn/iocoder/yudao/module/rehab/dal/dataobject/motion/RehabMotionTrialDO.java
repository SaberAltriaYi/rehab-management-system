package cn.iocoder.yudao.module.rehab.dal.dataobject.motion;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.*;

/**
 * 动作评估 Trial（租户隔离：继承 TenantBaseDO，由多租户拦截器自动过滤 tenant_id）
 */
@TableName("rehab_motion_trial")
@KeySequence("rehab_motion_trial_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabMotionTrialDO extends TenantBaseDO {

    /** Trial 编号 */
    @TableId
    private Long id;
    /** 动作评估编号 */
    private Long assessmentId;
    /** 评估内唯一键 */
    private String trialKey;
    /** 测试代码 */
    private String testCode;
    /** bilateral/left/right */
    private String side;
    /** 测试条件 */
    private String conditionCode;
    /** 尝试序号 */
    private Integer attemptNo;
    /** OpenCap trial 名 */
    private String opencapTrialName;
    /** OpenCap trial UUID */
    private String opencapTrialId;
    /** 是否有效 */
    private Boolean valid;
    /** 无效原因 */
    private String invalidReason;
    /** 是否疼痛（NULL=未记录） */
    private Boolean pain;
    /** 人工判定项 */
    private String manualCriteriaJson;
    /** 人工测量值 */
    private String manualValuesJson;
    /** pass/warn/fail/not_analyzed */
    private String qcStatus;
    /** 质控问题 */
    private String qcIssuesJson;
    /** 阶段与关键帧 */
    private String phasesJson;
    /** 阶段识别参数 */
    private String phaseDetectionJson;
    /** 重复/跳跃次数 */
    private Integer repCount;
    /** 时长秒 */
    private BigDecimal durationS;
    /** 排序 */
    private Integer sortNo;

}
