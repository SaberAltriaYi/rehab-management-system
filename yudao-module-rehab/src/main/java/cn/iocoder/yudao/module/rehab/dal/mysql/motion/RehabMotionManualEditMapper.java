package cn.iocoder.yudao.module.rehab.dal.mysql.motion;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionManualEditDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 动作评估人工修改记录 Mapper（只增不改）
 */
@Mapper
public interface RehabMotionManualEditMapper extends BaseMapperX<RehabMotionManualEditDO> {

    default List<RehabMotionManualEditDO> selectListByAssessmentId(Long assessmentId) {
        return selectList(new LambdaQueryWrapperX<RehabMotionManualEditDO>()
                .eq(RehabMotionManualEditDO::getAssessmentId, assessmentId)
                .orderByDesc(RehabMotionManualEditDO::getId));
    }
}
