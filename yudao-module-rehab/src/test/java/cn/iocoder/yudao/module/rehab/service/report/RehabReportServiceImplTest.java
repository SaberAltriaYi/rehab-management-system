package cn.iocoder.yudao.module.rehab.service.report;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.RehabReportLockReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.RehabReportUnlockReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.RehabReportGenerateReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.RehabReportGenerateRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.RehabReportPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.RehabReportPatientRespVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentModuleDataDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentOperationLogDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentRecordDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.episode.RehabEpisodeDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.report.RehabReportDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.report.RehabReportVersionMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentModuleDataMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentOperationLogMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assignment.RehabTherapistAssignmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.episode.RehabEpisodeMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.report.RehabReportMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabRoleCodeConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.log.RehabAuditLogService;
import cn.iocoder.yudao.module.rehab.service.notification.RehabNotificationService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.PATIENT_NO_PERMISSION;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.REPORT_CAN_NOT_EXPORT;

@ExtendWith(MockitoExtension.class)
class RehabReportServiceImplTest {

    private RehabReportServiceImpl reportService;

    @Mock
    private RehabReportMapper reportMapper;
    @Mock
    private RehabAssessmentRecordMapper assessmentRecordMapper;
    @Mock
    private RehabAssessmentModuleDataMapper moduleDataMapper;
    @Mock
    private RehabAssessmentOperationLogMapper assessmentOperationLogMapper;
    @Mock
    private RehabPatientMapper patientMapper;
    @Mock
    private RehabEpisodeMapper episodeMapper;
    @Mock
    private RehabTherapistAssignmentMapper assignmentMapper;
    @Mock
    private PermissionApi permissionApi;
    @Mock
    private AdminUserApi adminUserApi;
    @Mock
    private RehabReportVersionMapper reportVersionMapper;
    @Mock
    private RehabAuditLogService auditLogService;
    @Mock
    private RehabNotificationService notificationService;

    @BeforeEach
    void setUp() throws IOException {
        reportService = new RehabReportServiceImpl();

        RehabDataPermissionService dataPermissionService = new RehabDataPermissionService();
        ReflectionTestUtils.setField(dataPermissionService, "permissionApi", permissionApi);
        ReflectionTestUtils.setField(dataPermissionService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(dataPermissionService, "assignmentMapper", assignmentMapper);

        ReflectionTestUtils.setField(reportService, "reportMapper", reportMapper);
        ReflectionTestUtils.setField(reportService, "assessmentRecordMapper", assessmentRecordMapper);
        ReflectionTestUtils.setField(reportService, "moduleDataMapper", moduleDataMapper);
        ReflectionTestUtils.setField(reportService, "assessmentOperationLogMapper", assessmentOperationLogMapper);
        ReflectionTestUtils.setField(reportService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(reportService, "episodeMapper", episodeMapper);
        ReflectionTestUtils.setField(reportService, "dataPermissionService", dataPermissionService);
        ReflectionTestUtils.setField(reportService, "adminUserApi", adminUserApi);
        ReflectionTestUtils.setField(reportService, "reportVersionMapper", reportVersionMapper);
        ReflectionTestUtils.setField(reportService, "auditLogService", auditLogService);
        ReflectionTestUtils.setField(reportService, "notificationService", notificationService);
        ReflectionTestUtils.setField(reportService, "storagePath", Files.createTempDirectory("rehab-report-test").toString());
        ReflectionTestUtils.setField(reportService, "libreOfficePath", "disabled");
    }

    @Test
    void generateReport_shouldWorkWithMissingModulesAndKeepStructuredJson() {
        RehabReportGenerateReqVO reqVO = new RehabReportGenerateReqVO();
        reqVO.setAssessmentId(20001L);

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(assessmentRecordMapper.selectById(20001L)).thenReturn(RehabAssessmentRecordDO.builder()
                .id(20001L)
                .patientId(10001L)
                .episodeId(13001L)
                .assessmentType("initial")
                .build());
        when(patientMapper.selectById(10001L)).thenReturn(RehabPatientDO.builder()
                .id(10001L)
                .name("王小明")
                .chiefComplaint("跑跳后膝前侧不适")
                .build());
        when(episodeMapper.selectById(13001L)).thenReturn(RehabEpisodeDO.builder()
                .id(13001L)
                .patientId(10001L)
                .episodeNo("EP202603080001")
                .status("active")
                .build());
        when(moduleDataMapper.selectListByAssessmentId(20001L)).thenReturn(Collections.emptyList());
        when(reportMapper.selectListByAssessmentId(20001L)).thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            RehabReportDO report = invocation.getArgument(0);
            report.setId(30001L);
            return 1;
        }).when(reportMapper).insert(any(RehabReportDO.class));
        when(reportMapper.selectById(30001L)).thenReturn(RehabReportDO.builder()
                .id(30001L)
                .assessmentId(20001L)
                .patientId(10001L)
                .build());

        RehabReportGenerateRespVO respVO = reportService.generateReport(reqVO, 1L);

        assertNotNull(respVO);
        assertEquals(30001L, respVO.getId());
        assertEquals(1, respVO.getReportVersion());

        ArgumentCaptor<RehabReportDO> captor = ArgumentCaptor.forClass(RehabReportDO.class);
        verify(reportMapper, atLeast(2)).updateById(captor.capture());
        List<RehabReportDO> updates = captor.getAllValues();
        assertTrue(updates.stream().anyMatch(item ->
                item.getReportJson() != null && item.getReportJson().contains("missingModules")));
        verify(assessmentOperationLogMapper).insert(any(RehabAssessmentOperationLogDO.class));
    }

    @Test
    void generateReport_shouldExpandComprehensiveModulesAndDeferBinaryExport() throws Exception {
        RehabReportGenerateReqVO reqVO = new RehabReportGenerateReqVO();
        reqVO.setAssessmentId(20002L);

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(assessmentRecordMapper.selectById(20002L)).thenReturn(RehabAssessmentRecordDO.builder()
                .id(20002L)
                .patientId(10001L)
                .episodeId(13001L)
                .assessmentType("comprehensive_assessment")
                .chiefFocus("动作控制")
                .build());
        when(patientMapper.selectById(10001L)).thenReturn(RehabPatientDO.builder()
                .id(10001L)
                .name("测试患者")
                .build());
        when(episodeMapper.selectById(13001L)).thenReturn(RehabEpisodeDO.builder()
                .id(13001L)
                .patientId(10001L)
                .episodeNo("EP-COMPREHENSIVE")
                .status("active")
                .build());
        when(moduleDataMapper.selectListByAssessmentId(20002L)).thenReturn(Collections.singletonList(
                RehabAssessmentModuleDataDO.builder()
                        .assessmentId(20002L)
                        .moduleType("comprehensive")
                        .moduleStatus("completed")
                        .sourceType("manual")
                        .dataJson("{\"selectedModules\":[\"fms\"],\"modules\":{\"fms\":{\"summary\":{\"totalScore\":16,\"asymmetryCount\":1,\"painDetected\":false,\"riskLevel\":\"normal\",\"conclusion\":\"髋稳定性需继续训练\"}}},\"summary\":{\"conclusion\":\"优先改善髋部控制\"}}")
                        .build()));
        when(reportMapper.selectListByAssessmentId(20002L)).thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            RehabReportDO report = invocation.getArgument(0);
            report.setId(30002L);
            return 1;
        }).when(reportMapper).insert(any(RehabReportDO.class));
        when(reportMapper.selectById(30002L)).thenReturn(RehabReportDO.builder()
                .id(30002L)
                .assessmentId(20002L)
                .patientId(10001L)
                .build());

        reportService.generateReport(reqVO, 1L);

        ArgumentCaptor<RehabReportDO> captor = ArgumentCaptor.forClass(RehabReportDO.class);
        verify(reportMapper, atLeast(2)).updateById(captor.capture());
        RehabReportDO generated = captor.getAllValues().stream()
                .filter(item -> item.getReportJson() != null)
                .findFirst().orElseThrow(AssertionError::new);
        assertTrue(generated.getReportJson().contains("\"totalScore\":16"));
        assertTrue(generated.getReportJson().contains("\"fms\""));
        assertTrue(generated.getReportJson().contains("\"contentPolicy\":\"raw-data-only\""));
        assertFalse(generated.getReportJson().contains("优先改善髋部控制"));
        assertFalse(generated.getReportJson().contains("髋稳定性需继续训练"));

        assertNull(generated.getDocxPath());
        assertNull(generated.getPdfPath());

        RehabReportDO approvedReport = RehabReportDO.builder()
                .id(30002L)
                .patientId(10001L)
                .assessmentId(20002L)
                .reportNo("REP202608050002")
                .reportVersion(1)
                .reportStatus("approved")
                .reportJson(generated.getReportJson())
                .build();
        when(reportMapper.selectById(30002L)).thenReturn(approvedReport);
        byte[] docxBytes = reportService.exportDocx(30002L, 1L);
        assertTrue(docxBytes.length > 0);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            assertEquals(53, document.getTables().size());
            String text = document.getTables().stream().map(table -> table.getText())
                    .reduce("", (left, right) -> left + "\n" + right);
            assertTrue(text.contains("totalScore：16"));
            assertFalse(text.contains("髋稳定性需继续训练"));
        }
    }

    @Test
    void getReportPatientPage_shouldPageByPatientInsteadOfReport() {
        RehabReportPageReqVO reqVO = new RehabReportPageReqVO();
        reqVO.setPageNo(2);
        reqVO.setPageSize(10);
        reqVO.setKeyword("王");
        RehabReportPatientRespVO patient = new RehabReportPatientRespVO();
        patient.setPatientId(10001L);
        patient.setPatientNo("P202608050001");
        patient.setPatientName("王小明");
        patient.setAssessmentCount(2L);
        patient.setReportCount(3L);

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(reportMapper.selectPatientCount(reqVO, null)).thenReturn(11L);
        when(reportMapper.selectPatientPage(reqVO, null, 10L, 10)).thenReturn(Collections.singletonList(patient));

        PageResult<RehabReportPatientRespVO> result = reportService.getReportPatientPage(reqVO, 1L);

        assertEquals(11L, result.getTotal());
        assertEquals(1, result.getList().size());
        assertEquals(10001L, result.getList().get(0).getPatientId());
        assertEquals(3L, result.getList().get(0).getReportCount());
    }

    @Test
    void generateReport_shouldDenyWhenTherapistHasNoPermission() {
        RehabReportGenerateReqVO reqVO = new RehabReportGenerateReqVO();
        reqVO.setAssessmentId(20001L);

        when(assessmentRecordMapper.selectById(20001L)).thenReturn(RehabAssessmentRecordDO.builder()
                .id(20001L)
                .patientId(10001L)
                .episodeId(13001L)
                .assessmentType("initial")
                .build());
        when(permissionApi.hasAnyRoles(99L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(false);
        when(permissionApi.hasAnyRoles(99L, RehabRoleCodeConstants.REHAB_CLERK)).thenReturn(false);
        when(permissionApi.hasAnyRoles(99L, RehabRoleCodeConstants.REHAB_THERAPIST)).thenReturn(false);

        ServiceException ex = assertThrows(ServiceException.class, () -> reportService.generateReport(reqVO, 99L));
        assertEquals(1_011_000_001, ex.getCode());
        verify(reportMapper, never()).insert(any(RehabReportDO.class));
    }

    @Test
    void lockAndUnlock_shouldFollowPermissionRule() {
        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(reportMapper.selectById(30001L)).thenReturn(RehabReportDO.builder()
                .id(30001L)
                .patientId(10001L)
                .assessmentId(20001L)
                .reportStatus("approved")
                .reportVersion(2)
                .build());

        RehabReportLockReqVO lockReqVO = new RehabReportLockReqVO();
        lockReqVO.setId(30001L);
        lockReqVO.setReason("测试锁版");
        reportService.lockReport(lockReqVO, 1L);
        verify(reportMapper, atLeastOnce()).updateById(argThat((RehabReportDO item) ->
                item.getId() != null && item.getId().equals(30001L) && "locked".equals(item.getReportStatus())));

        when(permissionApi.hasAnyRoles(99L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(false);
        RehabReportUnlockReqVO unlockReqVO = new RehabReportUnlockReqVO();
        unlockReqVO.setId(30001L);
        unlockReqVO.setReason("非管理员尝试");
        ServiceException ex = assertThrows(ServiceException.class, () -> reportService.unlockReport(unlockReqVO, 99L));
        assertEquals(PATIENT_NO_PERMISSION.getCode(), ex.getCode());
    }

    @Test
    void exportDocx_shouldRejectWhenStatusNotApproved() {
        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(reportMapper.selectById(30002L)).thenReturn(RehabReportDO.builder()
                .id(30002L)
                .patientId(10001L)
                .assessmentId(20001L)
                .reportStatus("draft")
                .build());

        ServiceException ex = assertThrows(ServiceException.class, () -> reportService.exportDocx(30002L, 1L));
        assertEquals(REPORT_CAN_NOT_EXPORT.getCode(), ex.getCode());
    }

}
