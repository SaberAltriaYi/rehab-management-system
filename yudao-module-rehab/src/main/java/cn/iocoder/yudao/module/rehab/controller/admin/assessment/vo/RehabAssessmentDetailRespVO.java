package cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo;

import cn.iocoder.yudao.module.rehab.controller.admin.episode.vo.RehabEpisodeRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 评估详情 Response VO")
@Data
public class RehabAssessmentDetailRespVO {

    private RehabAssessmentRespVO assessment;
    private RehabPatientRespVO patient;
    private RehabEpisodeRespVO episode;
    private List<RehabAssessmentModuleDataRespVO> moduleDataList;
    private List<RehabAssessmentAttachmentRespVO> attachments;
    private List<RehabAssessmentOperationLogRespVO> operationLogs;

}
