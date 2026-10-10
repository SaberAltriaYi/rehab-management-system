package cn.iocoder.yudao.module.rehab.dal.mysql.motion;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.motion.vo.RehabMotionPageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 动作评估 Mapper
 */
@Mapper
public interface RehabMotionAssessmentMapper extends BaseMapperX<RehabMotionAssessmentDO> {

    /**
     * @param visiblePatientIds null = 全部可见；空集合 = 无可见患者
     */
    default PageResult<RehabMotionAssessmentDO> selectPage(RehabMotionPageReqVO reqVO, Collection<Long> visiblePatientIds) {
        LambdaQueryWrapperX<RehabMotionAssessmentDO> query = new LambdaQueryWrapperX<RehabMotionAssessmentDO>()
                .eqIfPresent(RehabMotionAssessmentDO::getPatientId, reqVO.getPatientId())
                .eqIfPresent(RehabMotionAssessmentDO::getStatus, reqVO.getStatus())
                .eqIfPresent(RehabMotionAssessmentDO::getVisitType, reqVO.getVisitType())
                .orderByDesc(RehabMotionAssessmentDO::getId);
        if (visiblePatientIds != null) {
            if (visiblePatientIds.isEmpty()) {
                return PageResult.empty();
            }
            query.in(RehabMotionAssessmentDO::getPatientId, visiblePatientIds);
        }
        return selectPage(reqVO, query);
    }

    default List<RehabMotionAssessmentDO> selectListByPatientId(Long patientId) {
        return selectList(new LambdaQueryWrapperX<RehabMotionAssessmentDO>()
                .eq(RehabMotionAssessmentDO::getPatientId, patientId)
                .orderByAsc(RehabMotionAssessmentDO::getCaptureTime)
                .orderByAsc(RehabMotionAssessmentDO::getId));
    }


    /** 行锁：串行化同一评估的任务创建/签署等并发操作（须在事务内调用）。 */
    @Select("SELECT id FROM rehab_motion_assessment WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Long lockById(@Param("id") Long id);

}
