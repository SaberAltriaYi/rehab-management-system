package cn.iocoder.yudao.module.rehab.dal.dataobject.assessment;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 评估操作日志
 */
@TableName(value = "rehab_assessment_operation_log")
@KeySequence("rehab_assessment_operation_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabAssessmentOperationLogDO extends BaseDO {

    @TableId
    private Long id;

    private Long assessmentId;
    private String operationType;
    private Long operatorUserId;
    private String beforeDataJson;
    private String afterDataJson;
    private String remark;

}
