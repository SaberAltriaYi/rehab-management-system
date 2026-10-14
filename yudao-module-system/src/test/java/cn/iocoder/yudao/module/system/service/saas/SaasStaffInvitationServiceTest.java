package cn.iocoder.yudao.module.system.service.saas;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class SaasStaffInvitationServiceTest {
    JdbcTemplate jdbc; TransactionTemplate tx; SaasStaffInvitationService service;
    AdminUserService users; PermissionService permissions; SaasTenantAccessService access;
    final String token=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    @BeforeEach void setup() {
        JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000");
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE tenant_subscriptions(tenant_id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE tenant_usage(tenant_id BIGINT PRIMARY KEY,user_count BIGINT)");
        jdbc.execute("CREATE TABLE system_role(id BIGINT PRIMARY KEY,tenant_id BIGINT,code VARCHAR(50),status INT,deleted INT)");
        jdbc.execute("CREATE TABLE system_users(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted INT)");
        jdbc.execute("CREATE TABLE saas_staff_invitations(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,role_id BIGINT,recipient_email VARCHAR(50),token_hash CHAR(64),status VARCHAR(20) DEFAULT 'pending',invited_by BIGINT,accepted_user_id BIGINT,expires_at TIMESTAMP,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,accepted_at TIMESTAMP,UNIQUE(tenant_id,token_hash))");
        jdbc.update("INSERT INTO tenant_subscriptions VALUES(11),(22)");jdbc.update("INSERT INTO tenant_usage VALUES(11,1),(22,1)");
        jdbc.update("INSERT INTO system_role VALUES(7,11,'rehab_therapist',0,0)");
        jdbc.update("INSERT INTO system_users VALUES(1,11,0)");
        users=mock(AdminUserService.class);permissions=mock(PermissionService.class);access=mock(SaasTenantAccessService.class);
        when(access.requireCurrentTenant()).thenReturn(Collections.singletonMap("max_users",2));
        SaasTenantAdministrator admin=mock(SaasTenantAdministrator.class);when(admin.requireCurrent()).thenReturn(1L);
        service=new SaasStaffInvitationService(jdbc,admin,access,users,permissions);
        AtomicLong ids=new AtomicLong(100);when(users.createUser(any(UserSaveReqVO.class))).thenAnswer(i->{long id=ids.incrementAndGet();jdbc.update("INSERT INTO system_users VALUES(?,?,0)",id,TenantContextHolder.getTenantId());return id;});
        TenantContextHolder.setTenantId(11L);invitation(token);
    }
    void invitation(String raw) {jdbc.update("INSERT INTO saas_staff_invitations(tenant_id,role_id,recipient_email,token_hash,invited_by,expires_at) VALUES(11,7,'synthetic@example.invalid',?,1,TIMESTAMPADD(HOUR,72,CURRENT_TIMESTAMP))",SaasStaffInvitationService.digest(11,raw));}
    Long accept(){return tx.execute(s->service.accept(token,"synthetic","synthetic-1234","合成治疗师"));}
    @AfterEach void cleanup(){TenantContextHolder.clear();jdbc.execute("SET DB_CLOSE_DELAY 0");}
    @Test void rawTokenIsNotStoredAndIssuedTokensAreUnique(){Map<String,Object> a=tx.execute(s->service.issue("test@example.invalid"));Map<String,Object> b=tx.execute(s->service.issue("test@example.invalid"));assertNotEquals(a.get("token"),b.get("token"));assertEquals(43,a.get("token").toString().length());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM saas_staff_invitations WHERE token_hash=?",Integer.class,a.get("token")));}
    @Test void singleUseAndScopedRole(){Long id=accept();verify(permissions).assignUserRole(id,Collections.singleton(7L));assertThrows(RuntimeException.class,this::accept);assertEquals(2L,jdbc.queryForObject("SELECT user_count FROM tenant_usage WHERE tenant_id=11",Long.class));}
    @Test void crossTenantCannotRedeem(){TenantContextHolder.setTenantId(22L);assertThrows(RuntimeException.class,this::accept);verifyNoInteractions(users);}
    @Test void expiredCannotRedeem(){jdbc.update("UPDATE saas_staff_invitations SET expires_at=TIMESTAMPADD(HOUR,-1,CURRENT_TIMESTAMP)");assertThrows(RuntimeException.class,this::accept);verifyNoInteractions(users);}
    @Test void revokedCannotRedeem(){tx.execute(s->{service.revoke(1);return null;});assertThrows(RuntimeException.class,this::accept);verifyNoInteractions(users);}
    @Test void inactiveRoleCannotRedeem(){jdbc.update("UPDATE system_role SET status=1");assertThrows(RuntimeException.class,this::accept);verifyNoInteractions(users);}
    @Test void quotaFailureDoesNotConsumeInvitation(){when(access.requireCurrentTenant()).thenReturn(Collections.singletonMap("max_users",1));assertThrows(RuntimeException.class,this::accept);assertEquals("pending",jdbc.queryForObject("SELECT status FROM saas_staff_invitations WHERE id=1",String.class));verifyNoInteractions(users);}
    @Test void assignmentFailureRollsBackUserAndInvitation(){doThrow(new IllegalStateException("synthetic failure")).when(permissions).assignUserRole(anyLong(),anySet());assertThrows(RuntimeException.class,this::accept);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_users",Integer.class));assertEquals("pending",jdbc.queryForObject("SELECT status FROM saas_staff_invitations WHERE id=1",String.class));}
    @Test void rejectsUnmanagedTransaction(){assertThrows(IllegalStateException.class,()->service.accept(token,"synthetic","synthetic-1234","test"));verifyNoInteractions(users);}
    @Test void parallelReplayCreatesOneUser() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        Callable<Boolean> attempt=()->{TenantContextHolder.setTenantId(11L);try{start.await();accept();return true;}catch(RuntimeException e){return false;}finally{TenantContextHolder.clear();}};
        try{Future<Boolean>a=pool.submit(attempt),b=pool.submit(attempt);start.countDown();assertNotEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM system_users",Integer.class));}finally{pool.shutdownNow();}
    }
    @Test void digestIsTenantAndPurposeScoped(){assertNotEquals(SaasStaffInvitationService.digest(11,token),SaasStaffInvitationService.digest(22,token));assertThrows(RuntimeException.class,()->SaasStaffInvitationService.digest(11,"short"));}
}
