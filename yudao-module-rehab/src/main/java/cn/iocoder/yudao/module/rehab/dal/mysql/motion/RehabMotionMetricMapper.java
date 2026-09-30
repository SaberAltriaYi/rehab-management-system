package cn.iocoder.yudao.module.rehab.dal.mysql.motion;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionMetricDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 动作评估指标 Mapper
 */
@Mapper
public interface RehabMotionMetricMapper extends BaseMapperX<RehabMotionMetricDO> {

    default List<RehabMotionMetricDO> selectListByAssessmentId(Long assessmentId) {
        return selectList(new LambdaQueryWrapperX<RehabMotionMetricDO>()
                .eq(RehabMotionMetricDO::getAssessmentId, assessmentId)
                .orderByAsc(RehabMotionMetricDO::getId));
    }

    default int deleteByAssessmentId(Long assessmentId) {
        return delete(new LambdaQueryWrapperX<RehabMotionMetricDO>().eq(RehabMotionMetricDO::getAssessmentId, assessmentId));
    }

    /**
     * 物理删除派生数据（每次分析整体替换；历史结果保存在结果 JSON 文件中，可追溯）。
     * 租户插件会自动追加 tenant_id 条件。
     */
    @Delete("DELETE FROM rehab_motion_metric WHERE assessment_id = #{assessmentId}")
    int deletePhysicalByAssessmentId(@Param("assessmentId") Long assessmentId);

}
