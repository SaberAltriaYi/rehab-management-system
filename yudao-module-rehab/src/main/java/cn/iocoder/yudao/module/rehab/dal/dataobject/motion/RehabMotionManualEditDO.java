package cn.iocoder.yudao.module.rehab.dal.dataobject.motion;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 动作评估人工修改记录（租户隔离：继承 TenantBaseDO，由多租户拦截器自动过滤 tenant_id）
 */
@TableName("rehab_motion_manual_edit")
@KeySequence("rehab_motion_manual_edit_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabMotionManualEditDO extends TenantBaseDO {

    /** 编号 */
    @TableId
    private Long id;
    /** 动作评估编号 */
    private Long assessmentId;
    /** trial/score/manual_inputs/ai_draft/assessment */
    private String targetType;
    /** 目标编号 */
    private Long targetId;
    /** 目标键 */
    private String targetKey;
    /** 字段 */
    private String fieldName;
    /** 修改前 */
    private String oldValue;
    /** 修改后 */
    private String newValue;
    /** 原因 */
    private String reason;
    /** 操作人 */
    private Long operatorUserId;

}
