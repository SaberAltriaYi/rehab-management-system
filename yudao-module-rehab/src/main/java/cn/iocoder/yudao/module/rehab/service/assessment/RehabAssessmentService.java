package cn.iocoder.yudao.module.rehab.service.assessment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 评估管理 Service
 */
public interface RehabAssessmentService {

    RehabAssessmentCreateRespVO createAssessment(RehabAssessmentCreateReqVO reqVO, Long operatorUserId);

    void updateAssessment(RehabAssessmentUpdateReqVO reqVO, Long operatorUserId);

    void deleteAssessment(Long id, Long operatorUserId);

    void archiveAssessment(RehabAssessmentArchiveReqVO reqVO, Long operatorUserId);

    RehabAssessmentDetailRespVO getAssessment(Long id, Long operatorUserId);

    PageResult<RehabAssessmentRespVO> getAssessmentPage(RehabAssessmentPageReqVO reqVO, Long operatorUserId);

    List<RehabAssessmentModuleDataRespVO> getModuleDataList(Long assessmentId, Long operatorUserId);

    RehabAssessmentModuleDataRespVO saveModuleData(RehabAssessmentModuleDataSaveReqVO reqVO, Long operatorUserId);

    Map<String, Object> getSfmaBookProtocol();

    List<RehabAssessmentAttachmentRespVO> getAttachmentList(Long assessmentId, Long operatorUserId);

    RehabAssessmentAttachmentRespVO uploadAttachment(Long assessmentId, String moduleType, MultipartFile file, Long operatorUserId);

    RehabAssessmentAttachmentFile downloadAttachment(Long attachmentId, Long operatorUserId);

    List<RehabAssessmentOperationLogRespVO> getOperationLogList(Long assessmentId, Long operatorUserId);

}
