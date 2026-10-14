package cn.iocoder.yudao.module.system.service.saas;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class SaasPlatformServiceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final SaasPlatformAuthority authority=mock(SaasPlatformAuthority.class);
    private final TenantService tenants=mock(TenantService.class);
    private final SaasPlatformService service=new SaasPlatformService(jdbc,authority,tenants);
    @Test void tenantListChecksAuthorityBeforeReading(){ when(authority.requireAdmin()).thenThrow(new IllegalStateException("denied")); assertThrows(IllegalStateException.class,()->service.tenants(0)); verifyNoInteractions(jdbc); }
    @Test void registrationCreatesNoUserOrTenant(){ String id=service.register("synthetic-tenant","synthetic-contact","synthetic@example.invalid"); assertDoesNotThrow(()->UUID.fromString(id)); verifyNoInteractions(tenants,authority); verify(jdbc).update(contains("INSERT INTO saas_registrations"),eq(id),eq("synthetic-tenant"),eq("synthetic-contact"),eq("synthetic@example.invalid")); }
    @Test void systemPackageCannotBeAssigned(){ when(authority.requireAdmin()).thenReturn(1L); assertThrows(IllegalArgumentException.class,()->service.approve(1,1,0,"syntheticadmin","synthetic-only-12",LocalDateTime.now().plusDays(30))); verifyNoInteractions(tenants,jdbc); }
    @Test void previouslyReviewedApplicationCannotProvisionTwice(){ when(authority.requireAdmin()).thenReturn(1L); Map<String,Object> row=new HashMap<>();row.put("status","approved"); when(jdbc.queryForList(anyString(),eq(1L))).thenReturn(Collections.singletonList(row)); assertThrows(IllegalArgumentException.class,()->service.approve(1,1,1,"syntheticadmin","synthetic-only-12",LocalDateTime.now().plusDays(30))); verifyNoInteractions(tenants); }
    @Test void rejectCannotOverwriteAnApproval(){ when(authority.requireAdmin()).thenReturn(1L); assertThrows(IllegalArgumentException.class,()->service.reject(2)); verify(jdbc).update(contains("status='pending'"),eq(1L),eq(2L)); }
}
