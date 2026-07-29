package cn.iocoder.yudao.module.rehab.dal.mysql.assessment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentModuleDataDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 评估模块数据 Mapper
 */
@Mapper
public interface RehabAssessmentModuleDataMapper extends BaseMapperX<RehabAssessmentModuleDataDO> {

    default List<RehabAssessmentModuleDataDO> selectListByAssessmentId(Long assessmentId) {
        return selectList(new LambdaQueryWrapperX<RehabAssessmentModuleDataDO>()
                .eq(RehabAssessmentModuleDataDO::getAssessmentId, assessmentId)
                .orderByAsc(RehabAssessmentModuleDataDO::getModuleType));
    }

    default RehabAssessmentModuleDataDO selectByAssessmentIdAndModuleType(Long assessmentId, String moduleType) {
        return selectOne(new LambdaQueryWrapperX<RehabAssessmentModuleDataDO>()
                .eq(RehabAssessmentModuleDataDO::getAssessmentId, assessmentId)
                .eq(RehabAssessmentModuleDataDO::getModuleType, moduleType));
    }

}
