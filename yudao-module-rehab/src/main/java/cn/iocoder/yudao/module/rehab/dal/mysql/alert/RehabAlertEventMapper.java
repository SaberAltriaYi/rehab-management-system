package cn.iocoder.yudao.module.rehab.dal.mysql.alert;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.RehabAlertEventPageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.alert.RehabAlertEventDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface RehabAlertEventMapper extends BaseMapperX<RehabAlertEventDO> {

    default PageResult<RehabAlertEventDO> selectPage(RehabAlertEventPageReqVO reqVO, Collection<Long> visiblePatientIds) {
        if (visiblePatientIds != null && visiblePatientIds.isEmpty()) {
            return PageResult.empty(0L);
        }
        LambdaQueryWrapperX<RehabAlertEventDO> query = new LambdaQueryWrapperX<RehabAlertEventDO>()
                .eqIfPresent(RehabAlertEventDO::getPatientId, reqVO.getPatientId())
                .eqIfPresent(RehabAlertEventDO::getEpisodeId, reqVO.getEpisodeId())
                .eqIfPresent(RehabAlertEventDO::getPlanId, reqVO.getPlanId())
                .eqIfPresent(RehabAlertEventDO::getAlertType, reqVO.getAlertType())
                .eqIfPresent(RehabAlertEventDO::getSeverity, reqVO.getSeverity())
                .eqIfPresent(RehabAlertEventDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(RehabAlertEventDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(RehabAlertEventDO::getCreateTime)
                .orderByDesc(RehabAlertEventDO::getId);
        if (visiblePatientIds != null) {
            query.in(RehabAlertEventDO::getPatientId, visiblePatientIds);
        }
        return selectPage(reqVO, query);
    }

    default RehabAlertEventDO selectActiveByPlanAndType(Long planId, String alertType) {
        List<RehabAlertEventDO> list = selectList(new LambdaQueryWrapperX<RehabAlertEventDO>()
                .eq(RehabAlertEventDO::getPlanId, planId)
                .eq(RehabAlertEventDO::getAlertType, alertType)
                .eq(RehabAlertEventDO::getStatus, "active")
                .orderByDesc(RehabAlertEventDO::getId));
        return CollUtil.isEmpty(list) ? null : list.get(0);
    }

    default List<RehabAlertEventDO> selectActiveList() {
        return selectList(new LambdaQueryWrapperX<RehabAlertEventDO>()
                .eq(RehabAlertEventDO::getStatus, "active")
                .orderByDesc(RehabAlertEventDO::getCreateTime)
                .orderByDesc(RehabAlertEventDO::getId));
    }

    default List<RehabAlertEventDO> selectActiveListByPatientId(Long patientId) {
        return selectList(new LambdaQueryWrapperX<RehabAlertEventDO>()
                .eq(RehabAlertEventDO::getPatientId, patientId)
                .eq(RehabAlertEventDO::getStatus, "active")
                .orderByDesc(RehabAlertEventDO::getCreateTime)
                .orderByDesc(RehabAlertEventDO::getId));
    }

    default long selectCountActiveHighRiskByPatientIds(Collection<Long> patientIds) {
        if (patientIds == null || patientIds.isEmpty()) {
            return 0;
        }
        return selectCount(new LambdaQueryWrapperX<RehabAlertEventDO>()
                .in(RehabAlertEventDO::getPatientId, patientIds)
                .eq(RehabAlertEventDO::getStatus, "active")
                .eq(RehabAlertEventDO::getSeverity, "high"));
    }

    default long selectCountByStatusAndType(String status, String alertType, Collection<Long> patientIds) {
        LambdaQueryWrapperX<RehabAlertEventDO> query = new LambdaQueryWrapperX<RehabAlertEventDO>()
                .eqIfPresent(RehabAlertEventDO::getStatus, status)
                .eqIfPresent(RehabAlertEventDO::getAlertType, alertType);
        if (patientIds != null) {
            if (patientIds.isEmpty()) {
                return 0;
            }
            query.in(RehabAlertEventDO::getPatientId, patientIds);
        }
        return selectCount(query);
    }

    default long selectUnresolvedCountBefore(LocalDateTime threshold) {
        return selectCount(new LambdaQueryWrapperX<RehabAlertEventDO>()
                .eq(RehabAlertEventDO::getStatus, "active")
                .le(RehabAlertEventDO::getCreateTime, threshold));
    }
}
