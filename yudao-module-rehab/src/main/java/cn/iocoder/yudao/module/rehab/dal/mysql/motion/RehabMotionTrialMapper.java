package cn.iocoder.yudao.module.rehab.dal.mysql.motion;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTrialDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 动作评估 Trial Mapper
 */
@Mapper
public interface RehabMotionTrialMapper extends BaseMapperX<RehabMotionTrialDO> {

    default List<RehabMotionTrialDO> selectListByAssessmentId(Long assessmentId) {
        return selectList(new LambdaQueryWrapperX<RehabMotionTrialDO>()
                .eq(RehabMotionTrialDO::getAssessmentId, assessmentId)
                .orderByAsc(RehabMotionTrialDO::getSortNo)
                .orderByAsc(RehabMotionTrialDO::getId));
    }
}
