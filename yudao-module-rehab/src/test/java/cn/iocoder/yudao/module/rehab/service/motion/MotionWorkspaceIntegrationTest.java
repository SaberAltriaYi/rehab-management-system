package cn.iocoder.yudao.module.rehab.service.motion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionAssessmentMapper;
import cn.iocoder.yudao.module.rehab.service.motion.workspace.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;

import org.junit.jupiter.api.*;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

class MotionWorkspaceIntegrationTest {
    @Test void controllerDoesNotExposeExceptionPayloads() {
        cn.iocoder.yudao.module.rehab.controller.admin.motion.RehabMotionWorkspaceController controller = new cn.iocoder.yudao.module.rehab.controller.admin.motion.RehabMotionWorkspaceController();
        MotionWorkspaceService failing=mock(MotionWorkspaceService.class);
        when(failing.launch(eq(2L),nullable(Long.class))).thenThrow(new IllegalStateException("synthetic-private-payload"));
        ReflectionTestUtils.setField(controller,"service",failing);
        cn.iocoder.yudao.framework.common.exception.ServiceException result=assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->controller.launch(2L));
        assertFalse(result.getMessage().contains("synthetic-private-payload"));
    }
    private RehabMotionAssessmentDO a;
    private MotionWorkspaceService service;
    private MotionWorkspaceClient client;
    private PermissionApi permissions;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        a =
                RehabMotionAssessmentDO.builder()
                        .id(2L)
                        .patientId(3L)
                        .episodeId(7L)
                        .inputRevision(1)
                        .status("WAITING_UPLOAD")
                        .build();
        a.setTenantId(1L);
        service = new MotionWorkspaceService();
        client = mock(MotionWorkspaceClient.class);
        RehabMotionAccess access = mock(RehabMotionAccess.class);
        when(access.readable(2L, 4L)).thenReturn(a);
        when(access.editable(2L, 4L)).thenReturn(a);
        RehabMotionEntitlement entitlement = mock(RehabMotionEntitlement.class);
        when(entitlement.enabled()).thenReturn(true);
        permissions = mock(PermissionApi.class);
        for (String right :
                Arrays.asList(
                        "rehab:motion:query",
                        "rehab:motion:workspace",
                        "rehab:motion:sync",
                        "rehab:motion:review"))
            when(permissions.hasAnyPermissions(4L, right)).thenReturn(true);
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                "jdbc:h2:mem:workspace"
                                        + UUID.randomUUID()
                                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                                "sa",
                                ""));
        jdbc.execute(
                "CREATE TABLE rehab_motion_workspace_inbox(id BIGINT AUTO_INCREMENT PRIMARY"
                    + " KEY,tenant_id BIGINT,assessment_id BIGINT,patient_id BIGINT,source_id"
                    + " VARCHAR(32),source_revision INT,core_revision INT,sha256"
                    + " VARCHAR(64),snapshot_json CLOB,received_by BIGINT,received_at TIMESTAMP"
                    + " DEFAULT CURRENT_TIMESTAMP,review_status VARCHAR(24) DEFAULT"
                    + " 'pending',review_note VARCHAR(1000),reviewed_by BIGINT,reviewed_at"
                    + " TIMESTAMP,UNIQUE(tenant_id,assessment_id,source_id,source_revision))");
        ReflectionTestUtils.setField(service, "access", access);
        ReflectionTestUtils.setField(service, "entitlement", entitlement);
        ReflectionTestUtils.setField(service, "permissionApi", permissions);
        ReflectionTestUtils.setField(service, "client", client);
        ReflectionTestUtils.setField(service, "mapper", mock(RehabMotionAssessmentMapper.class));
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
    }

    private Map<String, Object> result() {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("tenant", "1");
        binding.put("assessment", "2");
        binding.put("patient", "3");
        binding.put("episode", "7");
        binding.put("core_revision", 1);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schema", "rehab-workspace-sync/1");
        snapshot.put("binding", binding);
        snapshot.put("revision", 1);
        snapshot.put("assessment_id", "a-" + String.join("", Collections.nCopies(24, "a")));
        snapshot.put(
                "observations", Collections.singletonList(Collections.singletonMap("value", 0)));
        String raw = JsonUtils.toJsonString(snapshot);
        Map<String, Object> result = new HashMap<>();
        result.put("snapshot_json", raw);
        result.put("sha256", DigestUtil.sha256Hex(raw));
        result.put("revision", 1);
        return result;
    }

    @Test
    void structuredReceiveIsIdempotentAndDoesNotTouchScores() {
        when(client.exchange(anyString(), eq(HttpMethod.GET), isNull())).thenReturn(result());
        assertEquals("received_pending_clinical_review", service.receive(2L, 4L).get("state"));
        assertEquals("already_received", service.receive(2L, 4L).get("state"));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM rehab_motion_workspace_inbox", Integer.class));
        service.review(2L, 1L, "Synthetic clinician review", 4L);
        assertEquals(
                "reviewed",
                jdbc.queryForObject(
                        "SELECT review_status FROM rehab_motion_workspace_inbox", String.class));
        assertThrows(
                IllegalStateException.class,
                () -> service.review(2L, 1L, "must not overwrite prior review", 4L));
        assertEquals(
                0,
                ((Number)
                                ((Map<?, ?>)
                                                ((List<?>)
                                                                MotionWorkspaceService
                                                                        .validateSnapshot(
                                                                                result(), a)
                                                                        .get("observations"))
                                                        .get(0))
                                        .get("value"))
                        .intValue());
    }

    @Test
    void hashTamperingAndStaleOrWrongPatientAreRejected() {
        Map<String, Object> r = result();
        r.put("sha256", "0".repeat(64));
        assertThrows(
                IllegalStateException.class, () -> MotionWorkspaceService.validateSnapshot(r, a));
        a.setInputRevision(2);
        assertThrows(
                IllegalStateException.class,
                () -> MotionWorkspaceService.validateSnapshot(result(), a));
        a.setInputRevision(1);
        a.setPatientId(99L);
        assertThrows(
                IllegalStateException.class,
                () -> MotionWorkspaceService.validateSnapshot(result(), a));
        a.setPatientId(3L);
        a.setTenantId(9L);
        assertThrows(
                IllegalStateException.class,
                () -> MotionWorkspaceService.validateSnapshot(result(), a));
    }

    @Test
    void missingDedicatedPermissionNeverContactsWorkspace() {
        when(permissions.hasAnyPermissions(4L, "rehab:motion:sync")).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.receive(2L, 4L));
        verifyNoInteractions(client);
    }

    @Test
    void unsafeAddressesAndUntrustedEntryTokensAreRejected() {
        for (String url :
                Arrays.asList(
                        "http://192.168.1.2:8806",
                        "https://user:password@example.org",
                        "https://example.org/?token=x",
                        "file:///tmp/x"))
            assertThrows(IllegalStateException.class, () -> MotionWorkspaceClient.checkedUrl(url));
        assertEquals(
                "http://127.0.0.1:8806",
                MotionWorkspaceClient.checkedUrl("http://127.0.0.1:8806/"));
        assertEquals(
                "https://assessment.example.org",
                MotionWorkspaceClient.checkedUrl("https://assessment.example.org"));
        assertThrows(
                IllegalStateException.class,
                () -> new MotionWorkspaceClient().entryUrl("injected#token"));
    }
}
