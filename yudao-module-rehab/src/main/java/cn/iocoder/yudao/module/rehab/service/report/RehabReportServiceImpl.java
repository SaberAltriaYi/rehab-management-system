package cn.iocoder.yudao.module.rehab.service.report;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.audit.vo.RehabAuditLogRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentModuleDataDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentOperationLogDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentRecordDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.episode.RehabEpisodeDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.report.RehabReportDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.report.RehabReportVersionDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentModuleDataMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentOperationLogMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.episode.RehabEpisodeMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.report.RehabReportMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.report.RehabReportVersionMapper;
import cn.iocoder.yudao.module.rehab.enums.*;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.log.RehabAuditLogService;
import cn.iocoder.yudao.module.rehab.service.notification.RehabNotificationService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

/**
 * 报告管理 Service 实现
 */
@Service
@Validated
public class RehabReportServiceImpl implements RehabReportService {

    private static final DateTimeFormatter REPORT_NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String SOFTWARE_NAME = "运动康复评估与业务管理系统";
    private static final String SOFTWARE_VERSION = "V1.0";
    private static final String SOFTWARE_COPYRIGHT_HOLDER = "[软件著作权人名称]";
    private static final String REPORT_TITLE = "康复综合评估报告";
    private static final String BRANDED_REPORT_TITLE = SOFTWARE_NAME + " " + SOFTWARE_VERSION + " - " + REPORT_TITLE;

    @Value("${yudao.rehab.storage-path:./data/rehab}")
    private String storagePath;
    @Value("${yudao.rehab.pdf-font-path:${REHAB_PDF_FONT_PATH:}}")
    private String pdfFontPath;

    @Resource
    private RehabReportMapper reportMapper;
    @Resource
    private RehabAssessmentRecordMapper assessmentRecordMapper;
    @Resource
    private RehabAssessmentModuleDataMapper moduleDataMapper;
    @Resource
    private RehabAssessmentOperationLogMapper assessmentOperationLogMapper;
    @Resource
    private RehabPatientMapper patientMapper;
    @Resource
    private RehabEpisodeMapper episodeMapper;
    @Resource
    private RehabDataPermissionService dataPermissionService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private RehabReportVersionMapper reportVersionMapper;
    @Resource
    private RehabAuditLogService auditLogService;
    @Resource
    private RehabNotificationService notificationService;

    @Override
    public PageResult<RehabReportRespVO> getReportPage(RehabReportPageReqVO reqVO, Long operatorUserId) {
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
                            .or().like(RehabPatientDO::getPatientNo, reqVO.getKeyword())));
            Set<Long> keywordPatientIds = keywordPatients.stream().map(RehabPatientDO::getId).collect(Collectors.toSet());
            filteredPatientIds = intersectPatientIds(filteredPatientIds, keywordPatientIds);
            if (CollUtil.isEmpty(filteredPatientIds)) {
                return PageResult.empty();
            }
        }

        PageResult<RehabReportDO> pageResult = reportMapper.selectPage(reqVO, filteredPatientIds);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        return new PageResult<>(toReportRespVOList(pageResult.getList()), pageResult.getTotal());
    }

    @Override
    public RehabReportRespVO getReport(Long id, Long operatorUserId) {
        RehabReportDO report = validateReportExists(id);
        validatePatientReadable(report.getPatientId(), operatorUserId);
        return toReportRespVOList(Collections.singletonList(report)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RehabReportGenerateRespVO generateReport(RehabReportGenerateReqVO reqVO, Long operatorUserId) {
        RehabAssessmentRecordDO assessment = validateAssessmentExists(reqVO.getAssessmentId());
        validatePatientReadable(assessment.getPatientId(), operatorUserId);

        RehabPatientDO patient = patientMapper.selectById(assessment.getPatientId());
        RehabEpisodeDO episode = episodeMapper.selectById(assessment.getEpisodeId());
        List<RehabAssessmentModuleDataDO> modules = moduleDataMapper.selectListByAssessmentId(assessment.getId());

        int reportVersion = reportMapper.selectListByAssessmentId(assessment.getId()).size() + 1;
        RehabReportDO report = RehabReportDO.builder()
                .patientId(assessment.getPatientId())
                .episodeId(assessment.getEpisodeId())
                .assessmentId(assessment.getId())
                .reportType(StrUtil.blankToDefault(reqVO.getReportType(), resolveDefaultReportType(assessment.getAssessmentType())))
                .reportStatus(RehabReportConstants.STATUS_DRAFT)
                .reportVersion(reportVersion)
                .generatedBy(operatorUserId)
                .generationMode(StrUtil.blankToDefault(reqVO.getGenerationMode(), RehabReportConstants.GENERATION_AUTO))
                .lastGeneratedAt(LocalDateTime.now())
                .note(reqVO.getNote())
                .build();
        reportMapper.insert(report);

        String reportNo = generateReportNo(report.getId());
        reportMapper.updateById(new RehabReportDO().setId(report.getId()).setReportNo(reportNo));

        Map<String, Object> reportPayload = buildReportPayload(reportNo, patient, episode, assessment, modules);
        String reportJson = JsonUtils.toJsonString(reportPayload);

        boolean fallbackUsed = false;
        String fallbackMessage = null;
        String htmlPath = null;
        String docxPath = null;
        String pdfPath = null;
        try {
            String reportDir = storagePath + File.separator + "reports" + File.separator + patient.getId();
            FileUtil.mkdir(reportDir);
            String baseName = reportNo + "_v" + reportVersion;

            String html = buildHtml(reportPayload);
            htmlPath = reportDir + File.separator + baseName + ".html";
            FileUtil.writeString(html, htmlPath, StandardCharsets.UTF_8);

            docxPath = reportDir + File.separator + baseName + ".docx";
            generateDocx(reportPayload, docxPath);

            pdfPath = reportDir + File.separator + baseName + ".pdf";
            generatePdf(reportPayload, pdfPath);
        } catch (Exception ex) {
            fallbackUsed = true;
            fallbackMessage = "报告模板导出失败，已降级保留结构化 JSON";
        }

        RehabReportDO updateObj = new RehabReportDO().setId(report.getId())
                .setReportNo(reportNo)
                .setReportJson(reportJson)
                .setHtmlSnapshotPath(htmlPath)
                .setDocxPath(docxPath)
                .setPdfPath(pdfPath)
                .setLastGeneratedAt(LocalDateTime.now());
        if (fallbackUsed) {
            updateObj.setNote(StrUtil.blankToDefault(report.getNote(), "")
                    + (StrUtil.isBlank(report.getNote()) ? "" : "；") + fallbackMessage);
        }
        reportMapper.updateById(updateObj);
        RehabReportDO latestReport = reportMapper.selectById(report.getId());
        saveVersionSnapshot(latestReport, "生成报告");

        createAssessmentOperationLog(assessment.getId(), RehabOperationTypeConstants.GENERATE_REPORT,
                operatorUserId, null, latestReport, "由评估生成报告");
        auditLogService.createAuditLog("report", report.getId(), RehabOperationTypeConstants.GENERATE_REPORT,
                operatorUserId, resolveRole(operatorUserId), null, latestReport, "success", "由评估生成报告");

        RehabReportGenerateRespVO respVO = new RehabReportGenerateRespVO();
        respVO.setId(report.getId());
        respVO.setReportNo(reportNo);
        respVO.setReportVersion(reportVersion);
        respVO.setFallbackUsed(fallbackUsed);
        respVO.setFallbackMessage(fallbackMessage);
        return respVO;
    }

    @Override
    public RehabReportPreviewRespVO previewReport(Long id, Long operatorUserId) {
        RehabReportDO report = validateReportExists(id);
        validatePatientReadable(report.getPatientId(), operatorUserId);

        RehabReportPreviewRespVO respVO = new RehabReportPreviewRespVO();
        respVO.setId(report.getId());
        respVO.setReportNo(report.getReportNo());
        respVO.setReportStatus(report.getReportStatus());
        respVO.setReportVersion(report.getReportVersion());
        respVO.setReportJson(report.getReportJson());

        String html = "";
        if (StrUtil.isNotBlank(report.getHtmlSnapshotPath()) && FileUtil.exist(report.getHtmlSnapshotPath())) {
            html = FileUtil.readString(report.getHtmlSnapshotPath(), StandardCharsets.UTF_8);
        } else if (StrUtil.isNotBlank(report.getReportJson())) {
            Map<String, Object> payload = JsonUtils.parseObject(report.getReportJson(), Map.class);
            html = buildHtml(payload == null ? new LinkedHashMap<>() : payload);
        }
        respVO.setHtml(html);
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewReport(RehabReportReviewReqVO reqVO, Long operatorUserId) {
        RehabReportDO report = validateReportExists(reqVO.getId());
        validatePatientReadable(report.getPatientId(), operatorUserId);
        if (ObjUtil.equals(report.getReportStatus(), RehabReportConstants.STATUS_LOCKED)) {
            throw exception(REPORT_LOCKED);
        }
        if (!ObjUtil.equals(report.getReportStatus(), RehabReportConstants.STATUS_DRAFT)
                && !ObjUtil.equals(report.getReportStatus(), RehabReportConstants.STATUS_REVIEWED)) {
            throw exception(REPORT_CAN_NOT_APPROVE);
        }

        RehabReportDO updateObj = new RehabReportDO().setId(reqVO.getId())
                .setReportStatus(RehabReportConstants.STATUS_REVIEWED)
                .setReviewedBy(operatorUserId)
                .setNote(appendRemark(report.getNote(), reqVO.getRemark()));
        reportMapper.updateById(updateObj);
        RehabReportDO latest = reportMapper.selectById(reqVO.getId());
        saveVersionSnapshot(latest, "报告复核");
        createAssessmentOperationLog(report.getAssessmentId(), RehabOperationTypeConstants.REPORT_REVIEW,
                operatorUserId, report, latest, "复核报告");
        auditLogService.createAuditLog("report", report.getId(), RehabOperationTypeConstants.REPORT_REVIEW,
                operatorUserId, resolveRole(operatorUserId), report, latest, "success", reqVO.getRemark());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveReport(RehabReportApproveReqVO reqVO, Long operatorUserId) {
        RehabReportDO report = validateReportExists(reqVO.getId());
        validatePatientReadable(report.getPatientId(), operatorUserId);
        if (ObjUtil.equals(report.getReportStatus(), RehabReportConstants.STATUS_LOCKED)) {
            throw exception(REPORT_LOCKED);
        }
        if (!ObjUtil.equals(report.getReportStatus(), RehabReportConstants.STATUS_REVIEWED)) {
            throw exception(REPORT_CAN_NOT_APPROVE);
        }

        RehabReportDO updateObj = new RehabReportDO().setId(reqVO.getId())
                .setReportStatus(RehabReportConstants.STATUS_APPROVED)
                .setApprovedBy(operatorUserId)
                .setNote(appendRemark(report.getNote(), reqVO.getRemark()));
        reportMapper.updateById(updateObj);
        RehabReportDO latest = reportMapper.selectById(reqVO.getId());
        saveVersionSnapshot(latest, "报告审批");
        createAssessmentOperationLog(report.getAssessmentId(), RehabOperationTypeConstants.REPORT_APPROVE,
                operatorUserId, report, latest, "审批报告");
        auditLogService.createAuditLog("report", report.getId(), RehabOperationTypeConstants.REPORT_APPROVE,
                operatorUserId, resolveRole(operatorUserId), report, latest, "success", reqVO.getRemark());
        notificationService.createSystemNotification(RehabNotificationConstants.TARGET_PATIENT, null,
                report.getPatientId(), report.getEpisodeId(), RehabNotificationConstants.RELATED_REPORT, report.getId(),
                RehabNotificationConstants.TYPE_REPORT_READY, RehabNotificationConstants.SEVERITY_INFO,
                "报告摘要可查看", "您的最新评估报告已更新，可在患者端查看摘要。", RehabNotificationConstants.DELIVERY_MULTI,
                "/pages/report/index", "查看报告");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lockReport(RehabReportLockReqVO reqVO, Long operatorUserId) {
        RehabReportDO report = validateReportExists(reqVO.getId());
        validatePatientReadable(report.getPatientId(), operatorUserId);
        if (!ObjUtil.equals(report.getReportStatus(), RehabReportConstants.STATUS_APPROVED)
                && !ObjUtil.equals(report.getReportStatus(), RehabReportConstants.STATUS_EXPORTED)) {
            throw exception(REPORT_CAN_NOT_LOCK);
        }
        RehabReportDO updateObj = new RehabReportDO().setId(report.getId())
                .setReportStatus(RehabReportConstants.STATUS_LOCKED)
                .setLockedBy(operatorUserId)
                .setLockedTime(LocalDateTime.now())
                .setNote(appendRemark(report.getNote(), "锁版原因：" + reqVO.getReason()));
        reportMapper.updateById(updateObj);
        RehabReportDO latest = reportMapper.selectById(report.getId());
        saveVersionSnapshot(latest, "报告锁版");
        auditLogService.createAuditLog("report", report.getId(), RehabOperationTypeConstants.REPORT_LOCK,
                operatorUserId, resolveRole(operatorUserId), report, latest, "success", reqVO.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlockReport(RehabReportUnlockReqVO reqVO, Long operatorUserId) {
        if (!dataPermissionService.isSuperAdmin(operatorUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
        RehabReportDO report = validateReportExists(reqVO.getId());
        if (!ObjUtil.equals(report.getReportStatus(), RehabReportConstants.STATUS_LOCKED)) {
            throw exception(REPORT_CAN_NOT_UNLOCK);
        }
        RehabReportDO updateObj = new RehabReportDO().setId(report.getId())
                .setReportStatus(RehabReportConstants.STATUS_APPROVED)
                .setLockedBy(null)
                .setLockedTime(null)
                .setNote(appendRemark(report.getNote(), "解锁原因：" + reqVO.getReason()));
        reportMapper.updateById(updateObj);
        RehabReportDO latest = reportMapper.selectById(report.getId());
        saveVersionSnapshot(latest, "报告解锁");
        auditLogService.createAuditLog("report", report.getId(), RehabOperationTypeConstants.REPORT_UNLOCK,
                operatorUserId, resolveRole(operatorUserId), report, latest, "success", reqVO.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public byte[] exportDocx(Long id, Long operatorUserId) {
        RehabReportDO report = validateReportExists(id);
        validatePatientReadable(report.getPatientId(), operatorUserId);
        if (!ObjUtil.equals(report.getReportStatus(), RehabReportConstants.STATUS_APPROVED)
                && !ObjUtil.equals(report.getReportStatus(), RehabReportConstants.STATUS_EXPORTED)
                && !ObjUtil.equals(report.getReportStatus(), RehabReportConstants.STATUS_LOCKED)) {
            throw exception(REPORT_CAN_NOT_EXPORT);
        }
        if (StrUtil.isBlank(report.getDocxPath()) || !FileUtil.exist(report.getDocxPath())) {
            throw exception(REPORT_DOCX_NOT_EXISTS);
        }
        byte[] bytes = FileUtil.readBytes(report.getDocxPath());
        String targetStatus = ObjUtil.equals(report.getReportStatus(), RehabReportConstants.STATUS_LOCKED)
                ? RehabReportConstants.STATUS_LOCKED : RehabReportConstants.STATUS_EXPORTED;
        RehabReportDO updateObj = new RehabReportDO().setId(id)
                .setReportStatus(targetStatus)
                .setExportedAt(LocalDateTime.now());
        reportMapper.updateById(updateObj);
        RehabReportDO latest = reportMapper.selectById(id);
        saveVersionSnapshot(latest, "导出 DOCX");
        createAssessmentOperationLog(report.getAssessmentId(), RehabOperationTypeConstants.REPORT_EXPORT,
                operatorUserId, report, latest, "导出 DOCX 报告");
        auditLogService.createAuditLog("report", id, RehabOperationTypeConstants.REPORT_EXPORT,
                operatorUserId, resolveRole(operatorUserId), report, latest, "success", "导出 DOCX");
        return bytes;
    }

    @Override
    public byte[] exportPdf(Long id, Long operatorUserId) {
        RehabReportDO report = validateReportExists(id);
        validatePatientReadable(report.getPatientId(), operatorUserId);
        if (!ObjUtil.equals(report.getReportStatus(), RehabReportConstants.STATUS_APPROVED)
                && !ObjUtil.equals(report.getReportStatus(), RehabReportConstants.STATUS_EXPORTED)
                && !ObjUtil.equals(report.getReportStatus(), RehabReportConstants.STATUS_LOCKED)) {
            throw exception(REPORT_CAN_NOT_EXPORT);
        }
        if ((StrUtil.isBlank(report.getPdfPath()) || !FileUtil.exist(report.getPdfPath()))
                && StrUtil.isNotBlank(report.getReportJson())) {
            Map<String, Object> payload = JsonUtils.parseObject(report.getReportJson(), Map.class);
            if (payload != null) {
                String reportDir = storagePath + File.separator + "reports" + File.separator + report.getPatientId();
                String pdfPath = reportDir + File.separator + report.getReportNo()
                        + "_v" + report.getReportVersion() + ".pdf";
                try {
                    generatePdf(payload, pdfPath);
                    report.setPdfPath(pdfPath);
                    reportMapper.updateById(new RehabReportDO().setId(id).setPdfPath(pdfPath));
                } catch (IOException ignored) {
                    // 下方统一返回“PDF 文件不存在”，避免向前端暴露底层字体或文件系统细节
                }
            }
        }
        if (StrUtil.isBlank(report.getPdfPath()) || !FileUtil.exist(report.getPdfPath())) {
            throw exception(REPORT_PDF_NOT_EXISTS);
        }
        byte[] bytes = FileUtil.readBytes(report.getPdfPath());
        String targetStatus = ObjUtil.equals(report.getReportStatus(), RehabReportConstants.STATUS_LOCKED)
                ? RehabReportConstants.STATUS_LOCKED : RehabReportConstants.STATUS_EXPORTED;
        reportMapper.updateById(new RehabReportDO().setId(id)
                .setReportStatus(targetStatus)
                .setExportedAt(LocalDateTime.now()));
        RehabReportDO latest = reportMapper.selectById(id);
        saveVersionSnapshot(latest, "导出 PDF");
        auditLogService.createAuditLog("report", id, RehabOperationTypeConstants.REPORT_EXPORT,
                operatorUserId, resolveRole(operatorUserId), report, latest, "success", "导出 PDF");
        return bytes;
    }

    @Override
    public List<RehabReportRespVO> getReportListByAssessment(Long assessmentId, Long operatorUserId) {
        RehabAssessmentRecordDO assessment = validateAssessmentExists(assessmentId);
        validatePatientReadable(assessment.getPatientId(), operatorUserId);
        return toReportRespVOList(reportMapper.selectListByAssessmentId(assessmentId));
    }

    @Override
    public PageResult<RehabReportVersionRespVO> getReportVersionPage(RehabReportVersionPageReqVO reqVO, Long operatorUserId) {
        RehabReportDO report = validateReportExists(reqVO.getReportId());
        validatePatientReadable(report.getPatientId(), operatorUserId);
        PageResult<RehabReportVersionDO> pageResult = reportVersionMapper.selectPage(reqVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }

        Set<Long> userIds = new HashSet<>();
        pageResult.getList().forEach(item -> {
            if (item.getGeneratedBy() != null) {
                userIds.add(item.getGeneratedBy());
            }
            if (item.getReviewedBy() != null) {
                userIds.add(item.getReviewedBy());
            }
            if (item.getApprovedBy() != null) {
                userIds.add(item.getApprovedBy());
            }
            if (item.getLockedBy() != null) {
                userIds.add(item.getLockedBy());
            }
        });
        Map<Long, String> userNameMap = new HashMap<>();
        userIds.forEach(userId -> {
            AdminUserRespDTO user = adminUserApi.getUser(userId);
            userNameMap.put(userId, user == null ? "" : user.getNickname());
        });

        List<RehabReportVersionRespVO> list = pageResult.getList().stream().map(item -> {
            RehabReportVersionRespVO vo = BeanUtils.toBean(item, RehabReportVersionRespVO.class);
            vo.setGeneratedByName(userNameMap.get(item.getGeneratedBy()));
            vo.setReviewedByName(userNameMap.get(item.getReviewedBy()));
            vo.setApprovedByName(userNameMap.get(item.getApprovedBy()));
            vo.setLockedByName(userNameMap.get(item.getLockedBy()));
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    public List<RehabAuditLogRespVO> getReportAuditLogs(Long reportId, Long operatorUserId) {
        RehabReportDO report = validateReportExists(reportId);
        validatePatientReadable(report.getPatientId(), operatorUserId);
        return auditLogService.getModuleAuditLogs("report", reportId, operatorUserId);
    }

    private RehabReportDO validateReportExists(Long id) {
        RehabReportDO report = reportMapper.selectById(id);
        if (report == null) {
            throw exception(REPORT_NOT_EXISTS);
        }
        return report;
    }

    private RehabAssessmentRecordDO validateAssessmentExists(Long id) {
        RehabAssessmentRecordDO assessment = assessmentRecordMapper.selectById(id);
        if (assessment == null) {
            throw exception(ASSESSMENT_NOT_EXISTS);
        }
        return assessment;
    }

    private void validatePatientReadable(Long patientId, Long operatorUserId) {
        if (!dataPermissionService.canReadPatient(patientId, operatorUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
    }

    private String generateReportNo(Long id) {
        String datePart = REPORT_NO_DATE_FORMATTER.format(LocalDateTime.now());
        return "REP" + datePart + String.format("%04d", id % 10000);
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

    private String resolveDefaultReportType(String assessmentType) {
        if (ObjUtil.equals(assessmentType, RehabAssessmentConstants.TYPE_FOLLOWUP)) {
            return RehabReportConstants.TYPE_FOLLOWUP;
        }
        if (ObjUtil.equals(assessmentType, RehabAssessmentConstants.TYPE_DISCHARGE)) {
            return RehabReportConstants.TYPE_DISCHARGE;
        }
        return RehabReportConstants.TYPE_COMPREHENSIVE;
    }

    private String appendRemark(String original, String remark) {
        if (StrUtil.isBlank(remark)) {
            return original;
        }
        return StrUtil.blankToDefault(original, "")
                + (StrUtil.isBlank(original) ? "" : "；") + remark;
    }

    private void createAssessmentOperationLog(Long assessmentId, String operationType, Long operatorUserId,
                                              Object beforeData, Object afterData, String remark) {
        RehabAssessmentOperationLogDO log = RehabAssessmentOperationLogDO.builder()
                .assessmentId(assessmentId)
                .operationType(operationType)
                .operatorUserId(operatorUserId)
                .beforeDataJson(beforeData == null ? null : JsonUtils.toJsonString(beforeData))
                .afterDataJson(afterData == null ? null : JsonUtils.toJsonString(afterData))
                .remark(remark)
                .build();
        assessmentOperationLogMapper.insert(log);
    }

    private void saveVersionSnapshot(RehabReportDO report, String changeSummary) {
        if (report == null) {
            return;
        }
        RehabReportVersionDO version = RehabReportVersionDO.builder()
                .reportId(report.getId())
                .versionNo(report.getReportVersion())
                .reportStatus(report.getReportStatus())
                .generationMode(report.getGenerationMode())
                .reportJson(report.getReportJson())
                .docxPath(report.getDocxPath())
                .pdfPath(report.getPdfPath())
                .htmlSnapshotPath(report.getHtmlSnapshotPath())
                .basedOnAssessmentId(report.getAssessmentId())
                .changeSummary(changeSummary)
                .generatedBy(report.getGeneratedBy())
                .reviewedBy(report.getReviewedBy())
                .approvedBy(report.getApprovedBy())
                .lockedBy(report.getLockedBy())
                .lockedTime(report.getLockedTime())
                .build();
        reportVersionMapper.insert(version);
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

    private List<RehabReportRespVO> toReportRespVOList(List<RehabReportDO> list) {
        if (CollUtil.isEmpty(list)) {
            return Collections.emptyList();
        }
        Set<Long> patientIds = list.stream().map(RehabReportDO::getPatientId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> episodeIds = list.stream().map(RehabReportDO::getEpisodeId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> assessmentIds = list.stream().map(RehabReportDO::getAssessmentId).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> userIds = new HashSet<>();
        list.forEach(item -> {
            if (item.getGeneratedBy() != null) {
                userIds.add(item.getGeneratedBy());
            }
            if (item.getReviewedBy() != null) {
                userIds.add(item.getReviewedBy());
            }
            if (item.getApprovedBy() != null) {
                userIds.add(item.getApprovedBy());
            }
            if (item.getLockedBy() != null) {
                userIds.add(item.getLockedBy());
            }
        });

        Map<Long, RehabPatientDO> patientMap = patientMapper.selectBatchIds(patientIds).stream()
                .collect(Collectors.toMap(RehabPatientDO::getId, item -> item, (a, b) -> a));
        Map<Long, RehabEpisodeDO> episodeMap = episodeMapper.selectBatchIds(episodeIds).stream()
                .collect(Collectors.toMap(RehabEpisodeDO::getId, item -> item, (a, b) -> a));
        Map<Long, RehabAssessmentRecordDO> assessmentMap = assessmentRecordMapper.selectBatchIds(assessmentIds).stream()
                .collect(Collectors.toMap(RehabAssessmentRecordDO::getId, item -> item, (a, b) -> a));

        Map<Long, String> userNameMap = new HashMap<>();
        userIds.forEach(id -> {
            AdminUserRespDTO user = adminUserApi.getUser(id);
            userNameMap.put(id, user == null ? "" : user.getNickname());
        });

        return list.stream().map(item -> {
            RehabReportRespVO respVO = BeanUtils.toBean(item, RehabReportRespVO.class);
            RehabPatientDO patient = patientMap.get(item.getPatientId());
            if (patient != null) {
                respVO.setPatientNo(patient.getPatientNo());
                respVO.setPatientName(patient.getName());
            }
            RehabEpisodeDO episode = episodeMap.get(item.getEpisodeId());
            if (episode != null) {
                respVO.setEpisodeNo(episode.getEpisodeNo());
            }
            RehabAssessmentRecordDO assessment = assessmentMap.get(item.getAssessmentId());
            if (assessment != null) {
                respVO.setAssessmentNo(assessment.getAssessmentNo());
            }
            respVO.setGeneratedByName(userNameMap.get(item.getGeneratedBy()));
            respVO.setReviewedByName(userNameMap.get(item.getReviewedBy()));
            respVO.setApprovedByName(userNameMap.get(item.getApprovedBy()));
            respVO.setLockedByName(userNameMap.get(item.getLockedBy()));
            return respVO;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> buildReportPayload(String reportNo, RehabPatientDO patient, RehabEpisodeDO episode,
                                                   RehabAssessmentRecordDO assessment,
                                                   List<RehabAssessmentModuleDataDO> modules) {
        Map<String, RehabAssessmentModuleDataDO> moduleMap = modules.stream()
                .collect(Collectors.toMap(RehabAssessmentModuleDataDO::getModuleType, item -> item, (a, b) -> b));
        expandComprehensiveModules(moduleMap);
        List<String> missingModules = RehabAssessmentConstants.MODULE_TYPES.stream()
                .filter(type -> !moduleMap.containsKey(type))
                .collect(Collectors.toList());

        List<Map<String, Object>> sections = new ArrayList<>();
        sections.add(section(1, "封面页", BRANDED_REPORT_TITLE));
        sections.add(section(2, "基本信息与主诉", StrUtil.blankToDefault(patient.getChiefComplaint(), "未提供/数据不足")));
        sections.add(section(3, "数据来源与可用性", describeDataAvailability(moduleMap, missingModules)));
        sections.add(section(4, "总览摘要", buildOverviewSummary(assessment, moduleMap, missingModules)));
        sections.add(section(5, "身体成分与生长发育解读", moduleSummaryContent(moduleMap, RehabAssessmentConstants.MODULE_BODY_COMP, "未提供/暂不适用")));
        sections.add(section(6, "静态评估总章", moduleContent(moduleMap, RehabAssessmentConstants.MODULE_STATIC, "未提供/数据不足")));
        sections.add(section(7, "静态指标总表", moduleContent(moduleMap, RehabAssessmentConstants.MODULE_STATIC, "未提供/数据不足")));
        sections.add(section(8, "NASM-CES 动作评估汇总", buildNasmActionSummary(moduleMap)));
        sections.add(section(9, "SFMA 评估解读", buildSfmaInterpretationSummary(moduleMap)));
        sections.add(section(10, "FMS 评估解读", moduleSummaryContent(moduleMap, RehabAssessmentConstants.MODULE_FMS, "未提供/数据不足")));
        sections.add(section(11, "YBT 评估解读", moduleSummaryContent(moduleMap, RehabAssessmentConstants.MODULE_YBT, "未提供/数据不足")));
        sections.add(section(12, "OpenCap / OpenSim 运动学专项分析", moduleSummaryContent(moduleMap, RehabAssessmentConstants.MODULE_OPENCAP, "未提供/数据不足")));
        sections.add(section(13, "综合问题清单", buildIssueSummary(moduleMap, missingModules)));
        sections.add(section(14, "整体主要风险指向", buildRiskSummary(assessment, moduleMap)));
        sections.add(section(15, "优先干预顺序", buildPriorityInterventionSummary(moduleMap)));
        sections.add(section(16, "风险触发因素与红旗筛查", StrUtil.blankToDefault(assessment.getRedFlagNotes(), "证据不足；仅为功能学推测；需结合人工复核")));
        sections.add(section(17, "训练处方总览", "提示先从低痛阈、低冲击训练开始，逐步进阶；若疼痛升级需立即降阶。"));
        sections.add(section(18, "复评与进阶标准", "建议 2-4 周复评一次，优先跟踪疼痛、左右差与 QEI 趋势。"));
        sections.add(section(19, "动作执行质量评估（QEI）", buildQeiSummary(moduleMap)));
        sections.add(section(20, "治疗师快速查看表", "提示关注本次主要问题、风险等级、左右差和下次复测节点。"));
        sections.add(section(21, "患者/家长简版摘要", "当前重点是降低疼痛、提升稳定性、保证训练依从性。"));
        sections.add(section(22, "附录：原始证据页", "附录包含模块原始结构化数据摘要，供人工复核。"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportNo", reportNo);
        payload.put("generatedAt", LocalDateTime.now().toString());
        payload.put("patient", BeanUtils.toBean(patient, Map.class));
        payload.put("episode", BeanUtils.toBean(episode, Map.class));
        payload.put("assessment", BeanUtils.toBean(assessment, Map.class));
        payload.put("missingModules", missingModules);
        payload.put("sections", sections);
        payload.put("evidenceRefs", new ArrayList<>(moduleMap.keySet()));
        return payload;
    }

    private Map<String, Object> section(int index, String title, String content) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("index", index);
        map.put("title", title);
        map.put("content", content);
        return map;
    }

    private String describeDataAvailability(Map<String, RehabAssessmentModuleDataDO> moduleMap,
                                            List<String> missingModules) {
        return "已提供模块 " + String.join("、", moduleMap.keySet())
                + "；缺失模块 " + (CollUtil.isEmpty(missingModules) ? "无" : String.join("、", missingModules))
                + "。缺失数据章节已自动降级为“未提供/数据不足”。";
    }

    private String buildOverviewSummary(RehabAssessmentRecordDO assessment,
                                        Map<String, RehabAssessmentModuleDataDO> moduleMap,
                                        List<String> missingModules) {
        String nasmDynamicSummary = getNasmMappingText(moduleMap, "dynamic_function_summary_text");
        boolean hasDynamicEvidence = moduleMap.containsKey(RehabAssessmentConstants.MODULE_NASM)
                || moduleMap.containsKey(RehabAssessmentConstants.MODULE_SFMA)
                || moduleMap.containsKey(RehabAssessmentConstants.MODULE_FMS)
                || moduleMap.containsKey(RehabAssessmentConstants.MODULE_YBT);

        StringBuilder sb = new StringBuilder();
        String comprehensiveConclusion = getStructuredSummaryValue(
                moduleMap.get(RehabAssessmentConstants.MODULE_COMPREHENSIVE), "conclusion");
        if (StrUtil.isNotBlank(comprehensiveConclusion)) {
            sb.append("综合评估结论：").append(comprehensiveConclusion).append("\n");
        }
        sb.append("提示当前为功能评估结论，非医学确诊。")
                .append("从静态排列看，")
                .append(moduleMap.containsKey(RehabAssessmentConstants.MODULE_STATIC) ? "已记录体态线索" : "证据不足")
                .append("；从动态表现看，")
                .append(hasDynamicEvidence ? "存在动作控制与稳定性线索" : "证据不足")
                .append("。结合当前证据，优先考虑 ")
                .append(StrUtil.blankToDefault(assessment.getChiefFocus(), "动作质量与负荷管理"))
                .append("。\n");
        if (StrUtil.isNotBlank(nasmDynamicSummary)) {
            sb.append("CES 动态功能摘要：").append(nasmDynamicSummary).append("\n");
        }
        if (CollUtil.isNotEmpty(missingModules)) {
            sb.append("证据不足：缺失模块 ").append(String.join("、", missingModules))
                    .append("；仅为功能学推测；需结合人工复核。");
        }
        return sb.toString();
    }

    private String buildIssueSummary(Map<String, RehabAssessmentModuleDataDO> moduleMap, List<String> missingModules) {
        if (moduleMap.isEmpty()) {
            return "证据不足；仅为功能学推测；需结合人工复核。";
        }
        return "提示疑似存在动作模式效率下降与代偿链风险。已纳入证据模块："
                + String.join("、", moduleMap.keySet())
                + (CollUtil.isEmpty(missingModules) ? "。" : "；缺失模块：" + String.join("、", missingModules) + "。");
    }

    private String buildRiskSummary(RehabAssessmentRecordDO assessment, Map<String, RehabAssessmentModuleDataDO> moduleMap) {
        String mappedRiskText = getNasmMappingText(moduleMap, "overall_risk_direction_text");
        if (StrUtil.isNotBlank(mappedRiskText)) {
            return mappedRiskText;
        }
        String pain = assessment.getPainScore() == null ? "未提供" : assessment.getPainScore().toPlainString();
        String opencapInfo = moduleMap.containsKey(RehabAssessmentConstants.MODULE_OPENCAP) ? "含运动学证据" : "运动学证据不足";
        return "疼痛/症状风险：当前疼痛评分 " + pain + "；再代偿风险：" + opencapInfo
                + "。提示需结合训练负荷与复测趋势持续监测。";
    }

    private String buildNasmActionSummary(Map<String, RehabAssessmentModuleDataDO> moduleMap) {
        Map<String, Object> mapping = getNasmReportMapping(moduleMap);
        List<Map<String, Object>> actionBlocks = castToMapList(mapping.get("nasm_ces_action_blocks"));
        if (CollUtil.isEmpty(actionBlocks)) {
            return moduleContent(moduleMap, RehabAssessmentConstants.MODULE_NASM, "未提供/数据不足");
        }
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> item : actionBlocks) {
            String actionName = StrUtil.blankToDefault(String.valueOf(item.get("action_name_zh")), "-");
            String observation = StrUtil.blankToDefault(String.valueOf(item.get("observation")), "未提供");
            String analysis = StrUtil.blankToDefault(String.valueOf(item.get("analysis")), "未提供");
            String risk = StrUtil.blankToDefault(String.valueOf(item.get("risk")), "未提供");
            String suggestion = StrUtil.blankToDefault(String.valueOf(item.get("suggestion")), "需结合人工复核");
            lines.add(actionName + "｜观测：" + observation + "｜分析：" + analysis + "｜风险：" + risk + "｜建议：" + suggestion);
        }
        return String.join("\n", lines);
    }

    private String buildPriorityInterventionSummary(Map<String, RehabAssessmentModuleDataDO> moduleMap) {
        Map<String, Object> mapping = getNasmReportMapping(moduleMap);
        List<Map<String, Object>> drafts = castToMapList(mapping.get("priority_intervention_draft"));
        if (CollUtil.isEmpty(drafts)) {
            String sfmaPriorityText = getSfmaPriorityInterventionText(moduleMap);
            if (StrUtil.isNotBlank(sfmaPriorityText)) {
                return sfmaPriorityText;
            }
            return "结合当前证据，优先考虑动作控制重建、左右差优化与负荷管理。";
        }
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> item : drafts) {
            String rank = String.valueOf(item.getOrDefault("priority_rank", "-"));
            String region = String.valueOf(item.getOrDefault("region", "未提供"));
            String focus = String.valueOf(item.getOrDefault("focus", "需结合人工复核"));
            lines.add("第" + rank + "优先级｜区域：" + region + "｜关注点：" + focus);
        }
        return String.join("\n", lines);
    }

    private String buildQeiSummary(Map<String, RehabAssessmentModuleDataDO> moduleMap) {
        Map<String, Object> mapping = getNasmReportMapping(moduleMap);
        Map<String, Object> qeiSummary = castToMap(mapping.get("qei_summary"));
        if (qeiSummary == null) {
            return "若未提供 QEI 数据，显示“未提供/数据不足”。";
        }
        String summaryText = StrUtil.blankToDefault(
                String.valueOf(qeiSummary.get("qei_summary_text")),
                "当前阶段未启用自动 QEI 计算，建议结合治疗师人工评分。"
        );
        List<Map<String, Object>> rows = castToMapList(qeiSummary.get("qei_by_action"));
        if (CollUtil.isEmpty(rows)) {
            return summaryText;
        }
        String detailText = rows.stream()
                .map(row -> String.valueOf(row.getOrDefault("action_name_zh", "-")) + "：" + String.valueOf(row.get("qei")))
                .collect(Collectors.joining("；"));
        return summaryText + "\n" + detailText;
    }

    private String buildSfmaInterpretationSummary(Map<String, RehabAssessmentModuleDataDO> moduleMap) {
        Map<String, Object> sfmaMapping = getSfmaReportMapping(moduleMap);
        if (sfmaMapping.isEmpty()) {
            return moduleContent(moduleMap, RehabAssessmentConstants.MODULE_SFMA, "未提供/数据不足");
        }
        Map<String, Object> interpretation = castToMap(sfmaMapping.get("sfma_interpretation"));
        Map<String, Object> classification = castToMap(sfmaMapping.get("classification_and_priority"));
        Map<String, Object> protocolSummary = castToMap(sfmaMapping.get("book_protocol_summary"));

        StringBuilder sb = new StringBuilder();
        if (protocolSummary != null && !protocolSummary.isEmpty()) {
            sb.append("评估协议：原书版 SFMA ")
                    .append(normalizeMappingValue(protocolSummary.get("protocol_version")))
                    .append("；已记录步骤 ")
                    .append(normalizeMappingValue(protocolSummary.get("recorded_step_count")))
                    .append("；疼痛终止流程 ")
                    .append(normalizeMappingValue(protocolSummary.get("stopped_due_to_pain_count")))
                    .append("\n");
        }
        if (interpretation != null) {
            String judgement = normalizeMappingValue(interpretation.get("classification_judgement"));
            if (StrUtil.isNotBlank(judgement)) {
                sb.append("分类判定：").append(judgement).append("\n");
            }
            String clinicalMeaning = normalizeMappingValue(interpretation.get("clinical_meaning"));
            if (StrUtil.isNotBlank(clinicalMeaning)) {
                sb.append("临床意义：").append(clinicalMeaning).append("\n");
            }
            String trainingDirection = normalizeMappingValue(interpretation.get("training_direction"));
            if (StrUtil.isNotBlank(trainingDirection)) {
                sb.append("训练取向：").append(trainingDirection).append("\n");
            }
        }
        if (classification != null) {
            String primary = normalizeMappingValue(classification.get("primary"));
            String secondary = normalizeMappingValue(classification.get("secondary"));
            String p1 = normalizeMappingValue(classification.get("priority_1"));
            String p2 = normalizeMappingValue(classification.get("priority_2"));
            String p3 = normalizeMappingValue(classification.get("priority_3"));
            if (StrUtil.isNotBlank(primary) || StrUtil.isNotBlank(secondary)) {
                sb.append("主/次分类：")
                        .append(StrUtil.blankToDefault(primary, "-"))
                        .append(" / ")
                        .append(StrUtil.blankToDefault(secondary, "-"))
                        .append("\n");
            }
            if (StrUtil.isNotBlank(p1) || StrUtil.isNotBlank(p2) || StrUtil.isNotBlank(p3)) {
                sb.append("优先级：")
                        .append(StrUtil.blankToDefault(p1, "-"))
                        .append("；")
                        .append(StrUtil.blankToDefault(p2, "-"))
                        .append("；")
                        .append(StrUtil.blankToDefault(p3, "-"))
                        .append("\n");
            }
        }
        String limitationChains = normalizeMappingValue(sfmaMapping.get("major_limitation_chains"));
        if (StrUtil.isNotBlank(limitationChains) && !"[]".equals(limitationChains)) {
            sb.append("主要限制链条：").append(limitationChains).append("\n");
        }
        String controlChains = normalizeMappingValue(sfmaMapping.get("major_control_deficit_chains"));
        if (StrUtil.isNotBlank(controlChains) && !"[]".equals(controlChains)) {
            sb.append("主要控制障碍链条：").append(controlChains).append("\n");
        }
        String asymmetry = normalizeMappingValue(sfmaMapping.get("left_right_asymmetry_focus"));
        if (StrUtil.isNotBlank(asymmetry) && !"[]".equals(asymmetry)) {
            sb.append("左右差重点：").append(asymmetry).append("\n");
        }
        String manualHint = normalizeMappingValue(sfmaMapping.get("manual_review_hint"));
        if (StrUtil.isNotBlank(manualHint)) {
            sb.append("人工复核提示：").append(manualHint);
        }
        String content = sb.toString().trim();
        if (StrUtil.isBlank(content)) {
            return moduleContent(moduleMap, RehabAssessmentConstants.MODULE_SFMA, "未提供/数据不足");
        }
        return content;
    }

    private String getSfmaPriorityInterventionText(Map<String, RehabAssessmentModuleDataDO> moduleMap) {
        Map<String, Object> sfmaMapping = getSfmaReportMapping(moduleMap);
        if (sfmaMapping.isEmpty()) {
            return null;
        }
        Map<String, Object> classification = castToMap(sfmaMapping.get("classification_and_priority"));
        if (classification == null) {
            return null;
        }
        String p1 = normalizeMappingValue(classification.get("priority_1"));
        String p2 = normalizeMappingValue(classification.get("priority_2"));
        String p3 = normalizeMappingValue(classification.get("priority_3"));
        if (StrUtil.isAllBlank(p1, p2, p3)) {
            return null;
        }
        return "第1优先级｜" + StrUtil.blankToDefault(p1, "-") + "\n"
                + "第2优先级｜" + StrUtil.blankToDefault(p2, "-") + "\n"
                + "第3优先级｜" + StrUtil.blankToDefault(p3, "-");
    }

    private String getNasmMappingText(Map<String, RehabAssessmentModuleDataDO> moduleMap, String key) {
        Map<String, Object> mapping = getNasmReportMapping(moduleMap);
        if (mapping.isEmpty()) {
            return null;
        }
        String value = normalizeMappingValue(mapping.get(key));
        return StrUtil.isBlank(value) ? null : value;
    }

    private Map<String, Object> getNasmReportMapping(Map<String, RehabAssessmentModuleDataDO> moduleMap) {
        RehabAssessmentModuleDataDO nasmModule = moduleMap.get(RehabAssessmentConstants.MODULE_NASM);
        if (nasmModule == null || StrUtil.isBlank(nasmModule.getDataJson())) {
            return Collections.emptyMap();
        }
        Map<String, Object> nasmPayload = JsonUtils.parseObject(nasmModule.getDataJson(), Map.class);
        if (nasmPayload == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> mapping = castToMap(nasmPayload.get("report_mapping"));
        return mapping == null ? Collections.emptyMap() : mapping;
    }

    private Map<String, Object> getSfmaReportMapping(Map<String, RehabAssessmentModuleDataDO> moduleMap) {
        RehabAssessmentModuleDataDO sfmaModule = moduleMap.get(RehabAssessmentConstants.MODULE_SFMA);
        if (sfmaModule == null || StrUtil.isBlank(sfmaModule.getDataJson())) {
            return Collections.emptyMap();
        }
        Map<String, Object> sfmaPayload = JsonUtils.parseObject(sfmaModule.getDataJson(), Map.class);
        if (sfmaPayload == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> mapping = castToMap(sfmaPayload.get("report_mapping"));
        if (mapping == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> sfma = castToMap(mapping.get("sfma"));
        return sfma == null ? Collections.emptyMap() : sfma;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToMap(Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castToMapList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<?> list = (List<?>) value;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map) {
                result.add((Map<String, Object>) item);
            }
        }
        return result;
    }

    private String normalizeMappingValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if ("null".equalsIgnoreCase(text) || "undefined".equalsIgnoreCase(text)) {
            return null;
        }
        return text;
    }

    private String moduleContent(Map<String, RehabAssessmentModuleDataDO> moduleMap, String moduleType, String fallback) {
        RehabAssessmentModuleDataDO module = moduleMap.get(moduleType);
        if (module == null || StrUtil.isBlank(module.getDataJson())) {
            return fallback;
        }
        String content = module.getDataJson();
        if (content.length() > 600) {
            content = content.substring(0, 600) + "...";
        }
        return content;
    }

    @SuppressWarnings("unchecked")
    private void expandComprehensiveModules(Map<String, RehabAssessmentModuleDataDO> moduleMap) {
        RehabAssessmentModuleDataDO comprehensive = moduleMap.get(RehabAssessmentConstants.MODULE_COMPREHENSIVE);
        if (comprehensive == null || StrUtil.isBlank(comprehensive.getDataJson())) {
            return;
        }
        Map<String, Object> payload = JsonUtils.parseObject(comprehensive.getDataJson(), Map.class);
        Map<String, Object> nestedModules = payload == null ? null : castToMap(payload.get("modules"));
        if (CollUtil.isEmpty(nestedModules)) {
            return;
        }
        Map<String, String> typeMapping = new LinkedHashMap<>();
        typeMapping.put(RehabAssessmentConstants.TYPE_STATIC_ASSESSMENT, RehabAssessmentConstants.MODULE_STATIC);
        typeMapping.put(RehabAssessmentConstants.TYPE_BODY_COMPOSITION, RehabAssessmentConstants.MODULE_BODY_COMP);
        typeMapping.put(RehabAssessmentConstants.TYPE_NASM_CES, RehabAssessmentConstants.MODULE_NASM);
        typeMapping.put(RehabAssessmentConstants.TYPE_SFMA, RehabAssessmentConstants.MODULE_SFMA);
        typeMapping.put(RehabAssessmentConstants.TYPE_FMS, RehabAssessmentConstants.MODULE_FMS);
        typeMapping.put(RehabAssessmentConstants.TYPE_YBT, RehabAssessmentConstants.MODULE_YBT);
        typeMapping.put(RehabAssessmentConstants.TYPE_OPENCAP, RehabAssessmentConstants.MODULE_OPENCAP);
        typeMapping.put(RehabAssessmentConstants.TYPE_OBSERVATION_ONLY, RehabAssessmentConstants.MODULE_OBSERVATION);
        typeMapping.forEach((assessmentType, moduleType) -> {
            Object value = nestedModules.get(assessmentType);
            if (value == null || moduleMap.containsKey(moduleType)) {
                return;
            }
            moduleMap.put(moduleType, RehabAssessmentModuleDataDO.builder()
                    .assessmentId(comprehensive.getAssessmentId())
                    .moduleType(moduleType)
                    .moduleStatus(comprehensive.getModuleStatus())
                    .sourceType(comprehensive.getSourceType())
                    .version(comprehensive.getVersion())
                    .dataJson(JsonUtils.toJsonString(value))
                    .build());
        });
    }

    @SuppressWarnings("unchecked")
    private String moduleSummaryContent(Map<String, RehabAssessmentModuleDataDO> moduleMap,
                                        String moduleType, String fallback) {
        RehabAssessmentModuleDataDO module = moduleMap.get(moduleType);
        if (module == null || StrUtil.isBlank(module.getDataJson())) {
            return fallback;
        }
        Map<String, Object> payload = JsonUtils.parseObject(module.getDataJson(), Map.class);
        if (payload == null) {
            return moduleContent(moduleMap, moduleType, fallback);
        }
        List<String> lines = new ArrayList<>();
        Map<String, Object> summary = castToMap(payload.get("summary"));
        if (summary != null) {
            appendSummaryLine(lines, "核心问题", summary.get("chiefProblem"));
            appendSummaryLine(lines, "总分", summary.get("totalScore"));
            appendSummaryLine(lines, "左右不对称项", summary.get("asymmetryCount"));
            appendSummaryLine(lines, "疼痛提示", summary.get("painDetected"));
            appendSummaryLine(lines, "风险等级", summary.get("riskLevel"));
            appendSummaryLine(lines, "结论", summary.get("conclusion"));
            appendSummaryLine(lines, "干预优先级", summary.get("priority"));
            appendSummaryLine(lines, "建议", summary.get("recommendation"));
            appendSummaryLine(lines, "数据局限", summary.get("limitation"));
            appendSummaryLine(lines, "复查安排", summary.get("followUp"));
        }
        if (RehabAssessmentConstants.MODULE_BODY_COMP.equals(moduleType)) {
            Map<String, Object> measurements = castToMap(payload.get("measurements"));
            if (measurements != null) {
                appendSummaryLine(lines, "身高(cm)", measurements.get("heightCm"));
                appendSummaryLine(lines, "体重(kg)", measurements.get("weightKg"));
                appendSummaryLine(lines, "BMI", measurements.get("bmi"));
                appendSummaryLine(lines, "体脂率(%)", measurements.get("bodyFatPercent"));
                appendSummaryLine(lines, "骨骼肌(kg)", measurements.get("skeletalMuscleKg"));
                appendSummaryLine(lines, "腰臀比", measurements.get("waistHipRatio"));
            }
        }
        if (RehabAssessmentConstants.MODULE_YBT.equals(moduleType)) {
            appendYbtResult(lines, "下肢", castToMap(payload.get("lowerQuarter")));
            appendYbtResult(lines, "上肢", castToMap(payload.get("upperQuarter")));
        }
        if (RehabAssessmentConstants.MODULE_OPENCAP.equals(moduleType)) {
            Object trials = payload.get("trials");
            if (trials instanceof List) {
                lines.add("Trial 数量：" + ((List<Object>) trials).size());
            }
        }
        return CollUtil.isEmpty(lines) ? moduleContent(moduleMap, moduleType, fallback) : String.join("\n", lines);
    }

    private void appendYbtResult(List<String> lines, String label, Map<String, Object> region) {
        if (region == null || Boolean.FALSE.equals(region.get("enabled"))) {
            return;
        }
        Map<String, Object> result = castToMap(region.get("result"));
        if (result == null) {
            return;
        }
        appendSummaryLine(lines, label + "左侧综合分(%)", result.get("leftCompositePercent"));
        appendSummaryLine(lines, label + "右侧综合分(%)", result.get("rightCompositePercent"));
        appendSummaryLine(lines, label + "最大左右差(cm)", result.get("maxAsymmetryCm"));
        appendSummaryLine(lines, label + "风险提示", result.get("riskFlag"));
    }

    private void appendSummaryLine(List<String> lines, String label, Object value) {
        String normalized = normalizeMappingValue(value);
        if (StrUtil.isNotBlank(normalized)) {
            lines.add(label + "：" + normalized);
        }
    }

    private String getStructuredSummaryValue(RehabAssessmentModuleDataDO module, String key) {
        if (module == null || StrUtil.isBlank(module.getDataJson())) {
            return null;
        }
        Map<String, Object> payload = JsonUtils.parseObject(module.getDataJson(), Map.class);
        Map<String, Object> summary = payload == null ? null : castToMap(payload.get("summary"));
        return summary == null ? null : normalizeMappingValue(summary.get(key));
    }

    private String buildHtml(Map<String, Object> payload) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><meta charset=\"UTF-8\"><style>")
                .append("body{font-family:-apple-system,BlinkMacSystemFont,'PingFang SC','Microsoft YaHei',sans-serif;padding:24px;line-height:1.7;color:#1f2d3d;}")
                .append("h1{font-size:24px;margin:0 0 8px;}h2{font-size:18px;margin-top:20px;}pre{white-space:pre-wrap;background:#f6f8fa;padding:12px;border-radius:6px;}")
                .append(".meta{color:#6b7280;font-size:13px;margin-bottom:16px;}")
                .append("</style></head><body>");
        html.append("<h1>").append(BRANDED_REPORT_TITLE).append("</h1>")
                .append("<div class=\"meta\">报告编号：")
                .append(StrUtil.blankToDefault((String) payload.get("reportNo"), "-"))
                .append("；生成时间：")
                .append(StrUtil.blankToDefault((String) payload.get("generatedAt"), "-"))
                .append("</div>");

        List<Map<String, Object>> sections = (List<Map<String, Object>>) payload.get("sections");
        if (sections != null) {
            for (Map<String, Object> section : sections) {
                html.append("<h2>").append(section.get("index")).append(". ")
                        .append(section.get("title")).append("</h2>")
                        .append("<pre>").append(StrUtil.blankToDefault((String) section.get("content"), "未提供/数据不足"))
                        .append("</pre>");
            }
        }
        html.append("</body></html>");
        return html.toString();
    }

    private void generateDocx(Map<String, Object> payload, String docxPath) throws IOException {
        File target = new File(docxPath);
        FileUtil.mkParentDirs(target);

        try (XWPFDocument document = new XWPFDocument()) {
            document.getProperties().getCoreProperties().setTitle(BRANDED_REPORT_TITLE);
            document.getProperties().getCoreProperties().setCreator(SOFTWARE_COPYRIGHT_HOLDER);
            XWPFParagraph title = document.createParagraph();
            XWPFRun titleRun = title.createRun();
            titleRun.setText(BRANDED_REPORT_TITLE);
            titleRun.setBold(true);
            titleRun.setFontSize(16);

            XWPFParagraph meta = document.createParagraph();
            XWPFRun metaRun = meta.createRun();
            metaRun.setText("报告编号：" + StrUtil.blankToDefault((String) payload.get("reportNo"), "-"));
            metaRun.addBreak();
            metaRun.setText("生成时间：" + StrUtil.blankToDefault((String) payload.get("generatedAt"), "-"));

            List<Map<String, Object>> sections = (List<Map<String, Object>>) payload.get("sections");
            if (sections != null) {
                for (Map<String, Object> section : sections) {
                    XWPFParagraph heading = document.createParagraph();
                    XWPFRun headingRun = heading.createRun();
                    headingRun.setBold(true);
                    headingRun.setText(section.get("index") + ". " + section.get("title"));

                    XWPFParagraph body = document.createParagraph();
                    XWPFRun bodyRun = body.createRun();
                    bodyRun.setText(StrUtil.blankToDefault((String) section.get("content"), "未提供/数据不足"));
                }
            }

            try (FileOutputStream fos = new FileOutputStream(target)) {
                document.write(fos);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void generatePdf(Map<String, Object> payload, String pdfPath) throws IOException {
        File target = new File(pdfPath);
        FileUtil.mkParentDirs(target);

        try (PDDocument document = new PDDocument();
             PdfFontResource fontResource = loadPdfFont(document)) {
            PDDocumentInformation information = document.getDocumentInformation();
            information.setTitle(BRANDED_REPORT_TITLE);
            information.setSubject(REPORT_TITLE);
            information.setAuthor(SOFTWARE_COPYRIGHT_HOLDER);
            information.setCreator(SOFTWARE_NAME + " " + SOFTWARE_VERSION);

            List<PdfTextLine> lines = new ArrayList<>();
            lines.add(PdfTextLine.title(BRANDED_REPORT_TITLE));
            lines.add(PdfTextLine.body("报告编号："
                    + StrUtil.blankToDefault((String) payload.get("reportNo"), "-")));
            lines.add(PdfTextLine.body("生成时间："
                    + StrUtil.blankToDefault((String) payload.get("generatedAt"), "-")));
            lines.add(PdfTextLine.blank());

            List<Map<String, Object>> sections = (List<Map<String, Object>>) payload.get("sections");
            if (sections != null) {
                for (Map<String, Object> section : sections) {
                    lines.add(PdfTextLine.heading(section.get("index") + ". " + section.get("title")));
                    String content = StrUtil.blankToDefault((String) section.get("content"), "未提供/数据不足");
                    for (String paragraph : content.replace("\r", "").split("\n", -1)) {
                        List<String> wrapped = wrapPdfText(paragraph, 88);
                        if (wrapped.isEmpty()) {
                            lines.add(PdfTextLine.body(""));
                        } else {
                            wrapped.forEach(line -> lines.add(PdfTextLine.body(line)));
                        }
                    }
                    lines.add(PdfTextLine.blank());
                }
            }

            writePdfLines(document, fontResource.getFont(), lines);
            document.save(target);
        }
    }

    private PdfFontResource loadPdfFont(PDDocument document) throws IOException {
        List<String> candidates = new ArrayList<>();
        if (StrUtil.isNotBlank(pdfFontPath)) {
            candidates.add(pdfFontPath);
        }
        candidates.add("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc");
        candidates.add("/System/Library/Fonts/STHeiti Medium.ttc");
        candidates.add("/System/Library/Fonts/STHeiti Light.ttc");
        candidates.add("C:\\Windows\\Fonts\\msyh.ttc");
        candidates.add("C:\\Windows\\Fonts\\simhei.ttf");

        IOException lastError = null;
        for (String candidate : candidates) {
            File fontFile = new File(candidate);
            if (!fontFile.isFile()) {
                continue;
            }
            try {
                if (candidate.toLowerCase(Locale.ROOT).endsWith(".ttc")) {
                    TrueTypeCollection collection = new TrueTypeCollection(fontFile);
                    for (String fontName : Arrays.asList(
                            "WenQuanYiZenHei", "WenQuanYiZenHeiMono", "WenQuanYiZenHeiSharp",
                            "STHeitiSC-Medium", "STHeitiSC-Light",
                            "MicrosoftYaHei", "MicrosoftYaHeiUI", "SimHei")) {
                        TrueTypeFont trueTypeFont = collection.getFontByName(fontName);
                        if (trueTypeFont != null) {
                            return new PdfFontResource(
                                    PDType0Font.load(document, trueTypeFont, true), collection);
                        }
                    }
                    collection.close();
                    continue;
                }
                return new PdfFontResource(PDType0Font.load(document, fontFile), null);
            } catch (IOException ex) {
                lastError = ex;
            }
        }
        throw lastError == null
                ? new IOException("未找到可用的中文 PDF 字体")
                : new IOException("中文 PDF 字体加载失败", lastError);
    }

    private void writePdfLines(PDDocument document, PDFont font, List<PdfTextLine> lines) throws IOException {
        final float margin = 48F;
        final float pageTop = PDRectangle.A4.getHeight() - margin;
        final float pageBottom = margin;
        PDPageContentStream stream = null;
        float y = pageTop;
        try {
            for (PdfTextLine line : lines) {
                if (stream == null || y - line.getLeading() < pageBottom) {
                    if (stream != null) {
                        stream.endText();
                        stream.close();
                    }
                    PDPage page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    stream = new PDPageContentStream(document, page);
                    stream.beginText();
                    stream.newLineAtOffset(margin, pageTop);
                    y = pageTop;
                }
                stream.setFont(font, line.getFontSize());
                if (StrUtil.isNotEmpty(line.getText())) {
                    stream.showText(line.getText());
                }
                stream.newLineAtOffset(0, -line.getLeading());
                y -= line.getLeading();
            }
        } finally {
            if (stream != null) {
                stream.endText();
                stream.close();
            }
        }
    }

    private List<String> wrapPdfText(String text, int maxUnits) {
        if (StrUtil.isEmpty(text)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int units = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint)) {
                continue;
            }
            int charUnits = codePoint <= 0x7F ? 1 : 2;
            if (units + charUnits > maxUnits && line.length() > 0) {
                result.add(line.toString());
                line.setLength(0);
                units = 0;
            }
            line.appendCodePoint(codePoint);
            units += charUnits;
        }
        if (line.length() > 0) {
            result.add(line.toString());
        }
        return result;
    }

    private static final class PdfFontResource implements Closeable {

        private final PDFont font;
        private final TrueTypeCollection collection;

        private PdfFontResource(PDFont font, TrueTypeCollection collection) {
            this.font = font;
            this.collection = collection;
        }

        private PDFont getFont() {
            return font;
        }

        @Override
        public void close() throws IOException {
            if (collection != null) {
                collection.close();
            }
        }
    }

    private static final class PdfTextLine {

        private final String text;
        private final float fontSize;
        private final float leading;

        private PdfTextLine(String text, float fontSize, float leading) {
            this.text = text;
            this.fontSize = fontSize;
            this.leading = leading;
        }

        private static PdfTextLine title(String text) {
            return new PdfTextLine(text, 18F, 28F);
        }

        private static PdfTextLine heading(String text) {
            return new PdfTextLine(text, 13F, 22F);
        }

        private static PdfTextLine body(String text) {
            return new PdfTextLine(text, 10.5F, 16F);
        }

        private static PdfTextLine blank() {
            return new PdfTextLine("", 10.5F, 10F);
        }

        private String getText() {
            return text;
        }

        private float getFontSize() {
            return fontSize;
        }

        private float getLeading() {
            return leading;
        }
    }

}
