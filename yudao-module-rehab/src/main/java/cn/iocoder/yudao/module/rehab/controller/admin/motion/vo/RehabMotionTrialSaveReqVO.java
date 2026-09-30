package cn.iocoder.yudao.module.rehab.controller.admin.motion.vo;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 动作评估 Trial 映射保存 Request VO（覆盖式保存）")
@Data
public class RehabMotionTrialSaveReqVO {

    @NotNull
    private Long assessmentId;

    @Valid
    @NotNull
    @Size(max = 200)
    private List<Item> trials;

    @Data
    public static class Item {

        @Schema(description = "已有 Trial 编号；为空则新增")
        private Long id;

        @Schema(description = "Trial 键；为空时自动生成", example = "ds_1")
        @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9_.:\\-]{0,63}$")
        private String trialKey;

        @NotNull
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$")
        private String testCode;

        @NotNull
        @Pattern(regexp = "^(bilateral|left|right)$")
        private String side;

        @Schema(description = "条件，如 heels_elevated / knee_bent")
        @Pattern(regexp = "^$|^[a-z0-9_]{1,32}$")
        private String conditionCode;

        @Min(1) @Max(20)
        private Integer attemptNo;

        @Schema(description = "OpenCap trial 名（= .mot 文件名）")
        @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9_.\\-]{0,127}$")
        private String opencapTrialName;

        @Schema(description = "OpenCap trial ID（UUID）")
        @Pattern(regexp = "^$|^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        private String opencapTrialId;

        private Integer sortNo;
    }

}
