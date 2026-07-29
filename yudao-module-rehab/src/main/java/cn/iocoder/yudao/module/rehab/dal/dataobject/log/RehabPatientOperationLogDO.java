package cn.iocoder.yudao.module.rehab.dal.dataobject.log;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 患者操作日志 DO
 */
@TableName(value = "rehab_patient_operation_log")
@KeySequence("rehab_patient_operation_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabPatientOperationLogDO extends BaseDO {

    @TableId
    private Long id;

    private Long patientId;
    private String operationType;
    private Long operatorUserId;
    private String beforeDataJson;
    private String afterDataJson;
    private String remark;

}
