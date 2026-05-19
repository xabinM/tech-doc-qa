package com.example.backend.interfaces.query;

import com.example.backend.application.auth.port.TokenManager;
import com.example.backend.application.query.ChatSessionService;
import com.example.backend.common.config.SecurityConfig;
import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.example.backend.common.security.JwtAccessDeniedHandler;
import com.example.backend.common.security.JwtAuthenticationEntryPoint;
import com.example.backend.common.security.JwtAuthenticationFilter;
import com.example.backend.domain.query.ChatSession;
import com.example.backend.domain.query.QueryLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SessionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class SessionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChatSessionService chatSessionService;

    @MockitoBean
    TokenManager tokenManager;

    private ChatSession sessionFixture(Long id, Long userId, String title) {
        ChatSession session = ChatSession.create(userId, title);
        ReflectionTestUtils.setField(session, "id", id);
        ReflectionTestUtils.setField(session, "createdAt", LocalDateTime.now());
        return session;
    }

    @Test
    @WithMockUser
    @DisplayName("세션 목록 조회 성공 - 빈 목록")
    void listSessions_빈목록_성공() throws Exception {
        given(chatSessionService.listSessions(any(), isNull(), anyInt())).willReturn(List.of());

        mockMvc.perform(get("/api/v1/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @WithMockUser
    @DisplayName("세션 목록 조회 성공 - 세션 항목 반환")
    void listSessions_세션있음_성공() throws Exception {
        ChatSession session = sessionFixture(1L, 1L, "Spring이란?");
        given(chatSessionService.listSessions(any(), isNull(), anyInt())).willReturn(List.of(session));

        mockMvc.perform(get("/api/v1/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("Spring이란?"));
    }

    @Test
    @DisplayName("인증 없이 세션 목록 조회 시 401 반환")
    void listSessions_인증없음_401반환() throws Exception {
        mockMvc.perform(get("/api/v1/sessions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("세션 메시지 조회 성공")
    void getMessages_성공() throws Exception {
        QueryLog log = QueryLog.create(1L, 1L, "Spring이란?", "Spring은 프레임워크입니다.");
        ReflectionTestUtils.setField(log, "id", 1L);
        ReflectionTestUtils.setField(log, "createdAt", LocalDateTime.now());
        given(chatSessionService.getSessionMessages(any(), anyLong())).willReturn(List.of(log));

        mockMvc.perform(get("/api/v1/sessions/1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.messages[0].question").value("Spring이란?"))
                .andExpect(jsonPath("$.data.messages[0].answer").value("Spring은 프레임워크입니다."));
    }

    @Test
    @DisplayName("인증 없이 세션 메시지 조회 시 401 반환")
    void getMessages_인증없음_401반환() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/1/messages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("존재하지 않는 세션 메시지 조회 시 404 반환")
    void getMessages_존재하지않는세션_404반환() throws Exception {
        willThrow(new CustomException(ErrorCode.QUERY_SESSION_NOT_FOUND))
                .given(chatSessionService).getSessionMessages(any(), anyLong());

        mockMvc.perform(get("/api/v1/sessions/99/messages"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("QUERY_003"));
    }

    @Test
    @WithMockUser
    @DisplayName("다른 유저의 세션 메시지 조회 시 403 반환")
    void getMessages_다른유저세션_403반환() throws Exception {
        willThrow(new CustomException(ErrorCode.AUTH_FORBIDDEN))
                .given(chatSessionService).getSessionMessages(any(), anyLong());

        mockMvc.perform(get("/api/v1/sessions/1/messages"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_005"));
    }
}
