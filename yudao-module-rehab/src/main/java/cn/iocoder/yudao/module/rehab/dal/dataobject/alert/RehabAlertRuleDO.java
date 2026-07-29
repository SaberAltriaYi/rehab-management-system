package cn.iocoder.yudao.module.rehab.dal.dataobject.alert;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 提醒规则
 */
@TableName("rehab_alert_rule")
@KeySequence("rehab_alert_rule_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabAlertRuleDO extends BaseDO {

    @TableId
    private Long id;

    private String ruleCode;
    private String ruleName;
    private String alertType;
    private Boolean enabled;
    private String scopeType;
    private String conditionJson;
    private String severity;
    private String targetRoleType;
    private String notifyChannelsJson;
    private Integer cooldownHours;
}
