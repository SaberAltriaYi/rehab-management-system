package cn.iocoder.yudao.module.rehab.controller.admin.motion.vo;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 发起分析/任务 Request VO")
@Data
public class RehabMotionProcessReqVO {

    @NotNull
    private Long assessmentId;

    @Schema(description = "幂等键（前端每次点击生成一次，重复提交返回同一任务）")
    @Pattern(regexp = "^$|^[A-Za-z0-9_\\-]{8,64}$")
    private String idempotencyKey;

}
