package cn.iocoder.yudao.module.rehab.dal.mysql.log;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.log.RehabPlanOperationLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RehabPlanOperationLogMapper extends BaseMapperX<RehabPlanOperationLogDO> {

    default List<RehabPlanOperationLogDO> selectListByPlanId(Long planId) {
        return selectList(new LambdaQueryWrapperX<RehabPlanOperationLogDO>()
                .eq(RehabPlanOperationLogDO::getPlanId, planId)
                .orderByDesc(RehabPlanOperationLogDO::getCreateTime)
                .orderByDesc(RehabPlanOperationLogDO::getId));
    }

}
