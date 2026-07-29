package cn.iocoder.yudao.module.rehab.service.app;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.app.vo.*;
import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.RehabAlertEventPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.RehabAlertEventRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo.RehabDailyCheckinRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;

public interface RehabAppAdminService {

    AuthLoginRespVO login(AuthLoginReqVO reqVO);

    AppAdminDashboardSummaryRespVO getDashboardSummary(Long loginUserId);

    PageResult<AppAdminPatientMiniRespVO> getMyPatientPage(RehabPatientPageReqVO reqVO, Long loginUserId);

    AppAdminPatientSummaryRespVO getPatientSummary(Long patientId, Long loginUserId);

    PageResult<RehabDailyCheckinRespVO> getPatientCheckins(Long patientId, Integer pageNo, Integer pageSize, Long loginUserId);

    PageResult<RehabAlertEventRespVO> getAlertPage(RehabAlertEventPageReqVO reqVO, Long loginUserId);

    PageResult<AppAdminNotificationRespVO> getNotificationPage(Integer pageNo, Integer pageSize, Long loginUserId);

    void readNotification(Long id, Long loginUserId);

    Long createFollowupNote(AppAdminFollowupNoteCreateReqVO reqVO, Long loginUserId);

    PageResult<AppAdminFollowupNoteRespVO> getFollowupNotePage(AppAdminFollowupNotePageReqVO reqVO, Long loginUserId);

    AppAdminAiOutputRespVO getLatestAiSummary(Long patientId, Long loginUserId);

    AppAdminAiOutputRespVO getLatestAiFollowup(Long patientId, Long loginUserId);
}
