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
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
    private static final String SOFTWARE_COPYRIGHT_HOLDER = "杨玺龙";
    private static final String REPORT_TITLE = "康复综合评估报告";
    private static final String BRANDED_REPORT_TITLE = SOFTWARE_NAME + " " + SOFTWARE_VERSION + " - " + REPORT_TITLE;
    private static final String REPORT_TEMPLATE_RESOURCE = "/templates/rehab-assessment-report-v4.1.docx";

    @Value("${yudao.rehab.storage-path:./data/rehab}")
    private String storagePath;
    @Value("${yudao.rehab.pdf-font-path:${REHAB_PDF_FONT_PATH:}}")
    private String pdfFontPath;
    @Value("${yudao.rehab.libreoffice-path:${REHAB_LIBREOFFICE_PATH:}}")
    private String libreOfficePath;

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
    public PageResult<RehabReportPatientRespVO> getReportPatientPage(RehabReportPageReqVO reqVO,
                                                                     Long operatorUserId) {
        Set<Long> visiblePatientIds = dataPermissionService.getVisiblePatientIds(operatorUserId);
        if (visiblePatientIds != null && visiblePatientIds.isEmpty()) {
            return PageResult.empty();
        }

        Collection<Long> filteredPatientIds = visiblePatientIds;
        if (reqVO.getPatientId() != null) {
            validatePatientReadable(reqVO.getPatientId(), operatorUserId);
            filteredPatientIds = Collections.singleton(reqVO.getPatientId());
        }

        Long total = reportMapper.selectPatientCount(reqVO, filteredPatientIds);
        if (total == null || total == 0L) {
            return PageResult.empty();
        }
        long offset = (long) (reqVO.getPageNo() - 1) * reqVO.getPageSize();
        List<RehabReportPatientRespVO> list = reportMapper.selectPatientPage(
                reqVO, filteredPatientIds, offset, reqVO.getPageSize());
        return new PageResult<>(list, total);
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
        try {
            String reportDir = storagePath + File.separator + "reports" + File.separator + patient.getId();
            FileUtil.mkdir(reportDir);
            String baseName = reportNo + "_v" + reportVersion;

            String html = buildHtml(reportPayload);
            htmlPath = reportDir + File.separator + baseName + ".html";
            FileUtil.writeString(html, htmlPath, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            fallbackUsed = true;
            fallbackMessage = "报告预览生成失败，已保留结构化报告数据";
        }

        RehabReportDO updateObj = new RehabReportDO().setId(report.getId())
                .setReportNo(reportNo)
                .setReportJson(reportJson)
                .setHtmlSnapshotPath(htmlPath)
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
        if ((StrUtil.isBlank(report.getDocxPath()) || !FileUtil.exist(report.getDocxPath()))
                && StrUtil.isNotBlank(report.getReportJson())) {
            Map<String, Object> payload = JsonUtils.parseObject(report.getReportJson(), Map.class);
            if (payload != null) {
                String reportDir = storagePath + File.separator + "reports" + File.separator + report.getPatientId();
                FileUtil.mkdir(reportDir);
                String docxPath = reportDir + File.separator + report.getReportNo()
                        + "_v" + report.getReportVersion() + ".docx";
                try {
                    generateDocx(payload, docxPath);
                    report.setDocxPath(docxPath);
                    reportMapper.updateById(new RehabReportDO().setId(id).setDocxPath(docxPath));
                } catch (IOException ignored) {
                    // 下方统一返回“DOCX 文件不存在”，避免向前端暴露模板或文件系统细节
                }
            }
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
                FileUtil.mkdir(reportDir);
                String pdfPath = reportDir + File.separator + report.getReportNo()
                        + "_v" + report.getReportVersion() + ".pdf";
                try {
                    String docxPath = report.getDocxPath();
                    if (StrUtil.isBlank(docxPath) || !FileUtil.exist(docxPath)) {
                        docxPath = reportDir + File.separator + report.getReportNo()
                                + "_v" + report.getReportVersion() + ".docx";
                        generateDocx(payload, docxPath);
                        report.setDocxPath(docxPath);
                        reportMapper.updateById(new RehabReportDO().setId(id).setDocxPath(docxPath));
                    }
                    generatePdf(payload, docxPath, pdfPath);
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

        Map<String, Object> patientPayload = new LinkedHashMap<>();
        patientPayload.put("patientNo", patient.getPatientNo());
        patientPayload.put("name", patient.getName());
        patientPayload.put("gender", patient.getGender());
        patientPayload.put("birthday", patient.getBirthday());
        patientPayload.put("age", patient.getAge());
        patientPayload.put("heightCm", patient.getHeightCm());
        patientPayload.put("weightKg", patient.getWeightKg());
        patientPayload.put("bmi", patient.getBmi());
        patientPayload.put("dominantSide", patient.getDominantSide());
        patientPayload.put("sportType", patient.getSportType());
        patientPayload.put("schoolOrCompany", patient.getSchoolOrCompany());
        patientPayload.put("chiefComplaint", patient.getChiefComplaint());
        patientPayload.put("painArea", patient.getPainArea());
        patientPayload.put("painScore", patient.getPainScore());
        patientPayload.put("medicalHistory", patient.getMedicalHistory());
        patientPayload.put("injuryHistory", patient.getInjuryHistory());
        patientPayload.put("trainingHistory", patient.getTrainingHistory());

        Map<String, Object> episodePayload = new LinkedHashMap<>();
        episodePayload.put("episodeNo", episode == null ? null : episode.getEpisodeNo());
        episodePayload.put("episodeType", episode == null ? null : episode.getEpisodeType());
        episodePayload.put("startDate", episode == null ? null : episode.getStartDate());

        Map<String, Object> assessmentPayload = new LinkedHashMap<>();
        assessmentPayload.put("assessmentNo", assessment.getAssessmentNo());
        assessmentPayload.put("assessmentType", assessment.getAssessmentType());
        assessmentPayload.put("assessmentDate", assessment.getAssessmentDate());
        AdminUserRespDTO assessor = assessment.getAssessorUserId() == null
                ? null : adminUserApi.getUser(assessment.getAssessorUserId());
        assessmentPayload.put("assessorName", assessor == null ? null : assessor.getNickname());
        assessmentPayload.put("locationType", assessment.getLocationType());
        assessmentPayload.put("chiefFocus", assessment.getChiefFocus());
        assessmentPayload.put("painScore", assessment.getPainScore());
        assessmentPayload.put("redFlagNotes", assessment.getRedFlagNotes());
        assessmentPayload.put("sourceSummary", assessment.getSourceSummary());
        assessmentPayload.put("rawInputStatus", assessment.getRawInputStatus());

        Map<String, Object> rawModules = new LinkedHashMap<>();
        RehabAssessmentConstants.MODULE_TYPES.forEach(moduleType -> {
            RehabAssessmentModuleDataDO module = moduleMap.get(moduleType);
            if (module == null) {
                return;
            }
            Map<String, Object> modulePayload = new LinkedHashMap<>();
            modulePayload.put("moduleStatus", module.getModuleStatus());
            modulePayload.put("sourceType", module.getSourceType());
            modulePayload.put("version", module.getVersion());
            Map<String, Object> parsed = StrUtil.isBlank(module.getDataJson())
                    ? Collections.emptyMap() : JsonUtils.parseObject(module.getDataJson(), Map.class);
            modulePayload.put("rawData", removeDerivedReportContent(parsed));
            rawModules.put(moduleType, modulePayload);
        });

        List<Map<String, Object>> sections = new ArrayList<>();
        sections.add(section(1, "基本信息与生长发育", flattenForReport(patientPayload)));
        sections.add(section(2, "红旗、疼痛与安全原始记录", flattenForReport(assessmentPayload)));
        sections.add(section(3, "数据完整度与测量条件", buildModuleStatusText(rawModules)));
        sections.add(section(4, "影像与结构性证据", moduleRawContent(rawModules, RehabAssessmentConstants.MODULE_OBSERVATION)));
        sections.add(section(5, "静态体态原始数据", moduleRawContent(rawModules, RehabAssessmentConstants.MODULE_STATIC)));
        sections.add(section(6, "NASM-CES 原始数据", moduleRawContent(rawModules, RehabAssessmentConstants.MODULE_NASM)));
        sections.add(section(7, "SFMA 原始数据", moduleRawContent(rawModules, RehabAssessmentConstants.MODULE_SFMA)));
        sections.add(section(8, "FMS 原始数据", moduleRawContent(rawModules, RehabAssessmentConstants.MODULE_FMS)));
        sections.add(section(9, "YBT-LQ / YBT-UQ 原始数据", moduleRawContent(rawModules, RehabAssessmentConstants.MODULE_YBT)));
        sections.add(section(10, "力量、耐力、专项及 OpenCap 原始数据",
                joinRawContents(moduleRawContent(rawModules, RehabAssessmentConstants.MODULE_BODY_COMP),
                        moduleRawContent(rawModules, RehabAssessmentConstants.MODULE_OPENCAP))));
        sections.add(section(11, "结局量表原始数据", moduleRawContent(rawModules, RehabAssessmentConstants.MODULE_OUTCOME_SCALE)));
        sections.add(section(12, "综合评估原始数据", moduleRawContent(rawModules, RehabAssessmentConstants.MODULE_COMPREHENSIVE)));
        sections.add(section(13, "临床推理与假设", ""));
        sections.add(section(14, "风险分域与管理等级", ""));
        sections.add(section(15, "优先级目标与 KPI", ""));
        sections.add(section(16, "分阶段训练处方", ""));
        sections.add(section(17, "复评、转诊与结案", ""));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportNo", reportNo);
        payload.put("generatedAt", LocalDateTime.now().toString());
        payload.put("patient", patientPayload);
        payload.put("episode", episodePayload);
        payload.put("assessment", assessmentPayload);
        payload.put("rawModules", rawModules);
        payload.put("missingModules", missingModules);
        payload.put("sections", sections);
        payload.put("evidenceRefs", new ArrayList<>(rawModules.keySet()));
        payload.put("contentPolicy", "raw-data-only");
        return payload;
    }

    private Map<String, Object> section(int index, String title, String content) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("index", index);
        map.put("title", title);
        map.put("content", content);
        return map;
    }

    private Object removeDerivedReportContent(Object value) {
        if (value instanceof Map) {
            Map<?, ?> source = (Map<?, ?>) value;
            Map<String, Object> cleaned = new LinkedHashMap<>();
            source.forEach((key, item) -> {
                String keyText = String.valueOf(key);
                if (!isDerivedReportKey(keyText)) {
                    cleaned.put(keyText, removeDerivedReportContent(item));
                }
            });
            return cleaned;
        }
        if (value instanceof List) {
            return ((List<?>) value).stream().map(this::removeDerivedReportContent).collect(Collectors.toList());
        }
        return value;
    }

    private boolean isDerivedReportKey(String key) {
        String normalized = StrUtil.blankToDefault(key, "").toLowerCase(Locale.ROOT).replace("_", "");
        return Arrays.asList("reportmapping", "conclusion", "interpretation", "analysis",
                        "clinicalmeaning", "trainingdirection", "recommendation", "suggestion", "risklevel",
                        "riskflag", "mechanism", "priorityinterventiondraft", "confidence", "judgement")
                .stream().anyMatch(normalized::contains)
                || Arrays.asList("报告映射", "结论", "解读", "分析", "临床意义", "训练方向", "建议",
                        "风险等级", "风险提示", "机制", "优先干预", "置信度", "判定")
                .stream().anyMatch(key::contains);
    }

    @SuppressWarnings("unchecked")
    private String moduleRawContent(Map<String, Object> rawModules, String moduleType) {
        Object module = rawModules.get(moduleType);
        return module instanceof Map ? flattenForReport((Map<String, Object>) module) : "";
    }

    private String joinRawContents(String... contents) {
        return Arrays.stream(contents).filter(StrUtil::isNotBlank).collect(Collectors.joining("\n"));
    }

    private String buildModuleStatusText(Map<String, Object> rawModules) {
        return rawModules.entrySet().stream()
                .map(entry -> entry.getKey() + "：已记录")
                .collect(Collectors.joining("\n"));
    }

    private String flattenForReport(Map<String, Object> value) {
        List<String> lines = new ArrayList<>();
        flattenValue("", value, lines);
        return String.join("\n", lines);
    }

    private void flattenValue(String path, Object value, List<String> lines) {
        if (value instanceof Map) {
            ((Map<?, ?>) value).forEach((key, item) ->
                    flattenValue(StrUtil.isBlank(path) ? String.valueOf(key) : path + "." + key, item, lines));
            return;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            for (int index = 0; index < list.size(); index++) {
                flattenValue(path + "[" + index + "]", list.get(index), lines);
            }
            return;
        }
        if (value != null && StrUtil.isNotBlank(String.valueOf(value))) {
            lines.add(path + "：" + value);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToMap(Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        return (Map<String, Object>) value;
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

    private String buildHtml(Map<String, Object> payload) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><meta charset=\"UTF-8\"><style>")
                .append("body{font-family:'Noto Sans CJK SC','PingFang SC','Microsoft YaHei',sans-serif;padding:24px;line-height:1.7;color:#222;}")
                .append("h1{font-size:24px;margin:0 0 8px;}h2{font-size:18px;margin-top:20px;border-bottom:2px solid #2b2b2b;padding-bottom:6px;}")
                .append("pre{white-space:pre-wrap;background:#f2f2f2;padding:12px;border:1px solid #d8d8d8;margin:0;min-height:22px;}")
                .append(".meta{color:#555;font-size:13px;margin-bottom:16px;}")
                .append("</style></head><body>");
        html.append("<h1>").append(BRANDED_REPORT_TITLE).append("</h1>")
                .append("<div class=\"meta\">报告编号：")
                .append(escapeHtml(payload.get("reportNo")))
                .append("；生成时间：")
                .append(escapeHtml(payload.get("generatedAt")))
                .append("</div>");

        List<Map<String, Object>> sections = (List<Map<String, Object>>) payload.get("sections");
        if (sections != null) {
            for (Map<String, Object> section : sections) {
                html.append("<h2>").append(section.get("index")).append(". ")
                        .append(escapeHtml(section.get("title"))).append("</h2>")
                        .append("<pre>").append(escapeHtml(section.get("content")))
                        .append("</pre>");
            }
        }
        html.append("</body></html>");
        return html.toString();
    }

    private String escapeHtml(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void generateDocx(Map<String, Object> payload, String docxPath) throws IOException {
        File target = new File(docxPath);
        FileUtil.mkParentDirs(target);

        try (InputStream templateStream = RehabReportServiceImpl.class.getResourceAsStream(REPORT_TEMPLATE_RESOURCE)) {
            if (templateStream == null) {
                throw new IOException("评估报告模板资源不存在");
            }
            try (XWPFDocument document = new XWPFDocument(templateStream)) {
                document.getProperties().getCoreProperties().setTitle(BRANDED_REPORT_TITLE);
                document.getProperties().getCoreProperties().setCreator(SOFTWARE_COPYRIGHT_HOLDER);
                blankTemplatePlaceholders(document);
                fillTemplateBasicInformation(document, payload);
                fillTemplateModuleStatus(document, payload);
                fillTemplateRawModules(document, payload);
                fillTemplateEvidenceIndex(document, payload);
                applyCompatibleCjkFonts(document);

                try (FileOutputStream fos = new FileOutputStream(target)) {
                    document.write(fos);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void fillTemplateBasicInformation(XWPFDocument document, Map<String, Object> payload) {
        Map<String, Object> patient = (Map<String, Object>) payload.getOrDefault("patient", Collections.emptyMap());
        Map<String, Object> assessment = (Map<String, Object>) payload.getOrDefault("assessment", Collections.emptyMap());
        Map<String, Object> rawModules = (Map<String, Object>) payload.getOrDefault("rawModules", Collections.emptyMap());
        List<XWPFTable> tables = document.getTables();

        String genderAge = joinNonBlank(" / ", formatGender(patient.get("gender")), valueText(patient.get("age")));
        String heightWeight = joinNonBlank(" / ", withUnit(patient.get("heightCm"), "cm"),
                withUnit(patient.get("weightKg"), "kg"));
        String assessmentDate = valueText(assessment.get("assessmentDate"));
        String assessmentType = formatAssessmentType(assessment.get("assessmentType"));
        String tools = rawModules.keySet().stream().map(this::formatModuleName).collect(Collectors.joining("、"));

        setTableCellText(tables, 0, 0, 1, valueText(patient.get("name")));
        setTableCellText(tables, 0, 1, 1, genderAge);
        setTableCellText(tables, 0, 2, 1, heightWeight);
        setTableCellText(tables, 0, 3, 1, assessmentType);
        setTableCellText(tables, 0, 4, 1, assessmentDate);
        setTableCellText(tables, 0, 5, 1, valueText(patient.get("chiefComplaint")));
        setTableCellText(tables, 0, 6, 1, tools);
        setTableCellText(tables, 0, 8, 1, valueText(assessment.get("assessorName")));

        setTableCellText(tables, 5, 1, 1, valueText(patient.get("name")));
        setTableCellText(tables, 5, 2, 1, genderAge);
        setTableCellText(tables, 5, 3, 1, heightWeight);
        setTableCellText(tables, 5, 4, 1, withUnit(patient.get("bmi"), "kg/m²"));
        setTableCellText(tables, 5, 5, 1, valueText(patient.get("dominantSide")));
        setTableCellText(tables, 5, 6, 1,
                joinNonBlank(" / ", valueText(patient.get("schoolOrCompany")), valueText(patient.get("sportType"))));
        setTableCellText(tables, 5, 7, 1, valueText(patient.get("chiefComplaint")));
        setTableCellText(tables, 5, 8, 1, valueText(patient.get("trainingHistory")));
        setTableCellText(tables, 5, 9, 1, valueText(patient.get("injuryHistory")));
        setTableCellText(tables, 5, 10, 1, valueText(patient.get("medicalHistory")));

        setTableCellText(tables, 8, 8, 1, valueText(assessment.get("redFlagNotes")));
        setTableCellText(tables, 9, 1, 0, valueText(patient.get("painArea")));
        setTableCellText(tables, 9, 1, 2,
                valueText(firstNonNull(assessment.get("painScore"), patient.get("painScore"))));
    }

    @SuppressWarnings("unchecked")
    private void fillTemplateModuleStatus(XWPFDocument document, Map<String, Object> payload) {
        Map<String, Object> rawModules = (Map<String, Object>) payload.getOrDefault("rawModules", Collections.emptyMap());
        List<XWPFTable> tables = document.getTables();
        setTableCellText(tables, 10, 1, 1, rawModules.containsKey(RehabAssessmentConstants.MODULE_STATIC) ? "已提供" : "");
        setTableCellText(tables, 10, 2, 1, rawModules.containsKey(RehabAssessmentConstants.MODULE_STATIC) ? "已提供" : "");
        setTableCellText(tables, 10, 4, 1, rawModules.containsKey(RehabAssessmentConstants.MODULE_NASM) ? "已提供" : "");
        setTableCellText(tables, 10, 5, 1, rawModules.containsKey(RehabAssessmentConstants.MODULE_SFMA) ? "已提供" : "");
        setTableCellText(tables, 10, 6, 1, rawModules.containsKey(RehabAssessmentConstants.MODULE_FMS) ? "已提供" : "");
        setTableCellText(tables, 10, 7, 1, rawModules.containsKey(RehabAssessmentConstants.MODULE_YBT) ? "已提供" : "");
        setTableCellText(tables, 10, 8, 1, rawModules.containsKey(RehabAssessmentConstants.MODULE_YBT) ? "已提供" : "");
        setTableCellText(tables, 10, 11, 1, rawModules.containsKey(RehabAssessmentConstants.MODULE_OPENCAP) ? "已提供" : "");
    }

    @SuppressWarnings("unchecked")
    private void fillTemplateRawModules(XWPFDocument document, Map<String, Object> payload) {
        Map<String, Object> rawModules = (Map<String, Object>) payload.getOrDefault("rawModules", Collections.emptyMap());
        Map<String, Integer> tableMapping = new LinkedHashMap<>();
        tableMapping.put(RehabAssessmentConstants.MODULE_BODY_COMP, 6);
        tableMapping.put(RehabAssessmentConstants.MODULE_OBSERVATION, 15);
        tableMapping.put(RehabAssessmentConstants.MODULE_STATIC, 18);
        tableMapping.put(RehabAssessmentConstants.MODULE_NASM, 21);
        tableMapping.put(RehabAssessmentConstants.MODULE_SFMA, 25);
        tableMapping.put(RehabAssessmentConstants.MODULE_FMS, 27);
        tableMapping.put(RehabAssessmentConstants.MODULE_YBT, 29);
        tableMapping.put(RehabAssessmentConstants.MODULE_OPENCAP, 35);
        tableMapping.put(RehabAssessmentConstants.MODULE_COMPREHENSIVE, 34);
        tableMapping.put(RehabAssessmentConstants.MODULE_OUTCOME_SCALE, 47);
        tableMapping.forEach((moduleType, tableIndex) -> {
            Object value = rawModules.get(moduleType);
            String content = value instanceof Map ? flattenForReport((Map<String, Object>) value) : "";
            fillRawDataBlock(document.getTables(), tableIndex, formatModuleName(moduleType), content);
        });
    }

    @SuppressWarnings("unchecked")
    private void fillTemplateEvidenceIndex(XWPFDocument document, Map<String, Object> payload) {
        Map<String, Object> rawModules = (Map<String, Object>) payload.getOrDefault("rawModules", Collections.emptyMap());
        String metadata = rawModules.entrySet().stream().map(entry -> {
            Map<String, Object> module = entry.getValue() instanceof Map
                    ? (Map<String, Object>) entry.getValue() : Collections.emptyMap();
            return formatModuleName(entry.getKey()) + "：sourceType=" + valueText(module.get("sourceType"))
                    + "；version=" + valueText(module.get("version"));
        }).collect(Collectors.joining("\n"));
        fillRawDataBlock(document.getTables(), 48, "评估数据来源索引", metadata);
    }

    private void blankTemplatePlaceholders(XWPFDocument document) {
        document.getParagraphs().forEach(this::blankParagraphPlaceholders);
        document.getTables().forEach(table -> table.getRows().forEach(row ->
                uniqueCells(row).forEach(cell -> cell.getParagraphs().forEach(this::blankParagraphPlaceholders))));
    }

    private void blankParagraphPlaceholders(XWPFParagraph paragraph) {
        String text = paragraph.getText();
        if (StrUtil.isBlank(text) || !text.contains("【")) {
            return;
        }
        setParagraphText(paragraph, text.replaceAll("【[^】]*】", ""));
    }

    private void fillRawDataBlock(List<XWPFTable> tables, int tableIndex, String moduleName, String content) {
        if (tableIndex < 0 || tableIndex >= tables.size()) {
            return;
        }
        XWPFTable table = tables.get(tableIndex);
        if (table.getNumberOfRows() == 0) {
            return;
        }
        setUniqueRowCells(table.getRow(0), moduleName + "原始数据（仅记录，不含自动解读）", "");
        if (table.getNumberOfRows() > 1) {
            setUniqueRowCells(table.getRow(1), "原始字段", "原始值");
        }
        if (table.getNumberOfRows() > 2) {
            setUniqueRowCells(table.getRow(2), "原始记录", content);
        }
        for (int rowIndex = 3; rowIndex < table.getNumberOfRows(); rowIndex++) {
            setUniqueRowCells(table.getRow(rowIndex), "", "");
        }
    }

    private void setUniqueRowCells(XWPFTableRow row, String firstText, String lastText) {
        List<XWPFTableCell> cells = uniqueCells(row);
        if (cells.isEmpty()) {
            return;
        }
        setCellText(cells.get(0), firstText);
        for (int index = 1; index < cells.size() - 1; index++) {
            setCellText(cells.get(index), "");
        }
        if (cells.size() > 1) {
            setCellText(cells.get(cells.size() - 1), lastText);
        } else if (StrUtil.isNotBlank(lastText)) {
            setCellText(cells.get(0), firstText + "\n" + lastText);
        }
    }

    private List<XWPFTableCell> uniqueCells(XWPFTableRow row) {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<XWPFTableCell> cells = new ArrayList<>();
        for (XWPFTableCell cell : row.getTableCells()) {
            if (seen.add(cell.getCTTc())) {
                cells.add(cell);
            }
        }
        return cells;
    }

    private void setTableCellText(List<XWPFTable> tables, int tableIndex, int rowIndex, int cellIndex, String text) {
        if (tableIndex < 0 || tableIndex >= tables.size()) {
            return;
        }
        XWPFTable table = tables.get(tableIndex);
        if (rowIndex < 0 || rowIndex >= table.getNumberOfRows()) {
            return;
        }
        List<XWPFTableCell> cells = uniqueCells(table.getRow(rowIndex));
        if (cellIndex < 0 || cellIndex >= cells.size()) {
            return;
        }
        setCellText(cells.get(cellIndex), text);
    }

    private void setCellText(XWPFTableCell cell, String text) {
        List<XWPFParagraph> paragraphs = cell.getParagraphs();
        XWPFParagraph paragraph = paragraphs.isEmpty() ? cell.addParagraph() : paragraphs.get(0);
        for (int index = paragraphs.size() - 1; index > 0; index--) {
            cell.removeParagraph(index);
        }
        setParagraphText(paragraph, StrUtil.nullToEmpty(text));
    }

    private void setParagraphText(XWPFParagraph paragraph, String text) {
        CTRPr runProperties = null;
        if (!paragraph.getRuns().isEmpty() && paragraph.getRuns().get(0).getCTR().getRPr() != null) {
            runProperties = (CTRPr) paragraph.getRuns().get(0).getCTR().getRPr().copy();
        }
        for (int index = paragraph.getRuns().size() - 1; index >= 0; index--) {
            paragraph.removeRun(index);
        }
        XWPFRun run = paragraph.createRun();
        if (runProperties != null) {
            run.getCTR().setRPr(runProperties);
        }
        String[] lines = StrUtil.nullToEmpty(text).replace("\r", "").split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                run.addBreak();
            }
            run.setText(lines[index]);
        }
    }

    private void applyCompatibleCjkFonts(XWPFDocument document) {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String fontName = osName.contains("mac") ? "Arial Unicode MS"
                : osName.contains("win") ? "Microsoft YaHei" : "Noto Sans CJK SC";
        document.getParagraphs().forEach(paragraph -> applyParagraphFont(paragraph, fontName));
        document.getTables().forEach(table -> table.getRows().forEach(row -> uniqueCells(row).forEach(cell ->
                cell.getParagraphs().forEach(paragraph -> applyParagraphFont(paragraph, fontName)))));
        document.getHeaderList().forEach(header -> header.getParagraphs()
                .forEach(paragraph -> applyParagraphFont(paragraph, fontName)));
        document.getFooterList().forEach(footer -> footer.getParagraphs()
                .forEach(paragraph -> applyParagraphFont(paragraph, fontName)));
    }

    private void applyParagraphFont(XWPFParagraph paragraph, String fontName) {
        paragraph.getRuns().forEach(run -> {
            run.setFontFamily(fontName);
            CTRPr properties = run.getCTR().getRPr() == null ? run.getCTR().addNewRPr() : run.getCTR().getRPr();
            CTFonts fonts = properties.sizeOfRFontsArray() == 0
                    ? properties.addNewRFonts() : properties.getRFontsArray(0);
            fonts.setAscii(fontName);
            fonts.setHAnsi(fontName);
            fonts.setEastAsia(fontName);
            fonts.setCs(fontName);
        });
    }

    private String valueText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Object firstNonNull(Object first, Object second) {
        return first == null ? second : first;
    }

    private String withUnit(Object value, String unit) {
        return value == null || StrUtil.isBlank(String.valueOf(value)) ? "" : value + unit;
    }

    private String joinNonBlank(String separator, String... values) {
        return Arrays.stream(values).filter(StrUtil::isNotBlank).collect(Collectors.joining(separator));
    }

    private String formatGender(Object value) {
        return ObjUtil.equals(value, 1) ? "男" : ObjUtil.equals(value, 2) ? "女" : valueText(value);
    }

    private String formatAssessmentType(Object value) {
        Map<String, String> labels = new HashMap<>();
        labels.put(RehabAssessmentConstants.TYPE_INITIAL, "初评");
        labels.put(RehabAssessmentConstants.TYPE_FOLLOWUP, "复评");
        labels.put(RehabAssessmentConstants.TYPE_DISCHARGE, "结案");
        labels.put(RehabAssessmentConstants.TYPE_COMPREHENSIVE_ASSESSMENT, "综合评估");
        return labels.getOrDefault(valueText(value), valueText(value));
    }

    private String formatModuleName(String moduleType) {
        Map<String, String> labels = new HashMap<>();
        labels.put(RehabAssessmentConstants.MODULE_STATIC, "静态体态");
        labels.put(RehabAssessmentConstants.MODULE_BODY_COMP, "身体成分/生长发育");
        labels.put(RehabAssessmentConstants.MODULE_NASM, "NASM-CES");
        labels.put(RehabAssessmentConstants.MODULE_SFMA, "SFMA");
        labels.put(RehabAssessmentConstants.MODULE_FMS, "FMS");
        labels.put(RehabAssessmentConstants.MODULE_YBT, "YBT");
        labels.put(RehabAssessmentConstants.MODULE_OPENCAP, "OpenCap");
        labels.put(RehabAssessmentConstants.MODULE_OBSERVATION, "观察/影像");
        labels.put(RehabAssessmentConstants.MODULE_OUTCOME_SCALE, "结局量表");
        labels.put(RehabAssessmentConstants.MODULE_COMPREHENSIVE, "综合评估");
        return labels.getOrDefault(moduleType, moduleType);
    }

    @SuppressWarnings("unchecked")
    private void generatePdf(Map<String, Object> payload, String docxPath, String pdfPath) throws IOException {
        File target = new File(pdfPath);
        FileUtil.mkParentDirs(target);
        if (convertDocxToPdf(docxPath, target)) {
            return;
        }

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
                    String content = StrUtil.nullToEmpty((String) section.get("content"));
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

    private boolean convertDocxToPdf(String docxPath, File target) {
        if (StrUtil.isBlank(docxPath) || !FileUtil.exist(docxPath)) {
            return false;
        }
        if ("disabled".equalsIgnoreCase(libreOfficePath)) {
            return false;
        }
        List<String> candidates = new ArrayList<>();
        if (StrUtil.isNotBlank(libreOfficePath)) {
            candidates.add(libreOfficePath);
        }
        candidates.add("/usr/bin/libreoffice");
        candidates.add("/usr/bin/soffice");
        candidates.add("/Applications/LibreOffice.app/Contents/MacOS/soffice");
        candidates.add("libreoffice");
        candidates.add("soffice");

        for (String candidate : candidates) {
            Path profile = null;
            File processLog = null;
            try {
                if (candidate.contains(File.separator) && !new File(candidate).canExecute()) {
                    continue;
                }
                profile = Files.createTempDirectory("rehab-report-libreoffice-");
                processLog = File.createTempFile("rehab-report-convert-", ".log");
                FileUtil.del(target);
                ProcessBuilder builder = new ProcessBuilder(candidate,
                        "-env:UserInstallation=" + profile.toUri(),
                        "--headless", "--invisible", "--nologo", "--nodefault", "--norestore", "--nolockcheck",
                        "--convert-to", "pdf:writer_pdf_Export",
                        "--outdir", target.getParentFile().getAbsolutePath(), new File(docxPath).getAbsolutePath());
                builder.redirectErrorStream(true);
                builder.redirectOutput(processLog);
                Process process = builder.start();
                boolean finished = process.waitFor(90, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                }
                if (finished && process.exitValue() == 0 && target.isFile() && target.length() > 0) {
                    return true;
                }
            } catch (Exception ignored) {
                // 尝试下一候选；最终使用内置 PDF 渲染，不向日志输出患者内容或转换命令。
            } finally {
                if (profile != null) {
                    FileUtil.del(profile.toFile());
                }
                if (processLog != null) {
                    FileUtil.del(processLog);
                }
            }
        }
        return false;
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
