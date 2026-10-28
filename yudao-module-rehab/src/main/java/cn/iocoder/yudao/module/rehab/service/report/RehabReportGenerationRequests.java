package cn.iocoder.yudao.module.rehab.service.report;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.RehabReportGenerateRespVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.annotation.Resource;
import java.util.*;
import java.util.regex.Pattern;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. No clinical request bodies in the ledger. */
@Service
public class RehabReportGenerationRequests {
    // Private, versioned codec: global web JSON customizations must not change
    // the digest of a previously completed request after an application upgrade.
    private static final ObjectMapper FINGERPRINT_CODEC = new ObjectMapper();
    static final String FIND_SQL = "SELECT request_fingerprint,report_id,report_no,report_version,fallback_used,fallback_message FROM rehab_report_generation_request WHERE tenant_id=? AND assessment_id=? AND operator_user_id=? AND request_key_hash=? FOR UPDATE";
    static final String RESULT_SQL = "SELECT id FROM rehab_report WHERE tenant_id=? AND id=? AND assessment_id=? AND generated_by=? AND deleted=FALSE FOR UPDATE";
    private static final Pattern UUID = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private RehabReportVersionSequence versionSequence;

    public String fingerprint(Long assessmentId, String reportType, String generationMode, String note) {
        try {
            return DigestUtil.sha256Hex(FINGERPRINT_CODEC.writeValueAsBytes(Arrays.asList(
                    "rehab-report-generation-v1", assessmentId, reportType, generationMode, note == null ? "" : note)));
        } catch (JsonProcessingException ignored) {
            throw new IllegalStateException("报告请求摘要生成失败；请求正文已省略");
        }
    }

    private long requireContext(Long assessmentId, Long operator, String key) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("报告幂等账本必须与报告生成位于同一事务");
        }
        if (assessmentId == null || assessmentId <= 0 || operator == null || operator <= 0
                || key == null || !UUID.matcher(key).matches()) throw exception(REPORT_REQUEST_INVALID);
        return TenantContextHolder.getRequiredTenantId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public RehabReportGenerateRespVO findCompleted(Long assessmentId, Long operator, String key, String fingerprint) {
        long tenant = requireContext(assessmentId, operator, key);
        // All concurrent requests for this assessment acquire the same first lock.
        versionSequence.lockAssessment(assessmentId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                FIND_SQL,
                tenant, assessmentId, operator, DigestUtil.sha256Hex(key.toLowerCase(Locale.ROOT)));
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        if (!Objects.equals(fingerprint, row.get("request_fingerprint"))) throw exception(REPORT_REQUEST_CONFLICT);
        long reportId = ((Number) row.get("report_id")).longValue();
        if (jdbcTemplate.queryForList(RESULT_SQL,
                tenant, reportId, assessmentId, operator).size() != 1) throw exception(REPORT_REQUEST_RESULT_UNAVAILABLE);
        RehabReportGenerateRespVO result = new RehabReportGenerateRespVO();
        result.setId(reportId);
        result.setReportNo((String) row.get("report_no"));
        result.setReportVersion(((Number) row.get("report_version")).intValue());
        result.setFallbackUsed(Boolean.TRUE.equals(row.get("fallback_used")));
        result.setFallbackMessage((String) row.get("fallback_message"));
        return result;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCompleted(Long assessmentId, Long operator, String key, String fingerprint, RehabReportGenerateRespVO result) {
        long tenant = requireContext(assessmentId, operator, key);
        jdbcTemplate.update("INSERT INTO rehab_report_generation_request "
                        + "(tenant_id,assessment_id,operator_user_id,request_key_hash,request_fingerprint,report_id,report_no,report_version,fallback_used,fallback_message) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)", tenant, assessmentId, operator,
                DigestUtil.sha256Hex(key.toLowerCase(Locale.ROOT)), fingerprint, result.getId(), result.getReportNo(),
                result.getReportVersion(), Boolean.TRUE.equals(result.getFallbackUsed()), result.getFallbackMessage());
    }
}
