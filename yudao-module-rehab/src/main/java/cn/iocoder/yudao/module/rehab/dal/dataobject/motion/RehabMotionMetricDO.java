package cn.iocoder.yudao.module.rehab.dal.dataobject.motion;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.*;

/**
 * 动作评估指标（租户隔离：继承 TenantBaseDO，由多租户拦截器自动过滤 tenant_id）
 */
@TableName("rehab_motion_metric")
@KeySequence("rehab_motion_metric_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabMotionMetricDO extends TenantBaseDO {

    /** 指标编号 */
    @TableId
    private Long id;
    /** 动作评估编号 */
    private Long assessmentId;
    /** Trial 编号 */
    private Long trialId;
    /** 引擎指标 ID */
    private String metricKey;
    /** 指标代码 */
    private String code;
    /** 名称 */
    private String label;
    /** 侧别 */
    private String side;
    /** 阶段 */
    private String phase;
    /** 重复序号（NULL=全程） */
    private Integer repNo;
    /** 数值（NULL=不可用，不补 0） */
    private BigDecimal valueNum;
    /** 单位 */
    private String unit;
    /** standard_formula/mot_direct/derived/proxy/manual */
    private String classification;
    /** 验证状态 */
    private String validationStatus;
    /** 不可用原因 */
    private String unavailableReason;
    /** 来源信号 */
    private String sourceSignals;
    /** 计算方法 */
    private String method;
    /** 分析修订号 */
    private Integer analysisRevision;

}
