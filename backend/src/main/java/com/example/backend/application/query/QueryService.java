package com.example.backend.application.query;

import com.example.backend.application.query.event.QueryCompletedEvent;
import com.example.backend.application.query.port.RagPort;
import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.example.backend.domain.query.QueryLog;
import com.example.backend.domain.query.QueryLogRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QueryService {

    private static final String RATE_LIMIT_PREFIX = "rate:";
    private static final String SECONDS_PER_DAY = "86400";
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript.of(
            "local c = redis.call('INCR', KEYS[1])\n" +
            "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
            "return c",
            Long.class
    );

    private final RagPort ragPort;
    private final ChatSessionService chatSessionService;
    private final QueryLogRepository queryLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final QueryCacheService queryCacheService;

    @Value("${query.rate-limit.daily-max:20}")
    private int dailyMax;

    private Counter queryCounter;
    private Counter rateLimitCounter;
    private Timer queryTimer;

    @PostConstruct
    void initMetrics() {
        queryCounter = Counter.builder("query.requests.total")
                .description("질문 처리 총 횟수")
                .register(meterRegistry);
        rateLimitCounter = Counter.builder("query.rate_limit.blocked")
                .description("Rate Limit 초과 차단 횟수")
                .register(meterRegistry);
        queryTimer = Timer.builder("query.duration")
                .description("질문 처리 전체 소요 시간 (RAG 호출 포함)")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    // 트랜잭션 없음 - RAG 호출 중 DB 커넥션 점유 방지
    // prepareSession에서 트랜잭션을 열고 커밋한 뒤 RAG 호출
    public QueryResult query(Long userId, String question, Long sessionId) {
        checkRateLimit(userId);
        queryCounter.increment();

        String cacheKey = QueryCacheService.cacheKeyOf(question);

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // 세션 준비 (트랜잭션 커밋까지 완료)
            ChatSessionService.SessionContext ctx = chatSessionService.prepareSession(userId, question, sessionId);

            // L1 → L2 → RAG 순서로 조회 (Stampede 방지 포함)
            // 캐시 히트 시 loader lambda는 실행되지 않음
            String answer = queryCacheService.getOrCompute(
                    cacheKey,
                    () -> ragPort.ask(question, ctx.history())
            );

            eventPublisher.publishEvent(new QueryCompletedEvent(userId, question, answer, ctx.sessionId()));
            return new QueryResult(answer, ctx.sessionId());
        } finally {
            sample.stop(queryTimer);
        }
    }

    @Transactional(readOnly = true)
    public List<QueryLog> getHistory(Long userId, Long cursorId, int size) {
        return queryLogRepository.findByUserIdWithCursor(userId, cursorId, size);
    }

    private void checkRateLimit(Long userId) {
        String key = RATE_LIMIT_PREFIX + userId + ":" + LocalDate.now();
        Long count = redisTemplate.execute(RATE_LIMIT_SCRIPT, List.of(key), SECONDS_PER_DAY);

        if (count != null && count > dailyMax) {
            rateLimitCounter.increment();
            throw new CustomException(ErrorCode.QUERY_RATE_LIMIT_EXCEEDED);
        }
    }

    public record QueryResult(String answer, Long sessionId) {}
}
