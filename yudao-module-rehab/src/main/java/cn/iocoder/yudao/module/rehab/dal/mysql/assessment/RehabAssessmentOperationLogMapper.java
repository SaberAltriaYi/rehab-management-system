package cn.iocoder.yudao.module.rehab.dal.mysql.assessment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentOperationLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 评估操作日志 Mapper
 */
@Mapper
public interface RehabAssessmentOperationLogMapper extends BaseMapperX<RehabAssessmentOperationLogDO> {

    default List<RehabAssessmentOperationLogDO> selectListByAssessmentId(Long assessmentId) {
        return selectList(new LambdaQueryWrapperX<RehabAssessmentOperationLogDO>()
                .eq(RehabAssessmentOperationLogDO::getAssessmentId, assessmentId)
                .orderByDesc(RehabAssessmentOperationLogDO::getId));
    }

}
