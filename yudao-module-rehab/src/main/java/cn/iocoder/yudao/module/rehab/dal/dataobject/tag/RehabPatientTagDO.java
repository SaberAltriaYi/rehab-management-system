package cn.iocoder.yudao.module.rehab.dal.dataobject.tag;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 患者标签 DO
 */
@TableName(value = "rehab_patient_tag")
@KeySequence("rehab_patient_tag_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabPatientTagDO extends BaseDO {

    @TableId
    private Long id;

    private Long patientId;
    private String tagName;
    private String tagType;
    private String color;

}
