package cn.iocoder.yudao.module.rehab.service.report;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.RehabReportGenerateRespVO;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. Synthetic H2 transactions only. */
class RehabReportGenerationRequestsTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private RehabReportVersionSequence sequence;
    private RehabReportGenerationRequests requests;
    private final AtomicLong ids = new AtomicLong();
    private static final String KEY = "019f1234-abcd-4abc-8abc-0123456789ab";

    @BeforeEach
    void open() {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:report_requests_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE rehab_assessment_record(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE rehab_report(id BIGINT PRIMARY KEY,tenant_id BIGINT,assessment_id BIGINT,generated_by BIGINT,report_version INT,deleted BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE synthetic_audit(report_id BIGINT)");
        jdbc.execute("CREATE TABLE rehab_report_generation_request(tenant_id BIGINT,assessment_id BIGINT,operator_user_id BIGINT,request_key_hash CHAR(64),"
                + "request_fingerprint CHAR(64),report_id BIGINT,report_no VARCHAR(64),report_version INT,fallback_used BOOLEAN,fallback_message VARCHAR(256),"
                + "PRIMARY KEY(tenant_id,assessment_id,operator_user_id,request_key_hash))");
        jdbc.update("INSERT INTO rehab_assessment_record VALUES(801,71,FALSE),(802,72,FALSE)");
        sequence = new RehabReportVersionSequence();
        ReflectionTestUtils.setField(sequence, "jdbcTemplate", jdbc);
        requests = new RehabReportGenerationRequests();
        ReflectionTestUtils.setField(requests, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(requests, "versionSequence", sequence);
        TenantContextHolder.setTenantId(71L);
    }

    @AfterEach
    void close() {
        TenantContextHolder.clear();
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (java.sql.Statement statement = connection.createStatement()) { statement.execute("SHUTDOWN"); }
            return null;
        });
    }

    private RehabReportGenerateRespVO generate(Long assessment, Long operator, String key, String note, boolean fail) {
        return tx.execute(status -> {
            String fingerprint = requests.fingerprint(assessment, "comprehensive", "auto", note);
            RehabReportGenerateRespVO old = requests.findCompleted(assessment, operator, key, fingerprint);
            if (old != null) return old;
            int version = sequence.nextVersion(assessment);
            long id = ids.incrementAndGet();
            jdbc.update("INSERT INTO rehab_report(id,tenant_id,assessment_id,generated_by,report_version) VALUES(?,?,?,?,?)",
                    id, TenantContextHolder.getRequiredTenantId(), assessment, operator, version);
            jdbc.update("INSERT INTO synthetic_audit VALUES(?)", id);
            RehabReportGenerateRespVO result = new RehabReportGenerateRespVO();
            result.setId(id); result.setReportNo("SYNTHETIC-" + id); result.setReportVersion(version);
            result.setFallbackUsed(true); result.setFallbackMessage("SYNTHETIC_PREVIEW_UNAVAILABLE");
            requests.recordCompleted(assessment, operator, key, fingerprint, result);
            if (fail) throw new IllegalStateException("SYNTHETIC_FINAL_AUDIT_FAILURE");
            return result;
        });
    }

    @Test
    void retryReturnsExactOriginalMetadataAndDoesNotRepeatReportOrAudit() {
        assertEquals("4a2bfee7efb3250cc4f7c5f636c206cc18676ed7b49f91016900bcc22dea9c37",
                requests.fingerprint(801L, "comprehensive", "auto", null));
        assertEquals(requests.fingerprint(801L, "comprehensive", "auto", null),
                requests.fingerprint(801L, "comprehensive", "auto", ""));
        RehabReportGenerateRespVO first = generate(801L, 51L, KEY, "SYNTHETIC_NOTE", false);
        RehabReportGenerateRespVO retry = generate(801L, 51L, KEY.toUpperCase(Locale.ROOT), "SYNTHETIC_NOTE", false);
        assertEquals(first, retry);
        assertTrue(retry.getFallbackUsed());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM rehab_report", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_audit", Integer.class));
        String hash = jdbc.queryForObject("SELECT request_key_hash FROM rehab_report_generation_request", String.class);
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertNotEquals(KEY, hash);
    }

    @Test
    void sameKeyWithChangedParametersRejectsInsteadOfSilentlyGeneratingAgain() {
        generate(801L, 51L, KEY, "SYNTHETIC_A", false);
        ServiceException error = assertThrows(ServiceException.class, () -> generate(801L, 51L, KEY, "SYNTHETIC_B", false));
        assertEquals(REPORT_REQUEST_CONFLICT.getCode(), error.getCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM rehab_report", Integer.class));
    }

    @Test
    void explicitNewKeyAndDifferentOperatorOrTenantHaveIndependentResults() {
        assertEquals(1, generate(801L, 51L, KEY, null, false).getReportVersion());
        assertEquals(2, generate(801L, 51L, UUID.randomUUID().toString(), null, false).getReportVersion());
        assertEquals(3, generate(801L, 52L, KEY, null, false).getReportVersion());
        TenantContextHolder.setTenantId(72L);
        assertEquals(1, generate(802L, 51L, KEY, null, false).getReportVersion());
        assertThrows(ServiceException.class, () -> generate(801L, 51L, KEY, null, false));
    }

    @Test
    void concurrentRetryHasOneReportOneAuditAndOneCompletedResult() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Long> task = () -> {
                TenantContextHolder.setTenantId(71L);
                try { assertTrue(start.await(10, TimeUnit.SECONDS)); return generate(801L, 51L, KEY, null, false).getId(); }
                finally { TenantContextHolder.clear(); }
            };
            Future<Long> a = pool.submit(task), b = pool.submit(task);
            start.countDown();
            assertEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM rehab_report", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_audit", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM rehab_report_generation_request", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void failedTransactionRollsBackReportAuditAndLedgerAndAllowsSameKeyRetry() {
        assertThrows(IllegalStateException.class, () -> generate(801L, 51L, KEY, null, true));
        for (String table : Arrays.asList("rehab_report", "synthetic_audit", "rehab_report_generation_request")) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        }
        assertEquals(1, generate(801L, 51L, KEY, null, false).getReportVersion());
    }

    @Test
    void unavailableOrReassignedResultFailsClosedWithoutReplacingIt() {
        RehabReportGenerateRespVO first = generate(801L, 51L, KEY, null, false);
        jdbc.update("UPDATE rehab_report SET deleted=TRUE WHERE id=?", first.getId());
        ServiceException error = assertThrows(ServiceException.class, () -> generate(801L, 51L, KEY, null, false));
        assertEquals(REPORT_REQUEST_RESULT_UNAVAILABLE.getCode(), error.getCode());
        jdbc.update("UPDATE rehab_report SET deleted=FALSE,assessment_id=802 WHERE id=?", first.getId());
        assertThrows(ServiceException.class, () -> generate(801L, 51L, KEY, null, false));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM rehab_report", Integer.class));
    }

    @Test
    void invalidKeysActorsAndCallsOutsideTransactionAreRejected() {
        for (String key : Arrays.asList("", "not-a-uuid", "00000000-0000-0000-0000-000000000000")) {
            assertThrows(ServiceException.class, () -> generate(801L, 51L, key, null, false));
        }
        assertThrows(ServiceException.class, () -> generate(801L, null, KEY, null, false));
        assertThrows(IllegalStateException.class, () -> requests.findCompleted(801L, 51L, KEY, "x"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM rehab_report_generation_request", Integer.class));
    }
}
