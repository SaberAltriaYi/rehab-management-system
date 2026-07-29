package cn.iocoder.yudao.module.rehab.dal.dataobject.assessment;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 评估模块数据子表
 */
@TableName(value = "rehab_assessment_module_data")
@KeySequence("rehab_assessment_module_data_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabAssessmentModuleDataDO extends BaseDO {

    @TableId
    private Long id;

    private Long assessmentId;
    private String moduleType;
    private String moduleStatus;
    private String dataJson;
    private String sourceType;
    private String version;
    private String note;

}
