package cn.iocoder.yudao.module.rehab.controller.admin.episode.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - episode 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RehabEpisodePageReqVO extends PageParam {

    @Schema(description = "患者编号", example = "1")
    private Long patientId;

    @Schema(description = "状态", example = "active")
    private String status;

    @Schema(description = "阶段", example = "待评估")
    private String currentStage;

}
