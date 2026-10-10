package cn.iocoder.yudao.module.system.framework.license;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TenantLicenseStatusSynchronizerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TenantLicenseImportService importer = mock(TenantLicenseImportService.class);

    private TenantLicenseStatusSynchronizer make(String url) {
        TenantLicenseStatusSynchronizer sync = new TenantLicenseStatusSynchronizer(jdbc, importer);
        ReflectionTestUtils.setField(sync, "ownerUrl", url);
        return sync;
    }
    private Map<String, Object> row(long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("tenant_id", id);
        result.put("tenant_uuid", "f12b2687-09a1-40b5-b47a-747d75ce5551");
        result.put("installation_uuid", "d94123bf-2c94-4af4-9d5b-7ab8ce690509");
        return result;
    }
    @Test public void offlineModeDoesNotAccessNetworkOrDatabase() {
        TenantLicenseStatusSynchronizer service = make("");
        service.validateUrl();
        service.syncAll();
        verifyNoInteractions(jdbc, importer);
    }
    @Test public void statusSourceMustBeSinglePinnedHttpsEndpoint() {
        TenantLicenseStatusSynchronizer service = make("http://owner.example/");
        assertThrows(IllegalStateException.class, service::validateUrl);
    }
    @Test public void httpUrlCannotBeConfiguredAsTrustedOwner() {
        TenantLicenseStatusSynchronizer service = make("http://owner.example/license/v1/status");
        assertThrows(IllegalStateException.class, service::validateUrl);
        service = make("https://attacker@owner.example/license/v1/status");
        assertThrows(IllegalStateException.class, service::validateUrl);
        service = make("https://owner.example/license/v1/status?u=1");
        assertThrows(IllegalStateException.class, service::validateUrl);
        service = make("https://owner.example/license/v1/status");
        assertDoesNotThrow(service::validateUrl);
    }
    @Test public void signedStatusIsStoredOnlyViaVerifiedImportService() throws Exception {
        TenantLicenseStatusSynchronizer s = spy(make("https://owner.example/license/v1/status"));
        doReturn("synthetic-status").when(s).fetchSignedStatus(anyString(), anyString());
        when(jdbc.queryForList(contains("FROM rehab_tenant_license_binding")))
                .thenReturn(Collections.singletonList(row(11L)));
        s.syncAll();
        verify(importer).refreshSignedStatus(11L, "synthetic-status");
    }
    @Test public void unavailablePublisherLeavesLastLocalStatusUnchanged() throws Exception {
        TenantLicenseStatusSynchronizer s = spy(make("https://owner.example/license/v1/status"));
        doThrow(new IllegalStateException("synthetic network down"))
                .when(s).fetchSignedStatus(anyString(), anyString());
        when(jdbc.queryForList(contains("FROM rehab_tenant_license_binding")))
                .thenReturn(Collections.singletonList(row(11L)));
        s.syncAll();
        verifyNoInteractions(importer);
    }
    @Test public void batchIsBoundedAndRoundRobinFair() throws Exception {
        TenantLicenseStatusSynchronizer s = spy(make("https://owner.example/license/v1/status"));
        doReturn("synthetic").when(s).fetchSignedStatus(anyString(), anyString());
        List<Map<String, Object>> many = new ArrayList<>();
        for (long i = 0; i < 65; i++) many.add(row(100L + i));
        when(jdbc.queryForList(contains("FROM rehab_tenant_license_binding"))).thenReturn(many);
        s.syncAll();
        verify(importer, times(50)).refreshSignedStatus(anyLong(), eq("synthetic"));
        verify(importer, never()).refreshSignedStatus(eq(164L), anyString());
        s.syncAll();
        verify(importer, atLeastOnce()).refreshSignedStatus(eq(164L), eq("synthetic"));
        verify(importer, times(100)).refreshSignedStatus(anyLong(), eq("synthetic"));
    }
}
