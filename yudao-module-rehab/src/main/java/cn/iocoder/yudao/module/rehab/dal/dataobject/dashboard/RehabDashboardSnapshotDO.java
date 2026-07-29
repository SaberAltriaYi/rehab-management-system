package cn.iocoder.yudao.module.rehab.dal.dataobject.dashboard;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dashboard 统计快照
 */
@TableName("rehab_dashboard_snapshot")
@KeySequence("rehab_dashboard_snapshot_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabDashboardSnapshotDO extends BaseDO {

    @TableId
    private Long id;

    private LocalDate snapshotDate;
    private String ownerScope;
    private Long ownerUserId;
    private Integer patientTotal;
    private Integer activePatientTotal;
    private Integer activePlanTotal;
    private Integer pendingReassessmentTotal;
    private Integer highRiskTotal;
    private Integer reportGeneratedTotal;
    private Integer reportExportedTotal;
    private BigDecimal avgCheckinCompletionRate;
    private Integer lowAdherenceTotal;
}
