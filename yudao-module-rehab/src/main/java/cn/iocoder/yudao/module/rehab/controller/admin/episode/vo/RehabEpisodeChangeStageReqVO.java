package cn.iocoder.yudao.module.rehab.controller.admin.episode.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - episode 修改阶段 Request VO")
@Data
public class RehabEpisodeChangeStageReqVO {

    @NotNull(message = "episode 编号不能为空")
    private Long id;

    @NotBlank(message = "新阶段不能为空")
    private String currentStage;

    @Schema(description = "新状态（可选）", example = "paused")
    private String status;

    @Schema(description = "变更备注")
    private String remark;

}
