package cn.iocoder.yudao.module.rehab.controller.admin.app.vo;

import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "管理端小程序 - 患者摘要 Response VO")
@Data
public class AppAdminPatientSummaryRespVO {

    @Schema(description = "患者基本信息")
    private RehabPatientRespVO patient;

    @Schema(description = "最新评估编号", example = "ASM202603100001")
    private String latestAssessmentNo;

    @Schema(description = "最新评估日期")
    private LocalDate latestAssessmentDate;

    @Schema(description = "最新评估摘要")
    private String latestAssessmentSummary;

    @Schema(description = "最新报告编号", example = "RPT202603100001")
    private String latestReportNo;

    @Schema(description = "最新报告摘要")
    private String latestReportSummary;

    @Schema(description = "当前 active 计划编号")
    private String activePlanNo;

    @Schema(description = "当前 active 计划状态")
    private String activePlanStatus;

    @Schema(description = "最近进度摘要")
    private String latestProgressSummary;

    @Schema(description = "待处理触发数量")
    private Long pendingTriggerCount;

    @Schema(description = "最近随访备注")
    private List<AppAdminFollowupNoteRespVO> recentFollowupNotes;
}
