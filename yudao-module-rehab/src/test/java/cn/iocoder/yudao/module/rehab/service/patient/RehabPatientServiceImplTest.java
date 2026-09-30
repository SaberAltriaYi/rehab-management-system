package cn.iocoder.yudao.module.rehab.service.patient;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientBindCrmReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabCrmConflictCheckRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientCheckCrmConflictReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientCreateReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientCreateRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientImportExcelVO;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientImportRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientTransferReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assignment.RehabTherapistAssignmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.binding.RehabPatientCrmBindingDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.binding.RehabPatientUserBindingDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.log.RehabPatientOperationLogDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.assignment.RehabTherapistAssignmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.binding.RehabPatientCrmBindingMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.binding.RehabPatientUserBindingMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.episode.RehabEpisodeMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.log.RehabPatientOperationLogMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabAssignmentConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabCrmBindingConstants;
import cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabRoleCodeConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.episode.RehabEpisodeService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RehabPatientServiceImplTest {

    private RehabPatientServiceImpl patientService;

    @Mock
    private RehabPatientMapper patientMapper;
    @Mock
    private RehabPatientCrmBindingMapper crmBindingMapper;
    @Mock
    private RehabPatientUserBindingMapper patientUserBindingMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private RehabTherapistAssignmentMapper assignmentMapper;
    @Mock
    private RehabEpisodeMapper episodeMapper;
    @Mock
    private RehabPatientOperationLogMapper operationLogMapper;
    @Mock
    private RehabEpisodeService episodeService;
    @Mock
    private PermissionApi permissionApi;
    @Mock
    private AdminUserApi adminUserApi;

    @BeforeEach
    void setUp() {
        patientService = new RehabPatientServiceImpl();
        RehabDataPermissionService dataPermissionService = new RehabDataPermissionService();
        ReflectionTestUtils.setField(dataPermissionService, "permissionApi", permissionApi);
        ReflectionTestUtils.setField(dataPermissionService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(dataPermissionService, "assignmentMapper", assignmentMapper);

        ReflectionTestUtils.setField(patientService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(patientService, "crmBindingMapper", crmBindingMapper);
        ReflectionTestUtils.setField(patientService, "patientUserBindingMapper", patientUserBindingMapper);
        ReflectionTestUtils.setField(patientService, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(patientService, "assignmentMapper", assignmentMapper);
        ReflectionTestUtils.setField(patientService, "episodeMapper", episodeMapper);
        ReflectionTestUtils.setField(patientService, "operationLogMapper", operationLogMapper);
        ReflectionTestUtils.setField(patientService, "episodeService", episodeService);
        ReflectionTestUtils.setField(patientService, "dataPermissionService", dataPermissionService);
        ReflectionTestUtils.setField(patientService, "adminUserApi", adminUserApi);
        ReflectionTestUtils.setField(patientService, "self", patientService);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void getCrmBinding_shouldScopeRawQueryToCurrentTenant() throws Exception {
        TenantContextHolder.setTenantId(2L);
        when(patientMapper.selectById(10001L)).thenReturn(RehabPatientDO.builder().id(10001L).build());
        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(crmBindingMapper.selectByPatientId(10001L))
                .thenReturn(RehabPatientCrmBindingDO.builder().patientId(10001L).crmCustomerId(50001L).build());

        patientService.getCrmBinding(10001L, 1L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PreparedStatementSetter> setter = ArgumentCaptor.forClass(PreparedStatementSetter.class);
        verify(jdbcTemplate).query(sql.capture(), setter.capture(), any(ResultSetExtractor.class));
        assertTrue(sql.getValue().contains("tenant_id = ?"));
        PreparedStatement statement = mock(PreparedStatement.class);
        setter.getValue().setValues(statement);
        verify(statement).setLong(1, 50001L);
        verify(statement).setLong(2, 2L);
    }

    @Test
    void getMemberBinding_shouldScopeRawQueryToCurrentTenant() throws Exception {
        TenantContextHolder.setTenantId(3L);
        when(patientMapper.selectById(10001L)).thenReturn(RehabPatientDO.builder().id(10001L).build());
        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(patientUserBindingMapper.selectActiveByPatientId(10001L))
                .thenReturn(RehabPatientUserBindingDO.builder().patientId(10001L).appUserId(60001L).build());

        patientService.getMemberBinding(10001L, 1L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PreparedStatementSetter> setter = ArgumentCaptor.forClass(PreparedStatementSetter.class);
        verify(jdbcTemplate).query(sql.capture(), setter.capture(), any(ResultSetExtractor.class));
        assertTrue(sql.getValue().contains("tenant_id = ?"));
        PreparedStatement statement = mock(PreparedStatement.class);
        setter.getValue().setValues(statement);
        verify(statement).setLong(1, 60001L);
        verify(statement).setLong(2, 3L);
    }

    @Test
    void bindCrm_shouldRejectCustomerNotVisibleInTenantWithoutWritingBinding() {
        TenantContextHolder.setTenantId(2L);
        when(patientMapper.selectById(10001L)).thenReturn(RehabPatientDO.builder().id(10001L).build());
        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        RehabPatientBindCrmReqVO request = new RehabPatientBindCrmReqVO();
        request.setPatientId(10001L);
        request.setCrmCustomerId(50001L);

        ServiceException error = assertThrows(ServiceException.class, () -> patientService.bindCrm(request, 1L));

        assertEquals(ErrorCodeConstants.CRM_CUSTOMER_NOT_ACCESSIBLE.getCode(), error.getCode());
        verify(crmBindingMapper, never()).insert(any(RehabPatientCrmBindingDO.class));
        verify(crmBindingMapper, never()).updateById(any(RehabPatientCrmBindingDO.class));
    }

    @Test
    void bindCrm_shouldAcceptExistingCustomerWithinCurrentTenant() throws Exception {
        TenantContextHolder.setTenantId(2L);
        when(patientMapper.selectById(10001L)).thenReturn(RehabPatientDO.builder().id(10001L).build());
        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(ResultSetExtractor.class)))
                .thenAnswer(invocation -> {
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.next()).thenReturn(true);
                    when(resultSet.getLong("id")).thenReturn(50001L);
                    when(resultSet.getString("name")).thenReturn("测试 CRM 客户");
                    ResultSetExtractor<?> extractor = invocation.getArgument(2);
                    return extractor.extractData(resultSet);
                });
        RehabPatientBindCrmReqVO request = new RehabPatientBindCrmReqVO();
        request.setPatientId(10001L);
        request.setCrmCustomerId(50001L);

        patientService.bindCrm(request, 1L);

        verify(crmBindingMapper).insert(org.mockito.ArgumentMatchers.<RehabPatientCrmBindingDO>argThat(binding ->
                Long.valueOf(10001L).equals(binding.getPatientId())
                        && Long.valueOf(50001L).equals(binding.getCrmCustomerId())
                        && RehabCrmBindingConstants.STATUS_BOUND.equals(binding.getBindStatus())));
    }

    @Test
    void bindCrm_shouldFailClosedWhenTenantContextIsMissing() {
        when(patientMapper.selectById(10001L)).thenReturn(RehabPatientDO.builder().id(10001L).build());
        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);
        RehabPatientBindCrmReqVO request = new RehabPatientBindCrmReqVO();
        request.setPatientId(10001L);
        request.setCrmCustomerId(50001L);

        ServiceException error = assertThrows(ServiceException.class, () -> patientService.bindCrm(request, 1L));

        assertEquals(ErrorCodeConstants.CRM_CUSTOMER_NOT_ACCESSIBLE.getCode(), error.getCode());
        verifyNoInteractions(jdbcTemplate);
        verify(crmBindingMapper, never()).insert(any(RehabPatientCrmBindingDO.class));
    }

    @Test
    void createPatient_shouldGeneratePatientNoAndReturnDuplicateHint() {
        RehabPatientCreateReqVO reqVO = new RehabPatientCreateReqVO();
        reqVO.setName("王小明");
        reqVO.setPhone("13800138000");
        reqVO.setInitEpisode(false);

        when(patientMapper.selectListByNameAndPhone("王小明", "13800138000"))
                .thenReturn(Collections.singletonList(RehabPatientDO.builder().id(999L).build()));
        doAnswer(invocation -> {
            RehabPatientDO patient = invocation.getArgument(0);
            patient.setId(123L);
            return 1;
        }).when(patientMapper).insert(any(RehabPatientDO.class));
        when(episodeService.createInitialEpisodeIfNeeded(anyLong(), any(), any(), any(), any(), anyLong()))
                .thenReturn(null);

        RehabPatientCreateRespVO respVO = patientService.createPatient(reqVO, 1L);

        assertNotNull(respVO);
        assertTrue(respVO.getSuspectedDuplicate());
        assertEquals(Collections.singletonList(999L), respVO.getDuplicatePatientIds());
        String expectedPrefix = "PT" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertTrue(respVO.getPatientNo().startsWith(expectedPrefix));
        assertTrue(respVO.getPatientNo().endsWith("0123"));
        verify(patientMapper).updateById(org.mockito.ArgumentMatchers.<RehabPatientDO>argThat(update ->
                Objects.equals(update.getId(), 123L)
                        && Objects.equals(update.getPatientNo(), respVO.getPatientNo())));
        verify(operationLogMapper).insert(any(RehabPatientOperationLogDO.class));
    }

    @Test
    void importPatients_shouldCreateSkipDuplicateAndReturnFailureWorkbook() {
        RehabPatientImportExcelVO created = new RehabPatientImportExcelVO();
        created.setName("批量患者甲");
        created.setPhone("13800000001");

        RehabPatientImportExcelVO duplicate = new RehabPatientImportExcelVO();
        duplicate.setPatientNo("PT-EXISTS");
        duplicate.setName("重复患者");

        RehabPatientImportExcelVO invalid = new RehabPatientImportExcelVO();
        invalid.setName(" ");

        when(patientMapper.selectByPatientNo("PT-EXISTS"))
                .thenReturn(RehabPatientDO.builder().id(998L).patientNo("PT-EXISTS").build());
        when(patientMapper.selectListByNameAndPhone("批量患者甲", "13800000001"))
                .thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            RehabPatientDO patient = invocation.getArgument(0);
            patient.setId(321L);
            return 1;
        }).when(patientMapper).insert(any(RehabPatientDO.class));
        when(episodeService.createInitialEpisodeIfNeeded(anyLong(), any(), any(), any(), any(), anyLong()))
                .thenReturn(null);

        RehabPatientImportRespVO result = patientService.importPatients(
                List.of(created, duplicate, invalid), 1L);

        assertEquals(3, result.getTotalCount());
        assertEquals(1, result.getCreatedCount());
        assertEquals(1, result.getSkippedCount());
        assertEquals(1, result.getFailureCount());
        assertEquals(4, result.getFailures().get(0).getRowNumber());
        assertNotNull(result.getFailureExcelBase64());
        assertFalse(result.getFailureExcelBase64().isEmpty());
        verify(patientMapper, times(1)).insert(any(RehabPatientDO.class));
    }

    @Test
    void checkCrmConflict_shouldReturnConflictWhenOtherPatientAlreadyBound() {
        RehabPatientCheckCrmConflictReqVO reqVO = new RehabPatientCheckCrmConflictReqVO();
        reqVO.setPatientId(10001L);
        reqVO.setCrmCustomerId(50001L);

        List<RehabPatientCrmBindingDO> bindings = List.of(
                RehabPatientCrmBindingDO.builder().patientId(10001L).bindStatus(RehabCrmBindingConstants.STATUS_BOUND).build(),
                RehabPatientCrmBindingDO.builder().patientId(10002L).bindStatus(RehabCrmBindingConstants.STATUS_BOUND).build(),
                RehabPatientCrmBindingDO.builder().patientId(10003L).bindStatus(RehabCrmBindingConstants.STATUS_CONFLICT).build()
        );
        when(crmBindingMapper.selectListByCrmCustomerId(50001L)).thenReturn(bindings);

        RehabCrmConflictCheckRespVO respVO = patientService.checkCrmConflict(reqVO);

        assertTrue(respVO.getConflict());
        assertEquals(Collections.singletonList(10002L), respVO.getConflictPatientIds());
    }

    @Test
    void transferTherapist_shouldCloseOldPrimaryAndCreateNewPrimary() {
        RehabPatientTransferReqVO reqVO = new RehabPatientTransferReqVO();
        reqVO.setPatientId(10001L);
        reqVO.setToTherapistUserId(104L);
        reqVO.setReason("阶段转交");

        when(patientMapper.selectById(10001L)).thenReturn(RehabPatientDO.builder().id(10001L).build());
        when(permissionApi.hasAnyRoles(1L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(true);

        RehabTherapistAssignmentDO oldPrimary = RehabTherapistAssignmentDO.builder()
                .id(12001L)
                .patientId(10001L)
                .therapistUserId(100L)
                .roleType(RehabAssignmentConstants.ROLE_PRIMARY)
                .assignStatus(RehabAssignmentConstants.STATUS_ACTIVE)
                .build();
        when(assignmentMapper.selectActivePrimaryByPatientId(10001L)).thenReturn(oldPrimary);

        patientService.transferTherapist(reqVO, 1L);

        verify(assignmentMapper).updateById(org.mockito.ArgumentMatchers.<RehabTherapistAssignmentDO>argThat(item ->
                Objects.equals(item.getId(), 12001L)
                        && Objects.equals(item.getAssignStatus(), RehabAssignmentConstants.STATUS_TRANSFERRED)
                        && Objects.equals(item.getTransferToUserId(), 104L)
                        && item.getEndTime() != null));
        verify(assignmentMapper).insert(org.mockito.ArgumentMatchers.<RehabTherapistAssignmentDO>argThat(item ->
                Objects.equals(item.getPatientId(), 10001L)
                        && Objects.equals(item.getTherapistUserId(), 104L)
                        && Objects.equals(item.getRoleType(), RehabAssignmentConstants.ROLE_PRIMARY)
                        && Objects.equals(item.getTransferFromUserId(), 100L)));
        verify(patientMapper).updateById(org.mockito.ArgumentMatchers.<RehabPatientDO>argThat(item ->
                Objects.equals(item.getId(), 10001L)
                        && Objects.equals(item.getCurrentTherapistUserId(), 104L)));
        verify(operationLogMapper).insert(any(RehabPatientOperationLogDO.class));
    }

}
