package cn.iocoder.yudao.module.rehab.dal.mysql.trigger;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.trigger.vo.RehabReassessmentTriggerPageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.trigger.RehabReassessmentTriggerDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RehabReassessmentTriggerMapper extends BaseMapperX<RehabReassessmentTriggerDO> {

    default PageResult<RehabReassessmentTriggerDO> selectPage(RehabReassessmentTriggerPageReqVO reqVO, Collection<Long> patientIds) {
        if (patientIds != null && patientIds.isEmpty()) {
            return PageResult.empty(0L);
        }
        LambdaQueryWrapperX<RehabReassessmentTriggerDO> query = new LambdaQueryWrapperX<RehabReassessmentTriggerDO>()
                .eqIfPresent(RehabReassessmentTriggerDO::getPlanId, reqVO.getPlanId())
                .eqIfPresent(RehabReassessmentTriggerDO::getPatientId, reqVO.getPatientId())
                .eqIfPresent(RehabReassessmentTriggerDO::getEpisodeId, reqVO.getEpisodeId())
                .eqIfPresent(RehabReassessmentTriggerDO::getTriggerType, reqVO.getTriggerType())
                .eqIfPresent(RehabReassessmentTriggerDO::getTriggerLevel, reqVO.getTriggerLevel())
                .eqIfPresent(RehabReassessmentTriggerDO::getTriggerStatus, reqVO.getTriggerStatus())
                .betweenIfPresent(RehabReassessmentTriggerDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(RehabReassessmentTriggerDO::getCreateTime)
                .orderByDesc(RehabReassessmentTriggerDO::getId);
        if (patientIds != null) {
            query.in(RehabReassessmentTriggerDO::getPatientId, patientIds);
        }
        return selectPage(reqVO, query);
    }

    default RehabReassessmentTriggerDO selectPendingByPlanAndType(Long planId, String triggerType) {
        List<RehabReassessmentTriggerDO> list = selectList(new LambdaQueryWrapperX<RehabReassessmentTriggerDO>()
                .eq(RehabReassessmentTriggerDO::getPlanId, planId)
                .eq(RehabReassessmentTriggerDO::getTriggerType, triggerType)
                .eq(RehabReassessmentTriggerDO::getTriggerStatus, "pending")
                .orderByDesc(RehabReassessmentTriggerDO::getId));
        return list.isEmpty() ? null : list.get(0);
    }

    default List<RehabReassessmentTriggerDO> selectListByPatientId(Long patientId) {
        return selectList(new LambdaQueryWrapperX<RehabReassessmentTriggerDO>()
                .eq(RehabReassessmentTriggerDO::getPatientId, patientId)
                .orderByDesc(RehabReassessmentTriggerDO::getCreateTime)
                .orderByDesc(RehabReassessmentTriggerDO::getId));
    }

    default long selectPendingCountByPatientId(Long patientId) {
        return selectCount(new LambdaQueryWrapperX<RehabReassessmentTriggerDO>()
                .eq(RehabReassessmentTriggerDO::getPatientId, patientId)
                .eq(RehabReassessmentTriggerDO::getTriggerStatus, "pending"));
    }

}
