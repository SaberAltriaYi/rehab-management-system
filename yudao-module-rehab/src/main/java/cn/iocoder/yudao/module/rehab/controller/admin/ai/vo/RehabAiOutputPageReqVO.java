package cn.iocoder.yudao.module.rehab.controller.admin.ai.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - AI 输出分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RehabAiOutputPageReqVO extends PageParam {

    @Schema(description = "患者编号", example = "10001")
    private Long patientId;

    @Schema(description = "输出类型", example = "therapist_summary")
    private String outputType;

    @Schema(description = "目标对象类型", example = "report")
    private String targetObjectType;

    @Schema(description = "审核状态", example = "pending")
    private String reviewStatus;

    @Schema(description = "安全状态", example = "passed")
    private String safetyStatus;

    @Schema(description = "创建时间")
    private LocalDateTime[] createTime;
}
