package cn.iocoder.yudao.module.rehab.dal.mysql.task;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.task.RehabTaskScheduleDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RehabTaskScheduleMapper extends BaseMapperX<RehabTaskScheduleDO> {

    default List<RehabTaskScheduleDO> selectListByPlanId(Long planId) {
        return selectList(new LambdaQueryWrapperX<RehabTaskScheduleDO>()
                .eq(RehabTaskScheduleDO::getPlanId, planId)
                .orderByDesc(RehabTaskScheduleDO::getId));
    }

    default List<RehabTaskScheduleDO> selectListByTaskId(Long taskId) {
        return selectList(new LambdaQueryWrapperX<RehabTaskScheduleDO>()
                .eq(RehabTaskScheduleDO::getTaskId, taskId)
                .orderByDesc(RehabTaskScheduleDO::getId));
    }

}
