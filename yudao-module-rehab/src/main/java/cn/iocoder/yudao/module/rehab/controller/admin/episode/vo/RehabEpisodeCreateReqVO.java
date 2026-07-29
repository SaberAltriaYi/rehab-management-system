package cn.iocoder.yudao.module.rehab.controller.admin.episode.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 创建 episode Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class RehabEpisodeCreateReqVO extends RehabEpisodeBaseVO {

    @NotNull(message = "患者编号不能为空")
    private Long patientId;

}
