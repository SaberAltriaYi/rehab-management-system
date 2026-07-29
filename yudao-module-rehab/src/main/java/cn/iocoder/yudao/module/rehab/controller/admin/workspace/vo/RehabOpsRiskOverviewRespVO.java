package cn.iocoder.yudao.module.rehab.controller.admin.workspace.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 机构运营看板风险分布 Response VO")
@Data
public class RehabOpsRiskOverviewRespVO {

    @Schema(description = "提醒类型")
    private String alertType;

    @Schema(description = "数量")
    private Long count;

}
