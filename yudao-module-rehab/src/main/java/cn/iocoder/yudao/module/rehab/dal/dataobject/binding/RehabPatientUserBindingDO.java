package cn.iocoder.yudao.module.rehab.dal.dataobject.binding;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 患者与小程序用户绑定
 */
@TableName("rehab_patient_user_binding")
@KeySequence("rehab_patient_user_binding_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabPatientUserBindingDO extends BaseDO {

    @TableId
    private Long id;

    private Long patientId;
    /**
     * 会员用户编号（member_user.id）
     */
    private Long appUserId;
    /**
     * self / caregiver / imported
     */
    private String bindType;
    /**
     * active / pending / disabled
     */
    private String bindStatus;

    private String phone;
    private String nickname;
    private LocalDateTime lastLoginTime;
}
