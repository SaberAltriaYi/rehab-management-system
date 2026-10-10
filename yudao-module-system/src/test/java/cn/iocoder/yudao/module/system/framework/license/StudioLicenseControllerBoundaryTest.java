package cn.iocoder.yudao.module.system.framework.license;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.controller.admin.user.StudioLicenseController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Public controller contract must not grow an arbitrary tenant-ID parameter. */
public class StudioLicenseControllerBoundaryTest {
    @Test
    public void studioOverviewIsReadOnlyAndRequiresExistingStaffQueryPermission() throws Exception {
        Class<StudioLicenseController> controller = StudioLicenseController.class;
        RequestMapping base = controller.getAnnotation(RequestMapping.class);
        assertNotNull(base);
        assertArrayEquals(new String[]{"/system/studio-license"}, base.value());

        Method method = controller.getDeclaredMethod("overview");
        assertEquals(0, method.getParameterCount(), "A caller cannot choose another tenant");
        GetMapping get = method.getAnnotation(GetMapping.class);
        assertNotNull(get, "No owner signing or permit-all endpoint");
        assertArrayEquals(new String[]{"/overview"}, get.value());
        PreAuthorize permission = method.getAnnotation(PreAuthorize.class);
        assertNotNull(permission);
        assertEquals("@ss.hasPermission('system:user:query')", permission.value());
    }

    @Test
    public void studioEndpointCallsOnlyTenantScopedService() {
        StudioLicenseOverviewService service = mock(StudioLicenseOverviewService.class);
        Map<String, Object> snapshot = Collections.singletonMap("status", "NOT_CONFIGURED");
        when(service.readCurrentStudio()).thenReturn(snapshot);
        CommonResult<Map<String, Object>> output = new StudioLicenseController(service).overview();
        assertEquals(snapshot, output.getData());
        verify(service).readCurrentStudio();
        verifyNoMoreInteractions(service);
    }
}
