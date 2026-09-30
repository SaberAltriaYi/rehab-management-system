package cn.iocoder.yudao.module.rehab.dal.dataobject.motion;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 动作评估规则结果（租户隔离：继承 TenantBaseDO，由多租户拦截器自动过滤 tenant_id）
 */
@TableName("rehab_motion_rule_result")
@KeySequence("rehab_motion_rule_result_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabMotionRuleResultDO extends TenantBaseDO {

    /** 规则结果编号 */
    @TableId
    private Long id;
    /** 动作评估编号 */
    private Long assessmentId;
    /** Trial 编号 */
    private Long trialId;
    /** 引擎规则结果 ID */
    private String ruleKey;
    /** 规则代码 */
    private String ruleId;
    /** 体系 */
    private String family;
    /** 测试代码 */
    private String testCode;
    /** 侧别 */
    private String side;
    /** 名称 */
    private String label;
    /** 最终结论 */
    private String outcome;
    /** 自动结论 */
    private String autoOutcome;
    /** 人工结论 */
    private String manualOutcome;
    /** auto_official/auto_candidate/manual */
    private String decidedBy;
    /** 自动判定策略 */
    private String autoPolicy;
    /** 阈值来源状态 */
    private String thresholdStatus;
    /** 判定标准 */
    private String criterion;
    /** 来源 */
    private String source;
    /** 证据 */
    private String evidenceJson;
    /** 说明 */
    private String note;
    /** 分析修订号 */
    private Integer analysisRevision;

}
