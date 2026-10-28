package cn.iocoder.yudao.module.system.controller.admin.auth;

import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.service.auth.AdminAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
 * Standalone handler binding, synthetic auth service only; not an authenticated business E2E.
 */
@ExtendWith(MockitoExtension.class)
class AuthRefreshTransportTest {
    @Mock private AdminAuthService authService;
    @InjectMocks private AuthController controller;
    private MockMvc mvc;

    @BeforeEach
    void open() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void servletDecodedPostFormParameterIsForwardedWithoutChangingResponseEnvelope() throws Exception {
        String token = "SYNTHETIC_REFRESH_+&?=安全";
        when(authService.refreshToken(token)).thenReturn(AuthLoginRespVO.builder()
                .userId(71L).accessToken("SYNTHETIC_NEW_ACCESS").refreshToken("SYNTHETIC_NEW_REFRESH").build());
        // MockMvc param models the form fields decoded by the servlet container;
        // the browser's real Axios form encoding is tested separately in Node.
        mvc.perform(post("/system/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("refreshToken", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("SYNTHETIC_NEW_ACCESS"))
                .andExpect(jsonPath("$.data.refreshToken").value("SYNTHETIC_NEW_REFRESH"));
        verify(authService).refreshToken(token);
    }

    @Test
    void legacyQueryCallerRemainsCompatibleWhileNewBrowserUsesForm() throws Exception {
        when(authService.refreshToken("SYNTHETIC_LEGACY")).thenReturn(AuthLoginRespVO.builder().userId(71L).build());
        mvc.perform(post("/system/auth/refresh-token").queryParam("refreshToken", "SYNTHETIC_LEGACY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        verify(authService).refreshToken("SYNTHETIC_LEGACY");
    }

    @Test
    void absentTokenStillRejectsWithoutCallingAuthService() throws Exception {
        mvc.perform(post("/system/auth/refresh-token").contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }
}
