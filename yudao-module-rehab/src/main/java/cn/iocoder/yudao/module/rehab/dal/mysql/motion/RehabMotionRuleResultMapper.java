package cn.iocoder.yudao.module.rehab.dal.mysql.motion;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionRuleResultDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 动作评估规则结果 Mapper
 */
@Mapper
public interface RehabMotionRuleResultMapper extends BaseMapperX<RehabMotionRuleResultDO> {

    default List<RehabMotionRuleResultDO> selectListByAssessmentId(Long assessmentId) {
        return selectList(new LambdaQueryWrapperX<RehabMotionRuleResultDO>()
                .eq(RehabMotionRuleResultDO::getAssessmentId, assessmentId)
                .orderByAsc(RehabMotionRuleResultDO::getId));
    }

    default int deleteByAssessmentId(Long assessmentId) {
        return delete(new LambdaQueryWrapperX<RehabMotionRuleResultDO>().eq(RehabMotionRuleResultDO::getAssessmentId, assessmentId));
    }

    /**
     * 物理删除派生数据（每次分析整体替换；历史结果保存在结果 JSON 文件中，可追溯）。
     * 租户插件会自动追加 tenant_id 条件。
     */
    @Delete("DELETE FROM rehab_motion_rule_result WHERE assessment_id = #{assessmentId}")
    int deletePhysicalByAssessmentId(@Param("assessmentId") Long assessmentId);

}
