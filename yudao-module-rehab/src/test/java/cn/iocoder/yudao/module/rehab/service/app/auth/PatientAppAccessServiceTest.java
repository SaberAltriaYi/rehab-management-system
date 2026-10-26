package cn.iocoder.yudao.module.rehab.service.app.auth;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Synthetic-only enrollment boundary tests; no running service or real account. */
@ExtendWith(MockitoExtension.class)
class PatientAppAccessServiceTest {
    private static final Long TENANT = 71L;
    private static final Long MEMBER = 501L;
    private static final Long PATIENT = 801L;
    private static final String PHONE = "13800000001";
    private static final String INVITATION = new PatientInvitationTokens(new byte[32]).generate();
    private static final String SECRET = String.join("", Collections.nCopies(40, "q"));

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private RehabPatientMapper patientMapper;
    @Mock private RehabPatientUserBindingMapper bindingMapper;
    @Mock private MemberUserApi memberUserApi;
    @InjectMocks private PatientAppAccessService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        ReflectionTestUtils.setField(service, "invitationSecret", SECRET);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private MemberUserRespDTO currentMember() {
        MemberUserRespDTO member = new MemberUserRespDTO();
        member.setId(MEMBER);
        member.setMobile(PHONE);
        member.setStatus(CommonStatusEnum.ENABLE.getStatus());
        when(memberUserApi.getUser(MEMBER)).thenReturn(member);
        return member;
    }

    private void pendingInvitation() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 901L);
        row.put("patient_id", PATIENT);
        row.put("phone", PHONE);
        row.put("invitation_digest", new PatientInvitationTokens(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .digest(TENANT, INVITATION));
        when(jdbcTemplate.queryForList(anyString(), eq(TENANT), anyString(), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(row));
    }

    private void patient() {
        when(patientMapper.selectById(PATIENT)).thenReturn(RehabPatientDO.builder()
                .id(PATIENT).name("合成验收对象").patientNo("SYNTHETIC-801").phone(PHONE).build());
    }

    private Long redeem(String phone, String type) {
        return service.redeemInvitation("SYNTHETIC-801", phone, INVITATION, type, null, MEMBER);
    }

    @Test
    void requestPhoneCannotImpersonateAnotherMembersVerifiedPhone() {
        currentMember().setMobile("13800000002");
        assertEquals(APP_PATIENT_INVITATION_INVALID.getCode(),
                assertThrows(ServiceException.class, () -> redeem(PHONE, "self")).getCode());
        verifyNoInteractions(jdbcTemplate, patientMapper, bindingMapper);
    }

    @Test
    void disabledMemberCannotRedeemEvenWithCorrectPhoneAndInvitation() {
        currentMember().setStatus(CommonStatusEnum.DISABLE.getStatus());
        assertThrows(ServiceException.class, () -> redeem(PHONE, "self"));
        verifyNoInteractions(jdbcTemplate, patientMapper, bindingMapper);
    }

    @Test
    void missingMemberFailsClosed() {
        assertThrows(ServiceException.class, () -> redeem(PHONE, "self"));
        verifyNoInteractions(jdbcTemplate, patientMapper, bindingMapper);
    }

    @Test
    void wrongMemberReturnedByAdapterFailsClosed() {
        currentMember().setId(502L);
        assertThrows(ServiceException.class, () -> redeem(PHONE, "self"));
        verifyNoInteractions(jdbcTemplate, patientMapper, bindingMapper);
    }

    @Test
    void caregiverImportedAndUnknownTypesRequireIndependentAuthorization() {
        for (String type : new String[] {"caregiver", "imported", "admin", "SELF"}) {
            assertThrows(ServiceException.class, () -> redeem(PHONE, type));
        }
        verifyNoInteractions(memberUserApi, jdbcTemplate, patientMapper, bindingMapper);
    }

    @Test
    void missingIdentityIsRejectedBeforeAnyLookup() {
        assertThrows(ServiceException.class, () -> service.redeemInvitation(
                null, PHONE, INVITATION, "self", null, null));
        verifyNoInteractions(memberUserApi, jdbcTemplate, patientMapper, bindingMapper);
    }

    @Test
    void malformedInvitationHasUniformErrorWithoutDatabaseLookup() {
        currentMember();
        assertEquals(APP_PATIENT_INVITATION_INVALID.getCode(), assertThrows(ServiceException.class,
                () -> service.redeemInvitation(null, PHONE, "invalid", "self", null, MEMBER)).getCode());
        verifyNoInteractions(jdbcTemplate, patientMapper, bindingMapper);
    }

    @Test
    void weakOrAbsentDeploymentSecretKeepsEnrollmentClosed() {
        currentMember();
        ReflectionTestUtils.setField(service, "invitationSecret", "");
        assertEquals(APP_PATIENT_AUTH_DISABLED.getCode(),
                assertThrows(ServiceException.class, () -> redeem(PHONE, "self")).getCode());
        verifyNoInteractions(jdbcTemplate, patientMapper, bindingMapper);
    }

    @Test
    void successfulRedemptionLocksOnlyTenantDigestAndStoresSelfBinding() {
        currentMember();
        pendingInvitation();
        patient();
        when(jdbcTemplate.update(anyString(), eq(MEMBER), eq(TENANT), eq(901L))).thenReturn(1);
        assertEquals(PATIENT, redeem(PHONE, null));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture(), eq(TENANT), anyString(), any(LocalDateTime.class));
        assertTrue(sql.getValue().contains("tenant_id=? AND invitation_digest=?"));
        assertTrue(sql.getValue().contains("deleted=FALSE"));
        assertTrue(sql.getValue().endsWith("FOR UPDATE"));
        ArgumentCaptor<RehabPatientUserBindingDO> binding = ArgumentCaptor.forClass(RehabPatientUserBindingDO.class);
        verify(bindingMapper).insert(binding.capture());
        assertEquals("self", binding.getValue().getBindType());
        assertEquals(MEMBER, binding.getValue().getAppUserId());
        assertEquals(PHONE, binding.getValue().getPhone());
    }

    @Test
    void expiredRevokedOrConsumedInvitationCannotCreateBinding() {
        currentMember();
        when(jdbcTemplate.queryForList(anyString(), eq(TENANT), anyString(), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        assertThrows(ServiceException.class, () -> redeem(PHONE, "self"));
        verifyNoInteractions(patientMapper, bindingMapper);
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void wrongPatientNumberIsRejectedBeforeBinding() {
        currentMember();
        pendingInvitation();
        patient();
        assertThrows(ServiceException.class, () -> service.redeemInvitation(
                "ANOTHER", PHONE, INVITATION, "self", null, MEMBER));
        verifyNoInteractions(bindingMapper);
    }

    @Test
    void bindingToAnotherPatientCannotBeReplacedByInvitation() {
        currentMember();
        pendingInvitation();
        patient();
        when(bindingMapper.selectActiveByAppUserId(MEMBER)).thenReturn(
                RehabPatientUserBindingDO.builder().patientId(802L).build());
        assertEquals(APP_PATIENT_BINDING_CONFLICT.getCode(),
                assertThrows(ServiceException.class, () -> redeem(PHONE, "self")).getCode());
        verify(bindingMapper, never()).insert(any(RehabPatientUserBindingDO.class));
    }

    @Test
    void concurrentUniqueBindingConflictIsAnEnrollmentErrorNotSqlDetails() {
        currentMember();
        pendingInvitation();
        patient();
        when(bindingMapper.insert(any(RehabPatientUserBindingDO.class)))
                .thenThrow(new DuplicateKeyException("synthetic database detail"));
        ServiceException error = assertThrows(ServiceException.class, () -> redeem(PHONE, "self"));
        assertEquals(APP_PATIENT_BINDING_CONFLICT.getCode(), error.getCode());
        assertFalse(error.getMessage().contains("synthetic database detail"));
    }

    @Test
    void failedAtomicConsumptionIsRejectedAndMustRollBackTransaction() {
        currentMember();
        pendingInvitation();
        patient();
        assertEquals(APP_PATIENT_INVITATION_INVALID.getCode(),
                assertThrows(ServiceException.class, () -> redeem(PHONE, "self")).getCode());
    }

    @Test
    void noBindingDeniesSessionWithoutMemberLookup() {
        assertThrows(ServiceException.class, () -> service.validateActiveBinding(MEMBER));
        assertThrows(ServiceException.class, () -> service.validateActiveBinding(null));
        verifyNoInteractions(memberUserApi, patientMapper);
    }

    private void activeBinding() {
        when(bindingMapper.selectActiveByAppUserId(MEMBER)).thenReturn(RehabPatientUserBindingDO.builder()
                .patientId(PATIENT).appUserId(MEMBER).phone(PHONE).bindType("self").bindStatus("active").build());
    }

    @Test
    void changedMemberPhoneRevokesBindingAccess() {
        activeBinding();
        currentMember().setMobile("13800000002");
        assertThrows(ServiceException.class, () -> service.validateActiveBinding(MEMBER));
        verifyNoInteractions(patientMapper);
    }

    @Test
    void changedPatientPhoneRevokesBindingAccess() {
        activeBinding();
        currentMember();
        when(patientMapper.selectById(PATIENT)).thenReturn(RehabPatientDO.builder()
                .id(PATIENT).phone("13800000002").build());
        assertThrows(ServiceException.class, () -> service.validateActiveBinding(MEMBER));
    }

    @Test
    void unchangedActiveMemberAndPatientCanContinueSession() {
        activeBinding();
        currentMember();
        patient();
        assertDoesNotThrow(() -> service.validateActiveBinding(MEMBER));
    }
}
