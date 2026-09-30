package cn.iocoder.yudao.module.rehab.service.motion.task;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionFileDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTaskDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTrialDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionAssessmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionFileMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionTrialMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;
import cn.iocoder.yudao.module.rehab.service.motion.MotionEngineRequestBuilder;
import cn.iocoder.yudao.module.rehab.service.motion.MotionStorage;
import cn.iocoder.yudao.module.rehab.service.motion.MotionZipReader;
import cn.iocoder.yudao.module.rehab.service.motion.RehabMotionResultWriter;
import cn.iocoder.yudao.module.rehab.service.motion.ai.RehabMotionAiService;
import cn.iocoder.yudao.module.rehab.service.motion.engine.MotionEngineClient;
import cn.iocoder.yudao.module.rehab.service.motion.report.RehabMotionReportService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants.*;

/**
 * 任务步骤实现。每个步骤要么返回下一状态，要么返回等待时间，要么抛出 {@link MotionTaskException}
 * / {@link cn.iocoder.yudao.module.rehab.service.motion.engine.MotionEngineException}。
 * 步骤必须可重入（重试会从失败步骤重新执行）。
 */
@Service
public class RehabMotionPipelineSteps {

    /** 发送给引擎的数据上限（不含视频） */
    public static final long MAX_ANALYZE_BYTES = 200L * 1024 * 1024;

    @Resource
    private MotionEngineClient engineClient;
    @Resource
    private MotionStorage storage;
    @Resource
    private RehabMotionFileMapper motionFileMapper;
    @Resource
    private RehabMotionTrialMapper trialMapper;
    @Resource
    private RehabMotionAssessmentMapper assessmentMapper;
    @Resource
    private RehabMotionResultWriter resultWriter;
    @Resource
    private RehabMotionAiService aiService;
    @Resource
    @Lazy
    private RehabMotionReportService reportService;

    @Value("${yudao.rehab.motion.opencap-poll-seconds:30}")
    private int opencapPollSeconds = 30;

    public static final class Outcome {
        public final String nextState;
        public final LocalDateTime waitUntil;
        public final String message;

        private Outcome(String nextState, LocalDateTime waitUntil, String message) {
            this.nextState = nextState;
            this.waitUntil = waitUntil;
            this.message = message;
        }

        public static Outcome next(String state) {
            return new Outcome(state, null, null);
        }

        public static Outcome waitUntil(LocalDateTime time, String message) {
            return new Outcome(null, time, message);
        }
    }

    public Outcome execute(RehabMotionTaskDO task, RehabMotionAssessmentDO a) throws IOException {
        String state = task.getState();
        String next = MotionTaskStates.next(task.getTaskType(), a.getDataSource(), state);
        if (STATE_OPENCAP_PROCESSING.equals(state)) {
            return pollOpencap(task, a, next);
        }
        if (STATE_DOWNLOADING.equals(state)) {
            download(a);
        } else if (STATE_PARSING.equals(state)) {
            validateInputs(a);
        } else if (STATE_RULES.equals(state)) {
            analyze(a);
        } else if (STATE_AI_GENERATING.equals(state)) {
            RehabMotionAssessmentDO fresh = assessmentMapper.selectById(a.getId());
            if (fresh.getAnalyzedRevision() == null) {
                throw MotionTaskException.fatal("NOT_ANALYZED", "尚无分析结果，无法生成草稿");
            }
            aiService.generate(fresh, task.getId());
        } else if (STATE_PDF_RENDERING.equals(state)) {
            reportService.renderPendingPdfs(a);
        } else {
            throw MotionTaskException.fatal("STATE_UNKNOWN", "未知任务状态");
        }
        return Outcome.next(next);
    }

    // ========== OpenCap ==========

    @SuppressWarnings("unchecked")
    Outcome pollOpencap(RehabMotionTaskDO task, RehabMotionAssessmentDO a, String next) {
        if (StrUtil.isBlank(a.getOpencapSessionId())) {
            throw MotionTaskException.fatal("OPENCAP_SESSION_REQUIRED", "未填写 OpenCap 会话 ID");
        }
        if (task.getDeadlineTime() != null && LocalDateTime.now().isAfter(task.getDeadlineTime())) {
            throw MotionTaskException.fatal("OPENCAP_TIMEOUT", "OpenCap 处理超时，请在 OpenCap 中确认后重试");
        }
        List<RehabMotionTrialDO> trials = trialMapper.selectListByAssessmentId(a.getId());
        List<String> ids = opencapTrialIds(trials);
        if (ids.isEmpty()) {
            throw MotionTaskException.fatal("OPENCAP_TRIALS_MISSING", "未选择 OpenCap Trial");
        }
        Map<String, Object> status = engineClient.opencapTrialStatus(a.getOpencapSessionId(), ids);
        Map<String, Map<String, Object>> byId = new HashMap<String, Map<String, Object>>();
        Object list = status.get("trials");
        if (list instanceof List) {
            for (Object o : (List<Object>) list) {
                if (o instanceof Map) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    byId.put(String.valueOf(m.get("trial_id")).toLowerCase(java.util.Locale.ROOT), m);
                }
            }
        }
        Set<String> ready = new HashSet<String>();
        for (RehabMotionTrialDO t : trials) {
            if (StrUtil.isBlank(t.getOpencapTrialId())) {
                continue;
            }
            String tid = t.getOpencapTrialId().toLowerCase(java.util.Locale.ROOT);
            Map<String, Object> s = byId.get(tid);
            if (s == null || Boolean.TRUE.equals(s.get("trashed"))) {
                throw MotionTaskException.fatal("OPENCAP_TRIAL_NOT_FOUND", t.getTrialKey() + " 在 OpenCap 会话中不存在或已删除");
            }
            String st = StrUtil.toStringOrNull(s.get("status"));
            if ("error".equalsIgnoreCase(st) || "failed".equalsIgnoreCase(st)) {
                throw MotionTaskException.fatal("OPENCAP_TRIAL_FAILED", t.getTrialKey() + " 在 OpenCap 处理失败");
            }
            String stem = StrUtil.toStringOrNull(s.get("file_stem"));
            if (stem != null && !stem.equals(t.getOpencapTrialName())) {
                trialMapper.update(null, new LambdaUpdateWrapper<RehabMotionTrialDO>()
                        .set(RehabMotionTrialDO::getOpencapTrialName, StrUtil.maxLength(stem, 120))
                        .eq(RehabMotionTrialDO::getId, t.getId()));
            }
            if (Boolean.TRUE.equals(s.get("ready"))) {
                ready.add(tid);
            }
        }
        if (ready.size() < ids.size()) {
            return Outcome.waitUntil(LocalDateTime.now().plusSeconds(opencapPollSeconds),
                    "OpenCap 处理中 " + ready.size() + "/" + ids.size());
        }
        return Outcome.next(next);
    }

    static List<String> opencapTrialIds(List<RehabMotionTrialDO> trials) {
        Set<String> ids = new LinkedHashSet<String>();
        for (RehabMotionTrialDO t : trials) {
            if (StrUtil.isNotBlank(t.getOpencapTrialId())) {
                ids.add(t.getOpencapTrialId().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return new ArrayList<String>(ids);
    }

    void download(RehabMotionAssessmentDO a) throws IOException {
        List<String> ids = opencapTrialIds(trialMapper.selectListByAssessmentId(a.getId()));
        if (ids.size() > 50) {
            throw MotionTaskException.fatal("OPENCAP_TOO_MANY_TRIALS", "单次最多 50 个 Trial");
        }
        byte[] zip = engineClient.opencapDownload(a.getOpencapSessionId(), ids);
        List<MotionZipReader.Entry> entries;
        try {
            entries = MotionZipReader.read(zip, false);
        } catch (IOException | IllegalArgumentException ex) {
            throw MotionTaskException.fatal("OPENCAP_DOWNLOAD_INVALID", "OpenCap 下载内容不合法");
        }
        if (entries.isEmpty()) {
            throw MotionTaskException.fatal("OPENCAP_DOWNLOAD_EMPTY", "OpenCap 未返回运动学数据");
        }
        for (MotionZipReader.Entry e : entries) {
            saveFile(a, e.classified.getKind(), e.classified.getRelativePath(), e.classified.getTrialName(),
                    e.classified.getCameraKey(), e.classified.getExtension(), e.classified.getContentType(), e.data,
                    "opencap", null);
        }
    }

    /** 保存文件；同一相对路径替换旧文件（先写新文件再删旧文件）。 */
    public RehabMotionFileDO saveFile(RehabMotionAssessmentDO a, String kind, String relativePath, String trialName,
                                      String cameraKey, String ext, String contentType, byte[] data, String source,
                                      Long userId) throws IOException {
        MotionStorage.Stored stored = storage.save(a.getTenantId(), a.getId(), kind, ext, data);
        RehabMotionFileDO old = motionFileMapper.selectByRelativePath(a.getId(), relativePath);
        RehabMotionFileDO row = RehabMotionFileDO.builder()
                .assessmentId(a.getId())
                .fileKind(kind)
                .trialName(trialName)
                .cameraKey(cameraKey)
                .relativePath(relativePath)
                .storagePath(stored.relativePath)
                .fileSize(stored.size)
                .sha256(stored.sha256)
                .contentType(contentType)
                .source(source)
                .uploadUserId(userId)
                .build();
        motionFileMapper.insert(row);
        if (old != null) {
            motionFileMapper.deleteById(old.getId());
            storage.deleteQuietly(old.getStoragePath());
        }
        return row;
    }

    // ========== 解析与计算 ==========

    void validateInputs(RehabMotionAssessmentDO a) {
        List<RehabMotionTrialDO> trials = trialMapper.selectListByAssessmentId(a.getId());
        Set<String> motNames = new HashSet<String>();
        for (RehabMotionFileDO f : motionFileMapper.selectListByAssessmentId(a.getId())) {
            if (FILE_MOT.equals(f.getFileKind()) && f.getTrialName() != null) {
                motNames.add(f.getTrialName());
            }
        }
        List<String> errors = MotionEngineRequestBuilder.validateTrials(trials, motNames);
        errors.addAll(MotionEngineRequestBuilder.validateManualInputs(
                MotionEngineRequestBuilder.parseManualInputs(a.getManualInputsJson())));
        boolean anyKinematics = false;
        for (RehabMotionTrialDO t : trials) {
            if (StrUtil.isNotBlank(t.getOpencapTrialName())) {
                anyKinematics = true;
            }
        }
        if (!anyKinematics && !trials.isEmpty() && motNames.isEmpty()) {
            errors.add("没有任何 Trial 关联运动学数据（.mot）");
        }
        if (!errors.isEmpty()) {
            throw MotionTaskException.fatal("INPUT_INVALID", StrUtil.maxLength(String.join("；", errors), 480));
        }
    }

    void analyze(RehabMotionAssessmentDO stale) throws IOException {
        RehabMotionAssessmentDO a = assessmentMapper.selectById(stale.getId());
        int revision = a.getInputRevision() == null ? 1 : a.getInputRevision();
        List<RehabMotionTrialDO> trials = trialMapper.selectListByAssessmentId(a.getId());
        Map<String, Object> request = MotionEngineRequestBuilder.build(a, trials,
                MotionEngineRequestBuilder.parseManualInputs(a.getManualInputsJson()));
        List<MotionEngineRequestBuilder.InputFile> files = new ArrayList<MotionEngineRequestBuilder.InputFile>();
        long total = 0;
        for (RehabMotionFileDO f : motionFileMapper.selectListByAssessmentId(a.getId())) {
            if (!KINEMATIC_FILE_KINDS.contains(f.getFileKind())) {
                continue;
            }
            total += f.getFileSize() == null ? 0 : f.getFileSize();
            if (total > MAX_ANALYZE_BYTES) {
                throw MotionTaskException.fatal("INPUT_TOO_LARGE", "运动学数据总量超过上限");
            }
            byte[] data;
            try {
                data = storage.read(f.getStoragePath());
            } catch (IOException ex) {
                throw MotionTaskException.fatal("FILE_MISSING", "文件缺失：" + StrUtil.maxLength(f.getRelativePath(), 120));
            }
            files.add(new MotionEngineRequestBuilder.InputFile(f.getRelativePath(), data));
        }
        byte[] resultBytes = engineClient.analyze(MotionEngineRequestBuilder.zip(request, files));
        Map<String, Object> result;
        try {
            result = JsonUtils.parseObject(new String(resultBytes, StandardCharsets.UTF_8),
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
        } catch (RuntimeException ex) {
            throw MotionTaskException.retry("ENGINE_RESULT_INVALID", "引擎返回的结果不是合法 JSON");
        }
        if (result == null || result.get("trials") == null) {
            throw MotionTaskException.retry("ENGINE_RESULT_INVALID", "引擎结果缺少 trials");
        }
        Long fileId = resultWriter.storeResult(a, resultBytes);
        resultWriter.write(a, result, revision, fileId);
    }

    /** 同步评估流程状态（不会覆盖已签署状态）。 */
    public void syncAssessmentStatus(Long assessmentId, String status) {
        assessmentMapper.update(null, new LambdaUpdateWrapper<RehabMotionAssessmentDO>()
                .set(RehabMotionAssessmentDO::getStatus, status)
                .eq(RehabMotionAssessmentDO::getId, assessmentId)
                .ne(RehabMotionAssessmentDO::getStatus, RehabMotionConstants.STATE_COMPLETED));
    }

}
