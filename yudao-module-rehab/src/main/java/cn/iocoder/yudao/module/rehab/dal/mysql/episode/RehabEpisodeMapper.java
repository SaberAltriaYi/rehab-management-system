package cn.iocoder.yudao.module.rehab.dal.mysql.episode;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.episode.vo.RehabEpisodePageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.episode.RehabEpisodeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Episode Mapper
 */
@Mapper
public interface RehabEpisodeMapper extends BaseMapperX<RehabEpisodeDO> {

    default PageResult<RehabEpisodeDO> selectPage(RehabEpisodePageReqVO pageReqVO) {
        return selectPage(pageReqVO, new LambdaQueryWrapperX<RehabEpisodeDO>()
                .eqIfPresent(RehabEpisodeDO::getPatientId, pageReqVO.getPatientId())
                .eqIfPresent(RehabEpisodeDO::getStatus, pageReqVO.getStatus())
                .eqIfPresent(RehabEpisodeDO::getCurrentStage, pageReqVO.getCurrentStage())
                .orderByDesc(RehabEpisodeDO::getCreateTime));
    }

    default RehabEpisodeDO selectActiveByPatientId(Long patientId) {
        List<RehabEpisodeDO> list = selectList(new LambdaQueryWrapperX<RehabEpisodeDO>()
                .eq(RehabEpisodeDO::getPatientId, patientId)
                .eq(RehabEpisodeDO::getStatus, "active")
                .orderByDesc(RehabEpisodeDO::getCreateTime));
        return list.isEmpty() ? null : list.get(0);
    }

    default List<RehabEpisodeDO> selectListByPatientId(Long patientId) {
        return selectList(new LambdaQueryWrapperX<RehabEpisodeDO>()
                .eq(RehabEpisodeDO::getPatientId, patientId)
                .orderByDesc(RehabEpisodeDO::getCreateTime));
    }

}
