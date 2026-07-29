package cn.iocoder.yudao.module.rehab.dal.mysql.checkin;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo.RehabDailyCheckinPageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.checkin.RehabDailyCheckinDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface RehabDailyCheckinMapper extends BaseMapperX<RehabDailyCheckinDO> {

    default PageResult<RehabDailyCheckinDO> selectPage(RehabDailyCheckinPageReqVO reqVO, Collection<Long> patientIds) {
        if (patientIds != null && patientIds.isEmpty()) {
            return PageResult.empty(0L);
        }
        LambdaQueryWrapperX<RehabDailyCheckinDO> query = new LambdaQueryWrapperX<RehabDailyCheckinDO>()
                .eqIfPresent(RehabDailyCheckinDO::getPatientId, reqVO.getPatientId())
                .eqIfPresent(RehabDailyCheckinDO::getEpisodeId, reqVO.getEpisodeId())
                .eqIfPresent(RehabDailyCheckinDO::getPlanId, reqVO.getPlanId())
                .eqIfPresent(RehabDailyCheckinDO::getSubmitRoleType, reqVO.getSubmitRoleType())
                .betweenIfPresent(RehabDailyCheckinDO::getCheckinDate, reqVO.getCheckinDate())
                .orderByDesc(RehabDailyCheckinDO::getCheckinDate)
                .orderByDesc(RehabDailyCheckinDO::getId);
        if (patientIds != null) {
            query.in(RehabDailyCheckinDO::getPatientId, patientIds);
        }
        return selectPage(reqVO, query);
    }

    default List<RehabDailyCheckinDO> selectListByPlanIdAndDateRange(Long planId, LocalDate startDate, LocalDate endDate) {
        return selectList(new LambdaQueryWrapperX<RehabDailyCheckinDO>()
                .eq(RehabDailyCheckinDO::getPlanId, planId)
                .between(RehabDailyCheckinDO::getCheckinDate, startDate, endDate)
                .orderByAsc(RehabDailyCheckinDO::getCheckinDate)
                .orderByAsc(RehabDailyCheckinDO::getId));
    }

    default List<RehabDailyCheckinDO> selectListByPlanId(Long planId) {
        return selectList(new LambdaQueryWrapperX<RehabDailyCheckinDO>()
                .eq(RehabDailyCheckinDO::getPlanId, planId)
                .orderByDesc(RehabDailyCheckinDO::getCheckinDate)
                .orderByDesc(RehabDailyCheckinDO::getId));
    }

    default RehabDailyCheckinDO selectLatestByPatientId(Long patientId) {
        List<RehabDailyCheckinDO> list = selectList(new LambdaQueryWrapperX<RehabDailyCheckinDO>()
                .eq(RehabDailyCheckinDO::getPatientId, patientId)
                .orderByDesc(RehabDailyCheckinDO::getCheckinDate)
                .orderByDesc(RehabDailyCheckinDO::getId));
        return CollUtil.isEmpty(list) ? null : list.get(0);
    }

    default RehabDailyCheckinDO selectByPatientPlanAndDate(Long patientId, Long planId, LocalDate checkinDate) {
        return selectOne(new LambdaQueryWrapperX<RehabDailyCheckinDO>()
                .eq(RehabDailyCheckinDO::getPatientId, patientId)
                .eq(RehabDailyCheckinDO::getPlanId, planId)
                .eq(RehabDailyCheckinDO::getCheckinDate, checkinDate)
                .orderByDesc(RehabDailyCheckinDO::getId));
    }

    default List<RehabDailyCheckinDO> selectListByIds(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return selectBatchIds(ids);
    }

}
