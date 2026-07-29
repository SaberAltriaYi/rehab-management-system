package cn.iocoder.yudao.module.rehab.service.notification;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.notification.vo.RehabNotificationCreateReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.notification.vo.RehabNotificationPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.notification.vo.RehabNotificationRespVO;
import cn.iocoder.yudao.module.rehab.controller.app.patient.vo.AppPatientNotificationRespVO;

public interface RehabNotificationService {

    PageResult<RehabNotificationRespVO> getNotificationPage(RehabNotificationPageReqVO reqVO, Long operatorUserId);

    RehabNotificationRespVO getNotification(Long id, Long operatorUserId);

    Long createNotification(RehabNotificationCreateReqVO reqVO, Long operatorUserId);

    Long createSystemNotification(String targetType, Long targetUserId, Long patientId, Long episodeId,
                                  String relatedType, Long relatedId, String notificationType,
                                  String severity, String title, String content,
                                  String deliveryChannel, String actionUrl, String actionText);

    void readNotification(Long id, Long operatorUserId);

    void readAllNotification(Long operatorUserId);

    void deleteNotification(Long id, Long operatorUserId);

    Long countUnreadForAdminUser(Long loginUserId);

    PageResult<AppPatientNotificationRespVO> getPatientNotificationPage(Integer pageNo, Integer pageSize, Long patientId, Long appUserId);

    void readPatientNotification(Long id, Long patientId, Long appUserId);

    Long countUnreadForPatient(Long patientId, Long appUserId);

}
