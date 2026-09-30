package cn.iocoder.yudao.module.rehab.dal.dataobject.motion;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 动作评估评分（系统分与最终分分列）（租户隔离：继承 TenantBaseDO，由多租户拦截器自动过滤 tenant_id）
 */
@TableName("rehab_motion_score")
@KeySequence("rehab_motion_score_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabMotionScoreDO extends TenantBaseDO {

    /** 评分编号 */
    @TableId
    private Long id;
    /** 动作评估编号 */
    private Long assessmentId;
    /** 体系 */
    private String family;
    /** 测试代码 */
    private String testCode;
    /** overall/left/right */
    private String side;
    /** 计分方案 */
    private String scoringScheme;
    /** 系统建议分（NULL=不给出） */
    private BigDecimal systemScore;
    /** 候选分（含研究性判定，仅供参考） */
    private BigDecimal provisionalScore;
    /** 系统状态 */
    private String systemStatus;
    /** 治疗师最终分 */
    private BigDecimal finalScore;
    /** pending/confirmed/modified/not_applicable/needs_recheck */
    private String finalStatus;
    /** 修改原因 */
    private String changeReason;
    /** 审核人 */
    private Long reviewedUserId;
    /** 审核时间 */
    private LocalDateTime reviewedTime;
    /** 审核后系统分发生变化 */
    private Boolean systemScoreChanged;
    /** 计分明细 */
    private String detailJson;
    /** 证据引用 */
    private String evidenceJson;
    /** 分析修订号 */
    private Integer analysisRevision;

}
