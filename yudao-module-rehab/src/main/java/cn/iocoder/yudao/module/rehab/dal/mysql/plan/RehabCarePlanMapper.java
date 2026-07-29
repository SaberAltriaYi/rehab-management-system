package cn.iocoder.yudao.module.rehab.dal.mysql.plan;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.plan.vo.RehabCarePlanPageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface RehabCarePlanMapper extends BaseMapperX<RehabCarePlanDO> {

    default PageResult<RehabCarePlanDO> selectPage(RehabCarePlanPageReqVO reqVO, Collection<Long> patientIds) {
        if (patientIds != null && patientIds.isEmpty()) {
            return PageResult.empty(0L);
        }
        LambdaQueryWrapperX<RehabCarePlanDO> query = new LambdaQueryWrapperX<RehabCarePlanDO>()
                .eqIfPresent(RehabCarePlanDO::getPatientId, reqVO.getPatientId())
                .eqIfPresent(RehabCarePlanDO::getEpisodeId, reqVO.getEpisodeId())
                .eqIfPresent(RehabCarePlanDO::getPrimaryTherapistUserId, reqVO.getPrimaryTherapistUserId())
                .eqIfPresent(RehabCarePlanDO::getStatus, reqVO.getStatus())
                .eqIfPresent(RehabCarePlanDO::getPlanType, reqVO.getPlanType())
                .betweenIfPresent(RehabCarePlanDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(RehabCarePlanDO::getCreateTime)
                .orderByDesc(RehabCarePlanDO::getId);
        if (patientIds != null) {
            query.in(RehabCarePlanDO::getPatientId, patientIds);
        }
        return selectPage(reqVO, query);
    }

    default RehabCarePlanDO selectActiveByPatientEpisode(Long patientId, Long episodeId) {
        List<RehabCarePlanDO> list = selectList(new LambdaQueryWrapperX<RehabCarePlanDO>()
                .eq(RehabCarePlanDO::getPatientId, patientId)
                .eq(RehabCarePlanDO::getEpisodeId, episodeId)
                .eq(RehabCarePlanDO::getStatus, "active")
                .orderByDesc(RehabCarePlanDO::getCreateTime));
        return CollUtil.isEmpty(list) ? null : list.get(0);
    }

    default RehabCarePlanDO selectActiveByPatientId(Long patientId) {
        List<RehabCarePlanDO> list = selectList(new LambdaQueryWrapperX<RehabCarePlanDO>()
                .eq(RehabCarePlanDO::getPatientId, patientId)
                .eq(RehabCarePlanDO::getStatus, "active")
                .orderByDesc(RehabCarePlanDO::getCreateTime));
        return CollUtil.isEmpty(list) ? null : list.get(0);
    }

    default List<RehabCarePlanDO> selectListByPatientId(Long patientId) {
        return selectList(new LambdaQueryWrapperX<RehabCarePlanDO>()
                .eq(RehabCarePlanDO::getPatientId, patientId)
                .orderByDesc(RehabCarePlanDO::getCreateTime));
    }

    default List<RehabCarePlanDO> selectListByPatientIds(Collection<Long> patientIds) {
        if (CollUtil.isEmpty(patientIds)) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<RehabCarePlanDO>()
                .in(RehabCarePlanDO::getPatientId, patientIds)
                .orderByDesc(RehabCarePlanDO::getCreateTime));
    }

}
