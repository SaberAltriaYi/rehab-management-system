package cn.iocoder.yudao.module.rehab.dal.mysql.motion;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAiDraftDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 动作评估 AI 草稿 Mapper
 */
@Mapper
public interface RehabMotionAiDraftMapper extends BaseMapperX<RehabMotionAiDraftDO> {

    default List<RehabMotionAiDraftDO> selectListByAssessmentId(Long assessmentId) {
        return selectList(new LambdaQueryWrapperX<RehabMotionAiDraftDO>()
                .eq(RehabMotionAiDraftDO::getAssessmentId, assessmentId)
                .orderByDesc(RehabMotionAiDraftDO::getId));
    }
}
