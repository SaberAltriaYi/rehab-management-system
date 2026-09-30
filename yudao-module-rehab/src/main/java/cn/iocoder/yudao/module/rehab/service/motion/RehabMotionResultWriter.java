package cn.iocoder.yudao.module.rehab.service.motion;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAiDraftDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionFileDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionProtocolVersionDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionScoreDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTrialDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionAiDraftMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionAssessmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionFileMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionMetricMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionProtocolVersionMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionRuleResultMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionScoreMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionTrialMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把引擎结果写入规范化表（单事务）：指标/规则整体替换，评分合并（保留治疗师最终分），Trial 质控与分期更新，
 * 协议版本登记，旧 AI 草稿置为过期。结果原文另存为文件用于追溯与重放。
 */
@Service
public class RehabMotionResultWriter {

    @Resource
    private RehabMotionAssessmentMapper assessmentMapper;
    @Resource
    private RehabMotionTrialMapper trialMapper;
    @Resource
    private RehabMotionMetricMapper metricMapper;
    @Resource
    private RehabMotionRuleResultMapper ruleResultMapper;
    @Resource
    private RehabMotionScoreMapper scoreMapper;
    @Resource
    private RehabMotionAiDraftMapper aiDraftMapper;
    @Resource
    private RehabMotionProtocolVersionMapper protocolVersionMapper;
    @Resource
    private RehabMotionFileMapper motionFileMapper;
    @Resource
    private MotionStorage storage;

    /** 保存引擎结果原文，返回文件记录编号。 */
    public Long storeResult(RehabMotionAssessmentDO a, byte[] resultJson) throws IOException {
        MotionStorage.Stored stored = storage.save(a.getTenantId(), a.getId(), RehabMotionConstants.FILE_RESULT, "json",
                resultJson);
        RehabMotionFileDO file = RehabMotionFileDO.builder()
                .assessmentId(a.getId())
                .fileKind(RehabMotionConstants.FILE_RESULT)
                .relativePath("result/revision-" + a.getInputRevision() + ".json")
                .storagePath(stored.relativePath)
                .sha256(stored.sha256)
                .fileSize(stored.size)
                .contentType("application/json")
                .source("engine")
                .build();
        motionFileMapper.insert(file);
        return file.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public MotionResultMapper.Mapped write(RehabMotionAssessmentDO a, Map<String, Object> result, int revision,
                                           Long resultFileId) {
        List<RehabMotionTrialDO> trials = trialMapper.selectListByAssessmentId(a.getId());
        Map<String, Long> idByKey = new HashMap<String, Long>();
        for (RehabMotionTrialDO t : trials) {
            idByKey.put(t.getTrialKey(), t.getId());
        }
        MotionResultMapper.Mapped mapped = MotionResultMapper.map(result, a.getId(), revision, idByKey);

        metricMapper.deletePhysicalByAssessmentId(a.getId());
        ruleResultMapper.deletePhysicalByAssessmentId(a.getId());
        if (!mapped.getMetrics().isEmpty()) {
            metricMapper.insertBatch(mapped.getMetrics());
        }
        if (!mapped.getRuleResults().isEmpty()) {
            ruleResultMapper.insertBatch(mapped.getRuleResults());
        }
        for (RehabMotionTrialDO upd : mapped.getTrialUpdates().values()) {
            if (upd.getId() == null) {
                continue;
            }
            trialMapper.update(null, new LambdaUpdateWrapper<RehabMotionTrialDO>()
                    .set(RehabMotionTrialDO::getQcStatus, upd.getQcStatus())
                    .set(RehabMotionTrialDO::getQcIssuesJson, upd.getQcIssuesJson())
                    .set(RehabMotionTrialDO::getPhasesJson, upd.getPhasesJson())
                    .set(RehabMotionTrialDO::getPhaseDetectionJson, upd.getPhaseDetectionJson())
                    .set(RehabMotionTrialDO::getRepCount, upd.getRepCount())
                    .set(RehabMotionTrialDO::getDurationS, upd.getDurationS())
                    .eq(RehabMotionTrialDO::getId, upd.getId()));
        }

        MotionScoreRules.MergePlan plan = MotionScoreRules.merge(scoreMapper.selectListByAssessmentId(a.getId()),
                mapped.getScores());
        if (!plan.inserts.isEmpty()) {
            scoreMapper.insertBatch(plan.inserts);
        }
        for (RehabMotionScoreDO upd : plan.updates) {
            scoreMapper.updateSystemFields(upd);
        }
        if (!plan.deletes.isEmpty()) {
            scoreMapper.deleteBatchIds(plan.deletes);
        }

        registerProtocols(mapped.getProtocols());
        aiDraftMapper.update(null, new LambdaUpdateWrapper<RehabMotionAiDraftDO>()
                .set(RehabMotionAiDraftDO::getStatus, RehabMotionConstants.AI_STALE)
                .eq(RehabMotionAiDraftDO::getAssessmentId, a.getId())
                .in(RehabMotionAiDraftDO::getStatus, RehabMotionConstants.AI_GENERATED,
                        RehabMotionConstants.AI_FALLBACK, RehabMotionConstants.AI_ACCEPTED,
                        RehabMotionConstants.AI_REJECTED));

        assessmentMapper.update(null, new LambdaUpdateWrapper<RehabMotionAssessmentDO>()
                .set(RehabMotionAssessmentDO::getEngineVersion, StrUtil.maxLength(mapped.getEngineVersion(), 45))
                .set(RehabMotionAssessmentDO::getRuleVersion, StrUtil.maxLength(mapped.getRuleVersion(), 45))
                .set(RehabMotionAssessmentDO::getResultSchemaVersion, StrUtil.maxLength(mapped.getSchemaVersion(), 45))
                .set(RehabMotionAssessmentDO::getProtocolVersionsJson, JsonUtils.toJsonString(mapped.getProtocols()))
                .set(RehabMotionAssessmentDO::getSessionQualityStatus, mapped.getSessionQualityStatus())
                .set(RehabMotionAssessmentDO::getResultFileId, resultFileId)
                .set(RehabMotionAssessmentDO::getAnalyzedRevision, revision)
                .set(RehabMotionAssessmentDO::getAnalyzedTime, LocalDateTime.now())
                .eq(RehabMotionAssessmentDO::getId, a.getId()));
        return mapped;
    }

    @SuppressWarnings("unchecked")
    private void registerProtocols(Map<String, Object> protocols) {
        for (Map.Entry<String, Object> e : protocols.entrySet()) {
            if (!(e.getValue() instanceof Map)) {
                continue;
            }
            Map<String, Object> p = (Map<String, Object>) e.getValue();
            String version = StrUtil.maxLength(StrUtil.toStringOrNull(p.get("protocol_version")), 60);
            String sha = StrUtil.toStringOrNull(p.get("file_sha256"));
            if (version == null || sha == null || !sha.matches("^[0-9a-f]{64}$")) {
                continue;
            }
            String code = StrUtil.maxLength(e.getKey(), 29);
            if (protocolVersionMapper.selectByKey(code, version, sha) != null) {
                continue;
            }
            String source = StrUtil.toStringOrNull(p.get("source_sha256"));
            RehabMotionProtocolVersionDO row = RehabMotionProtocolVersionDO.builder()
                    .protocolCode(code)
                    .protocolId(StrUtil.maxLength(StrUtil.toStringOrNull(p.get("protocol_id")), 60))
                    .protocolVersion(version)
                    .source(StrUtil.maxLength(StrUtil.toStringOrNull(p.get("derived_from")), 250))
                    .fileSha256(sha)
                    .sourceSha256(source != null && source.matches("^[0-9a-f]{64}$") ? source : null)
                    .build();
            try {
                protocolVersionMapper.insert(row);
            } catch (org.springframework.dao.DuplicateKeyException ignored) {
                // 并发登记，忽略
            }
        }
    }

    /** 读取最近一次分析结果原文；不存在返回空 Map。 */
    public Map<String, Object> readResult(RehabMotionAssessmentDO a) {
        if (a.getResultFileId() == null) {
            return Collections.emptyMap();
        }
        RehabMotionFileDO file = motionFileMapper.selectById(a.getResultFileId());
        if (file == null || !a.getId().equals(file.getAssessmentId())) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> map = JsonUtils.parseObject(new String(storage.read(file.getStoragePath()), java.nio.charset.StandardCharsets.UTF_8),
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            return map == null ? Collections.<String, Object>emptyMap() : map;
        } catch (IOException ex) {
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    public static List<String> limitations(Map<String, Object> result) {
        List<String> out = new ArrayList<String>();
        Object lim = result.get("limitations");
        if (lim instanceof List) {
            for (Object o : (List<Object>) lim) {
                if (o instanceof String) {
                    out.add((String) o);
                } else if (o instanceof Map && ((Map<String, Object>) o).get("text") != null) {
                    out.add(String.valueOf(((Map<String, Object>) o).get("text")));
                }
            }
        }
        return out;
    }

}
