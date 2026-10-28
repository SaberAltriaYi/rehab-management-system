package cn.iocoder.yudao.module.rehab.service;

import cn.iocoder.yudao.module.rehab.service.patient.*;
import cn.iocoder.yudao.module.rehab.service.episode.*;
import cn.iocoder.yudao.module.rehab.service.assessment.*;
import cn.iocoder.yudao.module.rehab.service.report.*;
import cn.iocoder.yudao.module.rehab.service.plan.*;
import cn.iocoder.yudao.module.rehab.service.task.*;
import cn.iocoder.yudao.module.rehab.service.checkin.*;
import cn.iocoder.yudao.module.rehab.service.progress.*;
import cn.iocoder.yudao.module.rehab.service.trigger.*;
import cn.iocoder.yudao.module.rehab.service.log.RehabAuditLogService;
import cn.iocoder.yudao.module.rehab.service.notification.RehabNotificationService;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.*;
import cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo.*;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.*;
import cn.iocoder.yudao.module.rehab.controller.admin.plan.vo.*;
import cn.iocoder.yudao.module.rehab.controller.admin.task.vo.*;
import cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo.*;
import cn.iocoder.yudao.module.rehab.controller.admin.trigger.vo.*;
import cn.iocoder.yudao.module.rehab.dal.mysql.episode.RehabEpisodeMapper;
import cn.iocoder.yudao.module.rehab.enums.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. Synthetic service/ORM flow, no actual user login. */
class RehabCoreBusinessFlowTest {
    @TempDir Path storage;
    ClinicalFlowDatabase db;
    RehabPatientService patient; RehabAssessmentService assessment; RehabReportService report;
    RehabCarePlanService plans; RehabExerciseTaskService tasks; RehabDailyCheckinService checkins;
    RehabReassessmentTriggerService triggers;
    RehabEpisodeService episodes;
    RehabDailyCheckinServiceImpl checkinTarget;
    @BeforeEach void open() throws Exception {
        db = new ClinicalFlowDatabase();
        RehabPatientServiceImpl p = new RehabPatientServiceImpl(); RehabEpisodeServiceImpl e = new RehabEpisodeServiceImpl();
        RehabAssessmentServiceImpl a = new RehabAssessmentServiceImpl(); RehabReportServiceImpl r = new RehabReportServiceImpl();
        RehabCarePlanServiceImpl plan = new RehabCarePlanServiceImpl(); RehabExerciseTaskServiceImpl task = new RehabExerciseTaskServiceImpl();
        RehabDailyCheckinServiceImpl check = new RehabDailyCheckinServiceImpl(); RehabProgressRecordServiceImpl progress = new RehabProgressRecordServiceImpl();
        checkinTarget = check;
        RehabReassessmentTriggerServiceImpl trigger = new RehabReassessmentTriggerServiceImpl(); RehabDataPermissionService permissions = new RehabDataPermissionService();
        RehabClinicalSources sources = new RehabClinicalSources(); RehabReportVersionSequence sequence = new RehabReportVersionSequence();
        RehabReportGenerationRequests requests = new RehabReportGenerationRequests();
        db.registerMappers(p,e,a,r,plan,task,check,progress,trigger,permissions);
        PermissionApi roles = mock(PermissionApi.class, call -> {
            if (!call.getMethod().getName().equals("hasAnyRoles")) return org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
            Long id = call.getArgument(0);
            String expected = id == 601L ? "tenant_admin" : id == 501L ? RehabRoleCodeConstants.REHAB_THERAPIST : "SYNTHETIC_NO_ROLE";
            for (Object wanted : call.getArguments()) if (expected.equals(wanted)
                    || wanted instanceof String[] && Arrays.asList((String[]) wanted).contains(expected)) return true;
            return false;
        });
        patient=db.proxy(p); episodes=db.proxy(e); assessment=db.proxy(a); report=db.proxy(r);
        plans=db.proxy(plan); tasks=db.proxy(task); checkins=db.proxy(check); triggers=db.proxy(trigger);
        RehabProgressRecordService progresses=db.proxy(progress);
        Object[] ports={roles,mock(AdminUserApi.class),permissions,sources,sequence,requests,patient,episodes,assessment,report,plans,tasks,checkins,triggers,progresses,
                mock(RehabAuditLogService.class),mock(RehabNotificationService.class)};
        for(Object bean:new Object[]{p,e,a,r,plan,task,check,progress,trigger,permissions,sources,sequence,requests}) db.wire(bean,ports);
        // This Core-only fixture has no CRM department module; department policy has its own suite.
        ReflectionTestUtils.setField(permissions,"jdbcTemplate",null);
        ReflectionTestUtils.setField(p,"self",patient);
        ReflectionTestUtils.setField(a,"staticAssessmentSummaryBuilder",new RehabStaticAssessmentSummaryBuilder());
        ReflectionTestUtils.setField(a,"nasmCesSummaryBuilder",new RehabNasmCesSummaryBuilder());
        ReflectionTestUtils.setField(a,"sfmaSummaryBuilder",new RehabSfmaSummaryBuilder());
        ReflectionTestUtils.setField(a,"sfmaBookProtocol",new RehabSfmaBookProtocol());
        ReflectionTestUtils.setField(a,"storagePath",storage.toString());
        ReflectionTestUtils.setField(r,"storagePath",storage.toString()); ReflectionTestUtils.setField(r,"libreOfficePath","disabled");
        db.jdbc.execute("CREATE TABLE rehab_report_generation_request(tenant_id BIGINT,assessment_id BIGINT,operator_user_id BIGINT,request_key_hash CHAR(64),request_fingerprint CHAR(64),report_id BIGINT,report_no VARCHAR(64),report_version INT,fallback_used BOOLEAN,fallback_message VARCHAR(256),PRIMARY KEY(tenant_id,assessment_id,operator_user_id,request_key_hash))");
    }
    @AfterEach void close() { if(db!=null) db.close(); }
    Long createPatient() {
        RehabPatientCreateReqVO req=new RehabPatientCreateReqVO(); req.setName("SYNTHETIC_ADULT_FLOW"); req.setAge(35); req.setInitEpisode(true); req.setCurrentTherapistUserId(501L);
        req.setChiefComplaint("SYNTHETIC_SOFTWARE_TEST_ONLY");
        Long id=patient.createPatient(req,601L).getId();
        // Isolated fixture provenance, not a production classification shortcut.
        db.jdbc.update("UPDATE rehab_patient SET data_origin='SYNTHETIC' WHERE id=?",id);
        return id;
    }
    Long createAssessment(Long person,Long episode,String module) {
        RehabAssessmentCreateReqVO req=new RehabAssessmentCreateReqVO(); req.setPatientId(person); req.setEpisodeId(episode); req.setAssessmentType("comprehensive_assessment"); req.setStatus("draft");
        RehabAssessmentModuleDataItemVO item=new RehabAssessmentModuleDataItemVO(); item.setModuleType(module); item.setModuleStatus("completed");
        Map<String,Object> values=new LinkedHashMap<>(); values.put("zero",0); values.put("sourceNote","SYNTHETIC_RAW_ONLY"); item.setDataJson(values); req.setModuleDataList(Collections.singletonList(item));
        return assessment.createAssessment(req,501L).getId();
    }
    @Test void patientEpisodeAssessmentReportTrainingRiskAndExplicitReassessmentUseRealTransactions() {
        Long person=createPatient(), episode=db.mapper(RehabEpisodeMapper.class).selectActiveByPatientId(person).getId();
        Long first=createAssessment(person,episode,"fms");
        RehabReportGenerateReqVO generate=new RehabReportGenerateReqVO(); generate.setAssessmentId(first); generate.setRequestKey(UUID.randomUUID().toString());
        RehabReportGenerateRespVO original=report.generateReport(generate,501L);
        assertEquals(original,report.generateReport(generate,501L));
        assertThrows(ServiceException.class,()->report.generateReport(generate,999L));
        assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM rehab_report",Integer.class));
        String before=db.jdbc.queryForObject("SELECT report_json FROM rehab_report WHERE id=?",String.class,original.getId());
        Map<String,Object> payload=JsonUtils.parseObject(before,Map.class); assertTrue(payload.containsKey("sourceSnapshot")); assertEquals("SYNTHETIC",payload.get("dataOrigin"));
        RehabReportApproveReqVO approval=new RehabReportApproveReqVO(); approval.setId(original.getId());
        assertThrows(ServiceException.class,()->report.approveReport(approval,501L));
        RehabReportReviewReqVO review=new RehabReportReviewReqVO(); review.setId(original.getId()); review.setRemark("SYNTHETIC_WORKFLOW_ONLY"); report.reviewReport(review,501L); report.approveReport(approval,501L);
        RehabCarePlanCreateReqVO plan=new RehabCarePlanCreateReqVO(); plan.setPatientId(person); plan.setEpisodeId(episode); plan.setSourceAssessmentId(first); plan.setPrimaryTherapistUserId(501L); plan.setPlanName("SYNTHETIC_PLAN"); plan.setStartDate(LocalDate.now().minusDays(15)); plan.setEndDate(LocalDate.now().plusDays(14));
        Long planId=plans.createPlan(plan,501L).getId();
        RehabExerciseTaskCreateReqVO task=new RehabExerciseTaskCreateReqVO(); task.setPatientId(person); task.setEpisodeId(episode); task.setPlanId(planId); task.setTaskName("SYNTHETIC_MOVEMENT");
        Long taskId=tasks.createTask(task,501L);
        RehabCarePlanChangeStatusReqVO activate=new RehabCarePlanChangeStatusReqVO(); activate.setId(planId); plans.activatePlan(activate,501L);
        RehabDailyCheckinCreateReqVO training=new RehabDailyCheckinCreateReqVO(); training.setPatientId(person); training.setEpisodeId(episode); training.setPlanId(planId); training.setCheckinDate(LocalDate.now()); training.setPainScoreAfter(new BigDecimal("8"));
        RehabTaskExecutionItemVO execution=new RehabTaskExecutionItemVO(); execution.setTaskId(taskId); execution.setCompletionStatus("pain_stop"); execution.setPainScore(new BigDecimal("8")); training.setTaskExecutions(Collections.singletonList(execution));
        training.setCheckinDate(LocalDate.now().minusDays(8)); training.setPainScoreAfter(BigDecimal.ZERO); execution.setPainScore(BigDecimal.ZERO); execution.setCompletionStatus("completed");
        checkins.createCheckin(training,501L,true);
        training.setCheckinDate(LocalDate.now()); training.setPainScoreAfter(new BigDecimal("8")); execution.setPainScore(new BigDecimal("8")); execution.setCompletionStatus("pain_stop");
        checkins.createCheckin(training,501L,true);
        assertEquals(2,db.jdbc.queryForObject("SELECT COUNT(*) FROM rehab_daily_checkin",Integer.class));
        assertTrue(db.jdbc.queryForObject("SELECT COUNT(*) FROM rehab_reassessment_trigger WHERE trigger_type=?",Integer.class,RehabPlanConstants.TRIGGER_PAIN_UPGRADE)>0,
                "the existing cross-period pain rule did not create a review item");
        Long trigger=db.jdbc.queryForObject("SELECT MIN(id) FROM rehab_reassessment_trigger WHERE trigger_status='pending'",Long.class); assertNotNull(trigger);
        RehabReassessmentTriggerHandleReqVO handle=new RehabReassessmentTriggerHandleReqVO(); handle.setId(trigger);
        RehabTriggerConvertRespVO entry=triggers.convertToReassessment(handle,501L); assertEquals(person,entry.getPatientId());
        assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM rehab_assessment_record",Integer.class),"conversion is not a completed assessment");
        Long next=createAssessment(person,episode,"fms"); assertNotEquals(first,next);
        generate.setAssessmentId(next); generate.setRequestKey(UUID.randomUUID().toString()); report.generateReport(generate,501L);
        assertEquals(2,db.jdbc.queryForObject("SELECT COUNT(*) FROM rehab_report",Integer.class));
        assertEquals(before,db.jdbc.queryForObject("SELECT report_json FROM rehab_report WHERE id=?",String.class,original.getId()),"original report inputs were overwritten");
    }
    @Test void changedSourcesCreateNewRevisionButRetryKeepsOriginalReport() {
        Long person=createPatient(), episode=db.mapper(RehabEpisodeMapper.class).selectActiveByPatientId(person).getId();
        Long id=createAssessment(person,episode,"fms");
        RehabReportGenerateReqVO req=new RehabReportGenerateReqVO(); req.setAssessmentId(id); req.setRequestKey(UUID.randomUUID().toString());
        RehabReportGenerateRespVO first=report.generateReport(req,501L);
        String original=db.jdbc.queryForObject("SELECT report_json FROM rehab_report WHERE id=?",String.class,first.getId());
        RehabAssessmentModuleDataSaveReqVO save=new RehabAssessmentModuleDataSaveReqVO(); save.setAssessmentId(id); save.setModuleType("fms"); save.setModuleStatus("completed"); save.setKeepAssessmentDraft(true);
        Map<String,Object> next=new LinkedHashMap<>(); next.put("zero",1); next.put("sourceNote","SYNTHETIC_CHANGED"); save.setDataJson(next); assessment.saveModuleData(save,501L);
        assertEquals(first,report.generateReport(req,501L));
        req.setRequestKey(UUID.randomUUID().toString()); RehabReportGenerateRespVO second=report.generateReport(req,501L); assertEquals(2,second.getReportVersion());
        String changed=db.jdbc.queryForObject("SELECT report_json FROM rehab_report WHERE id=?",String.class,second.getId());
        assertNotEquals(JsonUtils.parseObject(original,Map.class).get("sourceSnapshot"),JsonUtils.parseObject(changed,Map.class).get("sourceSnapshot"));
        assertEquals(original,db.jdbc.queryForObject("SELECT report_json FROM rehab_report WHERE id=?",String.class,first.getId()));
        assertThrows(ServiceException.class,()->assessment.deleteAssessment(id,501L));
    }
    @Test void episodeAndPlanOwnershipCannotMoveEvenWhenTherapistCanReadBothPatients() {
        Long a=createPatient(), ea=db.mapper(RehabEpisodeMapper.class).selectActiveByPatientId(a).getId();
        Long b=createPatient(), eb=db.mapper(RehabEpisodeMapper.class).selectActiveByPatientId(b).getId();
        cn.iocoder.yudao.module.rehab.controller.admin.episode.vo.RehabEpisodeUpdateReqVO move=new cn.iocoder.yudao.module.rehab.controller.admin.episode.vo.RehabEpisodeUpdateReqVO();
        move.setId(ea); move.setPatientId(b); assertThrows(ServiceException.class,()->episodes.updateEpisode(move,501L));
        assertEquals(a,db.mapper(RehabEpisodeMapper.class).selectById(ea).getPatientId());
        Long source=createAssessment(a,ea,"fms"), other=createAssessment(b,eb,"fms");
        RehabCarePlanCreateReqVO create=new RehabCarePlanCreateReqVO(); create.setPatientId(a); create.setEpisodeId(ea); create.setSourceAssessmentId(source); create.setPlanName("SYNTHETIC_OWNER");
        Long id=plans.createPlan(create,501L).getId();
        RehabCarePlanUpdateReqVO update=new RehabCarePlanUpdateReqVO(); update.setId(id); update.setPatientId(b); update.setEpisodeId(eb);
        assertThrows(ServiceException.class,()->plans.updatePlan(update,501L));
        update.setPatientId(a); update.setEpisodeId(ea); update.setSourceAssessmentId(other);
        assertThrows(ServiceException.class,()->plans.updatePlan(update,501L));
        assertThrows(ServiceException.class,()->assessment.deleteAssessment(source,501L),"plan source must not become orphaned");
    }

    RehabDailyCheckinCreateReqVO trainingRequest() {
        Long person=createPatient(), episode=db.mapper(RehabEpisodeMapper.class).selectActiveByPatientId(person).getId();
        RehabCarePlanCreateReqVO plan=new RehabCarePlanCreateReqVO(); plan.setPatientId(person); plan.setEpisodeId(episode);
        plan.setPlanName("SYNTHETIC_TRANSACTION"); plan.setStartDate(LocalDate.now().minusDays(1));
        Long planId=plans.createPlan(plan,501L).getId();
        RehabExerciseTaskCreateReqVO task=new RehabExerciseTaskCreateReqVO(); task.setPatientId(person); task.setEpisodeId(episode);
        task.setPlanId(planId); task.setTaskName("SYNTHETIC_SINGLE_EXECUTION");
        Long taskId=tasks.createTask(task,501L);
        RehabTaskExecutionItemVO item=new RehabTaskExecutionItemVO(); item.setTaskId(taskId); item.setCompletionStatus("completed");
        RehabDailyCheckinCreateReqVO req=new RehabDailyCheckinCreateReqVO(); req.setPatientId(person); req.setEpisodeId(episode);
        req.setPlanId(planId); req.setCheckinDate(LocalDate.now()); req.setTaskExecutions(Collections.singletonList(item));
        return req;
    }

    void assertNoTrainingWrites(int logsBefore) {
        for (String table : Arrays.asList("rehab_daily_checkin", "rehab_task_execution", "rehab_progress_record", "rehab_reassessment_trigger")) {
            assertEquals(0,db.jdbc.queryForObject("SELECT COUNT(*) FROM " + table,Integer.class),table);
        }
        assertEquals(logsBefore,db.jdbc.queryForObject("SELECT COUNT(*) FROM rehab_plan_operation_log",Integer.class));
    }

    @Test void duplicateTaskInOneSubmissionIsRejectedWithoutInflatingProgress() {
        RehabDailyCheckinCreateReqVO req=trainingRequest();
        RehabTaskExecutionItemVO item=req.getTaskExecutions().get(0);
        req.setTaskExecutions(Arrays.asList(item,item));
        int logsBefore=db.jdbc.queryForObject("SELECT COUNT(*) FROM rehab_plan_operation_log",Integer.class);
        ServiceException rejected=assertThrows(ServiceException.class,()->checkins.createCheckin(req,501L,true));
        assertEquals(CHECKIN_TASK_EXECUTION_DUPLICATE.getCode(),rejected.getCode());
        assertNoTrainingWrites(logsBefore);
    }

    @Test void malformedExecutionIsRejectedBeforeAnyTrainingWrites() {
        RehabDailyCheckinCreateReqVO req=trainingRequest();
        RehabTaskExecutionItemVO item=req.getTaskExecutions().get(0); Long taskId=item.getTaskId();
        int logsBefore=db.jdbc.queryForObject("SELECT COUNT(*) FROM rehab_plan_operation_log",Integer.class);
        req.setTaskExecutions(Collections.singletonList(null));
        assertInvalidTraining(req);
        req.setTaskExecutions(Collections.singletonList(item)); item.setTaskId(null);
        assertInvalidTraining(req);
        item.setTaskId(0L);
        assertInvalidTraining(req);
        item.setTaskId(taskId); item.setCompletionStatus("SYNTHETIC_UNKNOWN_STATUS");
        assertInvalidTraining(req);
        item.setCompletionStatus(null);
        assertInvalidTraining(req);
        assertNoTrainingWrites(logsBefore);
    }

    void assertInvalidTraining(RehabDailyCheckinCreateReqVO req) {
        ServiceException rejected=assertThrows(ServiceException.class,()->checkins.createCheckin(req,501L,true));
        assertEquals(CHECKIN_TASK_EXECUTION_INVALID.getCode(),rejected.getCode());
    }

    @Test void distinctTasksKeepAllExistingCompletionStatesAndZeroPain() {
        RehabDailyCheckinCreateReqVO req=trainingRequest();
        List<RehabTaskExecutionItemVO> items=new ArrayList<>(req.getTaskExecutions());
        for (String status : Arrays.asList("partial", "skipped", "pain_stop")) {
            RehabExerciseTaskCreateReqVO task=new RehabExerciseTaskCreateReqVO(); task.setPatientId(req.getPatientId());
            task.setEpisodeId(req.getEpisodeId()); task.setPlanId(req.getPlanId()); task.setTaskName("SYNTHETIC_" + status);
            RehabTaskExecutionItemVO item=new RehabTaskExecutionItemVO(); item.setTaskId(tasks.createTask(task,501L));
            item.setCompletionStatus(status); item.setPainScore(BigDecimal.ZERO); items.add(item);
        }
        req.setTaskExecutions(items); req.setPainScoreAfter(BigDecimal.ZERO);
        Long id=checkins.createCheckin(req,501L,true);
        assertEquals(4,db.jdbc.queryForObject("SELECT COUNT(*) FROM rehab_task_execution",Integer.class));
        assertEquals(0,new BigDecimal("37.50").compareTo(db.jdbc.queryForObject(
                "SELECT overall_completion_rate FROM rehab_daily_checkin WHERE id=?",BigDecimal.class,id)));
        assertEquals(0,BigDecimal.ZERO.compareTo(db.jdbc.queryForObject(
                "SELECT pain_score_after FROM rehab_daily_checkin WHERE id=?",BigDecimal.class,id)));
    }

    @Test void downstreamFailureRollsBackTrainingProgressAndLogsThenRetryCanSucceed() {
        RehabDailyCheckinCreateReqVO req=trainingRequest();
        int logsBefore=db.jdbc.queryForObject("SELECT COUNT(*) FROM rehab_plan_operation_log",Integer.class);
        RehabReassessmentTriggerService failed=mock(RehabReassessmentTriggerService.class);
        doAnswer(call -> {
            for (String table : Arrays.asList("rehab_daily_checkin", "rehab_task_execution", "rehab_progress_record")) {
                assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM " + table,Integer.class),table);
            }
            throw new IllegalStateException("SYNTHETIC_DOWNSTREAM_FAILURE");
        }).when(failed)
                .evaluateByPlan(eq(req.getPlanId()),any(),eq(501L));
        ReflectionTestUtils.setField(checkinTarget,"triggerService",failed);
        assertThrows(IllegalStateException.class,()->checkins.createCheckin(req,501L,true));
        verify(failed).evaluateByPlan(eq(req.getPlanId()),any(),eq(501L));
        assertNoTrainingWrites(logsBefore);
        ReflectionTestUtils.setField(checkinTarget,"triggerService",triggers);
        Long id=checkins.createCheckin(req,501L,true); assertNotNull(id);
        assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM rehab_daily_checkin",Integer.class));
        assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM rehab_task_execution",Integer.class));
        assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM rehab_progress_record",Integer.class));
    }
}
