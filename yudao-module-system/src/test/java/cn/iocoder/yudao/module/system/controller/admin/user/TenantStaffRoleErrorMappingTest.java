package cn.iocoder.yudao.module.system.controller.admin.user;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.system.service.tenant.TenantStaffRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies that domain ServiceException codes survive the normal API envelope. */
class TenantStaffRoleErrorMappingTest {

    private final StubTenantStaffRoleService tenantStaffRoleService = new StubTenantStaffRoleService();
    private UserController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new UserController();
        ReflectionTestUtils.setField(controller, "tenantStaffRoleService", tenantStaffRoleService);
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler("test",
                mock(ApiErrorLogCommonApi.class));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @Test
    void forbiddenRoleMutationReturns403BusinessCodeInsteadOf500() throws Exception {
        tenantStaffRoleService.failure = new ServiceException(403, "没有该操作权限");

        mvc.perform(post("/system/user/assign-rehab-staff-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":200,\"roleCode\":\"tenant_rehab_therapist\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("没有该操作权限"));

        org.junit.jupiter.api.Assertions.assertEquals(200L, tenantStaffRoleService.lastUserId);
    }

    @Test
    void invalidRoleMutationReturns400BusinessCodeInsteadOf500() throws Exception {
        tenantStaffRoleService.failure = new ServiceException(400, "员工用户编号无效");

        mvc.perform(post("/system/user/assign-rehab-staff-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":0,\"roleCode\":\"tenant_rehab_therapist\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("员工用户编号无效"));

        org.junit.jupiter.api.Assertions.assertEquals(0L, tenantStaffRoleService.lastUserId);
    }

    private static final class StubTenantStaffRoleService extends TenantStaffRoleService {
        private RuntimeException failure;
        private Long lastUserId;

        @Override
        public void assignRole(Long userId, String roleCode) {
            lastUserId = userId;
            throw failure;
        }
    }
}
