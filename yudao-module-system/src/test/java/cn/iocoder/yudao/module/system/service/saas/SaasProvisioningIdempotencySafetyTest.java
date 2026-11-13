package cn.iocoder.yudao.module.system.service.saas;

import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Synthetic only. Must not connect to the installed Core database. */
class SaasProvisioningIdempotencySafetyTest {
    private JdbcTemplate syntheticDb() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:owner_idempotency_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE saas_provisioning_requests(" +
                "id BIGINT PRIMARY KEY, idempotency_key VARCHAR(36) UNIQUE, tenant_uuid VARCHAR(36) UNIQUE," +
                "organization_name VARCHAR(120),plan_id BIGINT,package_id BIGINT,tenant_id BIGINT," +
                "status VARCHAR(20),failure_reason VARCHAR(500))");
        jdbc.execute("CREATE TABLE system_tenant(id BIGINT PRIMARY KEY,tenant_uuid VARCHAR(36),deleted INT DEFAULT 0)");
        return jdbc;
    }

    private SaasProvisioningRequest request(String key, String uuid, String name) {
        SaasProvisioningRequest value = new SaasProvisioningRequest();
        value.idempotencyKey = key;
        value.tenantUuid = uuid;
        value.organizationName = name;
        value.contactName = "Synthetic contact";
        value.planId = 1;
        value.packageId = 1;
        value.expiresAt = LocalDateTime.now().plusDays(15);
        value.adminUsername = "syntheticAdmin";
        value.adminPassword = "SyntheticPassphrase123";
        return value;
    }

    @Test
    void sameKeyCannotRebindAnotherInstitutionOrPlan() {
        JdbcTemplate jdbc = syntheticDb();
        String key = UUID.randomUUID().toString();
        String ownerUuid = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO saas_provisioning_requests(id,idempotency_key,tenant_uuid," +
                "organization_name,plan_id,package_id,status,tenant_id) VALUES(1,?,?,?,1,1,'ACTIVE',71)",
                key, ownerUuid, "Synthetic clinic A");
        jdbc.update("INSERT INTO system_tenant(id,tenant_uuid,deleted) VALUES(71,?,0)", ownerUuid);
        TenantService tenants = mock(TenantService.class);
        SaasProvisioningService service = new SaasProvisioningService(jdbc, tenants);

        assertEquals("ACTIVE", service.provision(request(key, ownerUuid, "Synthetic clinic A")).get("status"));
        assertThrows(IllegalArgumentException.class,
                () -> service.provision(request(key, UUID.randomUUID().toString(), "Synthetic clinic A")));
        assertThrows(IllegalArgumentException.class,
                () -> service.provision(request(key, ownerUuid, "Synthetic clinic B")));
        SaasProvisioningRequest changedPlan = request(key, ownerUuid, "Synthetic clinic A");
        changedPlan.packageId = 2;
        assertThrows(IllegalArgumentException.class, () -> service.provision(changedPlan));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT status FROM saas_provisioning_requests WHERE id=1", String.class));
        jdbc.update("UPDATE system_tenant SET deleted=1 WHERE id=71");
        assertThrows(IllegalStateException.class,
                () -> service.provision(request(key, ownerUuid, "Synthetic clinic A")),
                "an existing Owner claim cannot reissue ACTIVE when the Core tenant has disappeared");
        verifyNoInteractions(tenants);
    }

    @Test
    void sameOwnerUuidCannotBeReintroducedUnderAnotherApplicationId() {
        JdbcTemplate jdbc = syntheticDb();
        String ownerUuid = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO saas_provisioning_requests(id,idempotency_key,tenant_uuid," +
                "organization_name,plan_id,package_id,status,tenant_id) VALUES(1,?,?,?,1,1,'FAILED',NULL)",
                UUID.randomUUID().toString(), ownerUuid, "Synthetic clinic A");
        SaasProvisioningService service = new SaasProvisioningService(jdbc, mock(TenantService.class));
        assertThrows(IllegalArgumentException.class, () ->
                service.provision(request(UUID.randomUUID().toString(), ownerUuid, "Synthetic clinic A")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM saas_provisioning_requests", Integer.class));
    }

    @Test
    void databaseExceptionsNeverLeakPasswordsOrSqlIntoOwnerError() {
        String message = "VALUES('SyntheticPassword123','secret-token') at 127.0.0.1:3306";
        String safe = SaasProvisioningService.safeMessage(new IllegalStateException(message));
        assertEquals("CORE_PROVISIONING_REQUIRES_REVIEW", safe);
        assertFalse(safe.contains("SyntheticPassword123"));
        assertFalse(safe.contains("secret-token"));
    }
}
