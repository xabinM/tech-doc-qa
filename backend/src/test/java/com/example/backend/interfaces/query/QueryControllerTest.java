package com.example.backend.interfaces.query;

import com.example.backend.application.auth.port.TokenManager;
import com.example.backend.application.query.QueryService;
import com.example.backend.common.config.SecurityConfig;
import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.example.backend.common.security.JwtAccessDeniedHandler;
import com.example.backend.common.security.JwtAuthenticationEntryPoint;
import com.example.backend.common.security.JwtAuthenticationFilter;
import com.example.backend.domain.query.QueryLog;
import com.example.backend.interfaces.query.dto.QueryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = QueryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class QueryControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    QueryService queryService;

    @MockitoBean
    TokenManager tokenManager;

    @Test
    @WithMockUser
    @DisplayName("질의 성공 시 200 OK와 답변 반환")
    void query_success() throws Exception {
        given(queryService.query(any(), anyString())).willReturn("Spring은 자바 프레임워크입니다.");

        mockMvc.perform(post("/api/v1/query")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QueryRequest("Spring이란?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.answer").value("Spring은 자바 프레임워크입니다."));
    }

    @Test
    @WithMockUser
    @DisplayName("질문 공백 시 400 Bad Request 반환")
    void query_blankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/query")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QueryRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @WithMockUser
    @DisplayName("일일 요청 한도 초과 시 429 Too Many Requests 반환")
    void query_rateLimitExceeded() throws Exception {
        willThrow(new CustomException(ErrorCode.QUERY_RATE_LIMIT_EXCEEDED))
                .given(queryService).query(any(), anyString());

        mockMvc.perform(post("/api/v1/query")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QueryRequest("Spring이란?"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("QUERY_002"));
    }

    @Test
    @DisplayName("인증 없이 질의 시 401 Unauthorized 반환")
    void query_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/query")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QueryRequest("Spring이란?"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("검색 이력 조회 성공 - 첫 페이지")
    void history_success() throws Exception {
        QueryLog log = QueryLog.create(1L, "Spring이란?", "Spring은 자바 프레임워크입니다.");
        given(queryService.getHistory(any(), isNull(), anyInt())).willReturn(List.of(log));

        mockMvc.perform(get("/api/v1/query/history").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].question").value("Spring이란?"))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @WithMockUser
    @DisplayName("검색 이력 조회 성공 - cursorId로 다음 페이지")
    void history_withCursor() throws Exception {
        given(queryService.getHistory(any(), anyLong(), anyInt())).willReturn(List.of());

        mockMvc.perform(get("/api/v1/query/history")
                        .param("cursorId", "100")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("인증 없이 이력 조회 시 401 Unauthorized 반환")
    void history_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/query/history"))
                .andExpect(status().isUnauthorized());
    }
}
