package cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;

@Schema(description = "管理后台 - 评估分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RehabAssessmentPageReqVO extends PageParam {

    @Schema(description = "患者编号", example = "10001")
    private Long patientId;

    @Schema(description = "episode 编号", example = "13001")
    private Long episodeId;

    @Schema(description = "关键字（患者姓名/患者编号）", example = "王")
    private String keyword;

    @Schema(description = "评估类型", example = "static_assessment")
    private String assessmentType;

    @Schema(description = "评估日期范围")
    private LocalDate[] assessmentDate;

    @Schema(description = "评估人", example = "100")
    private Long assessorUserId;

    @Schema(description = "评估状态", example = "completed")
    private String status;

}
