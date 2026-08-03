package cn.iocoder.yudao.module.rehab.service.patient;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.idev.excel.FastExcelFactory;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.rehab.controller.admin.episode.vo.RehabEpisodeRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assignment.RehabTherapistAssignmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.binding.RehabPatientCrmBindingDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.binding.RehabPatientUserBindingDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.log.RehabPatientOperationLogDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assignment.RehabTherapistAssignmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.binding.RehabPatientCrmBindingMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.binding.RehabPatientUserBindingMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.episode.RehabEpisodeMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.log.RehabPatientOperationLogMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.report.RehabReportMapper;
import cn.iocoder.yudao.module.rehab.enums.*;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.episode.RehabEpisodeService;
import cn.iocoder.yudao.module.rehab.service.log.RehabAuditLogService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

/**
 * 患者主档案 Service 实现
 */
@Service
@Validated
@Slf4j
public class RehabPatientServiceImpl implements RehabPatientService {

    private static final DateTimeFormatter PATIENT_NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_IMPORT_ROWS = 2000;

    @Resource
    private RehabPatientMapper patientMapper;
    @Resource
    private RehabPatientCrmBindingMapper crmBindingMapper;
    @Resource
    private RehabPatientUserBindingMapper patientUserBindingMapper;
    @Resource
    private RehabTherapistAssignmentMapper assignmentMapper;
    @Resource
    private RehabEpisodeMapper episodeMapper;
    @Resource
    private RehabPatientOperationLogMapper operationLogMapper;
    @Resource
    private RehabAssessmentRecordMapper assessmentRecordMapper;
    @Resource
    private RehabReportMapper reportMapper;
    @Resource
    private RehabEpisodeService episodeService;
    @Resource
    private RehabDataPermissionService dataPermissionService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private RehabAuditLogService auditLogService;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    @Lazy
    private RehabPatientService self;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RehabPatientCreateRespVO createPatient(RehabPatientCreateReqVO createReqVO, Long operatorUserId) {
        List<RehabPatientDO> duplicatePatients = patientMapper.selectListByNameAndPhone(createReqVO.getName(), createReqVO.getPhone());

        RehabPatientDO patient = BeanUtils.toBean(createReqVO, RehabPatientDO.class);
        if (StrUtil.isBlank(patient.getCurrentStatus())) {
            patient.setCurrentStatus(RehabPatientStatusConstants.ACTIVE);
        }
        if (StrUtil.isBlank(patient.getCurrentStage())) {
            patient.setCurrentStage(RehabStageConstants.INTAKE);
        }
        patientMapper.insert(patient);

        String patientNo = generatePatientNo(patient.getId());
        patientMapper.updateById(new RehabPatientDO().setId(patient.getId()).setPatientNo(patientNo));
        patient.setPatientNo(patientNo);

        Long createdEpisodeId = episodeService.createInitialEpisodeIfNeeded(patient.getId(), createReqVO.getCurrentTherapistUserId(),
                createReqVO.getInitEpisode(), createReqVO.getEpisodeType(), createReqVO.getEpisodePrimaryGoal(), operatorUserId);
        if (createdEpisodeId != null) {
            patientMapper.updateById(new RehabPatientDO().setId(patient.getId())
                    .setCurrentStage(RehabStageConstants.PENDING_ASSESSMENT)
                    .setCurrentStatus(RehabPatientStatusConstants.ACTIVE));
        }

        createOperationLog(patient.getId(), RehabOperationTypeConstants.CREATE, operatorUserId,
                null, patient, "患者建档");

        RehabPatientCreateRespVO respVO = new RehabPatientCreateRespVO();
        respVO.setId(patient.getId());
        respVO.setPatientNo(patientNo);
        respVO.setSuspectedDuplicate(CollUtil.isNotEmpty(duplicatePatients));
        respVO.setDuplicatePatientIds(duplicatePatients.stream().map(RehabPatientDO::getId).collect(Collectors.toList()));
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePatient(RehabPatientUpdateReqVO updateReqVO, Long operatorUserId) {
        RehabPatientDO oldPatient = validatePatientExists(updateReqVO.getId());
        validatePatientReadable(updateReqVO.getId(), operatorUserId);

        RehabPatientDO updateObj = BeanUtils.toBean(updateReqVO, RehabPatientDO.class);
        updateObj.clean();
        patientMapper.updateById(updateObj);

        RehabPatientDO newPatient = patientMapper.selectById(updateReqVO.getId());
        createOperationLog(updateReqVO.getId(), RehabOperationTypeConstants.UPDATE, operatorUserId,
                oldPatient, newPatient, "更新患者主档案");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePatient(Long id, Long operatorUserId) {
        RehabPatientDO oldPatient = validatePatientExists(id);
        validatePatientReadable(id, operatorUserId);
        if (episodeMapper.selectCount("patient_id", id) > 0
                || assessmentRecordMapper.selectCountByPatientId(id) > 0
                || reportMapper.selectCountByPatientId(id) > 0) {
            throw exception(PATIENT_CAN_NOT_DELETE);
        }
        patientMapper.deleteById(id);
        createOperationLog(id, RehabOperationTypeConstants.ARCHIVE, operatorUserId,
                oldPatient, null, "逻辑删除患者档案");
    }

    @Override
    public RehabPatientDetailRespVO getPatientDetail(Long id, Long operatorUserId) {
        validatePatientReadable(id, operatorUserId);
        RehabPatientDO patient = validatePatientExists(id);

        RehabPatientRespVO patientRespVO = toPatientRespVOList(Collections.singletonList(patient)).get(0);
        RehabPatientCrmBindingRespVO bindingRespVO = toCrmBindingRespVO(crmBindingMapper.selectByPatientId(id));
        RehabTherapistAssignmentDO currentPrimary = assignmentMapper.selectActivePrimaryByPatientId(id);

        RehabPatientDetailRespVO detailRespVO = new RehabPatientDetailRespVO();
        detailRespVO.setPatient(patientRespVO);
        detailRespVO.setCrmBinding(bindingRespVO);
        detailRespVO.setMemberBinding(getMemberBinding(id, operatorUserId));
        detailRespVO.setCurrentPrimaryAssignment(toAssignmentRespVOList(currentPrimary == null ? Collections.emptyList()
                : Collections.singletonList(currentPrimary)).stream().findFirst().orElse(null));
        detailRespVO.setCurrentEpisode(episodeService.getCurrentEpisode(id, operatorUserId));
        detailRespVO.setAssignmentHistory(getAssignmentHistory(id, operatorUserId));
        detailRespVO.setOperationLogs(getOperationLogList(id, operatorUserId));
        return detailRespVO;
    }

    @Override
    public PageResult<RehabPatientRespVO> getPatientPage(RehabPatientPageReqVO pageReqVO, Long operatorUserId) {
        Set<Long> visiblePatientIds = dataPermissionService.getVisiblePatientIds(operatorUserId);
        Collection<Long> crmFilteredPatientIds = getCrmFilteredPatientIds(pageReqVO.getCrmBindStatus());

        PageResult<RehabPatientDO> pageResult = patientMapper.selectPage(pageReqVO, visiblePatientIds, crmFilteredPatientIds);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        return new PageResult<>(toPatientRespVOList(pageResult.getList()), pageResult.getTotal());
    }

    @Override
    public List<RehabPatientExportRespVO> getPatientExportList(RehabPatientPageReqVO reqVO, Long operatorUserId) {
        reqVO.setPageSize(-1);
        Set<Long> visiblePatientIds = dataPermissionService.getVisiblePatientIds(operatorUserId);
        Collection<Long> crmFilteredPatientIds = getCrmFilteredPatientIds(reqVO.getCrmBindStatus());
        List<RehabPatientDO> patients = patientMapper.selectPage(reqVO, visiblePatientIds, crmFilteredPatientIds).getList();
        if (CollUtil.isEmpty(patients)) {
            return Collections.emptyList();
        }

        Set<Long> therapistIds = patients.stream().map(RehabPatientDO::getCurrentTherapistUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> therapistMap = therapistIds.isEmpty()
                ? Collections.emptyMap() : adminUserApi.getUserMap(therapistIds);
        List<Long> patientIds = patients.stream().map(RehabPatientDO::getId).collect(Collectors.toList());
        Map<Long, RehabPatientCrmBindingDO> crmBindingMap = crmBindingMapper.selectListByPatientIds(patientIds).stream()
                .collect(Collectors.toMap(RehabPatientCrmBindingDO::getPatientId, item -> item, (first, ignored) -> first));

        return patients.stream().map(patient -> {
            RehabPatientExportRespVO exportRespVO = BeanUtils.toBean(patient, RehabPatientExportRespVO.class);
            AdminUserRespDTO therapist = therapistMap.get(patient.getCurrentTherapistUserId());
            exportRespVO.setCurrentTherapistName(therapist == null ? "" : therapist.getNickname());
            RehabPatientCrmBindingDO crmBinding = crmBindingMap.get(patient.getId());
            exportRespVO.setCrmBindStatus(crmBinding == null
                    ? RehabCrmBindingConstants.STATUS_UNBOUND : crmBinding.getBindStatus());
            return exportRespVO;
        }).collect(Collectors.toList());
    }

    @Override
    public RehabPatientImportRespVO importPatients(List<RehabPatientImportExcelVO> rows, Long operatorUserId) {
        List<RehabPatientImportExcelVO> safeRows = rows == null ? Collections.emptyList() : rows;
        if (safeRows.size() > MAX_IMPORT_ROWS) {
            throw exception(PATIENT_IMPORT_ROWS_EXCEEDED);
        }

        List<String> createdPatients = new ArrayList<>();
        List<String> skippedPatients = new ArrayList<>();
        List<RehabPatientImportFailureVO> failures = new ArrayList<>();
        for (int index = 0; index < safeRows.size(); index++) {
            RehabPatientImportExcelVO row = safeRows.get(index);
            int excelRowNumber = index + 2;
            String identity = getImportIdentity(row);
            try {
                validateImportRow(row);
                if (StrUtil.isNotBlank(row.getPatientNo())
                        && patientMapper.selectByPatientNo(StrUtil.trim(row.getPatientNo())) != null) {
                    skippedPatients.add(identity + "（患者编号已存在）");
                    continue;
                }
                if (CollUtil.isNotEmpty(patientMapper.selectListByNameAndPhone(
                        StrUtil.trim(row.getName()), StrUtil.trim(row.getPhone())))) {
                    skippedPatients.add(identity + "（姓名和手机号已存在）");
                    continue;
                }

                RehabPatientCreateReqVO createReqVO = BeanUtils.toBean(row, RehabPatientCreateReqVO.class);
                createReqVO.setName(StrUtil.trim(row.getName()));
                createReqVO.setPhone(StrUtil.trim(row.getPhone()));
                createReqVO.setInitEpisode(false);
                // 通过代理逐行开启事务，单行失败不会留下半条患者档案，也不会回滚其他成功行。
                RehabPatientCreateRespVO createRespVO = self.createPatient(createReqVO, operatorUserId);
                createdPatients.add(createReqVO.getName() + "（" + createRespVO.getPatientNo() + "）");
            } catch (Exception ex) {
                String reason = getSafeImportFailureReason(ex);
                if (!(ex instanceof IllegalArgumentException) && !(ex instanceof ServiceException)) {
                    log.warn("患者批量导入单行失败，rowNumber={}, patientIdentity={}",
                            excelRowNumber, identity, ex);
                }
                failures.add(new RehabPatientImportFailureVO(excelRowNumber, identity, reason));
            }
        }

        return RehabPatientImportRespVO.builder()
                .totalCount(safeRows.size())
                .createdCount(createdPatients.size())
                .skippedCount(skippedPatients.size())
                .failureCount(failures.size())
                .createdPatients(createdPatients)
                .skippedPatients(skippedPatients)
                .failures(failures)
                .failureExcelBase64(buildFailureExcel(failures))
                .build();
    }

    private void validateImportRow(RehabPatientImportExcelVO row) {
        if (row == null || StrUtil.isBlank(row.getName())) {
            throw new IllegalArgumentException("姓名不能为空");
        }
        if (row.getName().trim().length() > 64) {
            throw new IllegalArgumentException("姓名不能超过 64 个字符");
        }
        if (row.getGender() != null && row.getGender() != 1 && row.getGender() != 2) {
            throw new IllegalArgumentException("性别只能填写 1（男）或 2（女）");
        }
        if (row.getAge() != null && (row.getAge() < 0 || row.getAge() > 150)) {
            throw new IllegalArgumentException("年龄必须在 0-150 之间");
        }
        if (StrUtil.length(row.getPhone()) > 20 || StrUtil.length(row.getContactPhone()) > 20
                || StrUtil.length(row.getEmergencyPhone()) > 20) {
            throw new IllegalArgumentException("手机号不能超过 20 个字符");
        }
        BigDecimal painScore = row.getPainScore();
        if (painScore != null && (painScore.compareTo(BigDecimal.ZERO) < 0
                || painScore.compareTo(BigDecimal.TEN) > 0)) {
            throw new IllegalArgumentException("疼痛评分必须在 0-10 之间");
        }
    }

    private String getImportIdentity(RehabPatientImportExcelVO row) {
        if (row == null) {
            return "空白行";
        }
        if (StrUtil.isNotBlank(row.getPatientNo())) {
            return StrUtil.trim(row.getPatientNo());
        }
        return StrUtil.blankToDefault(StrUtil.trim(row.getName()), "未命名患者");
    }

    /**
     * 只把业务校验消息返回给前端，避免数据库驱动、SQL 或内部路径出现在失败明细中。
     */
    private String getSafeImportFailureReason(Exception ex) {
        if (ex instanceof IllegalArgumentException || ex instanceof ServiceException) {
            return StrUtil.blankToDefault(ex.getMessage(), "导入失败，请检查该行数据");
        }
        return "系统校验失败，请联系管理员并查看服务端日志";
    }

    private String buildFailureExcel(List<RehabPatientImportFailureVO> failures) {
        if (CollUtil.isEmpty(failures)) {
            return null;
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            FastExcelFactory.write(outputStream, RehabPatientImportFailureVO.class)
                    .sheet("失败明细").doWrite(failures);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception ex) {
            log.warn("生成患者导入失败明细 Excel 失败，failureCount={}", failures.size(), ex);
            return null;
        }
    }

    @Override
    public RehabPatientCrmBindingRespVO getCrmBinding(Long patientId, Long operatorUserId) {
        validatePatientReadable(patientId, operatorUserId);
        validatePatientExists(patientId);
        return toCrmBindingRespVO(crmBindingMapper.selectByPatientId(patientId));
    }

    @Override
    public RehabPatientMemberBindingRespVO getMemberBinding(Long patientId, Long operatorUserId) {
        validatePatientReadable(patientId, operatorUserId);
        validatePatientExists(patientId);
        RehabPatientUserBindingDO binding = patientUserBindingMapper.selectActiveByPatientId(patientId);
        return toMemberBindingRespVO(binding);
    }

    @Override
    public RehabCrmConflictCheckRespVO checkCrmConflict(RehabPatientCheckCrmConflictReqVO reqVO) {
        List<RehabPatientCrmBindingDO> bindings = crmBindingMapper.selectListByCrmCustomerId(reqVO.getCrmCustomerId());
        List<Long> conflictPatientIds = bindings.stream()
                .filter(item -> ObjUtil.equal(item.getBindStatus(), RehabCrmBindingConstants.STATUS_BOUND))
                .map(RehabPatientCrmBindingDO::getPatientId)
                .filter(id -> !ObjUtil.equal(id, reqVO.getPatientId()))
                .distinct()
                .collect(Collectors.toList());

        RehabCrmConflictCheckRespVO respVO = new RehabCrmConflictCheckRespVO();
        respVO.setConflict(CollUtil.isNotEmpty(conflictPatientIds));
        respVO.setConflictPatientIds(conflictPatientIds);
        respVO.setMessage(CollUtil.isEmpty(conflictPatientIds) ? "未发现冲突" : "该 CRM 客户已绑定其他患者");
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RehabPatientCrmBindingRespVO bindCrm(RehabPatientBindCrmReqVO reqVO, Long operatorUserId) {
        RehabPatientDO patient = validatePatientExists(reqVO.getPatientId());
        validatePatientReadable(reqVO.getPatientId(), operatorUserId);

        RehabPatientCheckCrmConflictReqVO conflictReqVO = new RehabPatientCheckCrmConflictReqVO();
        conflictReqVO.setPatientId(reqVO.getPatientId());
        conflictReqVO.setCrmCustomerId(reqVO.getCrmCustomerId());
        RehabCrmConflictCheckRespVO conflict = checkCrmConflict(conflictReqVO);

        RehabPatientCrmBindingDO oldBinding = crmBindingMapper.selectByPatientId(reqVO.getPatientId());
        RehabPatientCrmBindingDO binding = oldBinding == null ? new RehabPatientCrmBindingDO() : oldBinding;
        binding.setPatientId(reqVO.getPatientId());
        binding.setCrmCustomerId(reqVO.getCrmCustomerId());
        binding.setBindSource(StrUtil.blankToDefault(reqVO.getBindSource(), RehabCrmBindingConstants.SOURCE_MANUAL));
        binding.setSyncStatus(StrUtil.blankToDefault(reqVO.getSyncStatus(), "manual"));
        binding.setSyncMessage(StrUtil.blankToDefault(reqVO.getSyncMessage(), conflict.getMessage()));
        binding.setLastSyncTime(LocalDateTime.now());

        if (Boolean.TRUE.equals(conflict.getConflict())) {
            binding.setBindStatus(RehabCrmBindingConstants.STATUS_CONFLICT);
        } else {
            binding.setBindStatus(RehabCrmBindingConstants.STATUS_BOUND);
            binding.setBindTime(LocalDateTime.now());
        }

        if (binding.getId() == null) {
            crmBindingMapper.insert(binding);
        } else {
            binding.clean();
            crmBindingMapper.updateById(binding);
        }

        createOperationLog(patient.getId(), RehabOperationTypeConstants.BIND_CRM, operatorUserId,
                oldBinding, binding, "绑定 CRM 客户编号=" + reqVO.getCrmCustomerId());
        return toCrmBindingRespVO(crmBindingMapper.selectByPatientId(reqVO.getPatientId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RehabPatientCrmBindingRespVO unbindCrm(RehabPatientUnbindCrmReqVO reqVO, Long operatorUserId) {
        if (!dataPermissionService.isSuperAdmin(operatorUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
        validatePatientReadable(reqVO.getPatientId(), operatorUserId);
        validatePatientExists(reqVO.getPatientId());

        RehabPatientCrmBindingDO binding = crmBindingMapper.selectByPatientId(reqVO.getPatientId());
        if (binding == null) {
            throw exception(CRM_BINDING_NOT_EXISTS);
        }

        RehabPatientCrmBindingDO oldBinding = BeanUtils.toBean(binding, RehabPatientCrmBindingDO.class);
        binding.setBindStatus(RehabCrmBindingConstants.STATUS_UNBOUND);
        binding.setCrmCustomerId(null);
        binding.setSyncMessage(StrUtil.blankToDefault(reqVO.getRemark(), "手动解绑"));
        binding.setLastSyncTime(LocalDateTime.now());
        binding.clean();
        crmBindingMapper.updateById(binding);

        createOperationLog(reqVO.getPatientId(), RehabOperationTypeConstants.UNBIND_CRM, operatorUserId,
                oldBinding, binding, StrUtil.blankToDefault(reqVO.getRemark(), "解绑 CRM"));
        return toCrmBindingRespVO(crmBindingMapper.selectByPatientId(reqVO.getPatientId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTherapist(RehabPatientAssignReqVO reqVO, Long operatorUserId) {
        RehabPatientDO patient = validatePatientExists(reqVO.getPatientId());
        if (!ObjUtil.equals(reqVO.getRoleType(), RehabAssignmentConstants.ROLE_PRIMARY)
                && !ObjUtil.equals(reqVO.getRoleType(), RehabAssignmentConstants.ROLE_COLLABORATOR)) {
            throw exception(ASSIGNMENT_ROLE_TYPE_INVALID);
        }
        if (!dataPermissionService.isSuperAdmin(operatorUserId) && !dataPermissionService.isClerk(operatorUserId)) {
            validatePatientReadable(reqVO.getPatientId(), operatorUserId);
        }
        adminUserApi.validateUser(reqVO.getTherapistUserId());

        if (ObjUtil.equal(reqVO.getRoleType(), RehabAssignmentConstants.ROLE_PRIMARY)) {
            RehabTherapistAssignmentDO activePrimary = assignmentMapper.selectActivePrimaryByPatientId(reqVO.getPatientId());
            if (activePrimary != null) {
                activePrimary.setAssignStatus(RehabAssignmentConstants.STATUS_CLOSED);
                activePrimary.setEndTime(LocalDateTime.now());
                activePrimary.clean();
                assignmentMapper.updateById(activePrimary);
            }
            patientMapper.updateById(new RehabPatientDO().setId(patient.getId()).setCurrentTherapistUserId(reqVO.getTherapistUserId()));
        }

        RehabTherapistAssignmentDO assignment = RehabTherapistAssignmentDO.builder()
                .patientId(reqVO.getPatientId())
                .therapistUserId(reqVO.getTherapistUserId())
                .roleType(reqVO.getRoleType())
                .assignStatus(RehabAssignmentConstants.STATUS_ACTIVE)
                .assignReason(reqVO.getAssignReason())
                .startTime(LocalDateTime.now())
                .assignedBy(operatorUserId)
                .remark(reqVO.getRemark())
                .build();
        assignmentMapper.insert(assignment);

        createOperationLog(reqVO.getPatientId(), RehabOperationTypeConstants.ASSIGN, operatorUserId,
                null, assignment, "分配治疗师");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferTherapist(RehabPatientTransferReqVO reqVO, Long operatorUserId) {
        RehabPatientDO patient = validatePatientExists(reqVO.getPatientId());
        if (!dataPermissionService.isSuperAdmin(operatorUserId)) {
            validatePatientReadable(reqVO.getPatientId(), operatorUserId);
        }
        adminUserApi.validateUser(reqVO.getToTherapistUserId());

        RehabTherapistAssignmentDO oldPrimary = assignmentMapper.selectActivePrimaryByPatientId(reqVO.getPatientId());
        if (oldPrimary == null) {
            throw exception(ASSIGNMENT_NOT_EXISTS);
        }
        if (ObjUtil.equal(oldPrimary.getTherapistUserId(), reqVO.getToTherapistUserId())) {
            throw exception(ASSIGNMENT_TRANSFER_TARGET_SAME);
        }

        oldPrimary.setAssignStatus(RehabAssignmentConstants.STATUS_TRANSFERRED);
        oldPrimary.setEndTime(LocalDateTime.now());
        oldPrimary.setTransferToUserId(reqVO.getToTherapistUserId());
        oldPrimary.clean();
        assignmentMapper.updateById(oldPrimary);

        RehabTherapistAssignmentDO newPrimary = RehabTherapistAssignmentDO.builder()
                .patientId(reqVO.getPatientId())
                .therapistUserId(reqVO.getToTherapistUserId())
                .roleType(RehabAssignmentConstants.ROLE_PRIMARY)
                .assignStatus(RehabAssignmentConstants.STATUS_ACTIVE)
                .assignReason(StrUtil.blankToDefault(reqVO.getReason(), "转交"))
                .startTime(LocalDateTime.now())
                .assignedBy(operatorUserId)
                .transferFromUserId(oldPrimary.getTherapistUserId())
                .transferToUserId(reqVO.getToTherapistUserId())
                .remark(reqVO.getRemark())
                .build();
        assignmentMapper.insert(newPrimary);

        patientMapper.updateById(new RehabPatientDO().setId(patient.getId()).setCurrentTherapistUserId(reqVO.getToTherapistUserId()));

        createOperationLog(reqVO.getPatientId(), RehabOperationTypeConstants.TRANSFER, operatorUserId,
                oldPrimary, newPrimary, StrUtil.blankToDefault(reqVO.getReason(), "转交主责治疗师"));
    }

    @Override
    public List<RehabTherapistAssignmentRespVO> getAssignmentHistory(Long patientId, Long operatorUserId) {
        validatePatientReadable(patientId, operatorUserId);
        validatePatientExists(patientId);
        return toAssignmentRespVOList(assignmentMapper.selectListByPatientId(patientId));
    }

    @Override
    public List<RehabPatientOperationLogRespVO> getOperationLogList(Long patientId, Long operatorUserId) {
        validatePatientReadable(patientId, operatorUserId);
        validatePatientExists(patientId);

        List<RehabPatientOperationLogDO> logs = operationLogMapper.selectListByPatientId(patientId);
        if (CollUtil.isEmpty(logs)) {
            return Collections.emptyList();
        }
        Set<Long> userIds = logs.stream()
                .map(RehabPatientOperationLogDO::getOperatorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> userMap = userIds.isEmpty() ? Collections.emptyMap() : adminUserApi.getUserMap(userIds);

        return logs.stream().map(log -> {
            RehabPatientOperationLogRespVO vo = BeanUtils.toBean(log, RehabPatientOperationLogRespVO.class);
            AdminUserRespDTO user = userMap.get(log.getOperatorUserId());
            vo.setOperatorName(user == null ? "" : user.getNickname());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void validatePatientReadable(Long patientId, Long operatorUserId) {
        validatePatientExists(patientId);
        if (!dataPermissionService.canReadPatient(patientId, operatorUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
    }

    private RehabPatientDO validatePatientExists(Long patientId) {
        RehabPatientDO patient = patientMapper.selectById(patientId);
        if (patient == null) {
            throw exception(PATIENT_NOT_EXISTS);
        }
        return patient;
    }

    private String generatePatientNo(Long id) {
        String datePart = PATIENT_NO_DATE_FORMATTER.format(LocalDateTime.now());
        return "PT" + datePart + String.format("%04d", id % 10000);
    }

    private Collection<Long> getCrmFilteredPatientIds(String crmBindStatus) {
        if (StrUtil.isBlank(crmBindStatus)) {
            return null;
        }
        List<RehabPatientCrmBindingDO> bindings = crmBindingMapper.selectListByBindStatus(crmBindStatus);
        return bindings.stream().map(RehabPatientCrmBindingDO::getPatientId).distinct().collect(Collectors.toList());
    }

    private List<RehabPatientRespVO> toPatientRespVOList(List<RehabPatientDO> patients) {
        if (CollUtil.isEmpty(patients)) {
            return Collections.emptyList();
        }

        Set<Long> therapistIds = patients.stream().map(RehabPatientDO::getCurrentTherapistUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> therapistMap = therapistIds.isEmpty()
                ? Collections.emptyMap() : adminUserApi.getUserMap(therapistIds);

        List<Long> patientIds = patients.stream().map(RehabPatientDO::getId).collect(Collectors.toList());
        Map<Long, RehabPatientCrmBindingDO> crmBindingMap = crmBindingMapper.selectListByPatientIds(patientIds).stream()
                .collect(Collectors.toMap(RehabPatientCrmBindingDO::getPatientId, item -> item, (v1, v2) -> v1));

        return patients.stream().map(patient -> {
            RehabPatientRespVO vo = BeanUtils.toBean(patient, RehabPatientRespVO.class);
            vo.setPhone(maskPhone(patient.getPhone()));
            AdminUserRespDTO therapist = therapistMap.get(patient.getCurrentTherapistUserId());
            vo.setCurrentTherapistName(therapist == null ? "" : therapist.getNickname());

            RehabPatientCrmBindingDO binding = crmBindingMap.get(patient.getId());
            vo.setCrmBindStatus(binding == null ? RehabCrmBindingConstants.STATUS_UNBOUND : binding.getBindStatus());
            return vo;
        }).collect(Collectors.toList());
    }

    private List<RehabTherapistAssignmentRespVO> toAssignmentRespVOList(List<RehabTherapistAssignmentDO> assignments) {
        if (CollUtil.isEmpty(assignments)) {
            return Collections.emptyList();
        }
        Set<Long> userIds = new HashSet<>();
        assignments.forEach(item -> {
            if (item.getTherapistUserId() != null) {
                userIds.add(item.getTherapistUserId());
            }
            if (item.getAssignedBy() != null) {
                userIds.add(item.getAssignedBy());
            }
            if (item.getTransferFromUserId() != null) {
                userIds.add(item.getTransferFromUserId());
            }
            if (item.getTransferToUserId() != null) {
                userIds.add(item.getTransferToUserId());
            }
        });
        Map<Long, AdminUserRespDTO> userMap = userIds.isEmpty() ? Collections.emptyMap() : adminUserApi.getUserMap(userIds);

        return assignments.stream().map(item -> {
            RehabTherapistAssignmentRespVO vo = BeanUtils.toBean(item, RehabTherapistAssignmentRespVO.class);
            vo.setTherapistName(getUserName(userMap, item.getTherapistUserId()));
            vo.setAssignedByName(getUserName(userMap, item.getAssignedBy()));
            vo.setTransferFromUserName(getUserName(userMap, item.getTransferFromUserId()));
            vo.setTransferToUserName(getUserName(userMap, item.getTransferToUserId()));
            return vo;
        }).collect(Collectors.toList());
    }

    private RehabPatientCrmBindingRespVO toCrmBindingRespVO(RehabPatientCrmBindingDO binding) {
        if (binding == null) {
            RehabPatientCrmBindingRespVO vo = new RehabPatientCrmBindingRespVO();
            vo.setBindStatus(RehabCrmBindingConstants.STATUS_UNBOUND);
            return vo;
        }
        RehabPatientCrmBindingRespVO vo = BeanUtils.toBean(binding, RehabPatientCrmBindingRespVO.class);
        if (binding.getCrmCustomerId() != null) {
            CrmCustomerMeta crmCustomer = queryCrmCustomerMeta(binding.getCrmCustomerId());
            if (crmCustomer != null) {
                vo.setCrmCustomerName(crmCustomer.getName());
                vo.setCrmCustomerMobile(maskPhone(crmCustomer.getMobile()));
            }
        }
        return vo;
    }

    private RehabPatientMemberBindingRespVO toMemberBindingRespVO(RehabPatientUserBindingDO binding) {
        if (binding == null) {
            return null;
        }
        RehabPatientMemberBindingRespVO vo = BeanUtils.toBean(binding, RehabPatientMemberBindingRespVO.class);
        if (binding.getAppUserId() != null) {
            MemberUserMeta memberUser = queryMemberUserMeta(binding.getAppUserId());
            if (memberUser != null) {
                vo.setMemberNickname(memberUser.getNickname());
                vo.setMemberMobile(maskPhone(memberUser.getMobile()));
                vo.setMemberStatus(memberUser.getStatus());
            }
        }
        return vo;
    }

    private String getUserName(Map<Long, AdminUserRespDTO> userMap, Long userId) {
        if (userId == null) {
            return "";
        }
        AdminUserRespDTO user = userMap.get(userId);
        return user == null ? "" : user.getNickname();
    }

    private String maskPhone(String phone) {
        if (StrUtil.isBlank(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private CrmCustomerMeta queryCrmCustomerMeta(Long crmCustomerId) {
        try {
            return jdbcTemplate.query("SELECT id, name, mobile FROM crm_customer WHERE id = ? AND deleted = 0 LIMIT 1",
                    ps -> ps.setLong(1, crmCustomerId),
                    rs -> rs.next() ? new CrmCustomerMeta(rs.getLong("id"), rs.getString("name"), rs.getString("mobile")) : null);
        } catch (DataAccessException ex) {
            log.warn("查询 CRM 客户失败，crmCustomerId={}", crmCustomerId, ex);
            return null;
        }
    }

    private MemberUserMeta queryMemberUserMeta(Long memberUserId) {
        try {
            return jdbcTemplate.query("SELECT id, nickname, mobile, status FROM member_user WHERE id = ? AND deleted = 0 LIMIT 1",
                    ps -> ps.setLong(1, memberUserId),
                    rs -> rs.next() ? new MemberUserMeta(rs.getLong("id"), rs.getString("nickname"),
                            rs.getString("mobile"), rs.getInt("status")) : null);
        } catch (DataAccessException ex) {
            log.warn("查询会员用户失败，memberUserId={}", memberUserId, ex);
            return null;
        }
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
        if (auditLogService != null) {
            auditLogService.createAuditLog("patient", patientId, operationType, operatorUserId,
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

    private static class CrmCustomerMeta {
        private final Long id;
        private final String name;
        private final String mobile;

        private CrmCustomerMeta(Long id, String name, String mobile) {
            this.id = id;
            this.name = name;
            this.mobile = mobile;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getMobile() {
            return mobile;
        }
    }

    private static class MemberUserMeta {
        private final Long id;
        private final String nickname;
        private final String mobile;
        private final Integer status;

        private MemberUserMeta(Long id, String nickname, String mobile, Integer status) {
            this.id = id;
            this.nickname = nickname;
            this.mobile = mobile;
            this.status = status;
        }

        public Long getId() {
            return id;
        }

        public String getNickname() {
            return nickname;
        }

        public String getMobile() {
            return mobile;
        }

        public Integer getStatus() {
            return status;
        }
    }

}
