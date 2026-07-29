package cn.iocoder.yudao.module.rehab.dal.dataobject.binding;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 患者 CRM 绑定 DO
 */
@TableName(value = "rehab_patient_crm_binding")
@KeySequence("rehab_patient_crm_binding_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabPatientCrmBindingDO extends BaseDO {

    @TableId
    private Long id;

    private Long patientId;
    private Long crmCustomerId;
    /**
     * bound / unbound / conflict / pending_sync
     */
    private String bindStatus;
    /**
     * manual / import / sync
     */
    private String bindSource;
    private String syncStatus;
    private String syncMessage;
    private LocalDateTime bindTime;
    private LocalDateTime lastSyncTime;

}
