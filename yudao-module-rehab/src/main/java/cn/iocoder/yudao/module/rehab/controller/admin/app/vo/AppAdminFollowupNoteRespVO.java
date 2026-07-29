package cn.iocoder.yudao.module.rehab.controller.admin.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理端小程序 - 随访备注 Response VO")
@Data
public class AppAdminFollowupNoteRespVO {

    @Schema(description = "备注编号", example = "1")
    private Long id;

    @Schema(description = "患者编号", example = "10001")
    private Long patientId;

    @Schema(description = "episode 编号", example = "13001")
    private Long episodeId;

    @Schema(description = "治疗师编号", example = "2")
    private Long therapistUserId;

    @Schema(description = "治疗师名称", example = "王治疗师")
    private String therapistName;

    @Schema(description = "备注类型", example = "followup")
    private String noteType;

    @Schema(description = "可见性", example = "internal")
    private String visibilityType;

    @Schema(description = "备注内容")
    private String content;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
