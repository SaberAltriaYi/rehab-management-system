package cn.iocoder.yudao.module.system.service.saas;

import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import cn.iocoder.yudao.module.system.controller.admin.saas.SaasProvisioningController;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.same;

/** Synthetic JDBC contract; not a deployed production MySQL or HTTP end-to-end test. */
class SaasEntitlementSyncContractTest {
    @Test
    void privateSyncEndpointRejectsAbsentOrIncorrectMachineToken() {
        SaasProvisioningService service = mock(SaasProvisioningService.class);
        String machineToken = "synthetic-only-strong-token-for-internal-tests";
        SaasProvisioningController controller = new SaasProvisioningController(service, machineToken);
        SaasEntitlementSyncRequest request = new SaasEntitlementSyncRequest();
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.syncEntitlements(null, request));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.syncEntitlements("invalid-token", request));
        when(service.syncEntitlements(same(request))).thenReturn(java.util.Map.of("status", "ACTIVE"));
        assertEquals(0, controller.syncEntitlements(machineToken, request).getCode());
    }

    @Test
    void coreSyncUpdatesOnlyOwnerTenantAndRejectsStaleRevision() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:core_ent_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE saas_provisioning_requests(tenant_uuid VARCHAR(36) PRIMARY KEY,tenant_id BIGINT,status VARCHAR(20),entitlement_revision BIGINT DEFAULT 0)");
        jdbc.execute("CREATE TABLE system_tenant(id BIGINT PRIMARY KEY,tenant_uuid VARCHAR(36),deleted INT DEFAULT 0,registration_source VARCHAR(40),expire_time TIMESTAMP,account_count INT)");
        jdbc.execute("CREATE TABLE tenant_subscriptions(tenant_id BIGINT PRIMARY KEY,status VARCHAR(20),expire_time TIMESTAMP)");
        String coreTenant = UUID.randomUUID().toString();
        String outsideTenant = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO saas_provisioning_requests VALUES(?,?,?,0)", coreTenant, 71L, "ACTIVE");
        jdbc.update("INSERT INTO system_tenant VALUES(?,?,?,?,?,?)", 71L, coreTenant, 0, "owner-control-plane", java.sql.Timestamp.valueOf(LocalDateTime.now().plusDays(5)), 6);
        jdbc.update("INSERT INTO tenant_subscriptions VALUES(?,?,?)", 71L, "active", java.sql.Timestamp.valueOf(LocalDateTime.now().plusDays(5)));
        jdbc.update("INSERT INTO system_tenant VALUES(?,?,?,?,?,?)", 72L, outsideTenant, 0, "self-service", java.sql.Timestamp.valueOf(LocalDateTime.now().plusDays(5)), 4);
        SaasProvisioningService service = new SaasProvisioningService(jdbc, mock(TenantService.class));
        SaasEntitlementSyncRequest first = new SaasEntitlementSyncRequest();
        first.tenantUuid = coreTenant;
        first.revision = 2;
        first.expiresAtMs = System.currentTimeMillis() + 60L * 86400000;
        first.seatLimit = 24;
        assertEquals("ACTIVE", service.syncEntitlements(first).get("status"));
        assertEquals(24, jdbc.queryForObject("SELECT account_count FROM system_tenant WHERE id=71", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT account_count FROM system_tenant WHERE id=72", Integer.class));
        SaasEntitlementSyncRequest old = new SaasEntitlementSyncRequest();
        old.tenantUuid = coreTenant;
        old.revision = 1;
        old.expiresAtMs = System.currentTimeMillis() + 10L * 86400000;
        old.seatLimit = 1;
        assertEquals("ACTIVE", service.syncEntitlements(old).get("status"));
        assertEquals(24, jdbc.queryForObject("SELECT account_count FROM system_tenant WHERE id=71", Integer.class));
        SaasEntitlementSyncRequest unlimited = new SaasEntitlementSyncRequest();
        unlimited.tenantUuid = coreTenant;
        unlimited.revision = 3;
        unlimited.expiresAtMs = System.currentTimeMillis() + 80L * 86400000;
        unlimited.seatLimit = null;
        assertEquals("ACTIVE", service.syncEntitlements(unlimited).get("status"));
        assertEquals(Integer.MAX_VALUE, jdbc.queryForObject("SELECT account_count FROM system_tenant WHERE id=71", Integer.class));
        jdbc.execute("UPDATE tenant_subscriptions SET status='cancelled' WHERE tenant_id=71");
        SaasEntitlementSyncRequest blocked = new SaasEntitlementSyncRequest();
        blocked.tenantUuid = coreTenant;
        blocked.revision = 4;
        blocked.expiresAtMs = System.currentTimeMillis() + 100L * 86400000;
        blocked.seatLimit = 8;
        assertThrows(IllegalArgumentException.class, () -> service.syncEntitlements(blocked));
    }
}
