package cn.iocoder.yudao.module.rehab.dal.mysql.task;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.task.vo.RehabExerciseTaskPageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.task.RehabExerciseTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface RehabExerciseTaskMapper extends BaseMapperX<RehabExerciseTaskDO> {

    default PageResult<RehabExerciseTaskDO> selectPage(RehabExerciseTaskPageReqVO reqVO, Collection<Long> patientIds) {
        if (patientIds != null && patientIds.isEmpty()) {
            return PageResult.empty(0L);
        }
        LambdaQueryWrapperX<RehabExerciseTaskDO> query = new LambdaQueryWrapperX<>();
        query.eqIfPresent(RehabExerciseTaskDO::getPlanId, reqVO.getPlanId())
                .eqIfPresent(RehabExerciseTaskDO::getPatientId, reqVO.getPatientId())
                .eqIfPresent(RehabExerciseTaskDO::getEpisodeId, reqVO.getEpisodeId())
                .eqIfPresent(RehabExerciseTaskDO::getStatus, reqVO.getStatus());
        query.orderByAsc(RehabExerciseTaskDO::getSortOrder);
        query.orderByDesc(RehabExerciseTaskDO::getId);
        if (patientIds != null) {
            query.in(RehabExerciseTaskDO::getPatientId, patientIds);
        }
        return selectPage(reqVO, query);
    }

    default List<RehabExerciseTaskDO> selectListByPlanId(Long planId) {
        return selectList(new LambdaQueryWrapperX<RehabExerciseTaskDO>()
                .eq(RehabExerciseTaskDO::getPlanId, planId)
                .orderByAsc(RehabExerciseTaskDO::getSortOrder)
                .orderByDesc(RehabExerciseTaskDO::getId));
    }

    default List<RehabExerciseTaskDO> selectActiveListByPlanId(Long planId) {
        return selectList(new LambdaQueryWrapperX<RehabExerciseTaskDO>()
                .eq(RehabExerciseTaskDO::getPlanId, planId)
                .eq(RehabExerciseTaskDO::getStatus, "active")
                .orderByAsc(RehabExerciseTaskDO::getSortOrder)
                .orderByDesc(RehabExerciseTaskDO::getId));
    }

    default List<RehabExerciseTaskDO> selectListByIds(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return selectBatchIds(ids);
    }

}
