package cn.iocoder.yudao.module.rehab.service.notification;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.notification.vo.RehabNotificationCreateReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.notification.vo.RehabNotificationPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.notification.vo.RehabNotificationRespVO;
import cn.iocoder.yudao.module.rehab.controller.app.patient.vo.AppPatientNotificationRespVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.notification.RehabNotificationDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.notification.RehabNotificationMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabNotificationConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabOperationTypeConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.log.RehabAuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

@Service
@Validated
public class RehabNotificationServiceImpl implements RehabNotificationService {

    private static final DateTimeFormatter NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Resource
    private RehabNotificationMapper notificationMapper;
    @Resource
    private RehabPatientMapper patientMapper;
    @Resource
    private RehabDataPermissionService dataPermissionService;
    @Resource
    private RehabAuditLogService auditLogService;

    @Override
    public PageResult<RehabNotificationRespVO> getNotificationPage(RehabNotificationPageReqVO reqVO, Long operatorUserId) {
        Set<Long> visiblePatientIds = dataPermissionService.getVisiblePatientIds(operatorUserId);
        if (visiblePatientIds != null && visiblePatientIds.isEmpty()) {
            return PageResult.empty();
        }
        if (reqVO.getPatientId() != null) {
            validatePatientReadable(reqVO.getPatientId(), operatorUserId);
            visiblePatientIds = Collections.singleton(reqVO.getPatientId());
        }

        boolean onlyMine = ObjUtil.defaultIfNull(reqVO.getOnlyMine(), Boolean.TRUE);
        String targetType = reqVO.getTargetType();
        Long targetUserId = reqVO.getTargetUserId();
        if (!dataPermissionService.isSuperAdmin(operatorUserId)) {
            onlyMine = true;
            targetUserId = operatorUserId;
            targetType = dataPermissionService.isTherapist(operatorUserId)
                    ? RehabNotificationConstants.TARGET_THERAPIST
                    : RehabNotificationConstants.TARGET_ADMIN;
        }

        PageResult<RehabNotificationDO> pageResult = notificationMapper.selectPage(reqVO, visiblePatientIds, targetType, targetUserId, onlyMine);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        return new PageResult<>(toRespVOList(pageResult.getList()), pageResult.getTotal());
    }

    @Override
    public RehabNotificationRespVO getNotification(Long id, Long operatorUserId) {
        RehabNotificationDO notification = validateReadable(id, operatorUserId);
        return toRespVOList(Collections.singletonList(notification)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createNotification(RehabNotificationCreateReqVO reqVO, Long operatorUserId) {
        validateCreateReq(reqVO);
        if (reqVO.getPatientId() != null) {
            validatePatientReadable(reqVO.getPatientId(), operatorUserId);
        }
        RehabNotificationDO notification = BeanUtils.toBean(reqVO, RehabNotificationDO.class);
        notification.setNotificationNo(generateNotificationNo());
        notification.setSeverity(StrUtil.blankToDefault(notification.getSeverity(), RehabNotificationConstants.SEVERITY_INFO));
        notification.setDeliveryChannel(StrUtil.blankToDefault(notification.getDeliveryChannel(), RehabNotificationConstants.DELIVERY_WEB));
        notification.setReadStatus(RehabNotificationConstants.READ_UNREAD);
        notification.setSendStatus(Boolean.TRUE.equals(reqVO.getSentNow())
                ? RehabNotificationConstants.SEND_SENT : RehabNotificationConstants.SEND_PENDING);
        if (notification.getVisibleFrom() == null) {
            notification.setVisibleFrom(LocalDateTime.now());
        }
        notificationMapper.insert(notification);
        auditLogService.createAuditLog("notification", notification.getId(), RehabOperationTypeConstants.NOTIFICATION_CREATE,
                operatorUserId, resolveRole(operatorUserId), null, notification, "success", "手动创建通知");
        return notification.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSystemNotification(String targetType, Long targetUserId, Long patientId, Long episodeId,
                                         String relatedType, Long relatedId, String notificationType,
                                         String severity, String title, String content,
                                         String deliveryChannel, String actionUrl, String actionText) {
        RehabNotificationDO existed = notificationMapper.selectLatestByRelated(relatedType, relatedId, notificationType, targetType, targetUserId);
        if (existed != null && existed.getCreateTime() != null
                && existed.getCreateTime().isAfter(LocalDateTime.now().minusHours(6))) {
            return existed.getId();
        }
        RehabNotificationDO notification = RehabNotificationDO.builder()
                .notificationNo(generateNotificationNo())
                .targetType(targetType)
                .targetUserId(targetUserId)
                .patientId(patientId)
                .episodeId(episodeId)
                .relatedType(relatedType)
                .relatedId(relatedId)
                .notificationType(notificationType)
                .title(title)
                .content(content)
                .severity(StrUtil.blankToDefault(severity, RehabNotificationConstants.SEVERITY_INFO))
                .deliveryChannel(StrUtil.blankToDefault(deliveryChannel, RehabNotificationConstants.DELIVERY_MULTI))
                .readStatus(RehabNotificationConstants.READ_UNREAD)
                .sendStatus(RehabNotificationConstants.SEND_SENT)
                .visibleFrom(LocalDateTime.now())
                .actionUrl(actionUrl)
                .actionText(actionText)
                .build();
        notificationMapper.insert(notification);
        return notification.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readNotification(Long id, Long operatorUserId) {
        RehabNotificationDO notification = validateReadable(id, operatorUserId);
        if (ObjUtil.equals(notification.getReadStatus(), RehabNotificationConstants.READ_READ)) {
            return;
        }
        notificationMapper.updateById(new RehabNotificationDO().setId(notification.getId())
                .setReadStatus(RehabNotificationConstants.READ_READ)
                .setReadTime(LocalDateTime.now()));
        auditLogService.createAuditLog("notification", notification.getId(), RehabOperationTypeConstants.NOTIFICATION_READ,
                operatorUserId, resolveRole(operatorUserId), notification, notificationMapper.selectById(notification.getId()),
                "success", "通知已读");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readAllNotification(Long operatorUserId) {
        String targetType = dataPermissionService.isTherapist(operatorUserId)
                ? RehabNotificationConstants.TARGET_THERAPIST
                : RehabNotificationConstants.TARGET_ADMIN;
        List<RehabNotificationDO> unreadList = notificationMapper.selectUnreadListByTarget(targetType, operatorUserId);
        if (CollUtil.isEmpty(unreadList)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        unreadList.forEach(item -> notificationMapper.updateById(new RehabNotificationDO().setId(item.getId())
                .setReadStatus(RehabNotificationConstants.READ_READ)
                .setReadTime(now)));
        auditLogService.createAuditLog("notification", 0L, RehabOperationTypeConstants.NOTIFICATION_READ_ALL,
                operatorUserId, resolveRole(operatorUserId), null, unreadList.stream().map(RehabNotificationDO::getId).collect(Collectors.toList()),
                "success", "通知全部已读");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotification(Long id, Long operatorUserId) {
        RehabNotificationDO notification = validateReadable(id, operatorUserId);
        notificationMapper.deleteById(id);
        auditLogService.createAuditLog("notification", id, RehabOperationTypeConstants.NOTIFICATION_DELETE,
                operatorUserId, resolveRole(operatorUserId), notification, null, "success", "删除通知");
    }

    @Override
    public Long countUnreadForAdminUser(Long loginUserId) {
        String targetType = dataPermissionService.isTherapist(loginUserId)
                ? RehabNotificationConstants.TARGET_THERAPIST
                : RehabNotificationConstants.TARGET_ADMIN;
        return notificationMapper.selectUnreadCountByTarget(targetType, loginUserId);
    }

    @Override
    public PageResult<AppPatientNotificationRespVO> getPatientNotificationPage(Integer pageNo, Integer pageSize, Long patientId, Long appUserId) {
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(pageNo);
        pageParam.setPageSize(pageSize);
        LocalDateTime now = LocalDateTime.now();
        PageResult<RehabNotificationDO> pageResult = notificationMapper.selectPage(pageParam, new LambdaQueryWrapperX<RehabNotificationDO>()
                .eq(RehabNotificationDO::getTargetType, RehabNotificationConstants.TARGET_PATIENT)
                .eq(RehabNotificationDO::getPatientId, patientId)
                .and(wrapper -> wrapper.isNull(RehabNotificationDO::getTargetUserId)
                        .or().eq(RehabNotificationDO::getTargetUserId, appUserId))
                .and(wrapper -> wrapper.isNull(RehabNotificationDO::getVisibleFrom)
                        .or().le(RehabNotificationDO::getVisibleFrom, now))
                .and(wrapper -> wrapper.isNull(RehabNotificationDO::getExpireTime)
                        .or().ge(RehabNotificationDO::getExpireTime, now))
                .orderByDesc(RehabNotificationDO::getCreateTime)
                .orderByDesc(RehabNotificationDO::getId));
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        List<AppPatientNotificationRespVO> list = pageResult.getList().stream().map(item -> {
            AppPatientNotificationRespVO vo = new AppPatientNotificationRespVO();
            vo.setId(item.getId());
            vo.setNotificationType(item.getNotificationType());
            vo.setTitle(item.getTitle());
            vo.setContent(item.getContent());
            vo.setReadStatus(item.getReadStatus());
            vo.setVisibleFrom(item.getVisibleFrom());
            vo.setExpireTime(item.getExpireTime());
            vo.setCreateTime(item.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readPatientNotification(Long id, Long patientId, Long appUserId) {
        RehabNotificationDO notification = notificationMapper.selectById(id);
        if (notification == null
                || !ObjUtil.equals(notification.getTargetType(), RehabNotificationConstants.TARGET_PATIENT)
                || !ObjUtil.equals(notification.getPatientId(), patientId)
                || (notification.getTargetUserId() != null && !ObjUtil.equals(notification.getTargetUserId(), appUserId))) {
            throw exception(APP_NOTIFICATION_NOT_EXISTS);
        }
        if (!ObjUtil.equals(notification.getReadStatus(), RehabNotificationConstants.READ_READ)) {
            notificationMapper.updateById(new RehabNotificationDO().setId(id)
                    .setReadStatus(RehabNotificationConstants.READ_READ)
                    .setReadTime(LocalDateTime.now()));
        }
    }

    @Override
    public Long countUnreadForPatient(Long patientId, Long appUserId) {
        return notificationMapper.selectCount(new LambdaQueryWrapperX<RehabNotificationDO>()
                .eq(RehabNotificationDO::getTargetType, RehabNotificationConstants.TARGET_PATIENT)
                .eq(RehabNotificationDO::getPatientId, patientId)
                .and(wrapper -> wrapper.isNull(RehabNotificationDO::getTargetUserId)
                        .or().eq(RehabNotificationDO::getTargetUserId, appUserId))
                .eq(RehabNotificationDO::getReadStatus, RehabNotificationConstants.READ_UNREAD));
    }

    private RehabNotificationDO validateReadable(Long id, Long operatorUserId) {
        RehabNotificationDO notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw exception(NOTIFICATION_NOT_EXISTS);
        }
        if (notification.getPatientId() != null && !dataPermissionService.canReadPatient(notification.getPatientId(), operatorUserId)) {
            throw exception(NOTIFICATION_NO_PERMISSION);
        }
        if (!dataPermissionService.isSuperAdmin(operatorUserId) && notification.getTargetUserId() != null
                && !ObjUtil.equals(notification.getTargetUserId(), operatorUserId)) {
            throw exception(NOTIFICATION_NO_PERMISSION);
        }
        return notification;
    }

    private void validateCreateReq(RehabNotificationCreateReqVO reqVO) {
        if (!RehabNotificationConstants.TARGET_TYPES.contains(reqVO.getTargetType())) {
            throw exception(NOTIFICATION_TARGET_TYPE_INVALID);
        }
    }

    private void validatePatientReadable(Long patientId, Long operatorUserId) {
        if (!dataPermissionService.canReadPatient(patientId, operatorUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
    }

    private String resolveRole(Long userId) {
        if (dataPermissionService.isSuperAdmin(userId)) {
            return "admin";
        }
        if (dataPermissionService.isTherapist(userId)) {
            return "therapist";
        }
        if (dataPermissionService.isClerk(userId)) {
            return "clerk";
        }
        return "unknown";
    }

    private String generateNotificationNo() {
        String datePart = NO_DATE_FORMATTER.format(LocalDateTime.now());
        return "NTF" + datePart + String.format("%04d", Math.abs(UUID.randomUUID().hashCode()) % 10000);
    }

    private List<RehabNotificationRespVO> toRespVOList(List<RehabNotificationDO> list) {
        Set<Long> patientIds = list.stream().map(RehabNotificationDO::getPatientId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, RehabPatientDO> patientMap = patientIds.isEmpty() ? Collections.emptyMap() : patientMapper.selectBatchIds(patientIds)
                .stream().collect(Collectors.toMap(RehabPatientDO::getId, item -> item, (a, b) -> a));
        return list.stream().map(item -> {
            RehabNotificationRespVO vo = BeanUtils.toBean(item, RehabNotificationRespVO.class);
            RehabPatientDO patient = patientMap.get(item.getPatientId());
            if (patient != null) {
                vo.setPatientName(patient.getName());
                vo.setPatientNo(patient.getPatientNo());
            }
            return vo;
        }).collect(Collectors.toList());
    }
}
