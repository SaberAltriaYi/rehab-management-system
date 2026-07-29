package cn.iocoder.yudao.module.rehab.controller.admin.workspace.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 机构运营看板治疗师负载 Response VO")
@Data
public class RehabOpsWorkloadRespVO {

    private Long therapistUserId;
    private String therapistName;
    private Long patientCount;
    private Long activePlanCount;

}
