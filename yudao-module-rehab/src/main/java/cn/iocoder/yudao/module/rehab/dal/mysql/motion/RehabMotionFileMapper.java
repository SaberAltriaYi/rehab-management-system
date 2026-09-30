package cn.iocoder.yudao.module.rehab.dal.mysql.motion;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionFileDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 动作评估文件 Mapper
 */
@Mapper
public interface RehabMotionFileMapper extends BaseMapperX<RehabMotionFileDO> {

    default List<RehabMotionFileDO> selectListByAssessmentId(Long assessmentId) {
        return selectList(new LambdaQueryWrapperX<RehabMotionFileDO>()
                .eq(RehabMotionFileDO::getAssessmentId, assessmentId)
                .orderByAsc(RehabMotionFileDO::getId));
    }

    default RehabMotionFileDO selectByRelativePath(Long assessmentId, String relativePath) {
        return selectFirstOne(RehabMotionFileDO::getAssessmentId, assessmentId,
                RehabMotionFileDO::getRelativePath, relativePath);
    }
}
