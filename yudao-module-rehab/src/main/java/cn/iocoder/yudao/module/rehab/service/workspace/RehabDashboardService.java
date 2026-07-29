package cn.iocoder.yudao.module.rehab.service.workspace;

import cn.iocoder.yudao.module.rehab.controller.admin.workspace.vo.*;

import java.util.List;

public interface RehabDashboardService {

    RehabDashboardSummaryRespVO getTherapistSummary(Long operatorUserId);

    RehabDashboardRecentItemsRespVO getTherapistRecentItems(Long operatorUserId);

    RehabOpsDashboardSummaryRespVO getOpsSummary(Long operatorUserId);

    List<RehabOpsWorkloadRespVO> getOpsWorkload(Long operatorUserId);

    List<RehabOpsRiskOverviewRespVO> getOpsRiskOverview(Long operatorUserId);

}
