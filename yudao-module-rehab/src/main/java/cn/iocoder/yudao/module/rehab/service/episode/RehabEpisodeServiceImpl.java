package cn.iocoder.yudao.module.rehab.service.episode;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.rehab.controller.admin.episode.vo.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.episode.RehabEpisodeDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.log.RehabPatientOperationLogDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.episode.RehabEpisodeMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.log.RehabPatientOperationLogMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabEpisodeConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabOperationTypeConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabPatientStatusConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabStageConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

/**
 * Episode Service 实现
 */
@Service
@Validated
public class RehabEpisodeServiceImpl implements RehabEpisodeService {

    private static final DateTimeFormatter EPISODE_NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Resource
    private RehabEpisodeMapper episodeMapper;
    @Resource
    private RehabPatientMapper patientMapper;
    @Resource
    private RehabPatientOperationLogMapper operationLogMapper;
    @Resource
    private RehabDataPermissionService dataPermissionService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createEpisode(RehabEpisodeCreateReqVO createReqVO, Long operatorUserId) {
        RehabPatientDO patient = validatePatientExists(createReqVO.getPatientId());
        validatePatientReadable(createReqVO.getPatientId(), operatorUserId);
        return createEpisodeInternal(patient, createReqVO, operatorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEpisode(RehabEpisodeUpdateReqVO updateReqVO, Long operatorUserId) {
        RehabEpisodeDO episode = validateEpisodeExists(updateReqVO.getId());
        validatePatientReadable(episode.getPatientId(), operatorUserId);

        RehabEpisodeDO oldEpisode = BeanUtils.toBean(episode, RehabEpisodeDO.class);

        RehabEpisodeDO updateObj = BeanUtils.toBean(updateReqVO, RehabEpisodeDO.class);
        updateObj.clean();
        episodeMapper.updateById(updateObj);

        RehabEpisodeDO newEpisode = episodeMapper.selectById(updateReqVO.getId());
        createOperationLog(episode.getPatientId(), RehabOperationTypeConstants.STAGE_CHANGE, operatorUserId,
                oldEpisode, newEpisode, "更新 episode 信息");
    }

    @Override
    public RehabEpisodeRespVO getEpisode(Long id, Long operatorUserId) {
        RehabEpisodeDO episode = validateEpisodeExists(id);
        validatePatientReadable(episode.getPatientId(), operatorUserId);
        return toRespVO(episode);
    }

    @Override
    public PageResult<RehabEpisodeRespVO> getEpisodePage(RehabEpisodePageReqVO pageReqVO, Long operatorUserId) {
        if (pageReqVO.getPatientId() != null) {
            validatePatientReadable(pageReqVO.getPatientId(), operatorUserId);
        } else if (!dataPermissionService.isSuperAdmin(operatorUserId) && !dataPermissionService.isClerk(operatorUserId)) {
            return PageResult.empty();
        }

        PageResult<RehabEpisodeDO> pageResult = episodeMapper.selectPage(pageReqVO);
        List<RehabEpisodeRespVO> list = pageResult.getList().stream().map(this::toRespVO).collect(Collectors.toList());
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStage(RehabEpisodeChangeStageReqVO reqVO, Long operatorUserId) {
        RehabEpisodeDO episode = validateEpisodeExists(reqVO.getId());
        validatePatientReadable(episode.getPatientId(), operatorUserId);

        if (!RehabStageConstants.ALL.contains(reqVO.getCurrentStage())) {
            throw exception(EPISODE_STAGE_INVALID);
        }

        RehabEpisodeDO oldEpisode = BeanUtils.toBean(episode, RehabEpisodeDO.class);

        episode.setCurrentStage(reqVO.getCurrentStage());
        if (StrUtil.isNotBlank(reqVO.getStatus())) {
            episode.setStatus(reqVO.getStatus());
        }
        if (ObjUtil.equal(episode.getStatus(), RehabEpisodeConstants.STATUS_CLOSED)
                || ObjUtil.equal(episode.getStatus(), RehabEpisodeConstants.STATUS_REFERRED_OUT)) {
            episode.setEndDate(LocalDate.now());
        }
        episode.clean();
        episodeMapper.updateById(episode);

        RehabPatientDO patientUpdate = new RehabPatientDO().setId(episode.getPatientId())
                .setCurrentStage(reqVO.getCurrentStage())
                .setCurrentStatus(resolvePatientStatusByEpisodeStatus(episode.getStatus()));
        patientMapper.updateById(patientUpdate);

        createOperationLog(episode.getPatientId(), RehabOperationTypeConstants.STAGE_CHANGE, operatorUserId,
                oldEpisode, episode, StrUtil.blankToDefault(reqVO.getRemark(), "修改 episode 阶段"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInitialEpisodeIfNeeded(Long patientId, Long primaryTherapistUserId,
                                             Boolean initEpisode, String episodeType, String primaryGoal,
                                             Long operatorUserId) {
        if (!Boolean.TRUE.equals(initEpisode)) {
            return null;
        }
        RehabPatientDO patient = validatePatientExists(patientId);

        RehabEpisodeCreateReqVO createReqVO = new RehabEpisodeCreateReqVO();
        createReqVO.setPatientId(patientId);
        createReqVO.setPrimaryTherapistUserId(primaryTherapistUserId);
        createReqVO.setEpisodeType(StrUtil.blankToDefault(episodeType, RehabEpisodeConstants.TYPE_INITIAL));
        createReqVO.setCurrentStage(RehabStageConstants.PENDING_ASSESSMENT);
        createReqVO.setStatus(RehabEpisodeConstants.STATUS_ACTIVE);
        createReqVO.setStartDate(LocalDate.now());
        createReqVO.setPrimaryGoal(primaryGoal);

        return createEpisodeInternal(patient, createReqVO, operatorUserId);
    }

    @Override
    public RehabEpisodeRespVO getCurrentEpisode(Long patientId, Long operatorUserId) {
        validatePatientReadable(patientId, operatorUserId);
        RehabEpisodeDO episode = episodeMapper.selectActiveByPatientId(patientId);
        return episode == null ? null : toRespVO(episode);
    }

    private Long createEpisodeInternal(RehabPatientDO patient, RehabEpisodeCreateReqVO createReqVO, Long operatorUserId) {
        RehabEpisodeDO activeEpisode = episodeMapper.selectActiveByPatientId(patient.getId());
        if (activeEpisode != null) {
            throw exception(EPISODE_ACTIVE_ALREADY_EXISTS);
        }

        RehabEpisodeDO episode = BeanUtils.toBean(createReqVO, RehabEpisodeDO.class);
        if (StrUtil.isBlank(episode.getEpisodeType())) {
            episode.setEpisodeType(RehabEpisodeConstants.TYPE_INITIAL);
        }
        if (StrUtil.isBlank(episode.getCurrentStage())) {
            episode.setCurrentStage(RehabStageConstants.PENDING_ASSESSMENT);
        }
        if (StrUtil.isBlank(episode.getStatus())) {
            episode.setStatus(RehabEpisodeConstants.STATUS_ACTIVE);
        }
        if (episode.getStartDate() == null) {
            episode.setStartDate(LocalDate.now());
        }

        episodeMapper.insert(episode);

        String episodeNo = generateEpisodeNo(episode.getId());
        episodeMapper.updateById(new RehabEpisodeDO().setId(episode.getId()).setEpisodeNo(episodeNo));
        episode.setEpisodeNo(episodeNo);

        RehabPatientDO patientUpdate = new RehabPatientDO().setId(patient.getId())
                .setCurrentStage(episode.getCurrentStage())
                .setCurrentStatus(RehabPatientStatusConstants.ACTIVE);
        if (episode.getPrimaryTherapistUserId() != null) {
            patientUpdate.setCurrentTherapistUserId(episode.getPrimaryTherapistUserId());
        }
        patientMapper.updateById(patientUpdate);

        createOperationLog(patient.getId(), RehabOperationTypeConstants.STAGE_CHANGE, operatorUserId,
                null, episode, "创建 episode " + episodeNo);

        return episode.getId();
    }

    private RehabEpisodeDO validateEpisodeExists(Long id) {
        RehabEpisodeDO episode = episodeMapper.selectById(id);
        if (episode == null) {
            throw exception(EPISODE_NOT_EXISTS);
        }
        return episode;
    }

    private RehabPatientDO validatePatientExists(Long id) {
        RehabPatientDO patient = patientMapper.selectById(id);
        if (patient == null) {
            throw exception(PATIENT_NOT_EXISTS);
        }
        return patient;
    }

    private void validatePatientReadable(Long patientId, Long operatorUserId) {
        if (!dataPermissionService.canReadPatient(patientId, operatorUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
    }

    private RehabEpisodeRespVO toRespVO(RehabEpisodeDO episode) {
        RehabEpisodeRespVO respVO = BeanUtils.toBean(episode, RehabEpisodeRespVO.class);
        if (episode.getPrimaryTherapistUserId() != null) {
            AdminUserRespDTO user = adminUserApi.getUser(episode.getPrimaryTherapistUserId());
            respVO.setPrimaryTherapistName(user == null ? "" : user.getNickname());
        }
        return respVO;
    }

    private String resolvePatientStatusByEpisodeStatus(String episodeStatus) {
        if (ObjUtil.equal(episodeStatus, RehabEpisodeConstants.STATUS_ACTIVE)) {
            return RehabPatientStatusConstants.ACTIVE;
        }
        if (ObjUtil.equal(episodeStatus, RehabEpisodeConstants.STATUS_PAUSED)) {
            return RehabPatientStatusConstants.INACTIVE;
        }
        return RehabPatientStatusConstants.ARCHIVED;
    }

    private String generateEpisodeNo(Long id) {
        String datePart = EPISODE_NO_DATE_FORMATTER.format(LocalDateTime.now());
        return "EP" + datePart + String.format("%04d", id % 10000);
    }

    private void createOperationLog(Long patientId, String operationType, Long operatorUserId,
                                    Object beforeData, Object afterData, String remark) {
        RehabPatientOperationLogDO log = RehabPatientOperationLogDO.builder()
                .patientId(patientId)
                .operationType(operationType)
                .operatorUserId(operatorUserId)
                .beforeDataJson(beforeData == null ? null : JsonUtils.toJsonString(beforeData))
                .afterDataJson(afterData == null ? null : JsonUtils.toJsonString(afterData))
                .remark(remark)
                .build();
        operationLogMapper.insert(log);
    }

}
