package cn.iocoder.yudao.module.rehab.dal.dataobject.motion;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 动作评估签署报告（租户隔离：继承 TenantBaseDO，由多租户拦截器自动过滤 tenant_id）
 */
@TableName("rehab_motion_report")
@KeySequence("rehab_motion_report_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabMotionReportDO extends TenantBaseDO {

    /** 编号 */
    @TableId
    private Long id;
    /** 动作评估编号 */
    private Long assessmentId;
    /** therapist/patient */
    private String reportType;
    /** 版本 */
    private Integer versionNo;
    /** signed/superseded */
    private String status;
    /** 报告 JSON */
    private String contentJson;
    /** 报告内容 SHA-256 */
    private String contentSha256;
    /** PDF 文件 */
    private Long pdfFileId;
    /** pending/ready/failed */
    private String pdfStatus;
    /** 签署人 */
    private Long signedUserId;
    /** 签署时间 */
    private LocalDateTime signedTime;
    /** 签署人姓名 */
    private String signerName;
    /** 采用的 AI 草稿 */
    private Long aiDraftId;
    /** 引擎版本 */
    private String engineVersion;
    /** 规则版本 */
    private String ruleVersion;
    /** 提示词版本 */
    private String promptVersion;
    /** AI 模型 */
    private String aiModel;

}
