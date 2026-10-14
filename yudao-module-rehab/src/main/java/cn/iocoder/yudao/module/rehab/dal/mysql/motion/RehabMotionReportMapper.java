package cn.iocoder.yudao.module.rehab.dal.mysql.motion;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionReportDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 动作评估签署报告 Mapper
 */
@Mapper
public interface RehabMotionReportMapper extends BaseMapperX<RehabMotionReportDO> {

    default List<RehabMotionReportDO> selectListByAssessmentId(Long assessmentId) {
        return selectList(new LambdaQueryWrapperX<RehabMotionReportDO>()
                .eq(RehabMotionReportDO::getAssessmentId, assessmentId)
                .orderByDesc(RehabMotionReportDO::getVersionNo)
                .orderByAsc(RehabMotionReportDO::getReportType));
    }
}
