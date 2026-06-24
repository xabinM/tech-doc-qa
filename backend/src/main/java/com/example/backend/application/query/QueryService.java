package com.example.backend.application.query;

import com.example.backend.application.query.event.QueryCompletedEvent;
import com.example.backend.application.query.port.QueryJobQueue;
import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.example.backend.domain.query.QueryLog;
import com.example.backend.domain.query.QueryLogRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.example.backend.common.filter.RequestLoggingFilter.MDC_REQUEST_ID;

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

    private final ChatSessionService chatSessionService;
    private final QueryLogRepository queryLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final QueryCacheService queryCacheService;
    private final QueryJobQueue queryJobQueue;

    @Value("${query.rate-limit.daily-max:20}")
    private int dailyMax;

    private Counter queryCounter;
    private Counter rateLimitCounter;
    private Timer submitTimer;

    @PostConstruct
    void initMetrics() {
        queryCounter = Counter.builder("query.requests.total")
                .description("질문 처리 총 횟수")
                .register(meterRegistry);
        rateLimitCounter = Counter.builder("query.rate_limit.blocked")
                .description("Rate Limit 초과 차단 횟수")
                .register(meterRegistry);
        submitTimer = Timer.builder("query.duration")
                .description("질의 제출 처리 소요 시간 (Rate Limit·세션 준비·캐시 조회·잡 발행)")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    /**
     * 질의를 제출한다. 비동기 처리 진입점.
     *
     * 동기 구간만 수행하고 즉시 반환한다 — RAG 호출은 워커가 비동기로 처리한다.
     *   1. Rate Limit 검사 (큐 오염 방지를 위해 발행 전에 거절)
     *   2. 세션 준비 (트랜잭션 커밋까지 완료)
     *   3. 캐시 조회 → 히트면 동기 즉시 반환(Completed), 미스면 작업 큐 발행(Accepted)
     */
    public SubmitResult submit(Long userId, String question, Long sessionId) {
        checkRateLimit(userId);
        queryCounter.increment();

        String cacheKey = QueryCacheService.cacheKeyOf(question);

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            ChatSessionService.SessionContext ctx = chatSessionService.prepareSession(userId, question, sessionId);

            Optional<String> cached = queryCacheService.getIfCached(cacheKey);
            if (cached.isPresent()) {
                // 캐시 히트 → 비동기 처리 없이 즉시 반환, 이력 저장 이벤트 발행
                eventPublisher.publishEvent(new QueryCompletedEvent(userId, question, cached.get(), ctx.sessionId()));
                return new SubmitResult.Completed(cached.get(), ctx.sessionId());
            }

            // 캐시 미스 → 비동기 작업 발행 (워커가 RAG 호출·스트리밍·이력 저장 담당)
            String jobId = UUID.randomUUID().toString();
            queryJobQueue.publish(new QueryJob(jobId, userId, question, ctx.sessionId(), MDC.get(MDC_REQUEST_ID)));
            return new SubmitResult.Accepted(jobId, ctx.sessionId());
        } finally {
            sample.stop(submitTimer);
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

    /**
     * 제출 결과.
     *   Completed → 캐시 히트, answer 즉시 반환 (HTTP 200)
     *   Accepted  → 캐시 미스, jobId 반환 후 클라이언트가 스트림 구독 (HTTP 202)
     */
    public sealed interface SubmitResult permits SubmitResult.Completed, SubmitResult.Accepted {
        record Completed(String answer, Long sessionId) implements SubmitResult {}
        record Accepted(String jobId, Long sessionId) implements SubmitResult {}
    }
}
