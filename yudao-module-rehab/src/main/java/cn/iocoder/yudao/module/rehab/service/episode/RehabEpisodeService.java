package cn.iocoder.yudao.module.rehab.service.episode;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.episode.vo.*;

import javax.validation.Valid;

/**
 * Episode Service
 */
public interface RehabEpisodeService {

    Long createEpisode(@Valid RehabEpisodeCreateReqVO createReqVO, Long operatorUserId);

    void updateEpisode(@Valid RehabEpisodeUpdateReqVO updateReqVO, Long operatorUserId);

    RehabEpisodeRespVO getEpisode(Long id, Long operatorUserId);

    PageResult<RehabEpisodeRespVO> getEpisodePage(RehabEpisodePageReqVO pageReqVO, Long operatorUserId);

    void changeStage(@Valid RehabEpisodeChangeStageReqVO reqVO, Long operatorUserId);

    Long createInitialEpisodeIfNeeded(Long patientId, Long primaryTherapistUserId,
                                      Boolean initEpisode, String episodeType, String primaryGoal,
                                      Long operatorUserId);

    RehabEpisodeRespVO getCurrentEpisode(Long patientId, Long operatorUserId);

}
