package cn.iocoder.yudao.module.system.service.saas;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.util.Collections;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class SaasPlatformAuthorityTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final SaasPlatformAuthority authority=new SaasPlatformAuthority(jdbc);
    @AfterEach void clear(){ SecurityContextHolder.clearContext(); }
    void login(int type){
        LoginUser user=new LoginUser(); user.setId(910001L); user.setTenantId(910010L); user.setUserType(type);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,Collections.emptyList()));
    }
    @Test void anonymousIsDenied(){ assertThrows(AccessDeniedException.class,authority::requireAdmin); verifyNoInteractions(jdbc); }
    @Test void patientWithCollidingIdIsDenied(){ login(1); assertThrows(AccessDeniedException.class,authority::requireAdmin); verifyNoInteractions(jdbc); }
    @Test void tenantAdministratorIsNotPlatformAdministrator(){ login(2); when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(910001L),eq(910010L))).thenReturn(0); assertThrows(AccessDeniedException.class,authority::requireAdmin); }
    @Test void activeExplicitPlatformAdministratorIsAllowed(){ login(2); when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(910001L),eq(910010L))).thenReturn(1); assertEquals(910001L,authority.requireAdmin()); }
    @Test void databaseOutageDoesNotGrantAccess(){ login(2); when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(910001L),eq(910010L))).thenThrow(new IllegalStateException("synthetic unavailable")); assertThrows(IllegalStateException.class,authority::requireAdmin); }
}
