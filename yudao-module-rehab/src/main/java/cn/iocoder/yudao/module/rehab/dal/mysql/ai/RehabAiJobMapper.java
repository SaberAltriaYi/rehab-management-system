package cn.iocoder.yudao.module.rehab.dal.mysql.ai;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.ai.vo.RehabAiJobPageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.ai.RehabAiJobDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface RehabAiJobMapper extends BaseMapperX<RehabAiJobDO> {

    default PageResult<RehabAiJobDO> selectPage(RehabAiJobPageReqVO reqVO, Collection<Long> patientIds) {
        if (patientIds != null && patientIds.isEmpty()) {
            return PageResult.empty();
        }
        LambdaQueryWrapperX<RehabAiJobDO> query = new LambdaQueryWrapperX<RehabAiJobDO>()
                .eqIfPresent(RehabAiJobDO::getJobType, reqVO.getJobType())
                .eqIfPresent(RehabAiJobDO::getStatus, reqVO.getStatus())
                .eqIfPresent(RehabAiJobDO::getTriggeredByUserId, reqVO.getTriggeredByUserId())
                .betweenIfPresent(RehabAiJobDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(RehabAiJobDO::getCreateTime)
                .orderByDesc(RehabAiJobDO::getId);
        if (reqVO.getPatientId() != null) {
            query.eq(RehabAiJobDO::getPatientId, reqVO.getPatientId());
        } else if (patientIds != null) {
            query.in(RehabAiJobDO::getPatientId, patientIds);
        }
        return selectPage(reqVO, query);
    }

    default List<RehabAiJobDO> selectListByPatientId(Long patientId) {
        return selectList(new LambdaQueryWrapperX<RehabAiJobDO>()
                .eq(RehabAiJobDO::getPatientId, patientId)
                .orderByDesc(RehabAiJobDO::getCreateTime)
                .orderByDesc(RehabAiJobDO::getId));
    }

    default RehabAiJobDO selectLatestByTypeAndTarget(String jobType, Long patientId, Long assessmentId,
                                                      Long reportId, Long planId, Long progressId,
                                                      Long alertId, Long triggerId) {
        LambdaQueryWrapperX<RehabAiJobDO> query = new LambdaQueryWrapperX<RehabAiJobDO>()
                .eqIfPresent(RehabAiJobDO::getJobType, jobType)
                .eqIfPresent(RehabAiJobDO::getPatientId, patientId)
                .eqIfPresent(RehabAiJobDO::getAssessmentId, assessmentId)
                .eqIfPresent(RehabAiJobDO::getReportId, reportId)
                .eqIfPresent(RehabAiJobDO::getPlanId, planId)
                .eqIfPresent(RehabAiJobDO::getProgressId, progressId)
                .eqIfPresent(RehabAiJobDO::getAlertId, alertId)
                .eqIfPresent(RehabAiJobDO::getTriggerId, triggerId)
                .orderByDesc(RehabAiJobDO::getCreateTime)
                .orderByDesc(RehabAiJobDO::getId);
        List<RehabAiJobDO> list = selectList(query);
        return list.isEmpty() ? null : list.get(0);
    }

    default List<RehabAiJobDO> selectListByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return selectBatchIds(ids);
    }
}
