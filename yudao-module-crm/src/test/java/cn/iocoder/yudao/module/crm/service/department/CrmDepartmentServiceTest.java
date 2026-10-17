package cn.iocoder.yudao.module.crm.service.department;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.yudao.module.crm.dal.mysql.customer.CrmCustomerMapper;
import cn.iocoder.yudao.module.crm.enums.common.CrmBizTypeEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrmDepartmentServiceTest {
    private final CrmDepartmentService service = new CrmDepartmentService();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AdminUserApi users = mock(AdminUserApi.class);
    private final CrmCustomerMapper customers = mock(CrmCustomerMapper.class);

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(8L);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "adminUserApi", users);
        ReflectionTestUtils.setField(service, "customerMapper", customers);
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(5L);
        user.setDeptId(10L);
        when(users.getUser(5L)).thenReturn(user);
    }

    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test void otherDepartmentCannotReadClaimedCustomer() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(8L), eq(100L)))
                .thenReturn(Arrays.asList(20L));
        assertThrows(ServiceException.class, () -> service.assertCustomerVisible(100L, 5L));
    }

    @Test void relatedBusinessCannotBypassCustomerDepartment() {
        when(jdbc.query(startsWith("SELECT customer_id FROM crm_business"), any(RowMapper.class), eq(8L), eq(301L)))
                .thenReturn(Collections.singletonList(100L));
        when(jdbc.query(startsWith("SELECT dept_id FROM crm_department_case"), any(RowMapper.class), eq(8L), eq(100L)))
                .thenReturn(Collections.singletonList(20L));
        assertThrows(ServiceException.class, () -> service.assertBizVisible(
                CrmBizTypeEnum.CRM_BUSINESS.getType(), 301L, 5L));
    }

    @Test void poolDistributionCannotChangeClaimedDepartment() {
        when(jdbc.query(startsWith("SELECT dept_id FROM crm_department_case"), any(RowMapper.class), eq(8L), eq(100L)))
                .thenReturn(Collections.singletonList(20L));
        assertThrows(ServiceException.class, () -> service.assertLegacyOwnerTransferWithinDepartment(100L, 5L));
    }

    @Test void duplicateClaimCannotStealAnotherDepartmentsCustomer() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(8L), eq(10L))).thenReturn(1);
        when(customers.selectById(100L)).thenReturn(new CrmCustomerDO().setId(100L));
        when(jdbc.update(anyString(), eq(8L), eq(100L), eq(10L), eq(5L)))
                .thenThrow(new DuplicateKeyException("already claimed"));
        when(jdbc.query(anyString(), any(RowMapper.class), eq(8L), eq(100L), eq(10L)))
                .thenReturn(Collections.emptyList());
        assertThrows(ServiceException.class, () -> service.claim(100L, 5L));
    }

    @Test void successfulClaimSetsCrmOwnerWithoutExposingOldOwnerPermission() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(8L), eq(10L))).thenReturn(1);
        when(customers.selectById(100L)).thenReturn(new CrmCustomerDO().setId(100L));
        when(jdbc.query(startsWith("SELECT id FROM crm_department_case"), any(RowMapper.class), eq(8L), eq(100L), eq(10L)))
                .thenReturn(Collections.singletonList(77L));
        when(jdbc.query(startsWith("SELECT id FROM crm_permission"), any(RowMapper.class), eq(8L), eq(100L), eq(5L)))
                .thenReturn(Collections.emptyList());
        assertEquals(77L, service.claim(100L, 5L));
        verify(jdbc).update(startsWith("UPDATE crm_customer SET owner_user_id"), eq(5L), eq(8L), eq(100L));
        verify(jdbc).update(startsWith("UPDATE crm_permission SET deleted"), eq(8L), eq(100L));
    }

    @Test void newCustomerWithEnabledWorkspaceIsBoundToOwnerDepartment() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(8L), eq(10L))).thenReturn(1);
        service.registerCreatedCustomer(100L, 5L);
        verify(jdbc).update(startsWith("INSERT IGNORE INTO crm_department_case"), eq(8L), eq(100L), eq(10L), eq(5L));
    }

    @Test void cannotResolveTransferAddressedToAnotherDepartment() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(8L), eq(77L), eq(10L)))
                .thenReturn(Collections.emptyList());
        assertThrows(ServiceException.class, () -> service.resolveTransfer(77L, true, 5L));
        verify(jdbc, never()).update(startsWith("UPDATE crm_department_case"), any(), any(), any(), any());
    }
}
