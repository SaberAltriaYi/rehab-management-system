package cn.iocoder.yudao.module.rehab.dal.dataobject.motion;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 动作评估 AI 草稿（租户隔离：继承 TenantBaseDO，由多租户拦截器自动过滤 tenant_id）
 */
@TableName("rehab_motion_ai_draft")
@KeySequence("rehab_motion_ai_draft_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabMotionAiDraftDO extends TenantBaseDO {

    /** 编号 */
    @TableId
    private Long id;
    /** 动作评估编号 */
    private Long assessmentId;
    /** 任务编号 */
    private Long taskId;
    /** generated/fallback/accepted/rejected/stale */
    private String status;
    /** 提供方 */
    private String provider;
    /** 模型 */
    private String model;
    /** 提供方请求 ID */
    private String providerRequestId;
    /** 耗时 */
    private Long latencyMs;
    /** Token 用量 */
    private String tokenUsageJson;
    /** 提示词版本 */
    private String promptVersion;
    /** 去标识化输入 SHA-256 */
    private String inputHash;
    /** 结构化输出（已校验） */
    private String contentJson;
    /** 渲染文本 */
    private String renderedText;
    /** 证据引用 */
    private String evidenceRefsJson;
    /** passed/downgraded/blocked */
    private String safetyStatus;
    /** 校验说明 */
    private String validationMessage;
    /** 降级原因 */
    private String fallbackReason;
    /** 审核人 */
    private Long reviewedUserId;
    /** 审核时间 */
    private LocalDateTime reviewedTime;
    /** 治疗师编辑后文本 */
    private String editedText;
    /** 分析修订号 */
    private Integer analysisRevision;

}
