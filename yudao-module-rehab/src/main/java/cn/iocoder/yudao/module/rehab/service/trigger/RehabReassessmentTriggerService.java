package cn.iocoder.yudao.module.rehab.service.trigger;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.trigger.vo.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.progress.RehabProgressRecordDO;

public interface RehabReassessmentTriggerService {

    PageResult<RehabReassessmentTriggerRespVO> getTriggerPage(RehabReassessmentTriggerPageReqVO reqVO, Long operatorUserId);

    RehabReassessmentTriggerRespVO getTrigger(Long id, Long operatorUserId);

    Long createTrigger(RehabReassessmentTriggerCreateReqVO reqVO, Long operatorUserId);

    void acknowledge(RehabReassessmentTriggerHandleReqVO reqVO, Long operatorUserId);

    RehabTriggerConvertRespVO convertToReassessment(RehabReassessmentTriggerHandleReqVO reqVO, Long operatorUserId);

    void dismiss(RehabReassessmentTriggerHandleReqVO reqVO, Long operatorUserId);

    void evaluateByPlan(Long planId, RehabProgressRecordDO latestProgress, Long operatorUserId);

}
