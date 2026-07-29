package cn.iocoder.yudao.module.rehab.dal.mysql.report;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.RehabReportPageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.report.RehabReportDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 康复报告 Mapper
 */
@Mapper
public interface RehabReportMapper extends BaseMapperX<RehabReportDO> {

    default PageResult<RehabReportDO> selectPage(RehabReportPageReqVO reqVO, Collection<Long> patientIds) {
        if (patientIds != null && patientIds.isEmpty()) {
            return PageResult.empty(0L);
        }
        LambdaQueryWrapperX<RehabReportDO> query = new LambdaQueryWrapperX<RehabReportDO>()
                .eqIfPresent(RehabReportDO::getEpisodeId, reqVO.getEpisodeId())
                .eqIfPresent(RehabReportDO::getAssessmentId, reqVO.getAssessmentId())
                .eqIfPresent(RehabReportDO::getReportType, reqVO.getReportType())
                .eqIfPresent(RehabReportDO::getReportStatus, reqVO.getReportStatus())
                .eqIfPresent(RehabReportDO::getGeneratedBy, reqVO.getGeneratedBy())
                .eqIfPresent(RehabReportDO::getGenerationMode, reqVO.getGenerationMode())
                .betweenIfPresent(RehabReportDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(RehabReportDO::getCreateTime)
                .orderByDesc(RehabReportDO::getId);

        if (reqVO.getPatientId() != null) {
            query.eq(RehabReportDO::getPatientId, reqVO.getPatientId());
        } else if (patientIds != null) {
            query.in(RehabReportDO::getPatientId, patientIds);
        }
        return selectPage(reqVO, query);
    }

    default List<RehabReportDO> selectListByAssessmentId(Long assessmentId) {
        return selectList(new LambdaQueryWrapperX<RehabReportDO>()
                .eq(RehabReportDO::getAssessmentId, assessmentId)
                .orderByDesc(RehabReportDO::getReportVersion)
                .orderByDesc(RehabReportDO::getId));
    }

    default RehabReportDO selectLatestByAssessmentId(Long assessmentId) {
        List<RehabReportDO> list = selectList(new LambdaQueryWrapperX<RehabReportDO>()
                .eq(RehabReportDO::getAssessmentId, assessmentId)
                .orderByDesc(RehabReportDO::getReportVersion)
                .orderByDesc(RehabReportDO::getId));
        return CollUtil.isEmpty(list) ? null : list.get(0);
    }

    default List<RehabReportDO> selectListByPatientIds(Collection<Long> patientIds) {
        if (CollUtil.isEmpty(patientIds)) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<RehabReportDO>()
                .in(RehabReportDO::getPatientId, patientIds)
                .orderByDesc(RehabReportDO::getCreateTime)
                .orderByDesc(RehabReportDO::getId));
    }

    default RehabReportDO selectLatestByPatientId(Long patientId) {
        List<RehabReportDO> list = selectList(new LambdaQueryWrapperX<RehabReportDO>()
                .eq(RehabReportDO::getPatientId, patientId)
                .orderByDesc(RehabReportDO::getCreateTime)
                .orderByDesc(RehabReportDO::getId));
        return CollUtil.isEmpty(list) ? null : list.get(0);
    }

    default long selectCountByPatientId(Long patientId) {
        return selectCount(RehabReportDO::getPatientId, patientId);
    }

}
