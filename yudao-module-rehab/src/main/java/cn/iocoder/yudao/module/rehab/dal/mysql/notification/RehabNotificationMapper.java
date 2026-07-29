package cn.iocoder.yudao.module.rehab.dal.mysql.notification;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.notification.vo.RehabNotificationPageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.notification.RehabNotificationDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface RehabNotificationMapper extends BaseMapperX<RehabNotificationDO> {

    default PageResult<RehabNotificationDO> selectPage(RehabNotificationPageReqVO reqVO, Collection<Long> visiblePatientIds,
                                                       String targetType, Long targetUserId, boolean onlyMine) {
        if (visiblePatientIds != null && visiblePatientIds.isEmpty()) {
            return PageResult.empty(0L);
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapperX<RehabNotificationDO> query = new LambdaQueryWrapperX<RehabNotificationDO>()
                .eqIfPresent(RehabNotificationDO::getTargetType, targetType)
                .eqIfPresent(RehabNotificationDO::getTargetUserId, targetUserId)
                .eqIfPresent(RehabNotificationDO::getPatientId, reqVO.getPatientId())
                .eqIfPresent(RehabNotificationDO::getNotificationType, reqVO.getNotificationType())
                .eqIfPresent(RehabNotificationDO::getSeverity, reqVO.getSeverity())
                .eqIfPresent(RehabNotificationDO::getReadStatus, reqVO.getReadStatus())
                .eqIfPresent(RehabNotificationDO::getSendStatus, reqVO.getSendStatus())
                .betweenIfPresent(RehabNotificationDO::getCreateTime, reqVO.getCreateTime());
        query.and(wrapper -> wrapper.isNull(RehabNotificationDO::getVisibleFrom)
                .or().le(RehabNotificationDO::getVisibleFrom, now));
        query.and(wrapper -> wrapper.isNull(RehabNotificationDO::getExpireTime)
                .or().ge(RehabNotificationDO::getExpireTime, now));
        query.orderByDesc(RehabNotificationDO::getCreateTime).orderByDesc(RehabNotificationDO::getId);

        if (onlyMine && targetType != null && targetUserId != null) {
            query.eq(RehabNotificationDO::getTargetType, targetType)
                    .eq(RehabNotificationDO::getTargetUserId, targetUserId);
        }
        if (visiblePatientIds != null) {
            query.and(wrapper -> wrapper.isNull(RehabNotificationDO::getPatientId)
                    .or().in(RehabNotificationDO::getPatientId, visiblePatientIds));
        }
        return selectPage(reqVO, query);
    }

    default RehabNotificationDO selectLatestByRelated(String relatedType, Long relatedId, String notificationType, String targetType, Long targetUserId) {
        List<RehabNotificationDO> list = selectList(new LambdaQueryWrapperX<RehabNotificationDO>()
                .eq(RehabNotificationDO::getRelatedType, relatedType)
                .eq(RehabNotificationDO::getRelatedId, relatedId)
                .eq(RehabNotificationDO::getNotificationType, notificationType)
                .eq(RehabNotificationDO::getTargetType, targetType)
                .eq(RehabNotificationDO::getTargetUserId, targetUserId)
                .orderByDesc(RehabNotificationDO::getCreateTime)
                .orderByDesc(RehabNotificationDO::getId));
        return CollUtil.isEmpty(list) ? null : list.get(0);
    }

    default List<RehabNotificationDO> selectUnreadListByTarget(String targetType, Long targetUserId) {
        if (targetUserId == null) {
            return Collections.emptyList();
        }
        LocalDateTime now = LocalDateTime.now();
        return selectList(new LambdaQueryWrapperX<RehabNotificationDO>()
                .eq(RehabNotificationDO::getTargetType, targetType)
                .eq(RehabNotificationDO::getTargetUserId, targetUserId)
                .eq(RehabNotificationDO::getReadStatus, "unread")
                .and(wrapper -> wrapper.isNull(RehabNotificationDO::getVisibleFrom)
                        .or().le(RehabNotificationDO::getVisibleFrom, now))
                .and(wrapper -> wrapper.isNull(RehabNotificationDO::getExpireTime)
                        .or().ge(RehabNotificationDO::getExpireTime, now))
                .orderByDesc(RehabNotificationDO::getCreateTime)
                .orderByDesc(RehabNotificationDO::getId));
    }

    default Long selectUnreadCountByTarget(String targetType, Long targetUserId) {
        return selectCount(new LambdaQueryWrapperX<RehabNotificationDO>()
                .eq(RehabNotificationDO::getTargetType, targetType)
                .eq(RehabNotificationDO::getTargetUserId, targetUserId)
                .eq(RehabNotificationDO::getReadStatus, "unread"));
    }
}
