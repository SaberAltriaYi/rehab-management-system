package cn.iocoder.yudao.module.rehab.service.app;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.app.patient.vo.*;

import java.util.List;

public interface RehabAppPatientService {

    AppPatientLoginRespVO login(AppPatientLoginReqVO reqVO);

    Long bindPatient(AppPatientAuthBindReqVO reqVO, Long appUserId);

    AppPatientHomeSummaryRespVO getHomeSummary(Long appUserId);

    PageResult<AppPatientReportRespVO> getReportPage(Integer pageNo, Integer pageSize, Long appUserId);

    AppPatientReportRespVO getReport(Long reportId, Long appUserId);

    AppPatientCurrentPlanRespVO getCurrentPlan(Long appUserId);

    List<AppPatientTaskRespVO> getTodayTasks(Long appUserId);

    Long createCheckin(AppPatientCheckinCreateReqVO reqVO, Long appUserId);

    PageResult<AppPatientCheckinHistoryRespVO> getCheckinHistory(Integer pageNo, Integer pageSize, Long appUserId);

    AppPatientProfileRespVO getProfile(Long appUserId);

    PageResult<AppPatientNotificationRespVO> getNotificationPage(Integer pageNo, Integer pageSize, Long appUserId);

    void markNotificationRead(Long notificationId, Long appUserId);

    AppPatientAiOutputRespVO getLatestAiSummary(Long appUserId);

    AppPatientAiOutputRespVO getLatestAiFollowup(Long appUserId);
}
