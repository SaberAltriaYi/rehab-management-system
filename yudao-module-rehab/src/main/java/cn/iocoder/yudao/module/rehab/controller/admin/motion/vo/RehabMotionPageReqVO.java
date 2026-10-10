package cn.iocoder.yudao.module.rehab.controller.admin.motion.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 动作评估分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RehabMotionPageReqVO extends PageParam {

    @Schema(description = "患者编号", example = "10001")
    private Long patientId;

    @Schema(description = "流程状态", example = "PENDING_REVIEW")
    private String status;

    @Schema(description = "评估类型 initial/follow_up/discharge", example = "initial")
    private String visitType;

}
