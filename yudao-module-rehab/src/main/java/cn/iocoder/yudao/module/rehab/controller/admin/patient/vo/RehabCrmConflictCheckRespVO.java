package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - CRM 冲突检查 Response VO")
@Data
public class RehabCrmConflictCheckRespVO {

    @Schema(description = "是否冲突")
    private Boolean conflict;

    @Schema(description = "冲突患者编号列表")
    private List<Long> conflictPatientIds;

    @Schema(description = "冲突提示")
    private String message;

}
