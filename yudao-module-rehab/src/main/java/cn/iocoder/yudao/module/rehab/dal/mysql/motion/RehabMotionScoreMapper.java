package cn.iocoder.yudao.module.rehab.dal.mysql.motion;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionScoreDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 动作评估评分 Mapper
 */
@Mapper
public interface RehabMotionScoreMapper extends BaseMapperX<RehabMotionScoreDO> {

    default List<RehabMotionScoreDO> selectListByAssessmentId(Long assessmentId) {
        return selectList(new LambdaQueryWrapperX<RehabMotionScoreDO>()
                .eq(RehabMotionScoreDO::getAssessmentId, assessmentId)
                .orderByAsc(RehabMotionScoreDO::getId));
    }

    default List<RehabMotionScoreDO> selectListByAssessmentIds(Collection<Long> assessmentIds) {
        return selectList(new LambdaQueryWrapperX<RehabMotionScoreDO>()
                .in(RehabMotionScoreDO::getAssessmentId, assessmentIds));
    }

    /** 更新系统字段（显式 set，允许把系统分置空）；不触碰治疗师最终分。 */
    default int updateSystemFields(RehabMotionScoreDO upd) {
        LambdaUpdateWrapper<RehabMotionScoreDO> w = new LambdaUpdateWrapper<RehabMotionScoreDO>()
                .set(RehabMotionScoreDO::getScoringScheme, upd.getScoringScheme())
                .set(RehabMotionScoreDO::getSystemScore, upd.getSystemScore())
                .set(RehabMotionScoreDO::getProvisionalScore, upd.getProvisionalScore())
                .set(RehabMotionScoreDO::getSystemStatus, upd.getSystemStatus())
                .set(RehabMotionScoreDO::getDetailJson, upd.getDetailJson())
                .set(RehabMotionScoreDO::getEvidenceJson, upd.getEvidenceJson())
                .set(RehabMotionScoreDO::getAnalysisRevision, upd.getAnalysisRevision())
                .eq(RehabMotionScoreDO::getId, upd.getId());
        if (Boolean.TRUE.equals(upd.getSystemScoreChanged())) {
            w.set(RehabMotionScoreDO::getSystemScoreChanged, true)
                    .set(RehabMotionScoreDO::getFinalStatus, upd.getFinalStatus());
        }
        return update(null, w);
    }

}
