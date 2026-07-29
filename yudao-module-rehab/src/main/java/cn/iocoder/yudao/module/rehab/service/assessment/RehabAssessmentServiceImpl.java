package cn.iocoder.yudao.module.rehab.service.assessment;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo.*;
import cn.iocoder.yudao.module.rehab.controller.admin.episode.vo.RehabEpisodeRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientRespVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.episode.RehabEpisodeDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.*;
import cn.iocoder.yudao.module.rehab.dal.mysql.episode.RehabEpisodeMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.report.RehabReportMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabAssessmentConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabOperationTypeConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.log.RehabAuditLogService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

/**
 * 评估管理 Service 实现
 */
@Service
@Validated
@Slf4j
public class RehabAssessmentServiceImpl implements RehabAssessmentService {

    private static final DateTimeFormatter ASSESSMENT_NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long MAX_ATTACHMENT_SIZE_BYTES = 16L * 1024L * 1024L;
    private static final int MAX_ATTACHMENT_FILE_NAME_LENGTH = 200;
    private static final Set<String> ALLOWED_ATTACHMENT_EXTENSIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("pdf", "jpg", "jpeg", "png", "webp", "heic",
                    "doc", "docx", "xls", "xlsx", "csv", "txt")));

    @Value("${yudao.rehab.storage-path:./data/rehab}")
    private String storagePath;

    @Resource
    private RehabAssessmentRecordMapper assessmentRecordMapper;
    @Resource
    private RehabAssessmentModuleDataMapper moduleDataMapper;
    @Resource
    private RehabAssessmentAttachmentMapper attachmentMapper;
    @Resource
    private RehabAssessmentOperationLogMapper operationLogMapper;
    @Resource
    private RehabPatientMapper patientMapper;
    @Resource
    private RehabEpisodeMapper episodeMapper;
    @Resource
    private RehabReportMapper reportMapper;
    @Resource
    private RehabDataPermissionService dataPermissionService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private RehabAuditLogService auditLogService;
    @Resource
    private RehabStaticAssessmentSummaryBuilder staticAssessmentSummaryBuilder;
    @Resource
    private RehabNasmCesSummaryBuilder nasmCesSummaryBuilder;
    @Resource
    private RehabSfmaSummaryBuilder sfmaSummaryBuilder;
    @Resource
    private RehabSfmaBookProtocol sfmaBookProtocol;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RehabAssessmentCreateRespVO createAssessment(RehabAssessmentCreateReqVO reqVO, Long operatorUserId) {
        RehabPatientDO patient = validatePatientExists(reqVO.getPatientId());
        validatePatientReadable(patient.getId(), operatorUserId);
        RehabEpisodeDO episode = validateEpisodeExists(reqVO.getEpisodeId());
        validateEpisodeBelongsToPatient(episode, patient.getId());

        RehabAssessmentRecordDO assessment = BeanUtils.toBean(reqVO, RehabAssessmentRecordDO.class);
        if (assessment.getAssessorUserId() == null) {
            assessment.setAssessorUserId(operatorUserId);
        }
        if (StrUtil.isBlank(assessment.getAssessmentType())) {
            assessment.setAssessmentType(RehabAssessmentConstants.TYPE_COMPREHENSIVE_ASSESSMENT);
        }
        validateAssessmentType(assessment.getAssessmentType());
        if (assessment.getAssessmentDate() == null) {
            assessment.setAssessmentDate(LocalDate.now());
        }
        if (StrUtil.isBlank(assessment.getLocationType())) {
            assessment.setLocationType(RehabAssessmentConstants.LOCATION_CLINIC);
        }
        if (StrUtil.isBlank(assessment.getStatus())) {
            assessment.setStatus(RehabAssessmentConstants.STATUS_DRAFT);
        }
        if (StrUtil.isBlank(assessment.getRawInputStatus())) {
            assessment.setRawInputStatus(RehabAssessmentConstants.RAW_INPUT_MISSING);
        }
        if (StrUtil.isBlank(assessment.getQualityGrade())) {
            assessment.setQualityGrade(RehabAssessmentConstants.QUALITY_D);
        }
        if (StrUtil.isBlank(assessment.getConfidenceGrade())) {
            assessment.setConfidenceGrade(RehabAssessmentConstants.CONFIDENCE_LOW);
        }

        assessmentRecordMapper.insert(assessment);
        String assessmentNo = generateAssessmentNo(assessment.getId());
        assessmentRecordMapper.updateById(new RehabAssessmentRecordDO().setId(assessment.getId()).setAssessmentNo(assessmentNo));

        if (CollUtil.isNotEmpty(reqVO.getModuleDataList())) {
            for (RehabAssessmentModuleDataItemVO module : reqVO.getModuleDataList()) {
                upsertModuleData(assessment.getId(), module.getModuleType(), module.getModuleStatus(), module.getDataJson(),
                        module.getSourceType(), module.getVersion(), module.getNote());
            }
        }
        ensurePrimaryModuleData(assessment.getId(), assessment.getAssessmentType());
        refreshAssessmentDerivedStatus(assessment.getId());

        createOperationLog(assessment.getId(), RehabOperationTypeConstants.ASSESSMENT_CREATE, operatorUserId,
                null, assessmentRecordMapper.selectById(assessment.getId()), "创建评估记录");

        RehabAssessmentCreateRespVO respVO = new RehabAssessmentCreateRespVO();
        respVO.setId(assessment.getId());
        respVO.setAssessmentNo(assessmentNo);
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAssessment(RehabAssessmentUpdateReqVO reqVO, Long operatorUserId) {
        RehabAssessmentRecordDO oldAssessment = validateAssessmentExists(reqVO.getId());
        validatePatientReadable(oldAssessment.getPatientId(), operatorUserId);
        ensureAssessmentEditable(oldAssessment);

        RehabEpisodeDO episode = validateEpisodeExists(reqVO.getEpisodeId());
        validateEpisodeBelongsToPatient(episode, reqVO.getPatientId());

        RehabAssessmentRecordDO updateObj = BeanUtils.toBean(reqVO, RehabAssessmentRecordDO.class);
        if (updateObj.getAssessorUserId() == null) {
            updateObj.setAssessorUserId(oldAssessment.getAssessorUserId());
        }
        if (StrUtil.isBlank(updateObj.getAssessmentType())) {
            updateObj.setAssessmentType(oldAssessment.getAssessmentType());
        }
        validateAssessmentType(updateObj.getAssessmentType());
        updateObj.clean();
        assessmentRecordMapper.updateById(updateObj);

        if (CollUtil.isNotEmpty(reqVO.getModuleDataList())) {
            for (RehabAssessmentModuleDataItemVO module : reqVO.getModuleDataList()) {
                upsertModuleData(reqVO.getId(), module.getModuleType(), module.getModuleStatus(), module.getDataJson(),
                        module.getSourceType(), module.getVersion(), module.getNote());
            }
        }
        ensurePrimaryModuleData(reqVO.getId(), updateObj.getAssessmentType());
        refreshAssessmentDerivedStatus(reqVO.getId());

        RehabAssessmentRecordDO newAssessment = assessmentRecordMapper.selectById(reqVO.getId());
        createOperationLog(reqVO.getId(), RehabOperationTypeConstants.ASSESSMENT_UPDATE, operatorUserId,
                oldAssessment, newAssessment, "更新评估记录");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAssessment(Long id, Long operatorUserId) {
        RehabAssessmentRecordDO assessment = validateAssessmentExists(id);
        validatePatientReadable(assessment.getPatientId(), operatorUserId);
        if (CollUtil.isNotEmpty(reportMapper.selectListByAssessmentId(id))) {
            throw exception(ASSESSMENT_CAN_NOT_DELETE);
        }
        assessmentRecordMapper.deleteById(id);
        moduleDataMapper.delete(new LambdaQueryWrapperX<RehabAssessmentModuleDataDO>()
                .eq(RehabAssessmentModuleDataDO::getAssessmentId, id));
        attachmentMapper.delete(new LambdaQueryWrapperX<RehabAssessmentAttachmentDO>()
                .eq(RehabAssessmentAttachmentDO::getAssessmentId, id));
        createOperationLog(id, RehabOperationTypeConstants.ARCHIVE, operatorUserId, assessment, null, "删除评估记录（逻辑删除）");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveAssessment(RehabAssessmentArchiveReqVO reqVO, Long operatorUserId) {
        RehabAssessmentRecordDO oldAssessment = validateAssessmentExists(reqVO.getId());
        validatePatientReadable(oldAssessment.getPatientId(), operatorUserId);

        RehabAssessmentRecordDO updateObj = new RehabAssessmentRecordDO().setId(reqVO.getId())
                .setStatus(RehabAssessmentConstants.STATUS_ARCHIVED);
        assessmentRecordMapper.updateById(updateObj);
        createOperationLog(reqVO.getId(), RehabOperationTypeConstants.ARCHIVE, operatorUserId,
                oldAssessment, assessmentRecordMapper.selectById(reqVO.getId()),
                StrUtil.blankToDefault(reqVO.getRemark(), "归档评估"));
    }

    @Override
    public RehabAssessmentDetailRespVO getAssessment(Long id, Long operatorUserId) {
        RehabAssessmentRecordDO assessment = validateAssessmentExists(id);
        validatePatientReadable(assessment.getPatientId(), operatorUserId);

        RehabAssessmentDetailRespVO detail = new RehabAssessmentDetailRespVO();
        detail.setAssessment(toAssessmentRespVOList(Collections.singletonList(assessment)).get(0));
        detail.setPatient(toPatientRespVO(patientMapper.selectById(assessment.getPatientId())));
        detail.setEpisode(toEpisodeRespVO(episodeMapper.selectById(assessment.getEpisodeId())));
        detail.setModuleDataList(getModuleDataList(id, operatorUserId));
        detail.setAttachments(getAttachmentList(id, operatorUserId));
        detail.setOperationLogs(getOperationLogList(id, operatorUserId));
        return detail;
    }

    @Override
    public PageResult<RehabAssessmentRespVO> getAssessmentPage(RehabAssessmentPageReqVO reqVO, Long operatorUserId) {
        Set<Long> visiblePatientIds = dataPermissionService.getVisiblePatientIds(operatorUserId);
        if (visiblePatientIds != null && visiblePatientIds.isEmpty()) {
            return PageResult.empty();
        }

        Collection<Long> filteredPatientIds = visiblePatientIds;
        if (reqVO.getPatientId() != null) {
            validatePatientReadable(reqVO.getPatientId(), operatorUserId);
            filteredPatientIds = Collections.singleton(reqVO.getPatientId());
        }

        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            List<RehabPatientDO> keywordPatients = patientMapper.selectList(new LambdaQueryWrapperX<RehabPatientDO>()
                    .and(wrapper -> wrapper.like(RehabPatientDO::getName, reqVO.getKeyword())
                            .or().like(RehabPatientDO::getPatientNo, reqVO.getKeyword()))
                    .orderByDesc(RehabPatientDO::getId));
            Set<Long> keywordPatientIds = keywordPatients.stream().map(RehabPatientDO::getId).collect(Collectors.toSet());
            filteredPatientIds = intersectPatientIds(filteredPatientIds, keywordPatientIds);
            if (CollUtil.isEmpty(filteredPatientIds)) {
                return PageResult.empty();
            }
        }

        PageResult<RehabAssessmentRecordDO> pageResult = assessmentRecordMapper.selectPage(reqVO, filteredPatientIds);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        return new PageResult<>(toAssessmentRespVOList(pageResult.getList()), pageResult.getTotal());
    }

    @Override
    public List<RehabAssessmentModuleDataRespVO> getModuleDataList(Long assessmentId, Long operatorUserId) {
        RehabAssessmentRecordDO assessment = validateAssessmentExists(assessmentId);
        validatePatientReadable(assessment.getPatientId(), operatorUserId);
        List<RehabAssessmentModuleDataDO> moduleList = moduleDataMapper.selectListByAssessmentId(assessmentId);
        return moduleList.stream().map(this::toModuleRespVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RehabAssessmentModuleDataRespVO saveModuleData(RehabAssessmentModuleDataSaveReqVO reqVO, Long operatorUserId) {
        RehabAssessmentRecordDO assessment = validateAssessmentExists(reqVO.getAssessmentId());
        validatePatientReadable(assessment.getPatientId(), operatorUserId);
        ensureAssessmentEditable(assessment);

        RehabAssessmentModuleDataDO oldData = moduleDataMapper.selectByAssessmentIdAndModuleType(reqVO.getAssessmentId(), reqVO.getModuleType());
        RehabAssessmentModuleDataDO saved = upsertModuleData(reqVO.getAssessmentId(), reqVO.getModuleType(), reqVO.getModuleStatus(),
                reqVO.getDataJson(), reqVO.getSourceType(), reqVO.getVersion(), reqVO.getNote());
        refreshAssessmentDerivedStatus(reqVO.getAssessmentId());

        createOperationLog(reqVO.getAssessmentId(), RehabOperationTypeConstants.ASSESSMENT_PARSE, operatorUserId,
                oldData, saved, "保存模块数据: " + reqVO.getModuleType());
        return toModuleRespVO(saved);
    }

    @Override
    public Map<String, Object> getSfmaBookProtocol() {
        return sfmaBookProtocol.getProtocol();
    }

    @Override
    public List<RehabAssessmentAttachmentRespVO> getAttachmentList(Long assessmentId, Long operatorUserId) {
        RehabAssessmentRecordDO assessment = validateAssessmentExists(assessmentId);
        validatePatientReadable(assessment.getPatientId(), operatorUserId);
        return attachmentMapper.selectListByAssessmentId(assessmentId).stream()
                .map(item -> BeanUtils.toBean(item, RehabAssessmentAttachmentRespVO.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RehabAssessmentAttachmentRespVO uploadAttachment(Long assessmentId, String moduleType, MultipartFile file, Long operatorUserId) {
        RehabAssessmentRecordDO assessment = validateAssessmentExists(assessmentId);
        validatePatientReadable(assessment.getPatientId(), operatorUserId);
        ensureAssessmentEditable(assessment);

        if (StrUtil.isBlank(moduleType) || !RehabAssessmentConstants.MODULE_TYPES.contains(moduleType)) {
            throw exception(ASSESSMENT_MODULE_TYPE_INVALID);
        }
        if (file == null || file.isEmpty()) {
            throw exception(ASSESSMENT_ATTACHMENT_NOT_FOUND);
        }
        if (file.getSize() > MAX_ATTACHMENT_SIZE_BYTES) {
            throw exception(ASSESSMENT_ATTACHMENT_SIZE_EXCEEDED);
        }

        String safeFileName = sanitizeAttachmentFileName(file.getOriginalFilename());
        String extName = FileUtil.extName(safeFileName).toLowerCase(Locale.ROOT);
        if (StrUtil.isBlank(extName) || !ALLOWED_ATTACHMENT_EXTENSIONS.contains(extName)) {
            throw exception(ASSESSMENT_ATTACHMENT_TYPE_INVALID);
        }
        String targetFileName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().replace("-", "")
                + "." + extName;
        String dir = storagePath + File.separator + "assessments" + File.separator + assessmentId + File.separator + "attachments";
        FileUtil.mkdir(dir);
        File target = new File(dir, targetFileName);
        try {
            file.transferTo(target);
        } catch (IOException e) {
            FileUtil.del(target);
            throw exception(ASSESSMENT_ATTACHMENT_STORE_FAILED);
        }

        RehabAssessmentAttachmentDO attachment = RehabAssessmentAttachmentDO.builder()
                .assessmentId(assessmentId)
                .moduleType(moduleType)
                .fileName(safeFileName)
                .fileType(StrUtil.blankToDefault(file.getContentType(), "application/octet-stream"))
                .filePath(target.getAbsolutePath())
                .fileSize(file.getSize())
                .uploadUserId(operatorUserId)
                .parseStatus("uploaded")
                .parseMessage("待解析")
                .build();
        try {
            attachmentMapper.insert(attachment);
            createOperationLog(assessmentId, RehabOperationTypeConstants.ASSESSMENT_PARSE, operatorUserId,
                    null, attachment, "上传附件: " + safeFileName);
        } catch (RuntimeException ex) {
            FileUtil.del(target);
            throw ex;
        }

        return BeanUtils.toBean(attachment, RehabAssessmentAttachmentRespVO.class);
    }

    @Override
    public RehabAssessmentAttachmentFile downloadAttachment(Long attachmentId, Long operatorUserId) {
        RehabAssessmentAttachmentDO attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw exception(ASSESSMENT_ATTACHMENT_NOT_FOUND);
        }
        RehabAssessmentRecordDO assessment = validateAssessmentExists(attachment.getAssessmentId());
        validatePatientReadable(assessment.getPatientId(), operatorUserId);

        File target = FileUtil.file(attachment.getFilePath());
        try {
            File storageRoot = FileUtil.file(storagePath).getCanonicalFile();
            File canonicalTarget = target.getCanonicalFile();
            String storagePrefix = storageRoot.getPath() + File.separator;
            if (!canonicalTarget.getPath().startsWith(storagePrefix) || !canonicalTarget.isFile()) {
                throw exception(ASSESSMENT_ATTACHMENT_NOT_FOUND);
            }
            if (canonicalTarget.length() > MAX_ATTACHMENT_SIZE_BYTES) {
                throw exception(ASSESSMENT_ATTACHMENT_SIZE_EXCEEDED);
            }
            return new RehabAssessmentAttachmentFile(
                    StrUtil.blankToDefault(attachment.getFileName(), canonicalTarget.getName()),
                    StrUtil.blankToDefault(attachment.getFileType(), "application/octet-stream"),
                    FileUtil.readBytes(canonicalTarget));
        } catch (IOException e) {
            throw exception(ASSESSMENT_ATTACHMENT_NOT_FOUND);
        }
    }

    private String sanitizeAttachmentFileName(String originalFilename) {
        String fileName = FileUtil.getName(StrUtil.blankToDefault(originalFilename, ""));
        fileName = fileName.replaceAll("[\\r\\n\\u0000]", "_").trim();
        if (StrUtil.isBlank(fileName) || fileName.length() > MAX_ATTACHMENT_FILE_NAME_LENGTH) {
            throw exception(ASSESSMENT_ATTACHMENT_TYPE_INVALID);
        }
        return fileName;
    }

    @Override
    public List<RehabAssessmentOperationLogRespVO> getOperationLogList(Long assessmentId, Long operatorUserId) {
        RehabAssessmentRecordDO assessment = validateAssessmentExists(assessmentId);
        validatePatientReadable(assessment.getPatientId(), operatorUserId);
        return operationLogMapper.selectListByAssessmentId(assessmentId).stream().map(item -> {
            RehabAssessmentOperationLogRespVO respVO = BeanUtils.toBean(item, RehabAssessmentOperationLogRespVO.class);
            if (item.getOperatorUserId() != null) {
                AdminUserRespDTO user = adminUserApi.getUser(item.getOperatorUserId());
                respVO.setOperatorName(user == null ? "" : user.getNickname());
            }
            return respVO;
        }).collect(Collectors.toList());
    }

    private RehabAssessmentRecordDO validateAssessmentExists(Long id) {
        RehabAssessmentRecordDO assessment = assessmentRecordMapper.selectById(id);
        if (assessment == null) {
            throw exception(ASSESSMENT_NOT_EXISTS);
        }
        return assessment;
    }

    private void validateAssessmentType(String assessmentType) {
        if (StrUtil.isBlank(assessmentType) || !RehabAssessmentConstants.isValidAssessmentTypeV2(assessmentType)) {
            throw exception(ASSESSMENT_TYPE_INVALID);
        }
    }

    private RehabPatientDO validatePatientExists(Long id) {
        RehabPatientDO patient = patientMapper.selectById(id);
        if (patient == null) {
            throw exception(PATIENT_NOT_EXISTS);
        }
        return patient;
    }

    private RehabEpisodeDO validateEpisodeExists(Long id) {
        RehabEpisodeDO episode = episodeMapper.selectById(id);
        if (episode == null) {
            throw exception(EPISODE_NOT_EXISTS);
        }
        return episode;
    }

    private void validateEpisodeBelongsToPatient(RehabEpisodeDO episode, Long patientId) {
        if (!ObjUtil.equals(episode.getPatientId(), patientId)) {
            throw exception(ASSESSMENT_PATIENT_EPISODE_MISMATCH);
        }
    }

    private void ensureAssessmentEditable(RehabAssessmentRecordDO assessment) {
        if (ObjUtil.equals(assessment.getStatus(), RehabAssessmentConstants.STATUS_ARCHIVED)) {
            throw exception(ASSESSMENT_ALREADY_ARCHIVED);
        }
    }

    private void validatePatientReadable(Long patientId, Long operatorUserId) {
        if (!dataPermissionService.canReadPatient(patientId, operatorUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
    }

    private void ensurePrimaryModuleData(Long assessmentId, String assessmentType) {
        validateAssessmentType(assessmentType);
        List<RehabAssessmentModuleDataDO> modules = moduleDataMapper.selectListByAssessmentId(assessmentId);
        if (CollUtil.isNotEmpty(modules)) {
            return;
        }
        String moduleType = RehabAssessmentConstants.resolvePrimaryModuleType(assessmentType);
        if (StrUtil.isBlank(moduleType)) {
            throw exception(ASSESSMENT_TYPE_INVALID);
        }
        upsertModuleData(assessmentId, moduleType, RehabAssessmentConstants.MODULE_STATUS_COMPLETED,
                Collections.emptyMap(), RehabAssessmentConstants.MODULE_SOURCE_MANUAL, "v1",
                "按评估类型自动初始化占位模块");
    }

    private String generateAssessmentNo(Long id) {
        String datePart = ASSESSMENT_NO_DATE_FORMATTER.format(LocalDateTime.now());
        return "ASM" + datePart + String.format("%04d", id % 10000);
    }

    private Collection<Long> intersectPatientIds(Collection<Long> baseIds, Collection<Long> extraIds) {
        if (extraIds == null) {
            return baseIds;
        }
        if (baseIds == null) {
            return extraIds;
        }
        Set<Long> result = new LinkedHashSet<>(baseIds);
        result.retainAll(extraIds);
        return result;
    }

    private RehabAssessmentModuleDataDO upsertModuleData(Long assessmentId, String moduleType, String moduleStatus,
                                                         Object dataJson, String sourceType, String version, String note) {
        if (StrUtil.isBlank(moduleType) || !RehabAssessmentConstants.MODULE_TYPES.contains(moduleType)) {
            throw exception(ASSESSMENT_MODULE_TYPE_INVALID);
        }
        RehabAssessmentModuleDataDO oldData = moduleDataMapper.selectByAssessmentIdAndModuleType(assessmentId, moduleType);

        RehabAssessmentModuleDataDO data = oldData == null ? new RehabAssessmentModuleDataDO() : oldData;
        data.setAssessmentId(assessmentId);
        data.setModuleType(moduleType);
        data.setModuleStatus(StrUtil.blankToDefault(moduleStatus, RehabAssessmentConstants.MODULE_STATUS_COMPLETED));
        data.setDataJson(buildAndSerializeModuleData(moduleType, dataJson));
        data.setSourceType(StrUtil.blankToDefault(sourceType, RehabAssessmentConstants.MODULE_SOURCE_MANUAL));
        data.setVersion(StrUtil.blankToDefault(version, "v1"));
        data.setNote(note);

        if (data.getId() == null) {
            moduleDataMapper.insert(data);
        } else {
            data.clean();
            moduleDataMapper.updateById(data);
        }
        return moduleDataMapper.selectByAssessmentIdAndModuleType(assessmentId, moduleType);
    }

    private String buildAndSerializeModuleData(String moduleType, Object dataJson) {
        if (StrUtil.equals(moduleType, RehabAssessmentConstants.MODULE_STATIC) && staticAssessmentSummaryBuilder != null) {
            try {
                return serializeJson(staticAssessmentSummaryBuilder.enrichWithSummary(dataJson));
            } catch (Exception ex) {
                log.warn("[assessment][static_summary] failed, fallback with conservative summary. reason={}", ex.getMessage(), ex);
                try {
                    return serializeJson(staticAssessmentSummaryBuilder.enrichWithFallback(dataJson, ex.getMessage()));
                } catch (Exception fallbackEx) {
                    log.error("[assessment][static_summary] fallback failed, save raw payload instead. reason={}",
                            fallbackEx.getMessage(), fallbackEx);
                    return serializeJson(dataJson);
                }
            }
        }
        if (StrUtil.equals(moduleType, RehabAssessmentConstants.MODULE_NASM) && nasmCesSummaryBuilder != null) {
            try {
                return serializeJson(nasmCesSummaryBuilder.enrichWithSummary(dataJson));
            } catch (Exception ex) {
                log.warn("[assessment][nasm_ces_summary] failed, fallback with conservative summary. reason={}", ex.getMessage(), ex);
                try {
                    return serializeJson(nasmCesSummaryBuilder.enrichWithFallback(dataJson, ex.getMessage()));
                } catch (Exception fallbackEx) {
                    log.error("[assessment][nasm_ces_summary] fallback failed, save raw payload instead. reason={}",
                            fallbackEx.getMessage(), fallbackEx);
                    return serializeJson(dataJson);
                }
            }
        }
        if (StrUtil.equals(moduleType, RehabAssessmentConstants.MODULE_SFMA) && sfmaSummaryBuilder != null) {
            if (sfmaBookProtocol != null) {
                try {
                    sfmaBookProtocol.validate(dataJson);
                } catch (IllegalArgumentException ex) {
                    throw exception(ASSESSMENT_SFMA_PROTOCOL_INVALID, ex.getMessage());
                }
            }
            try {
                return serializeJson(sfmaSummaryBuilder.enrichWithSummary(dataJson));
            } catch (Exception ex) {
                log.warn("[assessment][sfma_summary] failed, fallback with conservative summary. reason={}", ex.getMessage(), ex);
                try {
                    return serializeJson(sfmaSummaryBuilder.enrichWithFallback(dataJson, ex.getMessage()));
                } catch (Exception fallbackEx) {
                    log.error("[assessment][sfma_summary] fallback failed, save raw payload instead. reason={}",
                            fallbackEx.getMessage(), fallbackEx);
                    return serializeJson(dataJson);
                }
            }
        }
        return serializeJson(dataJson);
    }

    private void refreshAssessmentDerivedStatus(Long assessmentId) {
        RehabAssessmentRecordDO assessment = assessmentRecordMapper.selectById(assessmentId);
        if (assessment == null || ObjUtil.equals(assessment.getStatus(), RehabAssessmentConstants.STATUS_ARCHIVED)) {
            return;
        }

        List<RehabAssessmentModuleDataDO> modules = moduleDataMapper.selectListByAssessmentId(assessmentId);
        String rawInputStatus;
        if (CollUtil.isEmpty(modules)) {
            rawInputStatus = RehabAssessmentConstants.RAW_INPUT_MISSING;
        } else {
            long completedCount = modules.stream()
                    .filter(item -> ObjUtil.equals(item.getModuleStatus(), RehabAssessmentConstants.MODULE_STATUS_COMPLETED))
                    .count();
            if (completedCount == modules.size()) {
                rawInputStatus = RehabAssessmentConstants.RAW_INPUT_COMPLETE;
            } else {
                rawInputStatus = RehabAssessmentConstants.RAW_INPUT_PARTIAL;
            }
        }

        RehabAssessmentRecordDO updateObj = new RehabAssessmentRecordDO().setId(assessmentId)
                .setRawInputStatus(rawInputStatus);

        if (!ObjUtil.equals(assessment.getStatus(), RehabAssessmentConstants.STATUS_REVIEWED)) {
            if (ObjUtil.equals(rawInputStatus, RehabAssessmentConstants.RAW_INPUT_COMPLETE)) {
                updateObj.setStatus(RehabAssessmentConstants.STATUS_COMPLETED);
            } else {
                updateObj.setStatus(RehabAssessmentConstants.STATUS_DRAFT);
            }
        }
        assessmentRecordMapper.updateById(updateObj);
    }

    private void createOperationLog(Long assessmentId, String operationType, Long operatorUserId,
                                    Object beforeData, Object afterData, String remark) {
        RehabAssessmentOperationLogDO log = RehabAssessmentOperationLogDO.builder()
                .assessmentId(assessmentId)
                .operationType(operationType)
                .operatorUserId(operatorUserId)
                .beforeDataJson(beforeData == null ? null : JsonUtils.toJsonString(beforeData))
                .afterDataJson(afterData == null ? null : JsonUtils.toJsonString(afterData))
                .remark(remark)
                .build();
        operationLogMapper.insert(log);
        if (auditLogService != null) {
            auditLogService.createAuditLog("assessment", assessmentId, operationType, operatorUserId,
                    resolveRole(operatorUserId), beforeData, afterData, "success", remark);
        }
    }

    private String resolveRole(Long userId) {
        if (dataPermissionService.isSuperAdmin(userId)) {
            return "admin";
        }
        if (dataPermissionService.isTherapist(userId)) {
            return "therapist";
        }
        if (dataPermissionService.isClerk(userId)) {
            return "clerk";
        }
        return "unknown";
    }

    private List<RehabAssessmentRespVO> toAssessmentRespVOList(List<RehabAssessmentRecordDO> list) {
        Set<Long> patientIds = list.stream().map(RehabAssessmentRecordDO::getPatientId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> episodeIds = list.stream().map(RehabAssessmentRecordDO::getEpisodeId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> assessorIds = list.stream().map(RehabAssessmentRecordDO::getAssessorUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, RehabPatientDO> patientMap = patientMapper.selectBatchIds(patientIds).stream()
                .collect(Collectors.toMap(RehabPatientDO::getId, item -> item, (a, b) -> a));
        Map<Long, RehabEpisodeDO> episodeMap = episodeMapper.selectBatchIds(episodeIds).stream()
                .collect(Collectors.toMap(RehabEpisodeDO::getId, item -> item, (a, b) -> a));
        Map<Long, String> assessorNameMap = new HashMap<>();
        assessorIds.forEach(id -> {
            AdminUserRespDTO user = adminUserApi.getUser(id);
            assessorNameMap.put(id, user == null ? "" : user.getNickname());
        });

        return list.stream().map(item -> {
            RehabAssessmentRespVO respVO = BeanUtils.toBean(item, RehabAssessmentRespVO.class);
            RehabPatientDO patient = patientMap.get(item.getPatientId());
            if (patient != null) {
                respVO.setPatientNo(patient.getPatientNo());
                respVO.setPatientName(patient.getName());
            }
            RehabEpisodeDO episode = episodeMap.get(item.getEpisodeId());
            if (episode != null) {
                respVO.setEpisodeNo(episode.getEpisodeNo());
            }
            respVO.setAssessorName(assessorNameMap.get(item.getAssessorUserId()));
            return respVO;
        }).collect(Collectors.toList());
    }

    private RehabPatientRespVO toPatientRespVO(RehabPatientDO patient) {
        if (patient == null) {
            return null;
        }
        RehabPatientRespVO respVO = BeanUtils.toBean(patient, RehabPatientRespVO.class);
        if (patient.getCurrentTherapistUserId() != null) {
            AdminUserRespDTO therapist = adminUserApi.getUser(patient.getCurrentTherapistUserId());
            respVO.setCurrentTherapistName(therapist == null ? "" : therapist.getNickname());
        }
        return respVO;
    }

    private RehabEpisodeRespVO toEpisodeRespVO(RehabEpisodeDO episode) {
        if (episode == null) {
            return null;
        }
        RehabEpisodeRespVO respVO = BeanUtils.toBean(episode, RehabEpisodeRespVO.class);
        if (episode.getPrimaryTherapistUserId() != null) {
            AdminUserRespDTO user = adminUserApi.getUser(episode.getPrimaryTherapistUserId());
            respVO.setPrimaryTherapistName(user == null ? "" : user.getNickname());
        }
        return respVO;
    }

    private RehabAssessmentModuleDataRespVO toModuleRespVO(RehabAssessmentModuleDataDO data) {
        return BeanUtils.toBean(data, RehabAssessmentModuleDataRespVO.class);
    }

    private String serializeJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return JsonUtils.toJsonString(value);
    }

}
