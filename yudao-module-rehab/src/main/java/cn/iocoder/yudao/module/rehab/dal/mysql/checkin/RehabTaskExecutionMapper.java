package cn.iocoder.yudao.module.rehab.dal.mysql.checkin;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.checkin.RehabTaskExecutionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface RehabTaskExecutionMapper extends BaseMapperX<RehabTaskExecutionDO> {

    default List<RehabTaskExecutionDO> selectListByCheckinId(Long checkinId) {
        return selectList(new LambdaQueryWrapperX<RehabTaskExecutionDO>()
                .eq(RehabTaskExecutionDO::getCheckinId, checkinId)
                .orderByAsc(RehabTaskExecutionDO::getId));
    }

    default List<RehabTaskExecutionDO> selectListByCheckinIds(Collection<Long> checkinIds) {
        if (CollUtil.isEmpty(checkinIds)) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<RehabTaskExecutionDO>()
                .in(RehabTaskExecutionDO::getCheckinId, checkinIds)
                .orderByAsc(RehabTaskExecutionDO::getCheckinId)
                .orderByAsc(RehabTaskExecutionDO::getId));
    }

    default long selectCountByTaskId(Long taskId) {
        return selectCount(RehabTaskExecutionDO::getTaskId, taskId);
    }

}
