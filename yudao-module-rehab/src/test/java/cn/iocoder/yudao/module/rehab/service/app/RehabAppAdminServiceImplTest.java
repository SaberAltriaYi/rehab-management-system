package cn.iocoder.yudao.module.rehab.service.app;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.rehab.enums.RehabRoleCodeConstants;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.enums.logger.LoginLogTypeEnum;
import cn.iocoder.yudao.module.system.service.auth.AdminAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.APP_ADMIN_LOGIN_FORBIDDEN;

@ExtendWith(MockitoExtension.class)
class RehabAppAdminServiceImplTest {

    private RehabAppAdminServiceImpl appAdminService;

    @Mock
    private AdminAuthService adminAuthService;
    @Mock
    private PermissionApi permissionApi;

    @BeforeEach
    void setUp() {
        appAdminService = new RehabAppAdminServiceImpl();
        ReflectionTestUtils.setField(appAdminService, "adminAuthService", adminAuthService);
        ReflectionTestUtils.setField(appAdminService, "permissionApi", permissionApi);
    }

    @Test
    void emptyPatientScopeNeverQueriesGlobalStatisticsButKeepsUnreadNotifications() {
        cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService scope =
                mock(cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService.class);
        cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper patients =
                mock(cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper.class);
        cn.iocoder.yudao.module.rehab.service.notification.RehabNotificationService notifications =
                mock(cn.iocoder.yudao.module.rehab.service.notification.RehabNotificationService.class);
        ReflectionTestUtils.setField(appAdminService, "dataPermissionService", scope);
        ReflectionTestUtils.setField(appAdminService, "patientMapper", patients);
        ReflectionTestUtils.setField(appAdminService, "notificationService", notifications);
        when(permissionApi.hasAnyRoles(100L, RehabRoleCodeConstants.SUPER_ADMIN,
                RehabRoleCodeConstants.REHAB_THERAPIST, RehabRoleCodeConstants.REHAB_CLERK)).thenReturn(true);
        when(scope.getVisiblePatientIds(100L)).thenReturn(java.util.Collections.emptySet());
        when(notifications.countUnreadForAdminUser(100L)).thenReturn(5L);
        cn.iocoder.yudao.module.rehab.controller.admin.app.vo.AppAdminDashboardSummaryRespVO summary =
                appAdminService.getDashboardSummary(100L);
        assertEquals(0L, summary.getMyPatientCount());
        assertEquals(0L, summary.getPendingAlertCount());
        assertEquals(0L, summary.getPendingReassessmentCount());
        assertEquals(5L, summary.getUnreadNotificationCount());
        verifyNoInteractions(patients);
    }

    @Test
    void login_shouldRejectUserWithoutAppAdminRole() {
        AuthLoginReqVO reqVO = AuthLoginReqVO.builder().username("clerk001").password("123456").build();
        AuthLoginRespVO loginResp = AuthLoginRespVO.builder()
                .userId(2001L)
                .accessToken("token-abc")
                .refreshToken("refresh-abc")
                .expiresTime(LocalDateTime.now().plusHours(2))
                .build();

        when(adminAuthService.login(reqVO)).thenReturn(loginResp);
        when(permissionApi.hasAnyRoles(2001L,
                RehabRoleCodeConstants.SUPER_ADMIN,
                RehabRoleCodeConstants.REHAB_THERAPIST,
                RehabRoleCodeConstants.REHAB_CLERK)).thenReturn(false);

        ServiceException ex = assertThrows(ServiceException.class, () -> appAdminService.login(reqVO));

        assertEquals(APP_ADMIN_LOGIN_FORBIDDEN.getCode(), ex.getCode());
        verify(adminAuthService).logout("token-abc", LoginLogTypeEnum.LOGOUT_SELF.getType());
    }

    @Test
    void login_shouldPassWhenTherapistRolePresent() {
        AuthLoginReqVO reqVO = AuthLoginReqVO.builder().username("therapist1").password("123456").build();
        AuthLoginRespVO loginResp = AuthLoginRespVO.builder()
                .userId(100L)
                .accessToken("token-ok")
                .refreshToken("refresh-ok")
                .expiresTime(LocalDateTime.now().plusHours(2))
                .build();

        when(adminAuthService.login(reqVO)).thenReturn(loginResp);
        when(permissionApi.hasAnyRoles(100L,
                RehabRoleCodeConstants.SUPER_ADMIN,
                RehabRoleCodeConstants.REHAB_THERAPIST,
                RehabRoleCodeConstants.REHAB_CLERK)).thenReturn(true);

        AuthLoginRespVO respVO = appAdminService.login(reqVO);

        assertEquals("token-ok", respVO.getAccessToken());
        verify(adminAuthService, never()).logout(anyString(), anyInt());
    }
}
