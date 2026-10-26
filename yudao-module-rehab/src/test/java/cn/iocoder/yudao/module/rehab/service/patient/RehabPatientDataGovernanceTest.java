package cn.iocoder.yudao.module.rehab.service.patient;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientDataGovernanceReqVO;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.log.RehabAuditLogService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.*;

import static cn.iocoder.yudao.module.rehab.enums.RehabPatientDataOrigin.*;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. All rows are synthetic fixtures. */
class RehabPatientDataGovernanceTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private RehabPatientDataGovernanceService service;
    private RehabDataPermissionService roles;
    private PermissionApi permission;
    private RehabAuditLogService audit;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:origin_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.execute("CREATE TABLE rehab_patient (id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,"
                + "patient_no VARCHAR(64),data_origin VARCHAR(32) DEFAULT 'REAL' NOT NULL,"
                + "current_stage VARCHAR(32),chief_complaint VARCHAR(64),deleted BOOLEAN DEFAULT FALSE,update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE synthetic_clinical_record (id BIGINT PRIMARY KEY,patient_id BIGINT,payload VARCHAR(32))");
        jdbc.execute("CREATE TABLE synthetic_audit (id BIGINT AUTO_INCREMENT PRIMARY KEY,patient_id BIGINT,operation VARCHAR(32))");
        jdbc.update("INSERT INTO rehab_patient (id,tenant_id,patient_no,current_stage,chief_complaint) "
                + "VALUES (801,71,'SYNTHETIC-801','IN_PROGRESS','SYNTHETIC_CLINICAL_FIELD')");
        jdbc.update("INSERT INTO synthetic_clinical_record VALUES (1,801,'SYNTHETIC_ORIGINAL')");
        service = new RehabPatientDataGovernanceService();
        roles = mock(RehabDataPermissionService.class);
        permission = mock(PermissionApi.class);
        audit = mock(RehabAuditLogService.class);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(service, "dataPermissionService", roles);
        ReflectionTestUtils.setField(service, "permissionApi", permission);
        ReflectionTestUtils.setField(service, "auditLogService", audit);
        TenantContextHolder.setTenantId(71L);
    }

    private void authorized() {
        when(roles.isTenantAdmin(501L)).thenReturn(true);
        when(permission.hasAnyPermissions(501L, PERMISSION)).thenReturn(true);
        when(audit.createAuditLog(eq("patient"), eq(801L), anyString(), eq(501L), eq("data-governance"),
                any(), any(), eq("success"), anyString())).thenAnswer(call -> {
            jdbc.update("INSERT INTO synthetic_audit (patient_id,operation) VALUES (?,?)", 801L, call.getArgument(2));
            return 1L;
        });
    }

    private RehabPatientDataGovernanceReqVO request(String from, String operation) {
        RehabPatientDataGovernanceReqVO request = new RehabPatientDataGovernanceReqVO();
        request.setPatientId(801L);
        request.setConfirmPatientNo("SYNTHETIC-801");
        request.setExpectedDataOrigin(from);
        request.setOperation(operation);
        request.setReason("经核对属于隔离合成验收资料");
        return request;
    }

    private String change(RehabPatientDataGovernanceReqVO request) {
        return transaction.execute(ignored -> service.changeOrigin(request, 501L));
    }

    private String origin() {
        return jdbc.queryForObject("SELECT data_origin FROM rehab_patient WHERE id=801", String.class);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (java.sql.Statement statement = connection.createStatement()) { statement.execute("SHUTDOWN"); }
            return null;
        });
    }

    @Test
    void markArchiveRestorePreservesClinicalRowsAndStagesWithAudit() {
        authorized();
        assertEquals(SYNTHETIC, change(request(REAL, "MARK_SYNTHETIC")));
        assertEquals(ARCHIVED, change(request(SYNTHETIC, "ARCHIVE_SYNTHETIC")));
        assertEquals(SYNTHETIC, change(request(ARCHIVED, "RESTORE_SYNTHETIC")));
        assertEquals("IN_PROGRESS", jdbc.queryForObject("SELECT current_stage FROM rehab_patient", String.class));
        assertEquals("SYNTHETIC_CLINICAL_FIELD", jdbc.queryForObject("SELECT chief_complaint FROM rehab_patient", String.class));
        assertEquals("SYNTHETIC_ORIGINAL", jdbc.queryForObject("SELECT payload FROM synthetic_clinical_record", String.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_audit", Integer.class));
    }

    @Test
    void tenantAdminWithoutExplicitNewPermissionCannotChangeAnything() {
        when(roles.isTenantAdmin(501L)).thenReturn(true);
        assertEquals(PATIENT_NO_PERMISSION.getCode(), assertThrows(ServiceException.class,
                () -> change(request(REAL, "MARK_SYNTHETIC"))).getCode());
        assertEquals(REAL, origin());
        verifyNoInteractions(audit);
    }

    @Test
    void ordinaryStaffAndMissingActorAreRejectedBeforeDataLookup() {
        assertThrows(ServiceException.class, () -> change(request(REAL, "MARK_SYNTHETIC")));
        assertThrows(ServiceException.class, () -> service.changeOrigin(request(REAL, "MARK_SYNTHETIC"), null));
        verifyNoInteractions(permission, audit);
        assertEquals(REAL, origin());
    }

    @Test
    void crossTenantAndSoftDeletedPatientsAreNotFoundEvenForAuthorizedOperator() {
        authorized();
        TenantContextHolder.setTenantId(72L);
        assertEquals(PATIENT_NOT_EXISTS.getCode(), assertThrows(ServiceException.class,
                () -> change(request(REAL, "MARK_SYNTHETIC"))).getCode());
        TenantContextHolder.setTenantId(71L);
        jdbc.update("UPDATE rehab_patient SET deleted=TRUE");
        assertEquals(PATIENT_NOT_EXISTS.getCode(), assertThrows(ServiceException.class,
                () -> change(request(REAL, "MARK_SYNTHETIC"))).getCode());
        assertEquals(REAL, origin());
        verifyNoInteractions(audit);
    }

    @Test
    void exactPatientNumberAndReasonAreMandatory() {
        authorized();
        RehabPatientDataGovernanceReqVO request = request(REAL, "MARK_SYNTHETIC");
        request.setConfirmPatientNo("SYNTHETIC-OTHER");
        assertThrows(ServiceException.class, () -> change(request));
        request.setConfirmPatientNo("SYNTHETIC-801");
        request.setReason("短");
        assertThrows(ServiceException.class, () -> change(request));
        assertEquals(REAL, origin());
        verifyNoInteractions(audit);
    }

    @Test
    void staleExpectedOriginCannotOverwriteNewerState() {
        authorized();
        change(request(REAL, "MARK_SYNTHETIC"));
        assertEquals(PATIENT_DATA_ORIGIN_CONFLICT.getCode(), assertThrows(ServiceException.class,
                () -> change(request(REAL, "MARK_SYNTHETIC"))).getCode());
        assertEquals(SYNTHETIC, origin());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_audit", Integer.class));
    }

    @Test
    void realRecordsCannotBeArchivedAndSyntheticCannotBeRelabeledReal() {
        authorized();
        assertThrows(ServiceException.class, () -> change(request(REAL, "ARCHIVE_SYNTHETIC")));
        change(request(REAL, "MARK_SYNTHETIC"));
        assertThrows(ServiceException.class, () -> change(request(SYNTHETIC, "MARK_REAL")));
        assertEquals(SYNTHETIC, origin());
    }

    @Test
    void auditFailureRollsBackSourceChangeInSameTransaction() {
        authorized();
        when(audit.createAuditLog(anyString(), anyLong(), anyString(), anyLong(), anyString(), any(), any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("synthetic audit failure"));
        assertThrows(IllegalStateException.class, () -> change(request(REAL, "MARK_SYNTHETIC")));
        assertEquals(REAL, origin());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_audit", Integer.class));
    }

    @Test
    void concurrentConfirmationsHaveOneWinnerAndOneAuditEntry() throws Exception {
        authorized();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> mark = () -> {
            TenantContextHolder.setTenantId(71L);
            try {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                try { change(request(REAL, "MARK_SYNTHETIC")); return true; }
                catch (ServiceException expected) { return false; }
            } finally { TenantContextHolder.clear(); }
        };
        try {
            Future<Boolean> first = workers.submit(mark);
            Future<Boolean> second = workers.submit(mark);
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_audit", Integer.class));
        } finally {
            start.countDown(); workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
