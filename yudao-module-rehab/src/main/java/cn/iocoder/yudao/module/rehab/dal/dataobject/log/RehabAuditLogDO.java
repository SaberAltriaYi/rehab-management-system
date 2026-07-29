package cn.iocoder.yudao.module.rehab.dal.dataobject.log;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 统一审计日志
 */
@TableName("rehab_audit_log")
@KeySequence("rehab_audit_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabAuditLogDO extends BaseDO {

    @TableId
    private Long id;

    private String moduleType;
    private Long moduleId;
    private String operationType;
    private Long operatorUserId;
    private String operatorRole;
    private String beforeDataJson;
    private String afterDataJson;
    private String ip;
    private String userAgent;
    private String resultStatus;
    private String remark;
}
