package cn.iocoder.yudao.module.rehab.service.motion;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.rehab.controller.admin.motion.vo.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionFileDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionScoreDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTaskDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTrialDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionAssessmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionFileMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionScoreMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionTrialMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.service.motion.engine.MotionEngineClient;
import cn.iocoder.yudao.module.rehab.service.motion.engine.MotionEngineException;
import cn.iocoder.yudao.module.rehab.service.motion.task.MotionTaskStates;
import cn.iocoder.yudao.module.rehab.service.motion.task.RehabMotionPipelineSteps;
import cn.iocoder.yudao.module.rehab.service.motion.task.RehabMotionTaskService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.PATIENT_NOT_EXISTS;
import static cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants.*;
import static cn.iocoder.yudao.module.rehab.enums.RehabMotionErrorCodeConstants.*;

/**
 * 动作评估：创建、数据文件、Trial 配置、人工输入与发起分析。
 */
@Service
public class RehabMotionAssessmentService {

    @Resource
    private RehabMotionAssessmentMapper assessmentMapper;
    @Resource
    private RehabMotionFileMapper motionFileMapper;
    @Resource
    private RehabMotionTrialMapper trialMapper;
    @Resource
    private RehabMotionScoreMapper scoreMapper;
    @Resource
    private RehabPatientMapper patientMapper;
    @Resource
    private RehabMotionAccess access;
    @Resource
    private RehabMotionTaskService taskService;
    @Resource
    private RehabMotionPipelineSteps steps;
    @Resource
    private MotionEngineClient engineClient;
    @Resource
    private MotionStorage storage;

    // ========== 评估 ==========

    @Transactional(rollbackFor = Exception.class)
    public Long create(RehabMotionSaveReqVO req, Long userId) {
        access.requirePatient(req.getPatientId(), userId);
        RehabPatientDO patient = patientMapper.selectById(req.getPatientId());
        if (patient == null) {
            throw exception(PATIENT_NOT_EXISTS);
        }
        validateSave(req, null);
        RehabMotionAssessmentDO a = RehabMotionAssessmentDO.builder()
                .patientId(req.getPatientId())
                .episodeId(req.getEpisodeId())
                .assessmentRecordId(req.getAssessmentRecordId())
                .baselineId(req.getBaselineId())
                .visitType(StrUtil.blankToDefault(req.getVisitType(), "initial"))
                .protocolFamilies(String.join(",", req.getProtocolFamilies()))
                .title(StrUtil.blankToDefault(req.getTitle(), "动作评估"))
                .captureTime(req.getCaptureTime())
                .status(STATE_WAITING_UPLOAD)
                .dataSource(req.getDataSource())
                .opencapSessionId(StrUtil.emptyToNull(StrUtil.trim(req.getOpencapSessionId())))
                .cameraCount(req.getCameraCount())
                .modelName(OPENSIM_MODEL)
                .videoConsent(Boolean.TRUE.equals(req.getVideoConsent()))
                .aiAllowed(Boolean.TRUE.equals(req.getAiAllowed()))
                .sexGroup(StrUtil.emptyToNull(req.getSexGroup()))
                .limbLengthLeftCm(req.getLimbLengthLeftCm())
                .limbLengthRightCm(req.getLimbLengthRightCm())
                .tjaVariant(StrUtil.blankToDefault(req.getTjaVariant(), "TJA_MODIFIED_0_2"))
                .inputRevision(1)
                .reviewStatus("pending")
                .therapistUserId(req.getTherapistUserId() == null ? userId : req.getTherapistUserId())
                .remark(req.getRemark())
                .build();
        assessmentMapper.insert(a);
        access.audit(a.getId(), "create", userId, null, auditView(a), null);
        return a.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(RehabMotionSaveReqVO req, Long userId) {
        RehabMotionAssessmentDO a = access.editable(req.getId(), userId);
        if (!Objects.equals(a.getPatientId(), req.getPatientId())) {
            throw exception(MOTION_STATE_INVALID, "不允许更换患者");
        }
        validateSave(req, a.getId());
        boolean analysisInputChanged = !Objects.equals(a.getCameraCount(), req.getCameraCount())
                || !Objects.equals(a.getSexGroup(), StrUtil.emptyToNull(req.getSexGroup()))
                || !eqDecimal(a.getLimbLengthLeftCm(), req.getLimbLengthLeftCm())
                || !eqDecimal(a.getLimbLengthRightCm(), req.getLimbLengthRightCm())
                || !Objects.equals(a.getTjaVariant(), StrUtil.blankToDefault(req.getTjaVariant(), "TJA_MODIFIED_0_2"))
                || !Objects.equals(a.getOpencapSessionId(), StrUtil.emptyToNull(StrUtil.trim(req.getOpencapSessionId())));
        Map<String, Object> before = auditView(a);
        RehabMotionAssessmentDO upd = new RehabMotionAssessmentDO();
        upd.setId(a.getId());
        upd.setEpisodeId(req.getEpisodeId());
        upd.setAssessmentRecordId(req.getAssessmentRecordId());
        upd.setBaselineId(req.getBaselineId());
        upd.setVisitType(StrUtil.blankToDefault(req.getVisitType(), a.getVisitType()));
        upd.setProtocolFamilies(String.join(",", req.getProtocolFamilies()));
        upd.setTitle(StrUtil.blankToDefault(req.getTitle(), a.getTitle()));
        upd.setCaptureTime(req.getCaptureTime());
        upd.setDataSource(req.getDataSource());
        upd.setOpencapSessionId(StrUtil.emptyToNull(StrUtil.trim(req.getOpencapSessionId())));
        upd.setCameraCount(req.getCameraCount());
        upd.setVideoConsent(Boolean.TRUE.equals(req.getVideoConsent()));
        upd.setAiAllowed(Boolean.TRUE.equals(req.getAiAllowed()));
        upd.setSexGroup(StrUtil.emptyToNull(req.getSexGroup()));
        upd.setLimbLengthLeftCm(req.getLimbLengthLeftCm());
        upd.setLimbLengthRightCm(req.getLimbLengthRightCm());
        upd.setTjaVariant(StrUtil.blankToDefault(req.getTjaVariant(), "TJA_MODIFIED_0_2"));
        upd.setTherapistUserId(req.getTherapistUserId());
        upd.setRemark(req.getRemark());
        assessmentMapper.updateById(upd);
        if (Boolean.TRUE.equals(a.getVideoConsent()) && !Boolean.TRUE.equals(req.getVideoConsent())) {
            // 撤回视频同意：立即删除已保存的视频
            for (RehabMotionFileDO f : motionFileMapper.selectListByAssessmentId(a.getId())) {
                if (FILE_VIDEO.equals(f.getFileKind())) {
                    motionFileMapper.deleteById(f.getId());
                    storage.deleteQuietly(f.getStoragePath());
                }
            }
        }
        if (analysisInputChanged) {
            access.bumpRevision(a.getId());
        }
        access.audit(a.getId(), "update", userId, before, auditView(assessmentMapper.selectById(a.getId())), null);
    }

    private void validateSave(RehabMotionSaveReqVO req, Long selfId) {
        if ("opencap".equals(req.getDataSource()) && StrUtil.isBlank(req.getOpencapSessionId())) {
            throw exception(MOTION_OPENCAP_SESSION_REQUIRED);
        }
        if (req.getBaselineId() != null) {
            if (req.getBaselineId().equals(selfId)) {
                throw exception(MOTION_COMPARE_INVALID);
            }
            RehabMotionAssessmentDO base = assessmentMapper.selectById(req.getBaselineId());
            if (base == null || !Objects.equals(base.getPatientId(), req.getPatientId())) {
                throw exception(MOTION_COMPARE_INVALID);
            }
        }
    }

    private static boolean eqDecimal(java.math.BigDecimal x, java.math.BigDecimal y) {
        return x == null ? y == null : y != null && x.compareTo(y) == 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        RehabMotionAssessmentDO a = access.editable(id, userId);
        access.requireClinician(userId);
        if (taskService.hasActive(id)) {
            throw exception(MOTION_TASK_ACTIVE);
        }
        assessmentMapper.deleteById(id);
        access.audit(id, "delete", userId, auditView(a), null, null);
    }

    public PageResult<RehabMotionAssessmentRespVO> page(RehabMotionPageReqVO req, Long userId) {
        if (req.getPatientId() != null) {
            access.requirePatient(req.getPatientId(), userId);
        }
        PageResult<RehabMotionAssessmentDO> page = assessmentMapper.selectPage(req, access.visiblePatientIds(userId));
        return new PageResult<RehabMotionAssessmentRespVO>(toResp(page.getList()), page.getTotal());
    }

    public RehabMotionAssessmentRespVO get(Long id, Long userId) {
        return toResp(Arrays.asList(access.readable(id, userId))).get(0);
    }

    public List<RehabMotionAssessmentRespVO> listByPatient(Long patientId, Long userId) {
        access.requirePatient(patientId, userId);
        return toResp(assessmentMapper.selectListByPatientId(patientId));
    }

    List<RehabMotionAssessmentRespVO> toResp(List<RehabMotionAssessmentDO> list) {
        List<RehabMotionAssessmentRespVO> out = new ArrayList<RehabMotionAssessmentRespVO>();
        if (CollUtil.isEmpty(list)) {
            return out;
        }
        Set<Long> patientIds = new HashSet<Long>();
        Set<Long> ids = new HashSet<Long>();
        for (RehabMotionAssessmentDO a : list) {
            patientIds.add(a.getPatientId());
            ids.add(a.getId());
        }
        Map<Long, RehabPatientDO> patients = new HashMap<Long, RehabPatientDO>();
        for (RehabPatientDO p : patientMapper.selectBatchIds(patientIds)) {
            patients.put(p.getId(), p);
        }
        Map<Long, List<RehabMotionScoreDO>> scores = new HashMap<Long, List<RehabMotionScoreDO>>();
        for (RehabMotionScoreDO s : scoreMapper.selectListByAssessmentIds(ids)) {
            List<RehabMotionScoreDO> l = scores.get(s.getAssessmentId());
            if (l == null) {
                l = new ArrayList<RehabMotionScoreDO>();
                scores.put(s.getAssessmentId(), l);
            }
            l.add(s);
        }
        for (RehabMotionAssessmentDO a : list) {
            RehabMotionAssessmentRespVO vo = BeanUtils.toBean(a, RehabMotionAssessmentRespVO.class);
            vo.setProtocolFamilies(StrUtil.isBlank(a.getProtocolFamilies()) ? new ArrayList<String>()
                    : StrUtil.split(a.getProtocolFamilies(), ','));
            vo.setStatusLabel(stateLabel(a.getStatus()));
            vo.setStale(a.getAnalyzedRevision() != null && !Objects.equals(a.getAnalyzedRevision(), a.getInputRevision()));
            RehabPatientDO p = patients.get(a.getPatientId());
            if (p != null) {
                vo.setPatientName(p.getName());
                vo.setPatientNo(p.getPatientNo());
            }
            List<RehabMotionScoreDO> s = scores.get(a.getId());
            vo.setFmsTotal(s == null ? null : MotionScoreRules.fmsTotal(s));
            out.add(vo);
        }
        return out;
    }

    static Map<String, Object> auditView(RehabMotionAssessmentDO a) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", a.getId());
        m.put("patientId", a.getPatientId());
        m.put("status", a.getStatus());
        m.put("visitType", a.getVisitType());
        m.put("protocolFamilies", a.getProtocolFamilies());
        m.put("dataSource", a.getDataSource());
        m.put("cameraCount", a.getCameraCount());
        m.put("videoConsent", a.getVideoConsent());
        m.put("aiAllowed", a.getAiAllowed());
        m.put("inputRevision", a.getInputRevision());
        return m;
    }

    // ========== 文件 ==========

    /**
     * 上传单个文件（前端按文件夹逐个上传，保留 OpenCap 导出内相对路径）。
     * 路径白名单 + 大小上限 + 内容嗅探；视频仅在患者同意后保存且从不发送给引擎/AI。
     */
    public RehabMotionFileRespVO upload(Long assessmentId, String relativePath, MultipartFile file, Long userId) {
        RehabMotionAssessmentDO a = access.editable(assessmentId, userId);
        MotionFileRules.Classified c = MotionFileRules.classify(relativePath);
        if (c == null) {
            throw exception(MOTION_FILE_PATH_INVALID);
        }
        if (FILE_VIDEO.equals(c.getKind()) && !Boolean.TRUE.equals(a.getVideoConsent())) {
            throw exception(MOTION_VIDEO_CONSENT_REQUIRED);
        }
        if (file == null || file.isEmpty()) {
            throw exception(MOTION_FILE_TYPE_INVALID);
        }
        if (file.getSize() > c.getMaxBytes()) {
            throw exception(MOTION_FILE_TOO_LARGE);
        }
        byte[] data;
        try (InputStream in = file.getInputStream()) {
            data = readLimited(in, c.getMaxBytes());
        } catch (IOException ex) {
            throw exception(MOTION_FILE_STORE_FAILED);
        }
        if (data == null) {
            throw exception(MOTION_FILE_TOO_LARGE);
        }
        byte[] head = Arrays.copyOf(data, Math.min(data.length, MotionFileRules.sniffLength(c.getKind())));
        if (!MotionFileRules.contentLooksValid(c.getKind(), head)) {
            throw exception(MOTION_FILE_TYPE_INVALID);
        }
        RehabMotionFileDO row;
        try {
            row = steps.saveFile(a, c.getKind(), c.getRelativePath(), c.getTrialName(), c.getCameraKey(), c.getExtension(),
                    c.getContentType(), data, "upload", userId);
        } catch (IOException ex) {
            throw exception(MOTION_FILE_STORE_FAILED);
        }
        if (STATE_WAITING_UPLOAD.equals(a.getStatus())) {
            steps.syncAssessmentStatus(a.getId(), STATE_UPLOADING);
        }
        if (KINEMATIC_FILE_KINDS.contains(c.getKind())) {
            access.bumpRevision(a.getId());
        }
        access.audit(a.getId(), "file_upload", userId, null, fileAudit(row), null);
        return BeanUtils.toBean(row, RehabMotionFileRespVO.class);
    }

    /** @return null 表示超过上限 */
    static byte[] readLimited(InputStream in, long max) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > max) {
                return null;
            }
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static Map<String, Object> fileAudit(RehabMotionFileDO f) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("fileId", f.getId());
        m.put("kind", f.getFileKind());
        m.put("relativePath", f.getRelativePath());
        m.put("size", f.getFileSize());
        m.put("sha256", f.getSha256());
        return m;
    }

    public List<RehabMotionFileRespVO> listFiles(Long assessmentId, Long userId) {
        access.readable(assessmentId, userId);
        List<RehabMotionFileRespVO> out = new ArrayList<RehabMotionFileRespVO>();
        for (RehabMotionFileDO f : motionFileMapper.selectListByAssessmentId(assessmentId)) {
            if (FILE_RESULT.equals(f.getFileKind()) || FILE_PDF.equals(f.getFileKind())) {
                continue;
            }
            out.add(BeanUtils.toBean(f, RehabMotionFileRespVO.class));
        }
        return out;
    }

    public void deleteFile(Long fileId, Long userId) {
        RehabMotionFileDO f = motionFileMapper.selectById(fileId);
        if (f == null) {
            throw exception(MOTION_FILE_NOT_EXISTS);
        }
        RehabMotionAssessmentDO a = access.editable(f.getAssessmentId(), userId);
        if (FILE_RESULT.equals(f.getFileKind()) || FILE_PDF.equals(f.getFileKind())) {
            throw exception(MOTION_FILE_NOT_EXISTS);
        }
        motionFileMapper.deleteById(fileId);
        storage.deleteQuietly(f.getStoragePath());
        if (KINEMATIC_FILE_KINDS.contains(f.getFileKind())) {
            access.bumpRevision(a.getId());
        }
        access.audit(a.getId(), "file_delete", userId, fileAudit(f), null, null);
    }

    public static final class Download {
        public final String fileName;
        public final String contentType;
        public final java.io.File file;

        Download(String fileName, String contentType, java.io.File file) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.file = file;
        }
    }

    public Download download(Long fileId, Long userId) {
        RehabMotionFileDO f = motionFileMapper.selectById(fileId);
        if (f == null) {
            throw exception(MOTION_FILE_NOT_EXISTS);
        }
        RehabMotionAssessmentDO a = access.readable(f.getAssessmentId(), userId);
        if (FILE_VIDEO.equals(f.getFileKind()) && !Boolean.TRUE.equals(a.getVideoConsent())) {
            throw exception(MOTION_VIDEO_CONSENT_REQUIRED);
        }
        java.io.File file;
        try {
            file = storage.file(f.getStoragePath());
        } catch (IOException ex) {
            throw exception(MOTION_FILE_NOT_EXISTS);
        }
        if (!file.isFile()) {
            throw exception(MOTION_FILE_NOT_EXISTS);
        }
        access.audit(a.getId(), "file_download", userId, null, fileAudit(f), null);
        String name = f.getRelativePath().substring(f.getRelativePath().lastIndexOf('/') + 1);
        return new Download(name, StrUtil.blankToDefault(f.getContentType(), "application/octet-stream"), file);
    }

    // ========== Trial ==========

    public List<RehabMotionTrialDO> listTrials(Long assessmentId, Long userId) {
        access.readable(assessmentId, userId);
        return trialMapper.selectListByAssessmentId(assessmentId);
    }

    /** 覆盖式保存 Trial 映射（测试/侧别/条件/尝试/OpenCap trial）。临床字段不在此修改。 */
    @Transactional(rollbackFor = Exception.class)
    public void saveTrials(RehabMotionTrialSaveReqVO req, Long userId) {
        RehabMotionAssessmentDO a = access.editable(req.getAssessmentId(), userId);
        if (taskService.hasActive(a.getId())) {
            throw exception(MOTION_TASK_ACTIVE);
        }
        List<RehabMotionTrialDO> existing = trialMapper.selectListByAssessmentId(a.getId());
        Map<Long, RehabMotionTrialDO> byId = new HashMap<Long, RehabMotionTrialDO>();
        for (RehabMotionTrialDO t : existing) {
            byId.put(t.getId(), t);
        }
        Set<String> families = new HashSet<String>(StrUtil.split(a.getProtocolFamilies(), ','));
        List<RehabMotionTrialDO> desired = new ArrayList<RehabMotionTrialDO>();
        Set<Long> kept = new HashSet<Long>();
        int sort = 0;
        for (RehabMotionTrialSaveReqVO.Item item : req.getTrials()) {
            String family = familyOf(item.getTestCode());
            if (family == null || !families.contains(family)) {
                throw exception(MOTION_TRIAL_INVALID, item.getTestCode() + " 不属于本次评估体系");
            }
            RehabMotionTrialDO t;
            if (item.getId() != null) {
                RehabMotionTrialDO old = byId.get(item.getId());
                if (old == null) {
                    throw exception(MOTION_TRIAL_INVALID, "Trial 不属于该评估");
                }
                t = old;
                kept.add(old.getId());
            } else {
                t = RehabMotionTrialDO.builder().assessmentId(a.getId()).build();
            }
            t.setTestCode(item.getTestCode());
            t.setSide(item.getSide());
            t.setConditionCode(StrUtil.emptyToNull(item.getConditionCode()));
            t.setAttemptNo(item.getAttemptNo() == null ? 1 : item.getAttemptNo());
            t.setOpencapTrialName(StrUtil.emptyToNull(item.getOpencapTrialName()));
            t.setOpencapTrialId(item.getOpencapTrialId() == null ? null
                    : StrUtil.emptyToNull(item.getOpencapTrialId().toLowerCase(java.util.Locale.ROOT)));
            t.setSortNo(item.getSortNo() == null ? sort : item.getSortNo());
            String key = StrUtil.isNotBlank(item.getTrialKey()) ? item.getTrialKey() : t.getTrialKey();
            t.setTrialKey(StrUtil.isNotBlank(key) ? key : autoKey(t));
            desired.add(t);
            sort++;
        }
        List<String> errors = MotionEngineRequestBuilder.validateTrials(desired, null);
        if (!errors.isEmpty()) {
            throw exception(MOTION_TRIAL_INVALID, StrUtil.maxLength(String.join("；", errors), 200));
        }
        for (RehabMotionTrialDO old : existing) {
            if (!kept.contains(old.getId())) {
                trialMapper.deleteById(old.getId());
            }
        }
        for (RehabMotionTrialDO t : desired) {
            if (t.getId() == null) {
                trialMapper.insert(t);
            } else {
                trialMapper.updateById(t);
            }
        }
        access.bumpRevision(a.getId());
        access.manualEdit(a.getId(), "trial_mapping", null, null, "trials", mappingView(existing), mappingView(desired),
                null, userId);
        access.audit(a.getId(), "trial_save", userId, null, mappingView(desired), null);
    }

    static String autoKey(RehabMotionTrialDO t) {
        String base = t.getTestCode().toLowerCase(java.util.Locale.ROOT)
                + ("bilateral".equals(t.getSide()) ? "" : "_" + t.getSide().charAt(0))
                + (t.getConditionCode() == null ? "" : "_" + t.getConditionCode())
                + "_" + t.getAttemptNo();
        return StrUtil.sub(base, 0, 64);
    }

    private static List<Map<String, Object>> mappingView(List<RehabMotionTrialDO> trials) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (RehabMotionTrialDO t : trials) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("trialKey", t.getTrialKey());
            m.put("testCode", t.getTestCode());
            m.put("side", t.getSide());
            m.put("condition", t.getConditionCode());
            m.put("attempt", t.getAttemptNo());
            m.put("opencapTrialName", t.getOpencapTrialName());
            out.add(m);
        }
        return out;
    }

    /** 临床字段（有效性、疼痛、人工判定/测量）。已有分析结果时修改须填原因；逐字段留痕。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateTrialManual(RehabMotionTrialManualReqVO req, Long userId) {
        access.requireClinician(userId);
        RehabMotionTrialDO t = trialMapper.selectById(req.getTrialId());
        if (t == null) {
            throw exception(MOTION_TRIAL_INVALID, "Trial 不存在");
        }
        RehabMotionAssessmentDO a = access.editable(t.getAssessmentId(), userId);
        if (Boolean.FALSE.equals(req.getValid()) && StrUtil.isBlank(req.getInvalidReason())) {
            throw exception(MOTION_TRIAL_INVALID, "无效 Trial 必须填写原因");
        }
        String criteria = req.getManualCriteria() == null ? null : JsonUtils.toJsonString(req.getManualCriteria());
        String values = req.getManualValues() == null ? null : JsonUtils.toJsonString(req.getManualValues());
        RehabMotionTrialDO probe = new RehabMotionTrialDO();
        probe.setTrialKey(t.getTrialKey());
        probe.setTestCode(t.getTestCode());
        probe.setSide(t.getSide());
        probe.setAttemptNo(t.getAttemptNo());
        probe.setValid(req.getValid());
        probe.setInvalidReason(req.getInvalidReason());
        probe.setManualCriteriaJson(criteria);
        probe.setManualValuesJson(values);
        List<String> errors = MotionEngineRequestBuilder.validateTrials(Arrays.asList(probe), null);
        if (!errors.isEmpty()) {
            throw exception(MOTION_TRIAL_INVALID, StrUtil.maxLength(String.join("；", errors), 200));
        }
        String invalidReason = Boolean.FALSE.equals(req.getValid()) ? StrUtil.maxLength(req.getInvalidReason(), 252) : null;
        Map<String, Object[]> changes = new LinkedHashMap<String, Object[]>();
        diff(changes, "valid", t.getValid(), req.getValid());
        diff(changes, "invalidReason", t.getInvalidReason(), invalidReason);
        diff(changes, "pain", t.getPain(), req.getPain());
        diff(changes, "manualCriteria", normJson(t.getManualCriteriaJson()), normJson(criteria));
        diff(changes, "manualValues", normJson(t.getManualValuesJson()), normJson(values));
        if (changes.isEmpty()) {
            return;
        }
        if (a.getAnalyzedRevision() != null && StrUtil.isBlank(req.getReason())) {
            throw exception(MOTION_REASON_REQUIRED);
        }
        trialMapper.update(null, new LambdaUpdateWrapper<RehabMotionTrialDO>()
                .set(RehabMotionTrialDO::getValid, req.getValid())
                .set(RehabMotionTrialDO::getInvalidReason, invalidReason)
                .set(RehabMotionTrialDO::getPain, req.getPain())
                .set(RehabMotionTrialDO::getManualCriteriaJson, criteria)
                .set(RehabMotionTrialDO::getManualValuesJson, values)
                .eq(RehabMotionTrialDO::getId, t.getId()));
        for (Map.Entry<String, Object[]> e : changes.entrySet()) {
            access.manualEdit(a.getId(), "trial", t.getId(), t.getTrialKey(), e.getKey(), e.getValue()[0], e.getValue()[1],
                    req.getReason(), userId);
        }
        access.bumpRevision(a.getId());
        access.audit(a.getId(), "trial_clinical_update", userId, null, changes.keySet(), req.getReason());
    }

    private static Object normJson(String json) {
        return StrUtil.isBlank(json) ? null : MotionEngineRequestBuilder.parseObject(json);
    }

    private static void diff(Map<String, Object[]> changes, String field, Object oldV, Object newV) {
        if (!Objects.equals(oldV, newV)) {
            changes.put(field, new Object[]{oldV, newV});
        }
    }

    /** 评估级人工输入：清除测试疼痛、YBT 距离、TJA/LESS 人工评分、NASM 观察。 */
    @Transactional(rollbackFor = Exception.class)
    public void saveManualInputs(RehabMotionManualInputsReqVO req, Long userId) {
        access.requireClinician(userId);
        RehabMotionAssessmentDO a = access.editable(req.getAssessmentId(), userId);
        List<String> errors = MotionEngineRequestBuilder.validateManualInputs(req.getInputs());
        if (!errors.isEmpty()) {
            throw exception(MOTION_MANUAL_INPUT_INVALID, StrUtil.maxLength(String.join("；", errors), 200));
        }
        String json = JsonUtils.toJsonString(req.getInputs());
        if (json.length() > 60000) {
            throw exception(MOTION_MANUAL_INPUT_INVALID, "内容过大");
        }
        Map<String, Object> old = MotionEngineRequestBuilder.parseManualInputs(a.getManualInputsJson());
        if (old.equals(req.getInputs())) {
            return;
        }
        if (a.getAnalyzedRevision() != null && StrUtil.isBlank(req.getReason())) {
            throw exception(MOTION_REASON_REQUIRED);
        }
        RehabMotionAssessmentDO upd = new RehabMotionAssessmentDO();
        upd.setId(a.getId());
        upd.setManualInputsJson(json);
        assessmentMapper.updateById(upd);
        Set<String> keys = new HashSet<String>(old.keySet());
        keys.addAll(req.getInputs().keySet());
        for (String k : keys) {
            if (!Objects.equals(old.get(k), req.getInputs().get(k))) {
                access.manualEdit(a.getId(), "manual_inputs", a.getId(), null, k, old.get(k), req.getInputs().get(k),
                        req.getReason(), userId);
            }
        }
        access.bumpRevision(a.getId());
        access.audit(a.getId(), "manual_inputs_update", userId, null, keys, req.getReason());
    }

    // ========== 处理 ==========

    /**
     * 发起分析：首次（或 OpenCap 尚未下载）为 PIPELINE，已有运动学数据时为 RESCORE。
     */
    public RehabMotionTaskDO process(RehabMotionProcessReqVO req, Long userId) {
        RehabMotionAssessmentDO a = access.editable(req.getAssessmentId(), userId);
        if (!engineClient.isConfigured()) {
            throw exception(MOTION_ENGINE_UNAVAILABLE, "未配置 MOTION_ENGINE_URL / MOTION_ENGINE_TOKEN");
        }
        List<RehabMotionTrialDO> trials = trialMapper.selectListByAssessmentId(a.getId());
        if (trials.isEmpty()) {
            throw exception(MOTION_TRIAL_INVALID, "至少需要配置一个 Trial");
        }
        boolean hasMot = false;
        for (RehabMotionFileDO f : motionFileMapper.selectListByAssessmentId(a.getId())) {
            if (FILE_MOT.equals(f.getFileKind())) {
                hasMot = true;
                break;
            }
        }
        String type;
        if ("opencap".equals(a.getDataSource()) && !hasMot) {
            if (StrUtil.isBlank(a.getOpencapSessionId())) {
                throw exception(MOTION_OPENCAP_SESSION_REQUIRED);
            }
            type = TASK_PIPELINE;
        } else {
            if (!hasMot) {
                throw exception(MOTION_KINEMATICS_MISSING, "请先上传 OpenSimData/Kinematics/*.mot");
            }
            type = a.getAnalyzedRevision() == null ? TASK_PIPELINE : TASK_RESCORE;
        }
        String initial = TASK_PIPELINE.equals(type) ? MotionTaskStates.initialState(type, hasMot ? "upload" : a.getDataSource())
                : MotionTaskStates.initialState(type, a.getDataSource());
        RehabMotionTaskDO task = taskService.enqueue(a, type, initial, req.getIdempotencyKey(), userId, null);
        steps.syncAssessmentStatus(a.getId(), task.getState());
        access.audit(a.getId(), "process", userId, null, taskAudit(task), null);
        return task;
    }

    /** 列出 OpenCap 会话中的 Trial（供前端映射到测试/侧别/尝试）。 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> opencapTrials(RehabMotionOpencapReqVO req, Long userId) {
        RehabMotionAssessmentDO a = access.editable(req.getAssessmentId(), userId);
        if (!engineClient.isConfigured()) {
            throw exception(MOTION_ENGINE_UNAVAILABLE, "未配置动作分析引擎");
        }
        String session = req.getSessionId().toLowerCase(java.util.Locale.ROOT);
        Map<String, Object> resp;
        try {
            resp = engineClient.opencapTrialStatus(session, null);
        } catch (MotionEngineException ex) {
            throw exception(MOTION_ENGINE_UNAVAILABLE, ex.getCode());
        }
        if (!session.equals(a.getOpencapSessionId())) {
            RehabMotionAssessmentDO upd = new RehabMotionAssessmentDO();
            upd.setId(a.getId());
            upd.setOpencapSessionId(session);
            upd.setDataSource("opencap");
            assessmentMapper.updateById(upd);
            access.bumpRevision(a.getId());
        }
        Object trials = resp.get("trials");
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (trials instanceof List) {
            for (Object o : (List<Object>) trials) {
                if (o instanceof Map) {
                    out.add((Map<String, Object>) o);
                }
            }
        }
        return out;
    }

    static Map<String, Object> taskAudit(RehabMotionTaskDO t) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("taskId", t.getId());
        m.put("type", t.getTaskType());
        m.put("state", t.getState());
        m.put("inputRevision", t.getInputRevision());
        return m;
    }

    public List<RehabMotionTaskRespVO> listTasks(Long assessmentId, Long userId) {
        access.readable(assessmentId, userId);
        List<RehabMotionTaskRespVO> out = new ArrayList<RehabMotionTaskRespVO>();
        for (RehabMotionTaskDO t : taskService.list(assessmentId)) {
            out.add(toTaskResp(t));
        }
        return out;
    }

    public RehabMotionTaskRespVO getTask(Long taskId, Long userId) {
        RehabMotionTaskDO t = taskService.get(taskId);
        access.readable(t.getAssessmentId(), userId);
        return toTaskResp(t);
    }

    public void cancelTask(Long taskId, Long userId) {
        RehabMotionTaskDO t = taskService.get(taskId);
        RehabMotionAssessmentDO a = access.readable(t.getAssessmentId(), userId);
        boolean immediate = taskService.cancel(t);
        if (immediate && !TASK_PDF.equals(t.getTaskType())) {
            steps.syncAssessmentStatus(a.getId(), a.getAnalyzedRevision() != null ? STATE_PENDING_REVIEW : STATE_UPLOADING);
        }
        access.audit(a.getId(), "task_cancel", userId, null, taskAudit(t), null);
    }

    public RehabMotionTaskDO retryTask(Long taskId, String idempotencyKey, Long userId) {
        RehabMotionTaskDO t = taskService.get(taskId);
        RehabMotionAssessmentDO a = TASK_PDF.equals(t.getTaskType()) ? access.readable(t.getAssessmentId(), userId)
                : access.editable(t.getAssessmentId(), userId);
        if (MotionTaskStates.isAnalysis(t.getTaskType()) && !engineClient.isConfigured()) {
            throw exception(MOTION_ENGINE_UNAVAILABLE, "未配置动作分析引擎");
        }
        RehabMotionTaskDO task = taskService.retry(a, t, idempotencyKey, userId);
        if (!TASK_PDF.equals(task.getTaskType())) {
            steps.syncAssessmentStatus(a.getId(), task.getState());
        }
        access.audit(a.getId(), "task_retry", userId, taskAudit(t), taskAudit(task), null);
        return task;
    }

    public static RehabMotionTaskRespVO toTaskResp(RehabMotionTaskDO t) {
        RehabMotionTaskRespVO vo = BeanUtils.toBean(t, RehabMotionTaskRespVO.class);
        vo.setStateLabel(stateLabel(t.getState()));
        return vo;
    }

    public Collection<RehabMotionFileDO> kinematicFiles(Long assessmentId) {
        List<RehabMotionFileDO> out = new ArrayList<RehabMotionFileDO>();
        for (RehabMotionFileDO f : motionFileMapper.selectListByAssessmentId(assessmentId)) {
            if (KINEMATIC_FILE_KINDS.contains(f.getFileKind())) {
                out.add(f);
            }
        }
        return out;
    }

}
