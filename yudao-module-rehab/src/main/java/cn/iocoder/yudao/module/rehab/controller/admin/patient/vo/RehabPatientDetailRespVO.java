package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import cn.iocoder.yudao.module.rehab.controller.admin.episode.vo.RehabEpisodeRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 患者详情 Response VO")
@Data
public class RehabPatientDetailRespVO {

    private RehabPatientRespVO patient;

    private RehabPatientCrmBindingRespVO crmBinding;

    private RehabPatientMemberBindingRespVO memberBinding;

    private RehabTherapistAssignmentRespVO currentPrimaryAssignment;

    private RehabEpisodeRespVO currentEpisode;

    private List<RehabTherapistAssignmentRespVO> assignmentHistory;

    private List<RehabPatientOperationLogRespVO> operationLogs;

}
