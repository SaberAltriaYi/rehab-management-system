package cn.iocoder.yudao.module.rehab.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabRoleCodeConstants;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RehabDepartmentBoundaryTest {
    private final RehabDataPermissionService service = new RehabDataPermissionService();
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final AdminUserApi users = mock(AdminUserApi.class);
    private final RehabPatientMapper patients = mock(RehabPatientMapper.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(8L);
        ReflectionTestUtils.setField(service, "permissionApi", permissions);
        ReflectionTestUtils.setField(service, "adminUserApi", users);
        ReflectionTestUtils.setField(service, "patientMapper", patients);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(5L);
        user.setDeptId(10L);
        when(users.getUser(5L)).thenReturn(user);
    }

    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test void clerkCannotReadPatientTransferredToAnotherDepartment() {
        when(permissions.hasAnyRoles(5L, RehabRoleCodeConstants.REHAB_CLERK)).thenReturn(true);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(8L), eq(10L)))
                .thenReturn(Arrays.asList(101L));
        when(patients.selectList()).thenReturn(Arrays.asList(
                RehabPatientDO.builder().id(101L).build(), RehabPatientDO.builder().id(102L).build()));
        Set<Long> visible = service.getVisiblePatientIds(5L);
        assertEquals(1, visible.size());
        assertFalse(visible.contains(101L));
        assertTrue(visible.contains(102L));
    }
}
