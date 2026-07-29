package cn.iocoder.yudao.module.rehab.dal.mysql.progress;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.progress.vo.RehabProgressRecordPageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.progress.RehabProgressRecordDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RehabProgressRecordMapper extends BaseMapperX<RehabProgressRecordDO> {

    default PageResult<RehabProgressRecordDO> selectPage(RehabProgressRecordPageReqVO reqVO, Collection<Long> patientIds) {
        if (patientIds != null && patientIds.isEmpty()) {
            return PageResult.empty(0L);
        }
        LambdaQueryWrapperX<RehabProgressRecordDO> query = new LambdaQueryWrapperX<RehabProgressRecordDO>()
                .eqIfPresent(RehabProgressRecordDO::getPlanId, reqVO.getPlanId())
                .eqIfPresent(RehabProgressRecordDO::getPatientId, reqVO.getPatientId())
                .eqIfPresent(RehabProgressRecordDO::getEpisodeId, reqVO.getEpisodeId())
                .betweenIfPresent(RehabProgressRecordDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(RehabProgressRecordDO::getPeriodStart)
                .orderByDesc(RehabProgressRecordDO::getId);
        if (patientIds != null) {
            query.in(RehabProgressRecordDO::getPatientId, patientIds);
        }
        return selectPage(reqVO, query);
    }

    default RehabProgressRecordDO selectLatestByPlanId(Long planId) {
        List<RehabProgressRecordDO> list = selectList(new LambdaQueryWrapperX<RehabProgressRecordDO>()
                .eq(RehabProgressRecordDO::getPlanId, planId)
                .orderByDesc(RehabProgressRecordDO::getPeriodStart)
                .orderByDesc(RehabProgressRecordDO::getId));
        return list.isEmpty() ? null : list.get(0);
    }

    default RehabProgressRecordDO selectByPlanAndPeriod(Long planId, java.time.LocalDate periodStart, java.time.LocalDate periodEnd) {
        return selectOne(new LambdaQueryWrapperX<RehabProgressRecordDO>()
                .eq(RehabProgressRecordDO::getPlanId, planId)
                .eq(RehabProgressRecordDO::getPeriodStart, periodStart)
                .eq(RehabProgressRecordDO::getPeriodEnd, periodEnd));
    }

    default List<RehabProgressRecordDO> selectListByPlanId(Long planId) {
        return selectList(new LambdaQueryWrapperX<RehabProgressRecordDO>()
                .eq(RehabProgressRecordDO::getPlanId, planId)
                .orderByDesc(RehabProgressRecordDO::getPeriodStart)
                .orderByDesc(RehabProgressRecordDO::getId));
    }

}
