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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueryServiceTest {

    @InjectMocks
    QueryService queryService;

    @Mock
    RagPort ragPort;

    @Mock
    QueryLogRepository queryLogRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(queryService, "dailyMax", 20);
    }

    @Test
    @DisplayName("질의 성공 - RAG 답변 반환 및 이벤트 발행")
    void query_success() {
        String rateKey = "rate:1:" + LocalDate.now();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment(rateKey)).willReturn(1L);
        given(ragPort.ask("Spring이란?")).willReturn("Spring은 자바 프레임워크입니다.");

        String answer = queryService.query(1L, "Spring이란?");

        assertThat(answer).isEqualTo("Spring은 자바 프레임워크입니다.");

        ArgumentCaptor<QueryCompletedEvent> captor = ArgumentCaptor.forClass(QueryCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(1L);
        assertThat(captor.getValue().question()).isEqualTo("Spring이란?");
        assertThat(captor.getValue().answer()).isEqualTo("Spring은 자바 프레임워크입니다.");
    }

    @Test
    @DisplayName("일일 요청 한도 초과 시 QUERY_RATE_LIMIT_EXCEEDED 예외 발생")
    void query_rateLimitExceeded() {
        String rateKey = "rate:1:" + LocalDate.now();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment(rateKey)).willReturn(21L);

        assertThatThrownBy(() -> queryService.query(1L, "Spring이란?"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.QUERY_RATE_LIMIT_EXCEEDED));
    }

    @Test
    @DisplayName("검색 이력 조회 - cursor 없으면 첫 페이지 반환")
    void getHistory_firstPage() {
        QueryLog log1 = QueryLog.create(1L, "질문1", "답변1");
        QueryLog log2 = QueryLog.create(1L, "질문2", "답변2");
        given(queryLogRepository.findByUserIdWithCursor(1L, null, 20))
                .willReturn(List.of(log1, log2));

        List<QueryLog> result = queryService.getHistory(1L, null, 20);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("검색 이력 조회 - cursorId 기준으로 이전 데이터 반환")
    void getHistory_withCursor() {
        QueryLog log = QueryLog.create(1L, "질문", "답변");
        given(queryLogRepository.findByUserIdWithCursor(1L, 50L, 20))
                .willReturn(List.of(log));

        List<QueryLog> result = queryService.getHistory(1L, 50L, 20);

        assertThat(result).hasSize(1);
        verify(queryLogRepository).findByUserIdWithCursor(1L, 50L, 20);
    }
}
