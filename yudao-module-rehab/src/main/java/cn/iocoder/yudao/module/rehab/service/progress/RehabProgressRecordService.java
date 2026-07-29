package cn.iocoder.yudao.module.rehab.service.progress;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.progress.vo.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.progress.RehabProgressRecordDO;

public interface RehabProgressRecordService {

    PageResult<RehabProgressRecordRespVO> getProgressPage(RehabProgressRecordPageReqVO reqVO, Long operatorUserId);

    RehabProgressRecordRespVO getProgress(Long id, Long operatorUserId);

    RehabProgressRecordRespVO recalculate(RehabProgressRecalculateReqVO reqVO, Long operatorUserId);

    RehabProgressRecordDO recalculateByPlan(Long planId, java.time.LocalDate anchorDate, Long operatorUserId, String remark);

    RehabProgressRecordDO getLatestByPlanId(Long planId, Long operatorUserId);

}
