package cn.iocoder.yudao.module.rehab.controller.admin.motion.vo;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 动作评估创建/修改 Request VO")
@Data
public class RehabMotionSaveReqVO {

    @Schema(description = "编号（修改时必填）")
    private Long id;

    @Schema(description = "患者编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "患者不能为空")
    private Long patientId;

    @Schema(description = "康复周期编号")
    private Long episodeId;

    @Schema(description = "关联的常规评估编号")
    private Long assessmentRecordId;

    @Schema(description = "对比基线（同一患者的既往动作评估）")
    private Long baselineId;

    @Schema(description = "评估类型", example = "initial")
    @Pattern(regexp = "^(initial|follow_up|discharge)$", message = "评估类型不合法")
    private String visitType;

    @Schema(description = "评估体系", example = "[\"FMS\",\"NASM_CES\"]")
    @NotEmpty(message = "至少选择一个评估体系")
    @Size(max = 5)
    private List<@Pattern(regexp = "^(FMS|NASM_CES|YBT_LQ|TUCK_JUMP|LESS)$", message = "评估体系不合法") String> protocolFamilies;

    @Schema(description = "标题")
    @Size(max = 128)
    private String title;

    @Schema(description = "采集时间")
    private LocalDateTime captureTime;

    @Schema(description = "数据来源 upload/opencap", example = "upload")
    @NotNull
    @Pattern(regexp = "^(upload|opencap)$", message = "数据来源不合法")
    private String dataSource;

    @Schema(description = "OpenCap 会话 ID（UUID）")
    @Pattern(regexp = "^$|^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "OpenCap 会话 ID 须为 UUID")
    private String opencapSessionId;

    @Schema(description = "相机数量", example = "2")
    @NotNull
    @Min(value = 2, message = "OpenCap 至少需要 2 台相机")
    @Max(8)
    private Integer cameraCount;

    @Schema(description = "患者已书面同意保存视频")
    private Boolean videoConsent;

    @Schema(description = "允许将去标识化指标发送给 AI 生成草稿")
    private Boolean aiAllowed;

    @Schema(description = "性别分组（仅用于引擎读取 OpenSim 模型时的说明，不参与评分）")
    @Pattern(regexp = "^$|^(male|female)$")
    private String sexGroup;

    @Schema(description = "左下肢长度 cm（ASIS 至内踝，YBT 标准化用）")
    @DecimalMin("40") @DecimalMax("140")
    private BigDecimal limbLengthLeftCm;

    @Schema(description = "右下肢长度 cm")
    @DecimalMin("40") @DecimalMax("140")
    private BigDecimal limbLengthRightCm;

    @Schema(description = "团身跳版本", example = "TJA_MODIFIED_0_2")
    @Pattern(regexp = "^$|^(TJA_MODIFIED_0_2|TJA_MYER_2008_DICHOTOMOUS)$")
    private String tjaVariant;

    @Schema(description = "负责治疗师")
    private Long therapistUserId;

    @Schema(description = "备注（不会发送给 AI）")
    @Size(max = 500)
    private String remark;

}
