package cn.iocoder.yudao.module.system.service.saas;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.util.Collections;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class SaasTenantAdministratorTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final SaasTenantAdministrator service=new SaasTenantAdministrator(jdbc);
    @AfterEach void clear(){SecurityContextHolder.clearContext();TenantContextHolder.clear();}
    void login(int type,long tenant){LoginUser user=new LoginUser();user.setId(1L);user.setTenantId(tenant);user.setUserType(type);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,Collections.emptyList()));TenantContextHolder.setTenantId(11L);}
    @Test void anonymousDenied(){assertThrows(AccessDeniedException.class,service::requireCurrent);verifyNoInteractions(jdbc);}
    @Test void patientDenied(){login(1,11);assertThrows(AccessDeniedException.class,service::requireCurrent);verifyNoInteractions(jdbc);}
    @Test void differentTenantDenied(){login(2,22);assertThrows(AccessDeniedException.class,service::requireCurrent);verifyNoInteractions(jdbc);}
    @Test void ignoredTenantDenied(){login(2,11);TenantContextHolder.setIgnore(true);assertThrows(AccessDeniedException.class,service::requireCurrent);verifyNoInteractions(jdbc);}
    @Test void nonAdministratorDenied(){login(2,11);when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(11L),eq(1L))).thenReturn(0);assertThrows(AccessDeniedException.class,service::requireCurrent);}
    @Test void activeTenantAdministratorAllowed(){login(2,11);when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(11L),eq(1L))).thenReturn(1);assertEquals(1L,service.requireCurrent());}
}
