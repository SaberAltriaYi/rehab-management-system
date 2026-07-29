package cn.iocoder.yudao.module.rehab.service.assessment;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo.RehabAssessmentCreateReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo.RehabAssessmentCreateRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo.RehabAssessmentModuleDataSaveReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo.RehabAssessmentUpdateReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentAttachmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentModuleDataDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentOperationLogDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentRecordDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.episode.RehabEpisodeDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentAttachmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentModuleDataMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentOperationLogMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assignment.RehabTherapistAssignmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.episode.RehabEpisodeMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.report.RehabReportMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabAssessmentConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabRoleCodeConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RehabAssessmentServiceImplTest {

    private RehabAssessmentServiceImpl assessmentService;

    @TempDir
    Path tempDir;

    @Mock
    private RehabAssessmentRecordMapper assessmentRecordMapper;
    @Mock
    private RehabAssessmentModuleDataMapper moduleDataMapper;
    @Mock
    private RehabAssessmentAttachmentMapper attachmentMapper;
    @Mock
    private RehabAssessmentOperationLogMapper operationLogMapper;
    @Mock
    private RehabPatientMapper patientMapper;
    @Mock
    private RehabEpisodeMapper episodeMapper;
    @Mock
    private RehabTherapistAssignmentMapper assignmentMapper;
    @Mock
    private RehabReportMapper reportMapper;
    @Mock
    private PermissionApi permissionApi;
    @Mock
    private AdminUserApi adminUserApi;

    @BeforeEach
    void setUp() {
        assessmentService = new RehabAssessmentServiceImpl();

        RehabDataPermissionService dataPermissionService = new RehabDataPermissionService();
        ReflectionTestUtils.setField(dataPermissionService, "permissionApi", permissionApi);
        ReflectionTestUtils.setField(dataPermissionService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(dataPermissionService, "assignmentMapper", assignmentMapper);

        ReflectionTestUtils.setField(assessmentService, "assessmentRecordMapper", assessmentRecordMapper);
        ReflectionTestUtils.setField(assessmentService, "moduleDataMapper", moduleDataMapper);
        ReflectionTestUtils.setField(assessmentService, "attachmentMapper", attachmentMapper);
        ReflectionTestUtils.setField(assessmentService, "operationLogMapper", operationLogMapper);
        ReflectionTestUtils.setField(assessmentService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(assessmentService, "episodeMapper", episodeMapper);
        ReflectionTestUtils.setField(assessmentService, "reportMapper", reportMapper);
        ReflectionTestUtils.setField(assessmentService, "dataPermissionService", dataPermissionService);
        ReflectionTestUtils.setField(assessmentService, "adminUserApi", adminUserApi);
        ReflectionTestUtils.setField(assessmentService, "storagePath", tempDir.toString());
        ReflectionTestUtils.setField(assessmentService, "staticAssessmentSummaryBuilder",
                new RehabStaticAssessmentSummaryBuilder());
        ReflectionTestUtils.setField(assessmentService, "nasmCesSummaryBuilder",
                new RehabNasmCesSummaryBuilder());
        ReflectionTestUtils.setField(assessmentService, "sfmaSummaryBuilder",
                new RehabSfmaSummaryBuilder());
    }

    @Test
    void downloadAttachment_shouldReturnStoredFile() throws Exception {
        byte[] expected = "rehab attachment".getBytes(StandardCharsets.UTF_8);
        Path attachmentPath = tempDir.resolve("assessments/20001/attachments/evidence.txt");
        Files.createDirectories(attachmentPath.getParent());
        Files.write(attachmentPath, expected);

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(attachmentMapper.selectById(23001L)).thenReturn(RehabAssessmentAttachmentDO.builder()
                .id(23001L)
                .assessmentId(20001L)
                .fileName("evidence.txt")
                .fileType("text/plain")
                .filePath(attachmentPath.toString())
                .build());
        when(assessmentRecordMapper.selectById(20001L)).thenReturn(RehabAssessmentRecordDO.builder()
                .id(20001L)
                .patientId(10001L)
                .build());

        RehabAssessmentAttachmentFile file = assessmentService.downloadAttachment(23001L, 1L);

        assertEquals("evidence.txt", file.getFileName());
        assertEquals("text/plain", file.getFileType());
        assertArrayEquals(expected, file.getContent());
    }

    @Test
    void uploadAttachment_shouldRejectUnsafeFileType() {
        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(assessmentRecordMapper.selectById(20001L)).thenReturn(RehabAssessmentRecordDO.builder()
                .id(20001L)
                .patientId(10001L)
                .status(RehabAssessmentConstants.STATUS_DRAFT)
                .build());
        MockMultipartFile file = new MockMultipartFile(
                "file", "payload.html", "text/html", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> assessmentService.uploadAttachment(20001L, RehabAssessmentConstants.MODULE_SFMA, file, 1L));

        assertEquals(1_011_004_008, ex.getCode());
        verifyNoInteractions(attachmentMapper);
    }

    @Test
    void uploadAttachment_shouldStoreAllowedFileWithSanitizedName() {
        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(assessmentRecordMapper.selectById(20001L)).thenReturn(RehabAssessmentRecordDO.builder()
                .id(20001L)
                .patientId(10001L)
                .status(RehabAssessmentConstants.STATUS_DRAFT)
                .build());
        doAnswer(invocation -> {
            RehabAssessmentAttachmentDO attachment = invocation.getArgument(0);
            attachment.setId(23002L);
            return 1;
        }).when(attachmentMapper).insert(any(RehabAssessmentAttachmentDO.class));
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../evidence.pdf", "application/pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8));

        assessmentService.uploadAttachment(20001L, RehabAssessmentConstants.MODULE_SFMA, file, 1L);

        verify(attachmentMapper).insert(argThat((RehabAssessmentAttachmentDO attachment) ->
                "evidence.pdf".equals(attachment.getFileName())
                        && attachment.getFilePath() != null
                        && attachment.getFilePath().startsWith(tempDir.toString())));
        verify(operationLogMapper).insert(any(RehabAssessmentOperationLogDO.class));
    }

    @Test
    void createAssessment_shouldCreateAndAutoFillPrimaryModule() {
        RehabAssessmentCreateReqVO reqVO = new RehabAssessmentCreateReqVO();
        reqVO.setPatientId(10001L);
        reqVO.setEpisodeId(13001L);
        reqVO.setAssessmentType(RehabAssessmentConstants.TYPE_STATIC_ASSESSMENT);
        reqVO.setAssessmentDate(LocalDate.now());

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(patientMapper.selectById(10001L)).thenReturn(RehabPatientDO.builder().id(10001L).build());
        when(episodeMapper.selectById(13001L)).thenReturn(RehabEpisodeDO.builder().id(13001L).patientId(10001L).build());

        doAnswer(invocation -> {
            RehabAssessmentRecordDO assessment = invocation.getArgument(0);
            assessment.setId(20001L);
            return 1;
        }).when(assessmentRecordMapper).insert(any(RehabAssessmentRecordDO.class));

        RehabAssessmentRecordDO savedAssessment = RehabAssessmentRecordDO.builder()
                .id(20001L)
                .patientId(10001L)
                .episodeId(13001L)
                .status(RehabAssessmentConstants.STATUS_DRAFT)
                .build();
        when(assessmentRecordMapper.selectById(20001L)).thenReturn(savedAssessment, savedAssessment, savedAssessment);

        when(moduleDataMapper.selectListByAssessmentId(20001L)).thenReturn(
                Collections.emptyList(),
                Collections.singletonList(RehabAssessmentModuleDataDO.builder()
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_STATIC)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED)
                        .build())
        );
        when(moduleDataMapper.selectByAssessmentIdAndModuleType(20001L, RehabAssessmentConstants.MODULE_STATIC)).thenReturn(
                null,
                RehabAssessmentModuleDataDO.builder()
                        .id(21001L)
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_STATIC)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED)
                        .dataJson("{}")
                        .build()
        );

        RehabAssessmentCreateRespVO respVO = assessmentService.createAssessment(reqVO, 1L);

        assertNotNull(respVO);
        assertEquals(20001L, respVO.getId());
        assertTrue(respVO.getAssessmentNo().startsWith("ASM"));
        verify(moduleDataMapper).insert(org.mockito.ArgumentMatchers.<RehabAssessmentModuleDataDO>argThat(item ->
                Objects.equals(item.getAssessmentId(), 20001L)
                        && Objects.equals(item.getModuleType(), RehabAssessmentConstants.MODULE_STATIC)
                        && item.getDataJson() != null
                        && item.getDataJson().contains("\"static_summary\"")));
        verify(operationLogMapper).insert(any(RehabAssessmentOperationLogDO.class));
    }

    @Test
    void createAssessment_shouldRejectWhenAssessmentTypeInvalid() {
        RehabAssessmentCreateReqVO reqVO = new RehabAssessmentCreateReqVO();
        reqVO.setPatientId(10001L);
        reqVO.setEpisodeId(13001L);
        reqVO.setAssessmentType("initial");
        reqVO.setAssessmentDate(LocalDate.now());

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(patientMapper.selectById(10001L)).thenReturn(RehabPatientDO.builder().id(10001L).build());
        when(episodeMapper.selectById(13001L)).thenReturn(RehabEpisodeDO.builder().id(13001L).patientId(10001L).build());

        ServiceException ex = assertThrows(ServiceException.class, () -> assessmentService.createAssessment(reqVO, 1L));
        assertEquals(1_011_004_006, ex.getCode());
    }

    @Test
    void updateAssessment_shouldAutoFillComprehensiveModuleWhenModuleListMissing() {
        RehabAssessmentUpdateReqVO reqVO = new RehabAssessmentUpdateReqVO();
        reqVO.setId(20001L);
        reqVO.setPatientId(10001L);
        reqVO.setEpisodeId(13001L);
        reqVO.setAssessmentType(RehabAssessmentConstants.TYPE_COMPREHENSIVE_ASSESSMENT);
        reqVO.setAssessmentDate(LocalDate.now());

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(assessmentRecordMapper.selectById(20001L)).thenReturn(RehabAssessmentRecordDO.builder()
                .id(20001L)
                .patientId(10001L)
                .episodeId(13001L)
                .status(RehabAssessmentConstants.STATUS_DRAFT)
                .assessmentType(RehabAssessmentConstants.TYPE_COMPREHENSIVE_ASSESSMENT)
                .build());
        when(episodeMapper.selectById(13001L)).thenReturn(RehabEpisodeDO.builder().id(13001L).patientId(10001L).build());

        when(moduleDataMapper.selectListByAssessmentId(20001L)).thenReturn(
                Collections.emptyList(),
                Collections.singletonList(RehabAssessmentModuleDataDO.builder()
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_COMPREHENSIVE)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED)
                        .build())
        );
        when(moduleDataMapper.selectByAssessmentIdAndModuleType(20001L, RehabAssessmentConstants.MODULE_COMPREHENSIVE)).thenReturn(
                null,
                RehabAssessmentModuleDataDO.builder()
                        .id(21002L)
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_COMPREHENSIVE)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED)
                        .dataJson("{}")
                        .build()
        );

        assessmentService.updateAssessment(reqVO, 1L);

        verify(moduleDataMapper).insert(org.mockito.ArgumentMatchers.<RehabAssessmentModuleDataDO>argThat(item ->
                Objects.equals(item.getAssessmentId(), 20001L)
                        && Objects.equals(item.getModuleType(), RehabAssessmentConstants.MODULE_COMPREHENSIVE)));
        verify(operationLogMapper).insert(any(RehabAssessmentOperationLogDO.class));
    }

    @Test
    void saveModuleData_shouldUpsertAndRefreshRawInputStatus() {
        RehabAssessmentModuleDataSaveReqVO reqVO = new RehabAssessmentModuleDataSaveReqVO();
        reqVO.setAssessmentId(20001L);
        reqVO.setModuleType(RehabAssessmentConstants.MODULE_STATIC);
        reqVO.setModuleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED);
        reqVO.setDataJson("{\"head_forward_angle\":12.5}");

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(assessmentRecordMapper.selectById(20001L)).thenReturn(RehabAssessmentRecordDO.builder()
                .id(20001L)
                .patientId(10001L)
                .status(RehabAssessmentConstants.STATUS_DRAFT)
                .build());
        when(moduleDataMapper.selectByAssessmentIdAndModuleType(20001L, RehabAssessmentConstants.MODULE_STATIC)).thenReturn(
                null,
                RehabAssessmentModuleDataDO.builder()
                        .id(21001L)
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_STATIC)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED)
                        .build()
        );
        when(moduleDataMapper.selectListByAssessmentId(20001L)).thenReturn(Collections.singletonList(
                RehabAssessmentModuleDataDO.builder()
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_STATIC)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED)
                        .build()
        ));

        assessmentService.saveModuleData(reqVO, 1L);

        verify(moduleDataMapper).updateById(any(RehabAssessmentModuleDataDO.class));
        verify(assessmentRecordMapper, atLeastOnce()).updateById(org.mockito.ArgumentMatchers.<RehabAssessmentRecordDO>argThat(item ->
                Objects.equals(item.getId(), 20001L)
                        && (Objects.equals(item.getRawInputStatus(), RehabAssessmentConstants.RAW_INPUT_COMPLETE)
                        || Objects.equals(item.getStatus(), RehabAssessmentConstants.STATUS_COMPLETED))));
        verify(operationLogMapper).insert(any(RehabAssessmentOperationLogDO.class));
    }

    @Test
    void createAssessment_shouldRejectWhenEpisodeDoesNotBelongToPatient() {
        RehabAssessmentCreateReqVO reqVO = new RehabAssessmentCreateReqVO();
        reqVO.setPatientId(10001L);
        reqVO.setEpisodeId(13001L);
        reqVO.setAssessmentType(RehabAssessmentConstants.TYPE_STATIC_ASSESSMENT);
        reqVO.setAssessmentDate(LocalDate.now());

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(patientMapper.selectById(10001L)).thenReturn(RehabPatientDO.builder().id(10001L).build());
        when(episodeMapper.selectById(13001L)).thenReturn(RehabEpisodeDO.builder().id(13001L).patientId(10002L).build());

        ServiceException ex = assertThrows(ServiceException.class, () -> assessmentService.createAssessment(reqVO, 1L));
        assertEquals(1_011_004_001, ex.getCode());
    }

    @Test
    void saveModuleData_shouldNotBlockWhenStaticSummaryBuilderFails() {
        RehabStaticAssessmentSummaryBuilder summaryBuilder = new RehabStaticAssessmentSummaryBuilder() {
            @Override
            public java.util.Map<String, Object> enrichWithSummary(Object rawDataJson) {
                throw new RuntimeException("summary-error");
            }

            @Override
            public java.util.Map<String, Object> enrichWithFallback(Object rawDataJson, String reason) {
                throw new RuntimeException("fallback-error");
            }
        };
        ReflectionTestUtils.setField(assessmentService, "staticAssessmentSummaryBuilder", summaryBuilder);

        RehabAssessmentModuleDataSaveReqVO reqVO = new RehabAssessmentModuleDataSaveReqVO();
        reqVO.setAssessmentId(20001L);
        reqVO.setModuleType(RehabAssessmentConstants.MODULE_STATIC);
        reqVO.setModuleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED);
        reqVO.setDataJson("{\"posterior_view\":{}}");

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(assessmentRecordMapper.selectById(20001L)).thenReturn(RehabAssessmentRecordDO.builder()
                .id(20001L)
                .patientId(10001L)
                .status(RehabAssessmentConstants.STATUS_DRAFT)
                .build());
        when(moduleDataMapper.selectByAssessmentIdAndModuleType(20001L, RehabAssessmentConstants.MODULE_STATIC)).thenReturn(
                RehabAssessmentModuleDataDO.builder()
                        .id(21001L)
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_STATIC)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_PARTIAL)
                        .dataJson("{\"old\":true}")
                        .build(),
                RehabAssessmentModuleDataDO.builder()
                        .id(21001L)
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_STATIC)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED)
                        .dataJson("{\"posterior_view\":{}}")
                        .build()
        );
        when(moduleDataMapper.selectListByAssessmentId(20001L)).thenReturn(Collections.singletonList(
                RehabAssessmentModuleDataDO.builder()
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_STATIC)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED)
                        .build()
        ));

        assertDoesNotThrow(() -> assessmentService.saveModuleData(reqVO, 1L));
        verify(moduleDataMapper).updateById(org.mockito.ArgumentMatchers.<RehabAssessmentModuleDataDO>argThat(item ->
                Objects.equals(item.getAssessmentId(), 20001L)
                        && Objects.equals(item.getModuleType(), RehabAssessmentConstants.MODULE_STATIC)
                        && Objects.equals(item.getDataJson(), "{\"posterior_view\":{}}")));
    }

    @Test
    void saveModuleData_shouldGenerateCesSummaryWhenModuleNasm() {
        RehabAssessmentModuleDataSaveReqVO reqVO = new RehabAssessmentModuleDataSaveReqVO();
        reqVO.setAssessmentId(20001L);
        reqVO.setModuleType(RehabAssessmentConstants.MODULE_NASM);
        reqVO.setModuleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED);
        reqVO.setDataJson("{\"transition_assessments\":{\"push_up\":{\"full_view\":{\"lphc\":{\"lumbar_sag\":{\"present\":true}}}}}}");

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(assessmentRecordMapper.selectById(20001L)).thenReturn(RehabAssessmentRecordDO.builder()
                .id(20001L)
                .patientId(10001L)
                .status(RehabAssessmentConstants.STATUS_DRAFT)
                .build());
        when(moduleDataMapper.selectByAssessmentIdAndModuleType(20001L, RehabAssessmentConstants.MODULE_NASM)).thenReturn(
                null,
                RehabAssessmentModuleDataDO.builder()
                        .id(22001L)
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_NASM)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED)
                        .build()
        );
        when(moduleDataMapper.selectListByAssessmentId(20001L)).thenReturn(Collections.singletonList(
                RehabAssessmentModuleDataDO.builder()
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_NASM)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED)
                        .build()
        ));

        assessmentService.saveModuleData(reqVO, 1L);

        verify(moduleDataMapper).updateById(org.mockito.ArgumentMatchers.<RehabAssessmentModuleDataDO>argThat(item ->
                Objects.equals(item.getAssessmentId(), 20001L)
                        && Objects.equals(item.getModuleType(), RehabAssessmentConstants.MODULE_NASM)
                        && item.getDataJson() != null
                        && item.getDataJson().contains("\"ces_summary\"")
                        && item.getDataJson().contains("\"action_summaries\"")
                        && item.getDataJson().contains("\"risk_precheck\"")
                        && item.getDataJson().contains("\"report_mapping\"")));
    }

    @Test
    void saveModuleData_shouldNotBlockWhenNasmSummaryBuilderFails() {
        RehabNasmCesSummaryBuilder summaryBuilder = new RehabNasmCesSummaryBuilder() {
            @Override
            public java.util.Map<String, Object> enrichWithSummary(Object rawDataJson) {
                throw new RuntimeException("nasm-summary-error");
            }

            @Override
            public java.util.Map<String, Object> enrichWithFallback(Object rawDataJson, String reason) {
                throw new RuntimeException("nasm-fallback-error");
            }
        };
        ReflectionTestUtils.setField(assessmentService, "nasmCesSummaryBuilder", summaryBuilder);

        RehabAssessmentModuleDataSaveReqVO reqVO = new RehabAssessmentModuleDataSaveReqVO();
        reqVO.setAssessmentId(20001L);
        reqVO.setModuleType(RehabAssessmentConstants.MODULE_NASM);
        reqVO.setModuleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED);
        reqVO.setDataJson("{\"transition_assessments\":{}}");

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(assessmentRecordMapper.selectById(20001L)).thenReturn(RehabAssessmentRecordDO.builder()
                .id(20001L)
                .patientId(10001L)
                .status(RehabAssessmentConstants.STATUS_DRAFT)
                .build());
        when(moduleDataMapper.selectByAssessmentIdAndModuleType(20001L, RehabAssessmentConstants.MODULE_NASM)).thenReturn(
                RehabAssessmentModuleDataDO.builder()
                        .id(22001L)
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_NASM)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_PARTIAL)
                        .dataJson("{\"old\":true}")
                        .build(),
                RehabAssessmentModuleDataDO.builder()
                        .id(22001L)
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_NASM)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED)
                        .build()
        );
        when(moduleDataMapper.selectListByAssessmentId(20001L)).thenReturn(Collections.singletonList(
                RehabAssessmentModuleDataDO.builder()
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_NASM)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED)
                        .build()
        ));

        assertDoesNotThrow(() -> assessmentService.saveModuleData(reqVO, 1L));
        verify(moduleDataMapper).updateById(org.mockito.ArgumentMatchers.<RehabAssessmentModuleDataDO>argThat(item ->
                Objects.equals(item.getAssessmentId(), 20001L)
                        && Objects.equals(item.getModuleType(), RehabAssessmentConstants.MODULE_NASM)
                        && Objects.equals(item.getDataJson(), "{\"transition_assessments\":{}}")));
    }

    @Test
    void saveModuleData_shouldGenerateSfmaSummaryWhenModuleSfma() {
        RehabAssessmentModuleDataSaveReqVO reqVO = new RehabAssessmentModuleDataSaveReqVO();
        reqVO.setAssessmentId(20001L);
        reqVO.setModuleType(RehabAssessmentConstants.MODULE_SFMA);
        reqVO.setModuleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED);
        reqVO.setDataJson("{\"top_tier\":{\"cervical_flexion\":{\"classification\":\"DN\"}}}");

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(assessmentRecordMapper.selectById(20001L)).thenReturn(RehabAssessmentRecordDO.builder()
                .id(20001L)
                .patientId(10001L)
                .status(RehabAssessmentConstants.STATUS_DRAFT)
                .build());
        when(moduleDataMapper.selectByAssessmentIdAndModuleType(20001L, RehabAssessmentConstants.MODULE_SFMA)).thenReturn(
                null,
                RehabAssessmentModuleDataDO.builder()
                        .id(23001L)
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_SFMA)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED)
                        .build()
        );
        when(moduleDataMapper.selectListByAssessmentId(20001L)).thenReturn(Collections.singletonList(
                RehabAssessmentModuleDataDO.builder()
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_SFMA)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED)
                        .build()
        ));

        assessmentService.saveModuleData(reqVO, 1L);

        verify(moduleDataMapper).updateById(org.mockito.ArgumentMatchers.<RehabAssessmentModuleDataDO>argThat(item ->
                Objects.equals(item.getAssessmentId(), 20001L)
                        && Objects.equals(item.getModuleType(), RehabAssessmentConstants.MODULE_SFMA)
                        && item.getDataJson() != null
                        && item.getDataJson().contains("\"summary\"")
                        && item.getDataJson().contains("\"risk_precheck\"")
                        && item.getDataJson().contains("\"report_mapping\"")
                        && item.getDataJson().contains("\"breakout_recommendations\"")));
    }

    @Test
    void saveModuleData_shouldNotBlockWhenSfmaSummaryBuilderFails() {
        RehabSfmaSummaryBuilder summaryBuilder = new RehabSfmaSummaryBuilder() {
            @Override
            public java.util.Map<String, Object> enrichWithSummary(Object rawDataJson) {
                throw new RuntimeException("sfma-summary-error");
            }

            @Override
            public java.util.Map<String, Object> enrichWithFallback(Object rawDataJson, String reason) {
                throw new RuntimeException("sfma-fallback-error");
            }
        };
        ReflectionTestUtils.setField(assessmentService, "sfmaSummaryBuilder", summaryBuilder);

        RehabAssessmentModuleDataSaveReqVO reqVO = new RehabAssessmentModuleDataSaveReqVO();
        reqVO.setAssessmentId(20001L);
        reqVO.setModuleType(RehabAssessmentConstants.MODULE_SFMA);
        reqVO.setModuleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED);
        reqVO.setDataJson("{\"top_tier\":{}}");

        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(assessmentRecordMapper.selectById(20001L)).thenReturn(RehabAssessmentRecordDO.builder()
                .id(20001L)
                .patientId(10001L)
                .status(RehabAssessmentConstants.STATUS_DRAFT)
                .build());
        when(moduleDataMapper.selectByAssessmentIdAndModuleType(20001L, RehabAssessmentConstants.MODULE_SFMA)).thenReturn(
                RehabAssessmentModuleDataDO.builder()
                        .id(23001L)
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_SFMA)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_PARTIAL)
                        .dataJson("{\"old\":true}")
                        .build(),
                RehabAssessmentModuleDataDO.builder()
                        .id(23001L)
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_SFMA)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED)
                        .build()
        );
        when(moduleDataMapper.selectListByAssessmentId(20001L)).thenReturn(Collections.singletonList(
                RehabAssessmentModuleDataDO.builder()
                        .assessmentId(20001L)
                        .moduleType(RehabAssessmentConstants.MODULE_SFMA)
                        .moduleStatus(RehabAssessmentConstants.MODULE_STATUS_COMPLETED)
                        .build()
        ));

        assertDoesNotThrow(() -> assessmentService.saveModuleData(reqVO, 1L));
        verify(moduleDataMapper).updateById(org.mockito.ArgumentMatchers.<RehabAssessmentModuleDataDO>argThat(item ->
                Objects.equals(item.getAssessmentId(), 20001L)
                        && Objects.equals(item.getModuleType(), RehabAssessmentConstants.MODULE_SFMA)
                        && Objects.equals(item.getDataJson(), "{\"top_tier\":{}}")));
    }

}
