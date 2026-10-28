package cn.iocoder.yudao.module.rehab.service.report;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. Synthetic H2 only. */
class RehabReportVersionSequenceTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private RehabReportVersionSequence sequence;

    @BeforeEach
    void open() {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:report_sequence_"
                + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE rehab_assessment_record(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE rehab_report(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,assessment_id BIGINT,report_version INT,deleted BOOLEAN DEFAULT FALSE,original_payload VARCHAR(64))");
        jdbc.update("INSERT INTO rehab_assessment_record VALUES(801,71,FALSE),(802,72,FALSE),(803,71,TRUE)");
        sequence = new RehabReportVersionSequence();
        ReflectionTestUtils.setField(sequence, "jdbcTemplate", jdbc);
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

    private int append() {
        return tx.execute(status -> {
            int version = sequence.nextVersion(801L);
            jdbc.update("INSERT INTO rehab_report(tenant_id,assessment_id,report_version,original_payload) VALUES(71,801,?,'SYNTHETIC')", version);
            return version;
        });
    }

    @Test
    void concurrentReportsGetDistinctIncreasingVersions() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> task = () -> {
                TenantContextHolder.setTenantId(71L);
                try { assertTrue(start.await(10, TimeUnit.SECONDS)); return append(); }
                finally { TenantContextHolder.clear(); }
            };
            Future<Integer> a = pool.submit(task), b = pool.submit(task);
            start.countDown();
            assertEquals(new HashSet<>(Arrays.asList(1, 2)), new HashSet<>(Arrays.asList(
                    a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(DISTINCT report_version) FROM rehab_report", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void deletedAndGappedHistoryIsNeverReusedOrRenumbered() {
        jdbc.update("INSERT INTO rehab_report(tenant_id,assessment_id,report_version,deleted,original_payload) VALUES"
                + "(71,801,1,FALSE,'SYNTHETIC_ORIGINAL_1'),(71,801,5,TRUE,'SYNTHETIC_ORIGINAL_5')");
        assertEquals(6, append());
        assertEquals(Arrays.asList("SYNTHETIC_ORIGINAL_1", "SYNTHETIC_ORIGINAL_5"), jdbc.queryForList(
                "SELECT original_payload FROM rehab_report WHERE report_version IN (1,5) ORDER BY report_version", String.class));
    }

    @Test
    void failedReportTransactionDoesNotConsumeAVersionOrWriteHistory() {
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            assertEquals(1, sequence.nextVersion(801L));
            jdbc.update("INSERT INTO rehab_report(tenant_id,assessment_id,report_version) VALUES(71,801,1)");
            throw new IllegalStateException("SYNTHETIC_AUDIT_FAILURE");
        }));
        assertEquals(1, append());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM rehab_report", Integer.class));
    }

    @Test
    void tenantMissingDeletedAndNonTransactionalRequestsFailClosed() {
        assertThrows(IllegalStateException.class, () -> sequence.nextVersion(801L));
        for (Long id : Arrays.asList(802L, 803L, 999L)) {
            assertThrows(ServiceException.class, () -> tx.execute(status -> sequence.nextVersion(id)));
        }
        TenantContextHolder.clear();
        assertThrows(NullPointerException.class, () -> tx.execute(status -> sequence.nextVersion(801L)));
    }

    @Test
    void invalidOrExhaustedVersionRequiresReviewAndCannotOverwriteReports() {
        for (int version : new int[] {0, -1, Integer.MAX_VALUE}) {
            jdbc.update("DELETE FROM rehab_report"); // This test's synthetic H2 rows only.
            jdbc.update("INSERT INTO rehab_report(tenant_id,assessment_id,report_version) VALUES(71,801,?)", version);
            assertThrows(ServiceException.class, this::append);
            assertEquals(version, jdbc.queryForObject("SELECT report_version FROM rehab_report", Integer.class));
        }
    }

    @Test
    void legacyCountPlusOneFixtureReproducesDuplicateVersionRisk() {
        jdbc.update("INSERT INTO rehab_report(tenant_id,assessment_id,report_version) VALUES(71,801,1),(71,801,3)");
        int legacyNext = jdbc.queryForObject("SELECT COUNT(*)+1 FROM rehab_report WHERE tenant_id=71 AND assessment_id=801 AND deleted=FALSE", Integer.class);
        assertEquals(3, legacyNext); // The old formula reuses the existing v3.
        assertEquals(4, append());
    }
}
