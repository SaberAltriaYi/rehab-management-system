package cn.iocoder.yudao.module.rehab.dal.mysql.report;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.RehabReportPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.RehabReportPatientRespVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.report.RehabReportDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 康复报告 Mapper
 */
@Mapper
public interface RehabReportMapper extends BaseMapperX<RehabReportDO> {

    @Select({
            "<script>",
            "SELECT COUNT(DISTINCT r.patient_id)",
            "FROM rehab_report r",
            "INNER JOIN rehab_patient p ON p.id = r.patient_id AND p.deleted = 0",
            "WHERE r.deleted = 0",
            "<if test='reqVO.patientId != null'>AND r.patient_id = #{reqVO.patientId}</if>",
            "<if test='reqVO.episodeId != null'>AND r.episode_id = #{reqVO.episodeId}</if>",
            "<if test='reqVO.assessmentId != null'>AND r.assessment_id = #{reqVO.assessmentId}</if>",
            "<if test='reqVO.reportType != null and reqVO.reportType != \"\"'>AND r.report_type = #{reqVO.reportType}</if>",
            "<if test='reqVO.reportStatus != null and reqVO.reportStatus != \"\"'>AND r.report_status = #{reqVO.reportStatus}</if>",
            "<if test='reqVO.generationMode != null and reqVO.generationMode != \"\"'>AND r.generation_mode = #{reqVO.generationMode}</if>",
            "<if test='reqVO.generatedBy != null'>AND r.generated_by = #{reqVO.generatedBy}</if>",
            "<if test='reqVO.keyword != null and reqVO.keyword != \"\"'>",
            "AND (p.name LIKE CONCAT('%', #{reqVO.keyword}, '%') OR p.patient_no LIKE CONCAT('%', #{reqVO.keyword}, '%'))",
            "</if>",
            "<if test='reqVO.createTime != null and reqVO.createTime.length > 0 and reqVO.createTime[0] != null'>",
            "AND r.create_time &gt;= #{reqVO.createTime[0]}",
            "</if>",
            "<if test='reqVO.createTime != null and reqVO.createTime.length > 1 and reqVO.createTime[1] != null'>",
            "AND r.create_time &lt;= #{reqVO.createTime[1]}",
            "</if>",
            "<if test='patientIds != null'>",
            "AND r.patient_id IN",
            "<foreach collection='patientIds' item='patientId' open='(' separator=',' close=')'>#{patientId}</foreach>",
            "</if>",
            "</script>"
    })
    Long selectPatientCount(@Param("reqVO") RehabReportPageReqVO reqVO,
                            @Param("patientIds") Collection<Long> patientIds);

    @Select({
            "<script>",
            "SELECT r.patient_id AS patientId, p.patient_no AS patientNo, p.name AS patientName,",
            "COUNT(*) AS reportCount, COUNT(DISTINCT r.assessment_id) AS assessmentCount,",
            "MAX(r.update_time) AS latestReportTime",
            "FROM rehab_report r",
            "INNER JOIN rehab_patient p ON p.id = r.patient_id AND p.deleted = 0",
            "WHERE r.deleted = 0",
            "<if test='reqVO.patientId != null'>AND r.patient_id = #{reqVO.patientId}</if>",
            "<if test='reqVO.episodeId != null'>AND r.episode_id = #{reqVO.episodeId}</if>",
            "<if test='reqVO.assessmentId != null'>AND r.assessment_id = #{reqVO.assessmentId}</if>",
            "<if test='reqVO.reportType != null and reqVO.reportType != \"\"'>AND r.report_type = #{reqVO.reportType}</if>",
            "<if test='reqVO.reportStatus != null and reqVO.reportStatus != \"\"'>AND r.report_status = #{reqVO.reportStatus}</if>",
            "<if test='reqVO.generationMode != null and reqVO.generationMode != \"\"'>AND r.generation_mode = #{reqVO.generationMode}</if>",
            "<if test='reqVO.generatedBy != null'>AND r.generated_by = #{reqVO.generatedBy}</if>",
            "<if test='reqVO.keyword != null and reqVO.keyword != \"\"'>",
            "AND (p.name LIKE CONCAT('%', #{reqVO.keyword}, '%') OR p.patient_no LIKE CONCAT('%', #{reqVO.keyword}, '%'))",
            "</if>",
            "<if test='reqVO.createTime != null and reqVO.createTime.length > 0 and reqVO.createTime[0] != null'>",
            "AND r.create_time &gt;= #{reqVO.createTime[0]}",
            "</if>",
            "<if test='reqVO.createTime != null and reqVO.createTime.length > 1 and reqVO.createTime[1] != null'>",
            "AND r.create_time &lt;= #{reqVO.createTime[1]}",
            "</if>",
            "<if test='patientIds != null'>",
            "AND r.patient_id IN",
            "<foreach collection='patientIds' item='patientId' open='(' separator=',' close=')'>#{patientId}</foreach>",
            "</if>",
            "GROUP BY r.patient_id, p.patient_no, p.name",
            "ORDER BY latestReportTime DESC, r.patient_id DESC",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<RehabReportPatientRespVO> selectPatientPage(@Param("reqVO") RehabReportPageReqVO reqVO,
                                                     @Param("patientIds") Collection<Long> patientIds,
                                                     @Param("offset") long offset,
                                                     @Param("limit") int limit);

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
