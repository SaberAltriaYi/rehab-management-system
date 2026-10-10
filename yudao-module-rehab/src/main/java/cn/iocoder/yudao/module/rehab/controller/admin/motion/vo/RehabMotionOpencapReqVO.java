package cn.iocoder.yudao.module.rehab.controller.admin.motion.vo;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - OpenCap 会话 Trial 列表 Request VO")
@Data
public class RehabMotionOpencapReqVO {

    @NotNull
    private Long assessmentId;

    @NotNull
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private String sessionId;

}
