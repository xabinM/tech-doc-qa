package com.example.backend.application.query;

import com.example.backend.application.query.event.QueryCompletedEvent;
import com.example.backend.application.query.port.RagPort;
import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.example.backend.domain.query.QueryLog;
import com.example.backend.domain.query.QueryLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueryServiceTest {

    @InjectMocks
    QueryService queryService;

    @Mock
    RagPort ragPort;

    @Mock
    ChatSessionService chatSessionService;

    @Mock
    QueryLogRepository queryLogRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    QueryCacheService queryCacheService;

    @BeforeEach
    void setUp() {
        // @PostConstruct initMetrics()는 단위 테스트에서 호출되지 않으므로 수동 초기화
        ReflectionTestUtils.setField(queryService, "meterRegistry", new SimpleMeterRegistry());
        ReflectionTestUtils.invokeMethod(queryService, "initMetrics");
        ReflectionTestUtils.setField(queryService, "dailyMax", 20);
    }

    @Test
    @DisplayName("단일턴 질의 성공 - 캐시 경유 후 RAG 답변 반환 및 이벤트 발행")
    void query_singleTurn_success() {
        given(chatSessionService.prepareSession(eq(1L), eq("Spring이란?"), eq(null)))
                .willReturn(new ChatSessionService.SessionContext(100L, List.of()));
        // 단일턴(이력 없음) → 캐시 경유. loader를 그대로 실행해 RAG 호출 여부까지 검증
        given(queryCacheService.getOrCompute(anyString(), any())).willAnswer(inv -> {
            Supplier<String> loader = inv.getArgument(1);
            return loader.get();
        });
        given(ragPort.ask(eq("Spring이란?"), anyList())).willReturn("Spring은 자바 프레임워크입니다.");

        QueryService.QueryResult result = queryService.query(1L, "Spring이란?", null);

        assertThat(result.answer()).isEqualTo("Spring은 자바 프레임워크입니다.");
        assertThat(result.sessionId()).isEqualTo(100L);

        ArgumentCaptor<QueryCompletedEvent> captor = ArgumentCaptor.forClass(QueryCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(1L);
        assertThat(captor.getValue().question()).isEqualTo("Spring이란?");
        assertThat(captor.getValue().answer()).isEqualTo("Spring은 자바 프레임워크입니다.");
        assertThat(captor.getValue().sessionId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("멀티턴 질의 - 대화 이력이 있으면 캐시를 우회하고 RAG 직접 호출")
    void query_multiTurn_bypassesCache() {
        ConversationTurn previousTurn = new ConversationTurn("이전 질문", "이전 답변");
        given(chatSessionService.prepareSession(eq(1L), eq("그건 왜 그래?"), eq(10L)))
                .willReturn(new ChatSessionService.SessionContext(10L, List.of(previousTurn)));
        given(ragPort.ask(eq("그건 왜 그래?"), anyList())).willReturn("맥락 기반 답변");

        QueryService.QueryResult result = queryService.query(1L, "그건 왜 그래?", 10L);

        assertThat(result.answer()).isEqualTo("맥락 기반 답변");
        // 멀티턴은 캐시를 거치지 않아야 한다 (정합성·프라이버시)
        verify(queryCacheService, never()).getOrCompute(anyString(), any());
        verify(ragPort).ask(eq("그건 왜 그래?"), anyList());
    }

    @Test
    @DisplayName("일일 요청 한도 초과 시 QUERY_RATE_LIMIT_EXCEEDED 예외 발생")
    void query_rateLimitExceeded() {
        given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(21L);

        assertThatThrownBy(() -> queryService.query(1L, "Spring이란?", null))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.QUERY_RATE_LIMIT_EXCEEDED));

        // 한도 초과 시 RAG 호출로 진행되면 안 된다
        verify(ragPort, never()).ask(anyString(), anyList());
    }

    @Test
    @DisplayName("검색 이력 조회 - cursor 없으면 첫 페이지 반환")
    void getHistory_firstPage() {
        QueryLog log1 = QueryLog.create(1L, null, "질문1", "답변1");
        QueryLog log2 = QueryLog.create(1L, null, "질문2", "답변2");
        given(queryLogRepository.findByUserIdWithCursor(1L, null, 20))
                .willReturn(List.of(log1, log2));

        List<QueryLog> result = queryService.getHistory(1L, null, 20);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("검색 이력 조회 - cursorId 기준으로 이전 데이터 반환")
    void getHistory_withCursor() {
        QueryLog log = QueryLog.create(1L, null, "질문", "답변");
        given(queryLogRepository.findByUserIdWithCursor(1L, 50L, 20))
                .willReturn(List.of(log));

        List<QueryLog> result = queryService.getHistory(1L, 50L, 20);

        assertThat(result).hasSize(1);
        verify(queryLogRepository).findByUserIdWithCursor(1L, 50L, 20);
    }
}
