package cn.iocoder.yudao.module.rehab.controller.admin.plan.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 康复计划分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RehabCarePlanPageReqVO extends PageParam {

    @Schema(description = "患者编号", example = "10001")
    private Long patientId;

    @Schema(description = "episode 编号", example = "13001")
    private Long episodeId;

    @Schema(description = "主责治疗师", example = "100")
    private Long primaryTherapistUserId;

    @Schema(description = "计划状态", example = "active")
    private String status;

    @Schema(description = "计划类型", example = "rehab")
    private String planType;

    @Schema(description = "创建时间")
    private LocalDateTime[] createTime;

}
