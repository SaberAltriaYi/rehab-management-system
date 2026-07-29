package cn.iocoder.yudao.module.rehab.dal.dataobject.followup;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 随访备注
 */
@TableName("rehab_followup_note")
@KeySequence("rehab_followup_note_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabFollowupNoteDO extends BaseDO {

    @TableId
    private Long id;

    private Long patientId;
    private Long episodeId;
    private Long therapistUserId;
    /**
     * followup / reminder / pain_feedback / adherence_comment
     */
    private String noteType;
    private String content;
    /**
     * internal / patient_visible
     */
    private String visibilityType;
}
