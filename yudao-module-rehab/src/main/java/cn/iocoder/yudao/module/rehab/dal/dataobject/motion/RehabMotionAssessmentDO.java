package cn.iocoder.yudao.module.rehab.dal.dataobject.motion;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 动作评估（租户隔离：继承 TenantBaseDO，由多租户拦截器自动过滤 tenant_id）
 */
@TableName("rehab_motion_assessment")
@KeySequence("rehab_motion_assessment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabMotionAssessmentDO extends TenantBaseDO {

    /** 动作评估编号 */
    @TableId
    private Long id;
    /** 患者编号 */
    private Long patientId;
    /** 康复周期编号 */
    private Long episodeId;
    /** 关联的康复评估记录 */
    private Long assessmentRecordId;
    /** 对比基线（初评）动作评估编号 */
    private Long baselineId;
    /** initial 初评 / followup 复评 */
    private String visitType;
    /** 评估体系：FMS,NASM_CES,YBT_LQ,TUCK_JUMP,LESS */
    private String protocolFamilies;
    /** 标题 */
    private String title;
    /** 采集时间 */
    private LocalDateTime captureTime;
    /** 流程状态 */
    private String status;
    /** upload 本地导出 / opencap 接口拉取 */
    private String dataSource;
    /** OpenCap 会话 UUID */
    private String opencapSessionId;
    /** 相机数量 */
    private Integer cameraCount;
    /** OpenSim 模型 */
    private String modelName;
    /** 患者同意保存视频 */
    private Boolean videoConsent;
    /** 允许发送去标识化指标给 AI */
    private Boolean aiAllowed;
    /** 文献参考分组 male/female */
    private String sexGroup;
    /** 左下肢长 cm（ASIS-内踝） */
    private BigDecimal limbLengthLeftCm;
    /** 右下肢长 cm（ASIS-内踝） */
    private BigDecimal limbLengthRightCm;
    /** Tuck Jump 计分版本 */
    private String tjaVariant;
    /** 人工输入：清除测试、YBT、TJA、LESS、NASM 观察 */
    private String manualInputsJson;
    /** 输入修订号 */
    private Integer inputRevision;
    /** 最近一次分析使用的输入修订号 */
    private Integer analyzedRevision;
    /** 引擎版本 */
    private String engineVersion;
    /** 规则版本 */
    private String ruleVersion;
    /** 结果结构版本 */
    private String resultSchemaVersion;
    /** 协议版本与 SHA-256 */
    private String protocolVersionsJson;
    /** 引擎结果 JSON 文件 */
    private Long resultFileId;
    /** 引擎结果 SHA-256 */
    private String resultSha256;
    /** 会话质控 pass/warn/fail */
    private String sessionQualityStatus;
    /** 分析完成时间 */
    private LocalDateTime analyzedTime;
    /** not_started/in_review/reviewed */
    private String reviewStatus;
    /** 负责治疗师 */
    private Long therapistUserId;
    /** 签署人 */
    private Long signedUserId;
    /** 签署时间 */
    private LocalDateTime signedTime;
    /** 备注 */
    private String remark;

}
