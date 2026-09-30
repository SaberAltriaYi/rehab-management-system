package cn.iocoder.yudao.module.rehab.service.motion.report;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAiDraftDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionFileDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionReportDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionAiDraftMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionAssessmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionFileMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionMetricMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionReportMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionRuleResultMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionScoreMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionTrialMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;
import cn.iocoder.yudao.module.rehab.service.motion.MotionStorage;
import cn.iocoder.yudao.module.rehab.service.motion.RehabMotionResultWriter;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报告生成：签署时冻结治疗师版与患者版内容（JSON + SHA-256），PDF 由异步任务渲染。
 * 修订（amend）将旧报告置为 superseded，重新签署产生新版本。
 */
@Service
@Slf4j
public class RehabMotionReportService {

    @Resource
    private RehabMotionReportMapper reportMapper;
    @Resource
    private RehabMotionAssessmentMapper assessmentMapper;
    @Resource
    private RehabMotionTrialMapper trialMapper;
    @Resource
    private RehabMotionScoreMapper scoreMapper;
    @Resource
    private RehabMotionRuleResultMapper ruleResultMapper;
    @Resource
    private RehabMotionMetricMapper metricMapper;
    @Resource
    private RehabMotionAiDraftMapper aiDraftMapper;
    @Resource
    private RehabMotionFileMapper fileMapper;
    @Resource
    private RehabPatientMapper patientMapper;
    @Resource
    private RehabMotionResultWriter resultWriter;
    @Resource
    private MotionStorage storage;
    @Resource
    private MotionPdfRenderer pdfRenderer;

    /** 组装报告输入（签署、预览共用）。 */
    public MotionReportBuilder.Input buildInput(RehabMotionAssessmentDO a, RehabMotionAiDraftDO accepted,
                                                Map<String, Object> comparison, String signerName,
                                                LocalDateTime signedTime, Integer versionNo) {
        MotionReportBuilder.Input in = new MotionReportBuilder.Input();
        in.assessment = a;
        RehabPatientDO patient = patientMapper.selectById(a.getPatientId());
        if (patient != null) {
            in.patientName = patient.getName();
            in.patientNo = patient.getPatientNo();
            in.patientGender = patient.getGender() == null ? null : (patient.getGender() == 1 ? "男" : patient.getGender() == 2 ? "女" : "-");
            in.patientAge = patient.getAge();
        }
        in.trials = trialMapper.selectListByAssessmentId(a.getId());
        in.scores = scoreMapper.selectListByAssessmentId(a.getId());
        in.rules = ruleResultMapper.selectListByAssessmentId(a.getId());
        in.metrics = metricMapper.selectListByAssessmentId(a.getId());
        in.acceptedDraft = accepted;
        in.limitations = RehabMotionResultWriter.limitations(resultWriter.readResult(a));
        in.comparison = comparison;
        in.signerName = signerName;
        in.signedTime = signedTime;
        in.versionNo = versionNo;
        return in;
    }

    public int nextVersion(Long assessmentId) {
        int max = 0;
        for (RehabMotionReportDO r : reportMapper.selectListByAssessmentId(assessmentId)) {
            if (r.getVersionNo() != null && r.getVersionNo() > max) {
                max = r.getVersionNo();
            }
        }
        return max + 1;
    }

    /** 签署时创建两份报告（调用方负责事务与前置校验）。 */
    public void createSignedReports(RehabMotionAssessmentDO a, RehabMotionAiDraftDO accepted, Map<String, Object> comparison,
                                    Long signerId, String signerName, LocalDateTime signedTime) {
        int version = nextVersion(a.getId());
        MotionReportBuilder.Input in = buildInput(a, accepted, comparison, signerName, signedTime, version);
        insertReport(a, RehabMotionConstants.REPORT_THERAPIST, version, MotionReportBuilder.therapist(in), accepted,
                signerId, signerName, signedTime);
        insertReport(a, RehabMotionConstants.REPORT_PATIENT, version, MotionReportBuilder.patient(in), accepted,
                signerId, signerName, signedTime);
    }

    private void insertReport(RehabMotionAssessmentDO a, String type, int version, Map<String, Object> content,
                              RehabMotionAiDraftDO accepted, Long signerId, String signerName, LocalDateTime signedTime) {
        String json = JsonUtils.toJsonString(content);
        reportMapper.insert(RehabMotionReportDO.builder()
                .assessmentId(a.getId())
                .reportType(type)
                .versionNo(version)
                .status(RehabMotionConstants.REPORT_SIGNED)
                .contentJson(json)
                .contentSha256(DigestUtil.sha256Hex(json.getBytes(StandardCharsets.UTF_8)))
                .pdfStatus("pending")
                .signedUserId(signerId)
                .signedTime(signedTime)
                .signerName(signerName == null ? null : cn.hutool.core.util.StrUtil.maxLength(signerName, 60))
                .aiDraftId(accepted == null ? null : accepted.getId())
                .engineVersion(a.getEngineVersion())
                .ruleVersion(a.getRuleVersion())
                .promptVersion(accepted == null ? null : accepted.getPromptVersion())
                .aiModel(accepted == null ? null : accepted.getModel())
                .build());
    }

    public void supersede(Long assessmentId) {
        reportMapper.update(null, new LambdaUpdateWrapper<RehabMotionReportDO>()
                .set(RehabMotionReportDO::getStatus, RehabMotionConstants.REPORT_SUPERSEDED)
                .eq(RehabMotionReportDO::getAssessmentId, assessmentId)
                .eq(RehabMotionReportDO::getStatus, RehabMotionConstants.REPORT_SIGNED));
    }

    /** PDF 任务：渲染所有 pending 的已签署报告。字体缺失等错误可重试。 */
    public void renderPendingPdfs(RehabMotionAssessmentDO a) throws IOException {
        for (RehabMotionReportDO r : reportMapper.selectListByAssessmentId(a.getId())) {
            if (!RehabMotionConstants.REPORT_SIGNED.equals(r.getStatus()) || "ready".equals(r.getPdfStatus())) {
                continue;
            }
            Map<String, Object> content = JsonUtils.parseObject(r.getContentJson(),
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            byte[] pdf = pdfRenderer.render(content);
            MotionStorage.Stored stored = storage.save(a.getTenantId(), a.getId(), RehabMotionConstants.FILE_PDF, "pdf", pdf);
            RehabMotionFileDO file = RehabMotionFileDO.builder()
                    .assessmentId(a.getId())
                    .fileKind(RehabMotionConstants.FILE_PDF)
                    .relativePath("report/" + r.getReportType() + "-v" + r.getVersionNo() + ".pdf")
                    .storagePath(stored.relativePath)
                    .fileSize(stored.size)
                    .sha256(stored.sha256)
                    .contentType("application/pdf")
                    .source("system")
                    .build();
            fileMapper.insert(file);
            RehabMotionReportDO upd = new RehabMotionReportDO();
            upd.setId(r.getId());
            upd.setPdfFileId(file.getId());
            upd.setPdfStatus("ready");
            reportMapper.updateById(upd);
        }
    }

    public List<RehabMotionReportDO> list(Long assessmentId) {
        return reportMapper.selectListByAssessmentId(assessmentId);
    }

}
