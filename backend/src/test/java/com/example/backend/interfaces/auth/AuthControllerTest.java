package com.example.backend.interfaces.auth;

import com.example.backend.application.auth.AuthService;
import com.example.backend.application.auth.TokenResult;
import com.example.backend.common.config.SecurityConfig;
import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.example.backend.common.security.JwtAuthenticationFilter;
import com.example.backend.infrastructure.auth.JwtProvider;
import com.example.backend.interfaces.auth.dto.AuthLoginRequest;
import com.example.backend.interfaces.auth.dto.AuthSignupRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    JwtProvider jwtProvider;

    @Test
    @DisplayName("회원가입 성공 시 200 OK와 success:true 반환")
    void signup_success() throws Exception {
        var request = new AuthSignupRequest("test@example.com", "password1");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("이메일 형식 오류 시 400 Bad Request 반환")
    void signup_invalidEmail() throws Exception {
        var request = new AuthSignupRequest("not-an-email", "password1");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("중복 이메일 회원가입 시 409 Conflict 반환")
    void signup_duplicateEmail() throws Exception {
        var request = new AuthSignupRequest("test@example.com", "password1");
        willThrow(new CustomException(ErrorCode.AUTH_EMAIL_DUPLICATE))
                .given(authService).signup(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    @Test
    @DisplayName("로그인 성공 시 200 OK와 토큰 반환")
    void login_success() throws Exception {
        var request = new AuthLoginRequest("test@example.com", "password1");
        given(authService.login(anyString(), anyString()))
                .willReturn(new TokenResult("access-token", "refresh-token"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
    }

    @Test
    @DisplayName("잘못된 이메일/비밀번호로 로그인 시 401 Unauthorized 반환")
    void login_fail() throws Exception {
        var request = new AuthLoginRequest("test@example.com", "wrongpassword");
        given(authService.login(anyString(), anyString()))
                .willThrow(new CustomException(ErrorCode.AUTH_LOGIN_FAIL));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }
}
