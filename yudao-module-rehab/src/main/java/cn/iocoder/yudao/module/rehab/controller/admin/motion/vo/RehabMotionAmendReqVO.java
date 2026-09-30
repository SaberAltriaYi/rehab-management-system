package cn.iocoder.yudao.module.rehab.controller.admin.motion.vo;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 修订已签署评估 Request VO")
@Data
public class RehabMotionAmendReqVO {

    @NotNull
    private Long assessmentId;

    @NotBlank(message = "修订须填写原因")
    @Size(max = 255)
    private String reason;

}
