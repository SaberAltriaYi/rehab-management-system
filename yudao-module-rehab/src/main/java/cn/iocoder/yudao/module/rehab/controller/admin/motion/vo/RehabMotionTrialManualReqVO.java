package cn.iocoder.yudao.module.rehab.controller.admin.motion.vo;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - Trial 临床信息（有效性/疼痛/人工判定）Request VO")
@Data
public class RehabMotionTrialManualReqVO {

    @NotNull
    private Long trialId;

    @Schema(description = "是否有效；无效须填原因（无效≠1 分）")
    private Boolean valid;

    @Size(max = 255)
    private String invalidReason;

    @Schema(description = "是否疼痛；null = 未记录（未记录时 FMS 不给正式建议分）")
    private Boolean pain;

    @Schema(description = "人工判定 {criterion_key: true/false/null}")
    private Map<String, Object> manualCriteria;

    @Schema(description = "人工测量 {value_key: number}，如肩灵活性拳距与手长")
    private Map<String, Object> manualValues;

    @Schema(description = "修改原因（已有分析结果时必填）")
    @Size(max = 255)
    private String reason;

}
