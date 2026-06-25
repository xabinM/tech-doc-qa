package com.example.backend.application.query;

import com.example.backend.application.query.event.QueryCompletedEvent;
import com.example.backend.application.query.port.JobOwnershipStore;
import com.example.backend.application.query.port.QueryJobQueue;
import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.example.backend.domain.query.QueryLog;
import com.example.backend.domain.query.QueryLogRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueryServiceTest {

    QueryService queryService;

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

    @Mock
    QueryJobQueue queryJobQueue;

    @Mock
    JobOwnershipStore jobOwnershipStore;

    @BeforeEach
    void setUp() {
        // MeterRegistry는 실제 SimpleMeterRegistry 사용 — Counter.register()가 정상 동작해야 함
        queryService = new QueryService(chatSessionService, queryLogRepository, eventPublisher,
                redisTemplate, new SimpleMeterRegistry(), queryCacheService, queryJobQueue, jobOwnershipStore);
        ReflectionTestUtils.setField(queryService, "dailyMax", 20);
        queryService.initMetrics();
    }

    @Test
    @DisplayName("캐시 히트 시 동기 완료(Completed) 반환 및 이력 이벤트 발행")
    void submit_cacheHit_returnsCompletedAndPublishesEvent() {
        given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(1L);
        given(chatSessionService.prepareSession(eq(1L), eq("Spring이란?"), isNull()))
                .willReturn(new ChatSessionService.SessionContext(100L, List.of()));
        given(queryCacheService.getIfCached(anyString()))
                .willReturn(Optional.of("Spring은 자바 프레임워크입니다."));

        QueryService.SubmitResult result = queryService.submit(1L, "Spring이란?", null);

        assertThat(result).isInstanceOf(QueryService.SubmitResult.Completed.class);
        QueryService.SubmitResult.Completed completed = (QueryService.SubmitResult.Completed) result;
        assertThat(completed.answer()).isEqualTo("Spring은 자바 프레임워크입니다.");
        assertThat(completed.sessionId()).isEqualTo(100L);

        ArgumentCaptor<QueryCompletedEvent> captor = ArgumentCaptor.forClass(QueryCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().answer()).isEqualTo("Spring은 자바 프레임워크입니다.");
        assertThat(captor.getValue().sessionId()).isEqualTo(100L);
        verify(queryJobQueue, never()).publish(any());
    }

    @Test
    @DisplayName("캐시 미스 시 작업 큐 발행(Accepted) 및 jobId 반환")
    void submit_cacheMiss_publishesJobAndReturnsAccepted() {
        given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(1L);
        given(chatSessionService.prepareSession(eq(1L), eq("Spring이란?"), isNull()))
                .willReturn(new ChatSessionService.SessionContext(100L, List.of()));
        given(queryCacheService.getIfCached(anyString())).willReturn(Optional.empty());

        QueryService.SubmitResult result = queryService.submit(1L, "Spring이란?", null);

        assertThat(result).isInstanceOf(QueryService.SubmitResult.Accepted.class);
        QueryService.SubmitResult.Accepted accepted = (QueryService.SubmitResult.Accepted) result;
        assertThat(accepted.jobId()).isNotBlank();
        assertThat(accepted.sessionId()).isEqualTo(100L);

        ArgumentCaptor<QueryJob> captor = ArgumentCaptor.forClass(QueryJob.class);
        verify(queryJobQueue).publish(captor.capture());
        assertThat(captor.getValue().jobId()).isEqualTo(accepted.jobId());
        assertThat(captor.getValue().question()).isEqualTo("Spring이란?");
        assertThat(captor.getValue().sessionId()).isEqualTo(100L);
        verify(jobOwnershipStore).register(accepted.jobId(), 1L);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("일일 요청 한도 초과 시 QUERY_RATE_LIMIT_EXCEEDED 예외 발생")
    void submit_rateLimitExceeded() {
        given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(21L);

        assertThatThrownBy(() -> queryService.submit(1L, "Spring이란?", null))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.QUERY_RATE_LIMIT_EXCEEDED));
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
