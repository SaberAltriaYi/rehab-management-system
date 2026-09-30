package cn.iocoder.yudao.module.rehab.dal.dataobject.motion;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 动作评估协议版本登记（租户隔离：继承 TenantBaseDO，由多租户拦截器自动过滤 tenant_id）
 */
@TableName("rehab_motion_protocol_version")
@KeySequence("rehab_motion_protocol_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabMotionProtocolVersionDO extends TenantBaseDO {

    /** 编号 */
    @TableId
    private Long id;
    /** 协议代码 */
    private String protocolCode;
    /** 协议 ID */
    private String protocolId;
    /** 协议版本 */
    private String protocolVersion;
    /** 来源 */
    private String source;
    /** 协议文件 SHA-256 */
    private String fileSha256;
    /** 来源表格 SHA-256 */
    private String sourceSha256;

}
