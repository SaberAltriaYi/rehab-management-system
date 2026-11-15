package cn.iocoder.yudao.module.rehab.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. Actual typed JDBC + synthetic H2 transactions. */
class RehabClinicalSourcesTest {
    JdbcTemplate jdbc; TransactionTemplate tx; RehabClinicalSources sources;
    @BeforeEach void open() {
        DriverManagerDataSource ds=new DriverManagerDataSource("jdbc:h2:mem:sources_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds)); sources=new RehabClinicalSources();
        ReflectionTestUtils.setField(sources,"jdbcTemplate",jdbc); TenantContextHolder.setTenantId(71L);
        jdbc.execute("CREATE TABLE rehab_patient(id BIGINT PRIMARY KEY,tenant_id BIGINT,name VARCHAR(32),birthday DATE,height_cm DECIMAL(10,2),deleted BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE rehab_episode(id BIGINT PRIMARY KEY,tenant_id BIGINT,patient_id BIGINT,status VARCHAR(20),create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,deleted BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE rehab_assessment_record(id BIGINT PRIMARY KEY,tenant_id BIGINT,patient_id BIGINT,episode_id BIGINT,assessment_date DATE,deleted BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE rehab_assessment_module_data(id BIGINT PRIMARY KEY,tenant_id BIGINT,assessment_id BIGINT,module_type VARCHAR(20),data_json VARCHAR(256),deleted BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE rehab_report(id BIGINT PRIMARY KEY,tenant_id BIGINT,patient_id BIGINT,assessment_id BIGINT,deleted BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE rehab_care_plan(id BIGINT PRIMARY KEY,tenant_id BIGINT,patient_id BIGINT,episode_id BIGINT,source_assessment_id BIGINT,status VARCHAR(20),deleted BOOLEAN DEFAULT FALSE)");
        jdbc.update("INSERT INTO rehab_patient VALUES(1,71,'SYNTHETIC_A','2000-01-02',0,FALSE),(2,72,'SYNTHETIC_OTHER','2000-01-02',0,FALSE)");
        jdbc.update("INSERT INTO rehab_episode(id,tenant_id,patient_id,status) VALUES(3,71,1,'active')");
        jdbc.update("INSERT INTO rehab_assessment_record VALUES(4,71,1,3,'2026-10-27',FALSE)");
        jdbc.update("INSERT INTO rehab_assessment_module_data VALUES(5,71,4,'sfma','{}',FALSE),(6,71,4,'fms','{\"zero\":0}',FALSE)");
    }
    @AfterEach void close() { TenantContextHolder.clear(); jdbc.execute("DROP ALL OBJECTS"); }
    @Test void refusesMissingTransactionAndTenant() {
        assertThrows(IllegalStateException.class,()->sources.patient(1L));
        TenantContextHolder.clear(); assertThrows(RuntimeException.class,()->tx.execute(s->sources.patient(1L)));
    }
    @Test void typedCurrentReadsKeepDatesZeroAndModuleOrdering() {
        tx.execute(status->{
            assertEquals(LocalDate.of(2000,1,2),sources.patient(1L).getBirthday());
            assertEquals(0,BigDecimal.ZERO.compareTo(sources.patient(1L).getHeightCm()));
            assertEquals(LocalDate.of(2026,10,27),sources.assessment(4L).getAssessmentDate());
            assertEquals(1L,sources.episode(3L).getPatientId());
            assertEquals("fms",sources.modules(4L).get(0).getModuleType());
            assertEquals("{\"zero\":0}",sources.module(4L,"fms").getDataJson());
            assertEquals(3L,sources.activeEpisode(1L).getId()); return null;
        });
    }
    @Test void refusesForeignDeletedAndMissingSources() {
        tx.execute(status->{
            assertThrows(ServiceException.class,()->sources.patient(2L));
            assertThrows(ServiceException.class,()->sources.assessment(999L));
            jdbc.update("UPDATE rehab_patient SET deleted=TRUE WHERE id=1");
            assertThrows(ServiceException.class,()->sources.patient(1L)); return null;
        });
    }
    @Test void currentReferencesProtectAssessmentAndPatientAndIgnoreOtherTenants() {
        tx.execute(status->{
            assertFalse(sources.hasAssessmentReports(4L));
            jdbc.update("INSERT INTO rehab_care_plan VALUES(7,71,1,3,4,'active',FALSE)");
            assertTrue(sources.hasAssessmentReports(4L)); assertTrue(sources.hasReferences(1L));
            assertEquals(7L,sources.activePlan(1L,3L).getId()); assertEquals(1L,sources.plan(7L).getPatientId());
            assertThrows(ServiceException.class,()->sources.plan(8L));
            assertFalse(sources.hasReferences(2L)); return null;
        });
    }
    @Test void failedMutationRollsBackAndNextTransactionReadsOriginal() {
        assertThrows(IllegalStateException.class,()->tx.execute(status->{ sources.patient(1L); sources.assessment(4L);
            jdbc.update("UPDATE rehab_patient SET name='SYNTHETIC_CHANGED' WHERE id=1"); throw new IllegalStateException("SYNTHETIC_ROLLBACK"); }));
        assertEquals("SYNTHETIC_A",tx.execute(status->sources.patient(1L).getName()));
    }
}
