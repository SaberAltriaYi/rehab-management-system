package cn.iocoder.yudao.module.rehab.controller.app.patient;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.rehab.controller.app.patient.vo.AppPatientAuthBindReqVO;
import cn.iocoder.yudao.module.rehab.controller.app.patient.vo.AppPatientLoginReqVO;
import cn.iocoder.yudao.module.rehab.controller.app.patient.vo.AppPatientCheckinCreateReqVO;
import cn.iocoder.yudao.module.rehab.controller.app.patient.vo.AppPatientNotificationReadReqVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RehabAppPatientControllerTest {

    private final RehabAppPatientController controller = new RehabAppPatientController();

    @Test
    void loginIsClosedBeforePatientLookupOrTokenCreation() {
        ResponseEntity<? extends CommonResult<?>> response = controller.login(new AppPatientLoginReqVO());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(Integer.valueOf(403), response.getBody().getCode());
    }

    @Test
    void anonymousBindIsClosedBeforePatientLookup() {
        ResponseEntity<? extends CommonResult<?>> response = controller.bind(new AppPatientAuthBindReqVO());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(Integer.valueOf(403), response.getBody().getCode());
    }

    @Test
    void validLegacyLoginAndBindRequestsRemainHttpForbidden() throws Exception {
        // Exercise JSON binding and HTTP status, not just direct controller calls.
        // The old public identifiers must never issue a patient session.
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler("test", mock(ApiErrorLogCommonApi.class)))
                .build();
        mvc.perform(post("/app-patient/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800138000\",\"bindCode\":\"PAT202603100001\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(post("/app-patient/auth/bind")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":10001,\"phone\":\"13800138000\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void allPatientDataRoutesStayClosedEvenIfALegacySessionExists() {
        // Direct invocation ensures each route checks the gate before looking up a
        // patient, retrieving data, or writing a check-in/notification.
        Executable[] protectedRoutes = {
                controller::getHomeSummary,
                () -> controller.getReportPage(1, 10),
                () -> controller.getReport(1L),
                controller::getCurrentPlan,
                controller::getTodayTasks,
                () -> controller.createCheckin(new AppPatientCheckinCreateReqVO()),
                () -> controller.getCheckinHistory(1, 10),
                controller::getProfile,
                () -> controller.getNotificationPage(1, 10),
                () -> controller.readNotification(new AppPatientNotificationReadReqVO()),
                controller::getLatestAiSummary,
                controller::getLatestAiFollowup
        };
        for (Executable route : protectedRoutes) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, route);
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        }
    }

    @Test
    void protectedRouteResponds403InsteadOfBeingConvertedTo500ByGlobalHandler() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler("test", mock(ApiErrorLogCommonApi.class)))
                .build();
        mvc.perform(get("/app-patient/profile"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
}
