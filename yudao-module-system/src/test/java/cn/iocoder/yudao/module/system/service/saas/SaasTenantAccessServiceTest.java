package cn.iocoder.yudao.module.system.service.saas;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SaasTenantAccessServiceTest {
    private JdbcTemplate jdbc;
    private SaasTenantAccessService service;
    @BeforeEach void setup() {
        jdbc = mock(JdbcTemplate.class);
        service = new SaasTenantAccessService(jdbc);
        TenantContextHolder.setTenantId(910001L);
    }
    @AfterEach void cleanup() {
        TenantContextHolder.clear();
        TransactionSynchronizationManager.clear();
    }
    @Test void missingTenantIsDeniedWithoutDatabaseAccess() {
        TenantContextHolder.clear();
        assertThrows(SaasTenantAccessService.AccessDenied.class, service::requireCurrentTenant);
        verifyNoInteractions(jdbc);
    }
    @Test void ignoreTenantContextIsNotABypass() {
        TenantContextHolder.setIgnore(true);
        assertThrows(SaasTenantAccessService.AccessDenied.class, service::requireCurrentTenant);
        verifyNoInteractions(jdbc);
    }
    @Test void unavailableStoreFailsClosed() {
        when(jdbc.queryForList(anyString(), eq(910001L))).thenThrow(new IllegalStateException("synthetic unavailable"));
        assertThrows(IllegalStateException.class, service::requireCurrentTenant);
    }
    @Test void missingSubscriptionIsDenied() {
        when(jdbc.queryForList(anyString(), eq(910001L))).thenReturn(Collections.emptyList());
        assertThrows(SaasTenantAccessService.AccessDenied.class, service::requireCurrentTenant);
    }
    @Test void queryAlwaysScopesTenantAndLifecycle() {
        when(jdbc.queryForList(anyString(), eq(910001L))).thenReturn(Collections.singletonList(new HashMap<>()));
        service.requireCurrentTenant();
        verify(jdbc).queryForList(argThat((String sql) -> sql.contains("s.tenant_id=?")
                && sql.contains("t.registration_status='active'") && sql.contains("t.deleted=0")
                && sql.contains("s.expire_time>CURRENT_TIMESTAMP")), eq(910001L));
    }
    @Test void quotaRequiresBusinessTransaction() {
        assertThrows(IllegalStateException.class, () -> service.reserve(SaasTenantAccessService.Resource.USER, 1));
        verifyNoInteractions(jdbc);
    }
    @Test void negativeReservationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.reserve(SaasTenantAccessService.Resource.USER, -1));
        verifyNoInteractions(jdbc);
    }
    private void plan(long limit) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(910001L))).thenReturn(Collections.singletonList(910001L));
        Map<String,Object> row = new HashMap<>(); row.put("max_users", limit);
        when(jdbc.queryForList(anyString(), eq(910001L))).thenReturn(Collections.singletonList(row));
    }
    @Test void overQuotaNeverUpdatesUsage() {
        plan(0);
        assertThrows(SaasTenantAccessService.AccessDenied.class, () -> service.reserve(SaasTenantAccessService.Resource.USER, 1));
        verify(jdbc, never()).update(anyString(), anyLong(), anyLong(), anyLong());
    }
    @Test void missingUsageCannotGrantQuota() {
        plan(10);
        when(jdbc.update(anyString(), eq(1L), eq(910001L), eq(9L))).thenReturn(0);
        assertThrows(SaasTenantAccessService.AccessDenied.class, () -> service.reserve(SaasTenantAccessService.Resource.USER, 1));
    }
    @Test void reservationUsesBoundedAtomicTenantUpdate() {
        plan(10);
        when(jdbc.update(anyString(), eq(1L), eq(910001L), eq(9L))).thenReturn(1);
        assertDoesNotThrow(() -> service.reserve(SaasTenantAccessService.Resource.USER, 1));
        verify(jdbc).update(contains("WHERE tenant_id=?"), eq(1L), eq(910001L), eq(9L));
    }
}
