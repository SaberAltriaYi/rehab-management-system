package cn.iocoder.yudao.module.rehab.controller.admin.motion.vo;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 评估级人工输入（清除测试、YBT、TJA、LESS、NASM 观察）Request VO")
@Data
public class RehabMotionManualInputsReqVO {

    @NotNull
    private Long assessmentId;

    @NotNull
    private Map<String, Object> inputs;

    @Size(max = 255)
    private String reason;

}
