package com.example.backend.application.query;

import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.example.backend.domain.query.ChatSession;
import com.example.backend.domain.query.ChatSessionRepository;
import com.example.backend.domain.query.QueryLog;
import com.example.backend.domain.query.QueryLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

    @InjectMocks
    ChatSessionService chatSessionService;

    @Mock
    ChatSessionRepository chatSessionRepository;

    @Mock
    QueryLogRepository queryLogRepository;

    @Test
    @DisplayName("새 세션 생성 - sessionId null이면 세션 생성 후 빈 히스토리 반환")
    void prepareSession_새세션생성_빈히스토리반환() {
        given(chatSessionRepository.save(any())).willAnswer(inv -> {
            ChatSession s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 1L);
            return s;
        });

        ChatSessionService.SessionContext ctx = chatSessionService.prepareSession(1L, "Spring이란?", null);

        assertThat(ctx.sessionId()).isEqualTo(1L);
        assertThat(ctx.history()).isEmpty();
        verify(chatSessionRepository).save(any());
    }

    @Test
    @DisplayName("새 세션 생성 - 질문이 100자 초과하면 제목을 100자로 자름")
    void prepareSession_긴질문_제목100자로자름() {
        String longQuestion = "A".repeat(150);
        given(chatSessionRepository.save(any())).willAnswer(inv -> {
            ChatSession s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 1L);
            return s;
        });

        chatSessionService.prepareSession(1L, longQuestion, null);

        verify(chatSessionRepository).save(any());
    }

    @Test
    @DisplayName("기존 세션 로드 - 이전 대화 이력을 히스토리로 반환")
    void prepareSession_기존세션로드_이력반환() {
        ChatSession session = ChatSession.create(1L, "Spring이란?");
        ReflectionTestUtils.setField(session, "id", 10L);

        QueryLog log1 = QueryLog.create(1L, 10L, "Spring이란?", "Spring은 프레임워크입니다.");
        QueryLog log2 = QueryLog.create(1L, 10L, "AOP란?", "AOP는 관점 지향 프로그래밍입니다.");

        given(chatSessionRepository.findById(10L)).willReturn(Optional.of(session));
        // findLatestBySessionId는 최신순(DESC) 반환 — 실제 JPA처럼 가변 리스트로 모킹
        // (prepareSession이 Collections.reverse로 시간순 정렬하므로 불변 List.of는 예외)
        given(queryLogRepository.findLatestBySessionId(eq(10L), anyInt()))
                .willReturn(new ArrayList<>(List.of(log2, log1)));

        ChatSessionService.SessionContext ctx = chatSessionService.prepareSession(1L, "추가 질문", 10L);

        assertThat(ctx.sessionId()).isEqualTo(10L);
        assertThat(ctx.history()).hasSize(2);
        // reverse 후 시간순(오래된 것 먼저): Spring → AOP
        assertThat(ctx.history().get(0).question()).isEqualTo("Spring이란?");
        assertThat(ctx.history().get(1).question()).isEqualTo("AOP란?");
    }

    @Test
    @DisplayName("존재하지 않는 세션 접근 시 QUERY_SESSION_NOT_FOUND 예외 발생")
    void prepareSession_존재하지않는세션_예외발생() {
        given(chatSessionRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatSessionService.prepareSession(1L, "질문", 99L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.QUERY_SESSION_NOT_FOUND));
    }

    @Test
    @DisplayName("다른 유저의 세션 접근 시 AUTH_FORBIDDEN 예외 발생")
    void prepareSession_다른유저세션접근_예외발생() {
        ChatSession session = ChatSession.create(2L, "타인의 세션");
        ReflectionTestUtils.setField(session, "id", 10L);
        given(chatSessionRepository.findById(10L)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> chatSessionService.prepareSession(1L, "질문", 10L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_FORBIDDEN));
    }

    @Test
    @DisplayName("세션 메시지 조회 성공")
    void getSessionMessages_성공() {
        ChatSession session = ChatSession.create(1L, "Spring이란?");
        ReflectionTestUtils.setField(session, "id", 10L);
        QueryLog log = QueryLog.create(1L, 10L, "Spring이란?", "답변");

        given(chatSessionRepository.findById(10L)).willReturn(Optional.of(session));
        given(queryLogRepository.findBySessionId(10L)).willReturn(List.of(log));

        List<QueryLog> messages = chatSessionService.getSessionMessages(1L, 10L);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getQuestion()).isEqualTo("Spring이란?");
    }

    @Test
    @DisplayName("세션 메시지 조회 - 존재하지 않는 세션이면 예외 발생")
    void getSessionMessages_존재하지않는세션_예외발생() {
        given(chatSessionRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatSessionService.getSessionMessages(1L, 99L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.QUERY_SESSION_NOT_FOUND));
    }

    @Test
    @DisplayName("세션 메시지 조회 - 다른 유저의 세션이면 예외 발생")
    void getSessionMessages_다른유저세션_예외발생() {
        ChatSession session = ChatSession.create(2L, "타인의 세션");
        ReflectionTestUtils.setField(session, "id", 10L);
        given(chatSessionRepository.findById(10L)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> chatSessionService.getSessionMessages(1L, 10L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_FORBIDDEN));
    }
}
