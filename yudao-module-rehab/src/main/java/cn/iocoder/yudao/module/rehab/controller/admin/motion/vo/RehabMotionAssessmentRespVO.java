package cn.iocoder.yudao.module.rehab.controller.admin.motion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 动作评估 Response VO")
@Data
public class RehabMotionAssessmentRespVO {

    private Long id;
    private Long patientId;
    private String patientName;
    private String patientNo;
    private Long episodeId;
    private Long assessmentRecordId;
    private Long baselineId;
    private String visitType;
    private List<String> protocolFamilies;
    private String title;
    private LocalDateTime captureTime;
    private String status;
    private String statusLabel;
    private String dataSource;
    private String opencapSessionId;
    private Integer cameraCount;
    private String modelName;
    private Boolean videoConsent;
    private Boolean aiAllowed;
    private String sexGroup;
    private BigDecimal limbLengthLeftCm;
    private BigDecimal limbLengthRightCm;
    private String tjaVariant;
    private Integer inputRevision;
    private Integer analyzedRevision;
    @Schema(description = "输入已变更但尚未重新分析")
    private Boolean stale;
    private String engineVersion;
    private String ruleVersion;
    private String resultSchemaVersion;
    private String sessionQualityStatus;
    private LocalDateTime analyzedTime;
    private String reviewStatus;
    private Long therapistUserId;
    private Long signedUserId;
    private LocalDateTime signedTime;
    private String remark;
    private LocalDateTime createTime;
    @Schema(description = "FMS 总分（仅 7 项全部审核后有值，描述性）")
    private BigDecimal fmsTotal;

}
