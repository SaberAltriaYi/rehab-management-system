package cn.iocoder.yudao.module.rehab.controller.admin.report.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 报告分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RehabReportPageReqVO extends PageParam {

    @Schema(description = "患者编号", example = "10001")
    private Long patientId;

    @Schema(description = "episode 编号", example = "13001")
    private Long episodeId;

    @Schema(description = "评估编号", example = "20001")
    private Long assessmentId;

    @Schema(description = "关键字（患者姓名/患者编号）", example = "王")
    private String keyword;

    @Schema(description = "报告类型", example = "comprehensive")
    private String reportType;

    @Schema(description = "报告状态", example = "reviewed")
    private String reportStatus;

    @Schema(description = "生成模式", example = "auto")
    private String generationMode;

    @Schema(description = "生成人", example = "100")
    private Long generatedBy;

    @Schema(description = "创建时间")
    private LocalDateTime[] createTime;

}
