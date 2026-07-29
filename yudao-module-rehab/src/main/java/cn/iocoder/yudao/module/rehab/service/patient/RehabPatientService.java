package cn.iocoder.yudao.module.rehab.service.patient;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 患者主档案 Service
 */
public interface RehabPatientService {

    RehabPatientCreateRespVO createPatient(@Valid RehabPatientCreateReqVO createReqVO, Long operatorUserId);

    void updatePatient(@Valid RehabPatientUpdateReqVO updateReqVO, Long operatorUserId);

    void deletePatient(Long id, Long operatorUserId);

    RehabPatientDetailRespVO getPatientDetail(Long id, Long operatorUserId);

    PageResult<RehabPatientRespVO> getPatientPage(RehabPatientPageReqVO pageReqVO, Long operatorUserId);

    List<RehabPatientExportRespVO> getPatientExportList(RehabPatientPageReqVO reqVO, Long operatorUserId);

    RehabPatientCrmBindingRespVO getCrmBinding(Long patientId, Long operatorUserId);

    RehabPatientMemberBindingRespVO getMemberBinding(Long patientId, Long operatorUserId);

    RehabCrmConflictCheckRespVO checkCrmConflict(@Valid RehabPatientCheckCrmConflictReqVO reqVO);

    RehabPatientCrmBindingRespVO bindCrm(@Valid RehabPatientBindCrmReqVO reqVO, Long operatorUserId);

    RehabPatientCrmBindingRespVO unbindCrm(@Valid RehabPatientUnbindCrmReqVO reqVO, Long operatorUserId);

    void assignTherapist(@Valid RehabPatientAssignReqVO reqVO, Long operatorUserId);

    void transferTherapist(@Valid RehabPatientTransferReqVO reqVO, Long operatorUserId);

    List<RehabTherapistAssignmentRespVO> getAssignmentHistory(Long patientId, Long operatorUserId);

    List<RehabPatientOperationLogRespVO> getOperationLogList(Long patientId, Long operatorUserId);

    void validatePatientReadable(Long patientId, Long operatorUserId);

}
