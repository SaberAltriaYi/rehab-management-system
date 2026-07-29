package cn.iocoder.yudao.module.rehab.service.plan;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.plan.vo.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;

import java.util.List;

public interface RehabCarePlanService {

    RehabCarePlanCreateRespVO createPlan(RehabCarePlanCreateReqVO reqVO, Long operatorUserId);

    void updatePlan(RehabCarePlanUpdateReqVO reqVO, Long operatorUserId);

    void activatePlan(RehabCarePlanChangeStatusReqVO reqVO, Long operatorUserId);

    void pausePlan(RehabCarePlanChangeStatusReqVO reqVO, Long operatorUserId);

    void completePlan(RehabCarePlanChangeStatusReqVO reqVO, Long operatorUserId);

    RehabCarePlanCreateRespVO copyPlan(RehabCarePlanCopyReqVO reqVO, Long operatorUserId);

    void deletePlan(Long id, Long operatorUserId);

    RehabCarePlanRespVO getPlan(Long id, Long operatorUserId);

    PageResult<RehabCarePlanRespVO> getPlanPage(RehabCarePlanPageReqVO reqVO, Long operatorUserId);

    List<RehabPlanOperationLogRespVO> getOperationLogList(Long planId, Long operatorUserId);

    RehabCarePlanDO validatePlanReadable(Long id, Long operatorUserId);

}
