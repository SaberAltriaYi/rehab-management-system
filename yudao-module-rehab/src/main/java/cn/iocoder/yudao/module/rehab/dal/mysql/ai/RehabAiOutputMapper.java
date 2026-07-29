package cn.iocoder.yudao.module.rehab.dal.mysql.ai;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.ai.vo.RehabAiOutputPageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.ai.RehabAiOutputDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RehabAiOutputMapper extends BaseMapperX<RehabAiOutputDO> {

    default PageResult<RehabAiOutputDO> selectPage(RehabAiOutputPageReqVO reqVO, Collection<Long> visibleJobIds) {
        if (visibleJobIds != null && visibleJobIds.isEmpty()) {
            return PageResult.empty();
        }
        LambdaQueryWrapperX<RehabAiOutputDO> query = new LambdaQueryWrapperX<RehabAiOutputDO>()
                .eqIfPresent(RehabAiOutputDO::getOutputType, reqVO.getOutputType())
                .eqIfPresent(RehabAiOutputDO::getTargetObjectType, reqVO.getTargetObjectType())
                .eqIfPresent(RehabAiOutputDO::getReviewStatus, reqVO.getReviewStatus())
                .eqIfPresent(RehabAiOutputDO::getSafetyStatus, reqVO.getSafetyStatus())
                .betweenIfPresent(RehabAiOutputDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(RehabAiOutputDO::getCreateTime)
                .orderByDesc(RehabAiOutputDO::getId);
        if (visibleJobIds != null) {
            query.in(RehabAiOutputDO::getAiJobId, visibleJobIds);
        }
        return selectPage(reqVO, query);
    }

    default List<RehabAiOutputDO> selectListByJobId(Long jobId) {
        return selectList(new LambdaQueryWrapperX<RehabAiOutputDO>()
                .eq(RehabAiOutputDO::getAiJobId, jobId)
                .orderByDesc(RehabAiOutputDO::getId));
    }

    default RehabAiOutputDO selectLatestByJobId(Long jobId) {
        List<RehabAiOutputDO> list = selectListByJobId(jobId);
        return list.isEmpty() ? null : list.get(0);
    }

    default RehabAiOutputDO selectLatestAcceptedByTarget(String outputType, Long targetObjectId, String targetObjectType) {
        List<RehabAiOutputDO> list = selectList(new LambdaQueryWrapperX<RehabAiOutputDO>()
                .eqIfPresent(RehabAiOutputDO::getOutputType, outputType)
                .eqIfPresent(RehabAiOutputDO::getTargetObjectType, targetObjectType)
                .eqIfPresent(RehabAiOutputDO::getTargetObjectId, targetObjectId)
                .eq(RehabAiOutputDO::getReviewStatus, "accepted")
                .eq(RehabAiOutputDO::getPatientVisible, true)
                .orderByDesc(RehabAiOutputDO::getReviewedTime)
                .orderByDesc(RehabAiOutputDO::getId));
        return list.isEmpty() ? null : list.get(0);
    }

    default RehabAiOutputDO selectLatestByTarget(String outputType, Long targetObjectId, String targetObjectType) {
        List<RehabAiOutputDO> list = selectList(new LambdaQueryWrapperX<RehabAiOutputDO>()
                .eqIfPresent(RehabAiOutputDO::getOutputType, outputType)
                .eqIfPresent(RehabAiOutputDO::getTargetObjectType, targetObjectType)
                .eqIfPresent(RehabAiOutputDO::getTargetObjectId, targetObjectId)
                .orderByDesc(RehabAiOutputDO::getCreateTime)
                .orderByDesc(RehabAiOutputDO::getId));
        return list.isEmpty() ? null : list.get(0);
    }
}
