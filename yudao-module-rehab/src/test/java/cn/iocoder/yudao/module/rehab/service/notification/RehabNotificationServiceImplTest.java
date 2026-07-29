package cn.iocoder.yudao.module.rehab.service.notification;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.rehab.dal.dataobject.notification.RehabNotificationDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.assignment.RehabTherapistAssignmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.notification.RehabNotificationMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabNotificationConstants;
import cn.iocoder.yudao.module.rehab.enums.RehabRoleCodeConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.log.RehabAuditLogService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;

import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.APP_NOTIFICATION_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RehabNotificationServiceImplTest {

    private RehabNotificationServiceImpl service;

    @Mock
    private RehabNotificationMapper notificationMapper;
    @Mock
    private RehabPatientMapper patientMapper;
    @Mock
    private RehabTherapistAssignmentMapper assignmentMapper;
    @Mock
    private PermissionApi permissionApi;
    @Mock
    private RehabAuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        service = new RehabNotificationServiceImpl();

        RehabDataPermissionService dataPermissionService = new RehabDataPermissionService();
        ReflectionTestUtils.setField(dataPermissionService, "permissionApi", permissionApi);
        ReflectionTestUtils.setField(dataPermissionService, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(dataPermissionService, "assignmentMapper", assignmentMapper);

        ReflectionTestUtils.setField(service, "notificationMapper", notificationMapper);
        ReflectionTestUtils.setField(service, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(service, "dataPermissionService", dataPermissionService);
        ReflectionTestUtils.setField(service, "auditLogService", auditLogService);
    }

    @Test
    void readNotification_shouldMarkAsRead() {
        RehabNotificationDO notification = RehabNotificationDO.builder()
                .id(70001L)
                .targetType(RehabNotificationConstants.TARGET_THERAPIST)
                .targetUserId(100L)
                .readStatus(RehabNotificationConstants.READ_UNREAD)
                .build();
        when(notificationMapper.selectById(70001L)).thenReturn(notification);
        when(permissionApi.hasAnyRoles(100L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(false);
        when(permissionApi.hasAnyRoles(100L, RehabRoleCodeConstants.REHAB_THERAPIST)).thenReturn(true);

        service.readNotification(70001L, 100L);

        verify(notificationMapper).updateById(argThat((RehabNotificationDO item) ->
                item.getId() != null && item.getId().equals(70001L)
                        && RehabNotificationConstants.READ_READ.equals(item.getReadStatus())
                        && item.getReadTime() != null));
        verify(auditLogService).createAuditLog(eq("notification"), eq(70001L), eq("notification_read"),
                eq(100L), any(), any(), any(), eq("success"), any());
    }

    @Test
    void readAllNotification_shouldUpdateUnreadList() {
        when(permissionApi.hasAnyRoles(100L, RehabRoleCodeConstants.REHAB_THERAPIST)).thenReturn(true);
        when(permissionApi.hasAnyRoles(100L, RehabRoleCodeConstants.SUPER_ADMIN)).thenReturn(false);
        when(notificationMapper.selectUnreadListByTarget(RehabNotificationConstants.TARGET_THERAPIST, 100L)).thenReturn(Arrays.asList(
                RehabNotificationDO.builder().id(71001L).build(),
                RehabNotificationDO.builder().id(71002L).build()
        ));

        service.readAllNotification(100L);

        verify(notificationMapper, times(2)).updateById(argThat((RehabNotificationDO item) ->
                item.getId() != null
                        && (item.getId().equals(71001L) || item.getId().equals(71002L))
                        && RehabNotificationConstants.READ_READ.equals(item.getReadStatus())));
    }

    @Test
    void readPatientNotification_shouldRejectCrossPatient() {
        when(notificationMapper.selectById(72001L)).thenReturn(RehabNotificationDO.builder()
                .id(72001L)
                .targetType(RehabNotificationConstants.TARGET_PATIENT)
                .patientId(10001L)
                .targetUserId(90000001L)
                .readStatus(RehabNotificationConstants.READ_UNREAD)
                .build());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.readPatientNotification(72001L, 10002L, 90000001L));
        assertEquals(APP_NOTIFICATION_NOT_EXISTS.getCode(), ex.getCode());
    }
}
