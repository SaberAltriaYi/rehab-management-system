package cn.iocoder.yudao.module.rehab.dal.dataobject.assignment;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 患者治疗师归属记录 DO
 */
@TableName(value = "rehab_therapist_assignment")
@KeySequence("rehab_therapist_assignment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabTherapistAssignmentDO extends BaseDO {

    @TableId
    private Long id;

    private Long patientId;
    private Long therapistUserId;
    /**
     * primary / collaborator
     */
    private String roleType;
    /**
     * active / transferred / closed
     */
    private String assignStatus;
    private String assignReason;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long assignedBy;
    private Long transferFromUserId;
    private Long transferToUserId;
    private String remark;

}
