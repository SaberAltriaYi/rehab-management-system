package cn.iocoder.yudao.module.rehab.controller.admin.motion.vo;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 评分审核 Request VO")
@Data
public class RehabMotionScoreReviewReqVO {

    @NotNull
    private Long scoreId;

    @NotBlank
    @Pattern(regexp = "^(confirmed|modified|not_applicable)$")
    private String finalStatus;

    @Schema(description = "最终分（confirmed 时取系统分，可不填）")
    private BigDecimal finalScore;

    @Schema(description = "原因（modified / not_applicable 必填）")
    @Size(max = 500)
    private String reason;

}
