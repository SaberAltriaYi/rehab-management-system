package cn.iocoder.yudao.module.rehab.service.report;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
 * Allocates within the report creation transaction; never renumbers history.
 */
@Service
public class RehabReportVersionSequence {
    static final String LOCK_ASSESSMENT_SQL = "SELECT id FROM rehab_assessment_record WHERE tenant_id=? AND id=? AND deleted=FALSE FOR UPDATE";
    static final String LATEST_VERSION_SQL = "SELECT report_version FROM rehab_report WHERE tenant_id=? AND assessment_id=? ORDER BY report_version DESC LIMIT 1 FOR UPDATE";
    @Resource private JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAssessment(Long assessmentId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("报告版本分配必须与报告写入位于同一事务");
        }
        long tenantId = TenantContextHolder.getRequiredTenantId();
        if (assessmentId == null || assessmentId <= 0 || jdbcTemplate.queryForList(
                LOCK_ASSESSMENT_SQL,
                tenantId, assessmentId).size() != 1) {
            throw exception(ASSESSMENT_NOT_EXISTS);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int nextVersion(Long assessmentId) {
        lockAssessment(assessmentId);
        long tenantId = TenantContextHolder.getRequiredTenantId();
        // Use a locking current read on MySQL REPEATABLE READ: the caller may
        // already have an older snapshot before waiting for the assessment lock.
        // Include deleted versions so historical numbers are never reused.
        List<Integer> versions = jdbcTemplate.queryForList(
                LATEST_VERSION_SQL, Integer.class, tenantId, assessmentId);
        if (versions.isEmpty()) return 1;
        Integer latest = versions.get(0);
        if (latest == null || latest < 1 || latest == Integer.MAX_VALUE) {
            throw exception(REPORT_VERSION_SEQUENCE_INVALID);
        }
        return latest + 1;
    }
}
