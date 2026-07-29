package cn.iocoder.yudao.module.rehab.dal.mysql.assessment;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo.RehabAssessmentPageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentRecordDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 评估主表 Mapper
 */
@Mapper
public interface RehabAssessmentRecordMapper extends BaseMapperX<RehabAssessmentRecordDO> {

    default PageResult<RehabAssessmentRecordDO> selectPage(RehabAssessmentPageReqVO reqVO, Collection<Long> patientIds) {
        if (patientIds != null && patientIds.isEmpty()) {
            return PageResult.empty(0L);
        }
        LambdaQueryWrapperX<RehabAssessmentRecordDO> query = new LambdaQueryWrapperX<RehabAssessmentRecordDO>()
                .eqIfPresent(RehabAssessmentRecordDO::getEpisodeId, reqVO.getEpisodeId())
                .eqIfPresent(RehabAssessmentRecordDO::getAssessmentType, reqVO.getAssessmentType())
                .eqIfPresent(RehabAssessmentRecordDO::getAssessorUserId, reqVO.getAssessorUserId())
                .eqIfPresent(RehabAssessmentRecordDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(RehabAssessmentRecordDO::getAssessmentDate, reqVO.getAssessmentDate())
                .orderByDesc(RehabAssessmentRecordDO::getAssessmentDate)
                .orderByDesc(RehabAssessmentRecordDO::getId);

        if (reqVO.getPatientId() != null) {
            query.eq(RehabAssessmentRecordDO::getPatientId, reqVO.getPatientId());
        } else if (patientIds != null) {
            query.in(RehabAssessmentRecordDO::getPatientId, patientIds);
        }
        return selectPage(reqVO, query);
    }

    default List<RehabAssessmentRecordDO> selectListByPatientId(Long patientId) {
        return selectList(new LambdaQueryWrapperX<RehabAssessmentRecordDO>()
                .eq(RehabAssessmentRecordDO::getPatientId, patientId)
                .orderByDesc(RehabAssessmentRecordDO::getAssessmentDate)
                .orderByDesc(RehabAssessmentRecordDO::getId));
    }

    default RehabAssessmentRecordDO selectLatestByPatientId(Long patientId) {
        List<RehabAssessmentRecordDO> list = selectList(new LambdaQueryWrapperX<RehabAssessmentRecordDO>()
                .eq(RehabAssessmentRecordDO::getPatientId, patientId)
                .orderByDesc(RehabAssessmentRecordDO::getAssessmentDate)
                .orderByDesc(RehabAssessmentRecordDO::getId));
        return CollUtil.isEmpty(list) ? null : list.get(0);
    }

    default List<RehabAssessmentRecordDO> selectListByPatientIds(Collection<Long> patientIds) {
        if (CollUtil.isEmpty(patientIds)) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<RehabAssessmentRecordDO>()
                .in(RehabAssessmentRecordDO::getPatientId, patientIds)
                .orderByDesc(RehabAssessmentRecordDO::getAssessmentDate)
                .orderByDesc(RehabAssessmentRecordDO::getId));
    }

    default long selectCountByPatientId(Long patientId) {
        return selectCount(RehabAssessmentRecordDO::getPatientId, patientId);
    }

}
