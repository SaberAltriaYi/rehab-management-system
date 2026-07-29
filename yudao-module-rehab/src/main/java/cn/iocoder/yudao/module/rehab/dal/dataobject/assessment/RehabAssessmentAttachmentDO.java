package cn.iocoder.yudao.module.rehab.dal.dataobject.assessment;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 评估附件
 */
@TableName(value = "rehab_assessment_attachment")
@KeySequence("rehab_assessment_attachment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabAssessmentAttachmentDO extends BaseDO {

    @TableId
    private Long id;

    private Long assessmentId;
    private String moduleType;
    private String fileName;
    private String fileType;
    private String filePath;
    private Long fileSize;
    private Long uploadUserId;
    private String parseStatus;
    private String parseMessage;

}
