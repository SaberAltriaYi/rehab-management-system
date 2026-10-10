package cn.iocoder.yudao.module.rehab.controller.admin.motion.vo;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - AI 草稿审核 Request VO")
@Data
public class RehabMotionAiReviewReqVO {

    @NotNull
    private Long draftId;

    @NotNull
    @Pattern(regexp = "^(accept|reject)$")
    private String action;

    @Schema(description = "治疗师编辑后的文本（接受时可选）")
    @Size(max = 20000)
    private String editedText;

}
