package cn.iocoder.yudao.module.system.controller.admin.user;

import cn.iocoder.yudao.module.system.controller.admin.user.vo.user.UserAssignRehabStaffRoleReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import javax.validation.constraints.Pattern;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TenantStaffRoleControllerContractTest {

    @Test
    void staffRoleEndpointUsesUserUpdateAndOnlyAcceptsTherapistRole() throws Exception {
        Method endpoint = UserController.class.getMethod("assignRehabStaffRole", UserAssignRehabStaffRoleReqVO.class);
        PreAuthorize preAuthorize = endpoint.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals("@ss.hasPermission('system:user:update')", preAuthorize.value());

        Field roleCode = UserAssignRehabStaffRoleReqVO.class.getDeclaredField("roleCode");
        Pattern pattern = roleCode.getAnnotation(Pattern.class);
        assertNotNull(pattern);
        assertEquals("tenant_rehab_therapist", pattern.regexp());
    }
}
