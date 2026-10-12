package cn.iocoder.yudao.module.system.service.saas;

import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real JDBC SQL contract, synthetic H2 only. Not a full provisioning/transaction integration test. */
class SaasPlatformSqlContractTest {
    JdbcTemplate jdbc;
    SaasPlatformService service;
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:saas_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE saas_registrations(id BIGINT AUTO_INCREMENT PRIMARY KEY,request_id VARCHAR(36),organization_name VARCHAR(80),contact_name VARCHAR(50),contact_email VARCHAR(120),status VARCHAR(20) DEFAULT 'pending',tenant_id BIGINT,reviewed_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,reviewed_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE saas_platform_audit(id BIGINT AUTO_INCREMENT PRIMARY KEY,actor_user_id BIGINT,action VARCHAR(40),target_id BIGINT)");
        jdbc.execute("CREATE TABLE saas_plans(id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(64) UNIQUE,max_users INT,max_patients INT,motion_quota BIGINT,storage_quota BIGINT,status INT DEFAULT 1)");
        jdbc.execute("CREATE TABLE system_tenant(id BIGINT PRIMARY KEY,name VARCHAR(80),status INT DEFAULT 0,deleted INT DEFAULT 0,registration_status VARCHAR(20) DEFAULT 'active',registration_source VARCHAR(32),expire_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE tenant_subscriptions(tenant_id BIGINT PRIMARY KEY,plan_id BIGINT,status VARCHAR(20),start_time TIMESTAMP,expire_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE tenant_usage(tenant_id BIGINT PRIMARY KEY,user_count INT)");
        SaasPlatformAuthority authority=mock(SaasPlatformAuthority.class); when(authority.requireAdmin()).thenReturn(910001L);
        TenantService tenants=mock(TenantService.class);
        when(tenants.createTenant(any())).thenAnswer(invocation -> {jdbc.update("INSERT INTO system_tenant(id,name) VALUES (910002,'synthetic-tenant')");return 910002L;});
        service=new SaasPlatformService(jdbc,authority,tenants);
    }
    @AfterEach void cleanup() { jdbc.execute("DROP ALL OBJECTS"); jdbc.execute("SET DB_CLOSE_DELAY 0"); }
    @Test void registerApproveSuspendRenewPersistsExpectedState() {
        long plan=service.createPlan("synthetic-plan",10,100,1000,1024);
        service.register("synthetic-tenant","synthetic-contact","synthetic@example.invalid");
        assertEquals(910002L,service.approve(1,plan,1,"syntheticadmin","synthetic-only-12",LocalDateTime.now().plusDays(30)));
        assertEquals("approved",jdbc.queryForObject("SELECT status FROM saas_registrations WHERE id=1",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT user_count FROM tenant_usage WHERE tenant_id=910002",Integer.class));
        service.suspend(910002);
        assertEquals("suspended",jdbc.queryForObject("SELECT status FROM tenant_subscriptions WHERE tenant_id=910002",String.class));
        service.renew(910002,LocalDateTime.now().plusDays(60));
        assertEquals("active",jdbc.queryForObject("SELECT status FROM tenant_subscriptions WHERE tenant_id=910002",String.class));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM saas_platform_audit",Integer.class));
    }
    @Test void rejectedApplicationDoesNotProvisionTenant() {
        service.register("synthetic-rejected","synthetic-contact","synthetic@example.invalid");service.reject(1);
        assertEquals("rejected",jdbc.queryForObject("SELECT status FROM saas_registrations WHERE id=1",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_tenant",Integer.class));
    }
    @Test void expiredRenewalIsRejectedWithoutMutation() {
        assertThrows(IllegalArgumentException.class,()->service.renew(910002,LocalDateTime.now().minusDays(1)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM saas_platform_audit",Integer.class));
    }
}
