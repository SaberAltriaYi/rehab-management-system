package cn.iocoder.yudao.module.rehab.controller.admin.motion.vo;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 签署 Request VO")
@Data
public class RehabMotionSignReqVO {

    @NotNull
    private Long assessmentId;

    @Schema(description = "治疗师确认已审阅全部评分、证据与局限性")
    @AssertTrue(message = "须确认已审阅")
    private Boolean confirmed;

}
