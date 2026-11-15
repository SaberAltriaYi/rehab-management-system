package cn.iocoder.yudao.module.rehab.service.app.auth;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.binding.RehabPatientUserBindingDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.binding.RehabPatientUserBindingMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Real isolated H2 JDBC/locking/rollback with synthetic domain adapters.
 * This does not substitute for the MySQL/proxy/member-SMS/browser release gate.
 */
class PatientInvitationTransactionTest {
    private static final long TENANT = 71L;
    private static final long MEMBER = 501L;
    private static final long PATIENT = 801L;
    private static final String PHONE = "13800000001";
    private static final String SECRET = String.join("", Collections.nCopies(40, "x"));
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private PatientAppAccessService service;
    private PatientInvitationTokens codec;

    @BeforeEach
    void setUp() {
        // No environment variable or production datasource participates.
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:patient_invite_" + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc.execute("CREATE TABLE rehab_patient (tenant_id BIGINT NOT NULL,id BIGINT NOT NULL,"
                + "deleted BOOLEAN DEFAULT FALSE,PRIMARY KEY(tenant_id,id))");
        jdbc.update("INSERT INTO rehab_patient (tenant_id,id) VALUES (?,?)", TENANT, PATIENT);
        jdbc.execute("CREATE TABLE rehab_patient_app_invitation (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "tenant_id BIGINT NOT NULL,patient_id BIGINT NOT NULL,phone VARCHAR(32) NOT NULL,"
                + "invitation_digest CHAR(64) NOT NULL,expires_at TIMESTAMP NOT NULL,status VARCHAR(16) NOT NULL,"
                + "issued_by BIGINT,redeemed_by_user_id BIGINT,redeemed_at TIMESTAMP,deleted BOOLEAN DEFAULT FALSE,"
                + "UNIQUE (tenant_id,invitation_digest))");
        jdbc.execute("CREATE TABLE synthetic_binding (tenant_id BIGINT NOT NULL, app_user_id BIGINT NOT NULL, "
                + "patient_id BIGINT NOT NULL,phone VARCHAR(32),bind_type VARCHAR(16),bind_status VARCHAR(16),"
                + "UNIQUE (tenant_id,app_user_id,bind_status))");

        RehabPatientMapper patients = mock(RehabPatientMapper.class);
        when(patients.selectById(PATIENT)).thenReturn(RehabPatientDO.builder().id(PATIENT)
                .patientNo("SYNTHETIC-801").phone(PHONE).name("合成事务验收对象").build());
        RehabPatientUserBindingMapper bindings = mock(RehabPatientUserBindingMapper.class);
        when(bindings.selectActiveByAppUserId(MEMBER)).thenAnswer(ignored -> {
            List<RehabPatientUserBindingDO> rows = jdbc.query("SELECT * FROM synthetic_binding "
                            + "WHERE tenant_id=? AND app_user_id=? AND bind_status='active'",
                    (row, index) -> RehabPatientUserBindingDO.builder().patientId(row.getLong("patient_id"))
                            .appUserId(row.getLong("app_user_id")).phone(row.getString("phone")).build(),
                    TenantContextHolder.getRequiredTenantId(), MEMBER);
            return rows.isEmpty() ? null : rows.get(0);
        });
        when(bindings.insert(any(RehabPatientUserBindingDO.class))).thenAnswer(invocation -> {
            RehabPatientUserBindingDO binding = invocation.getArgument(0);
            return jdbc.update("INSERT INTO synthetic_binding VALUES (?,?,?,?,?,?)",
                    TenantContextHolder.getRequiredTenantId(), binding.getAppUserId(), binding.getPatientId(),
                    binding.getPhone(), binding.getBindType(), binding.getBindStatus());
        });
        MemberUserApi members = mock(MemberUserApi.class);
        MemberUserRespDTO member = new MemberUserRespDTO();
        member.setId(MEMBER);
        member.setMobile(PHONE);
        member.setStatus(0);
        when(members.getUser(MEMBER)).thenReturn(member);
        service = new PatientAppAccessService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(service, "patientMapper", patients);
        ReflectionTestUtils.setField(service, "bindingMapper", bindings);
        ReflectionTestUtils.setField(service, "memberUserApi", members);
        ReflectionTestUtils.setField(service, "invitationSecret", SECRET);
        codec = new PatientInvitationTokens(SECRET.getBytes(StandardCharsets.UTF_8));
        TenantContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        // H2 closes its connection on SHUTDOWN; the StatementCallback variant
        // asks that closed statement for warnings and reports a false failure.
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (java.sql.Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
            return null;
        });
    }

    private String invitation(long tenant, String status, LocalDateTime expiry, boolean deleted) {
        String token = codec.generate();
        jdbc.update("INSERT INTO rehab_patient_app_invitation "
                        + "(tenant_id,patient_id,phone,invitation_digest,expires_at,status,deleted) VALUES (?,?,?,?,?,?,?)",
                tenant, PATIENT, PHONE, codec.digest(tenant, token), expiry, status, deleted);
        return token;
    }

    private String validInvitation() {
        return invitation(TENANT, "PENDING", LocalDateTime.now().plusMinutes(10), false);
    }

    private Long redeem(String token) {
        return transaction.execute(ignored -> service.redeemInvitation(
                "SYNTHETIC-801", PHONE, token, "self", null, MEMBER));
    }

    @Test
    void redeemCommitsOnceAndReplayCannotAddAnotherBinding() {
        String token = validInvitation();
        assertEquals(PATIENT, redeem(token));
        assertThrows(ServiceException.class, () -> redeem(token));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_binding", Integer.class));
        assertEquals("REDEEMED", jdbc.queryForObject(
                "SELECT status FROM rehab_patient_app_invitation", String.class));
        assertEquals(MEMBER, jdbc.queryForObject(
                "SELECT redeemed_by_user_id FROM rehab_patient_app_invitation", Long.class));
    }

    @Test
    void anotherTenantCannotConsumeInvitationEvenForSameNumericSubject() {
        String token = validInvitation();
        TenantContextHolder.setTenantId(72L);
        assertThrows(ServiceException.class, () -> redeem(token));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_binding", Integer.class));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM rehab_patient_app_invitation", String.class));
    }

    @Test
    void expiredRevokedAndSoftDeletedInvitationsFailWithoutWrites() {
        for (String token : Arrays.asList(
                invitation(TENANT, "PENDING", LocalDateTime.now().minusSeconds(1), false),
                invitation(TENANT, "REVOKED", LocalDateTime.now().plusMinutes(10), false),
                invitation(TENANT, "PENDING", LocalDateTime.now().plusMinutes(10), true))) {
            assertThrows(ServiceException.class, () -> redeem(token));
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_binding", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM rehab_patient_app_invitation "
                + "WHERE status='REDEEMED'", Integer.class));
    }

    @Test
    void failedConsumptionRollsBackBindingAndPreservesPendingInvite() throws Exception {
        Transactional annotation = PatientAppAccessService.class.getMethod("redeemInvitation",
                String.class, String.class, String.class, String.class, String.class, Long.class)
                .getAnnotation(Transactional.class);
        assertNotNull(annotation);
        assertTrue(Arrays.asList(annotation.rollbackFor()).contains(Exception.class));
        String token = validInvitation();
        // Database failure after binding insertion must roll back both operations.
        jdbc.execute("ALTER TABLE rehab_patient_app_invitation ADD CONSTRAINT synthetic_failure "
                + "CHECK (redeemed_by_user_id IS NULL)");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> redeem(token));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_binding", Integer.class));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM rehab_patient_app_invitation", String.class));
    }

    @Test
    void twoConcurrentRedemptionsHaveExactlyOneWinner() throws Exception {
        String token = validInvitation();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> attempt = () -> {
            TenantContextHolder.setTenantId(TENANT);
            try {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                try {
                    redeem(token);
                    return true;
                } catch (ServiceException expected) {
                    return false;
                }
            } finally {
                TenantContextHolder.clear();
            }
        };
        try {
            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int winners = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, winners);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_binding", Integer.class));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void reissuingRevokesPreviousInvitationWithoutTouchingOtherTenant() {
        String old = validInvitation();
        String foreign = invitation(72L, "PENDING", LocalDateTime.now().plusMinutes(10), false);
        String issued = transaction.execute(ignored -> service.issueInvitation(PATIENT, 30, 601L));
        assertThrows(ServiceException.class, () -> redeem(old));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM rehab_patient_app_invitation "
                + "WHERE tenant_id=? AND invitation_digest=?", String.class, 72L, codec.digest(72L, foreign)));
        assertEquals(PATIENT, redeem(issued));
    }

    @Test
    void issuanceLifetimeIsAtMostOneDayWithoutRewritingExistingInvitations() {
        String previous = validInvitation();
        assertThrows(ServiceException.class, () -> transaction.execute(ignored ->
                service.issueInvitation(PATIENT, 1441, 601L)));
        assertThrows(ServiceException.class, () -> transaction.execute(ignored ->
                service.issueInvitation(PATIENT, 4, 601L)));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM rehab_patient_app_invitation "
                + "WHERE invitation_digest=?", String.class, codec.digest(TENANT, previous)));
        String issued = transaction.execute(ignored -> service.issueInvitation(PATIENT, 1440, 601L));
        assertEquals(PATIENT, redeem(issued));
    }

    @Test
    void concurrentIssuersLeaveOnlyOneUsableInvitation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        java.util.concurrent.Callable<String> issue = () -> {
            TenantContextHolder.setTenantId(TENANT);
            try {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return transaction.execute(ignored -> service.issueInvitation(PATIENT, 30, 601L));
            } finally {
                TenantContextHolder.clear();
            }
        };
        try {
            Future<String> first = executor.submit(issue);
            Future<String> second = executor.submit(issue);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<String> codes = Arrays.asList(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertNotEquals(codes.get(0), codes.get(1));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM rehab_patient_app_invitation "
                    + "WHERE status='PENDING'", Integer.class));
            int usable = 0;
            for (String code : codes) {
                try {
                    redeem(code);
                    usable++;
                } catch (ServiceException expected) {
                    // One code was explicitly superseded by the later issuance.
                }
            }
            assertEquals(1, usable);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
