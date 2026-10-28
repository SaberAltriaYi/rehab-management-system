package cn.iocoder.yudao.module.rehab.service.hep;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.rehab.controller.admin.hep.RehabHepBridgeController;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Disposable H2 database: Core actually creates, reviews, issues and exports
 * a prescription through its real domain service and private port. */
class RehabHepPrescriptionFlowTest {
    @AfterEach void resetTenant(){TenantContextHolder.clear();}
    @SuppressWarnings("unchecked")
    @Test void coreDraftReviewedActionIssuedVersionExportedAndUnauthorizedRejected(){
        JdbcDataSource data=new JdbcDataSource();
        data.setURL("jdbc:h2:mem:corehep"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        JdbcTemplate db=new JdbcTemplate(data);
        db.execute("CREATE TABLE rehab_patient(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted BOOLEAN DEFAULT FALSE)");
        db.execute("CREATE TABLE rehab_episode(id BIGINT PRIMARY KEY,tenant_id BIGINT,patient_id BIGINT,status VARCHAR(20),deleted BOOLEAN DEFAULT FALSE)");
        db.execute("CREATE TABLE rehab_therapist_assignment(tenant_id BIGINT,patient_id BIGINT,therapist_user_id BIGINT,assign_status VARCHAR(20),deleted BOOLEAN DEFAULT FALSE)");
        db.execute("CREATE TABLE rehab_hep_exercise(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,exercise_code VARCHAR(64),name VARCHAR(200),status VARCHAR(20) DEFAULT 'DRAFT',body_region VARCHAR(128),target_deficit VARCHAR(500),instruction_text VARCHAR(500),contraindications VARCHAR(500),media_url VARCHAR(500),source_type VARCHAR(30),source_reference VARCHAR(500),license_status VARCHAR(30),reviewed_by BIGINT,reviewed_time TIMESTAMP,creator VARCHAR(60),updater VARCHAR(60),deleted BOOLEAN DEFAULT FALSE)");
        db.execute("CREATE TABLE rehab_hep_prescription(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,prescription_no VARCHAR(64),patient_id BIGINT,episode_id BIGINT,core_plan_id BIGINT,therapist_user_id BIGINT,template_id BIGINT,start_date DATE,end_date DATE,creator VARCHAR(60),updater VARCHAR(60),current_version_id BIGINT,status VARCHAR(20) DEFAULT 'DRAFT',core_sync_status VARCHAR(20),deleted BOOLEAN DEFAULT FALSE)");
        db.execute("CREATE TABLE rehab_hep_prescription_version(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,prescription_id BIGINT,version_no INT,status VARCHAR(20),change_reason VARCHAR(200),source_template_id BIGINT,reviewed_by BIGINT,reviewed_time TIMESTAMP,issued_by BIGINT,issued_time TIMESTAMP,creator VARCHAR(60),updater VARCHAR(60),deleted BOOLEAN DEFAULT FALSE)");
        db.execute("CREATE TABLE rehab_hep_prescription_item(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,version_id BIGINT,exercise_id BIGINT,training_day_no INT,group_code VARCHAR(40),sort_order INT,dosage_json VARCHAR(1000),instruction_text VARCHAR(1000),pain_limit_rule VARCHAR(255),creator VARCHAR(60),updater VARCHAR(60),deleted BOOLEAN DEFAULT FALSE)");
        db.execute("CREATE TABLE rehab_hep_training_day(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,version_id BIGINT,day_no INT,status VARCHAR(30),creator VARCHAR(60),updater VARCHAR(60),deleted BOOLEAN DEFAULT FALSE)");
        db.execute("CREATE TABLE rehab_hep_training_group(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,training_day_id BIGINT,group_code VARCHAR(40),group_name VARCHAR(50),sort_order INT,creator VARCHAR(60),updater VARCHAR(60),deleted BOOLEAN DEFAULT FALSE)");
        db.update("INSERT INTO rehab_patient(id,tenant_id) VALUES (202,101)");
        db.update("INSERT INTO rehab_episode(id,tenant_id,patient_id,status) VALUES (203,101,202,'active')");
        db.update("INSERT INTO rehab_therapist_assignment(tenant_id,patient_id,therapist_user_id,assign_status) VALUES (101,202,205,'active')");
        RehabHepService clinical=new RehabHepService();
        ReflectionTestUtils.setField(clinical,"jdbcTemplate",db);
        TenantContextHolder.setTenantId(101L);
        Long action=clinical.createExercise(Map.of("exerciseCode","HEP_SYNTH_01",
            "name","Synthetic controlled movement","instructionText","Move slowly"),205L);
        assertEquals("DRAFT",db.queryForObject("SELECT status FROM rehab_hep_exercise WHERE id=?",String.class,action));
        clinical.reviewExercise(action,205L);
        assertEquals("REVIEWED",db.queryForObject("SELECT status FROM rehab_hep_exercise WHERE id=?",String.class,action));
        Long prescription=clinical.createPrescription(Map.of(
            "patientId",202,"episodeId",203,"prescriptionNo","HEP_SYNTHETIC_01",
            "startDate",LocalDate.now().toString(),"endDate",LocalDate.now().plusDays(2).toString(),
            "items",List.of(Map.of("exerciseId",action,"trainingDayNo",1,"groupCode","A",
                "dosageJson","{\"sets\":2,\"reps\":10,\"allowedPain\":3}"))),205L);
        assertEquals("DRAFT",db.queryForObject("SELECT status FROM rehab_hep_prescription WHERE id=?",
            String.class,prescription));
        assertThrows(RuntimeException.class,()->clinical.issuePrescription(prescription,999L),
            "Another therapist must not be able to issue this patient's prescription");
        assertEquals("DRAFT",db.queryForObject("SELECT status FROM rehab_hep_prescription WHERE id=?",
            String.class,prescription));
        clinical.issuePrescription(prescription,205L);
        assertEquals("ISSUED",db.queryForObject("SELECT status FROM rehab_hep_prescription WHERE id=?",
            String.class,prescription));
        assertEquals("ISSUED",db.queryForObject(
            "SELECT status FROM rehab_hep_prescription_version WHERE prescription_id=?",
            String.class,prescription));
        assertThrows(RuntimeException.class,()->clinical.issuePrescription(prescription,205L),
            "An issued clinical prescription cannot be reissued silently");
        RehabHepBridgeController bridge=new RehabHepBridgeController(db,"synthetic-machine-key-longer-than-32");
        RehabHepBridgeController.PrescriptionLookup lookup=new RehabHepBridgeController.PrescriptionLookup();
        lookup.tenantId=101L;lookup.patientId=202L;lookup.episodeId=203L;
        lookup.therapistUserId=205L;lookup.prescriptionId=prescription;
        org.springframework.http.ResponseEntity<?> exported=
            bridge.exportPrescription("synthetic-machine-key-longer-than-32",lookup);
        assertEquals(200,exported.getStatusCodeValue());
        Map<String,Object> result=(Map<String,Object>)exported.getBody();
        assertNotNull(result);
        List<Map<String,Object>> items=(List<Map<String,Object>>)result.get("items");
        assertEquals(1,items.size());
        assertEquals(action.longValue(),((Number)items.get(0).get("exerciseId")).longValue());
        assertEquals(401,bridge.exportPrescription("not-authorized",lookup).getStatusCodeValue());
        lookup.tenantId=999L;
        assertEquals(400,bridge.exportPrescription("synthetic-machine-key-longer-than-32",lookup).getStatusCodeValue());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM rehab_hep_training_day",Integer.class));
    }
}
