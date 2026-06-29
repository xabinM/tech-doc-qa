package com.example.backend.application.query;

import com.example.backend.application.query.port.JobOwnershipStore;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
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
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final QueryJobQueue queryJobQueue;
    private final JobOwnershipStore jobOwnershipStore;

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
                .description("질의 제출 처리 소요 시간 (Rate Limit·세션 준비·잡 발행)")
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
     *   3. 작업 큐 발행 후 jobId 반환 (워커가 RAG 호출·스트리밍·이력 저장 담당)
     */
    public SubmitResult submit(Long userId, String question, Long sessionId) {
        checkRateLimit(userId);
        queryCounter.increment();

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            ChatSessionService.SessionContext ctx = chatSessionService.prepareSession(userId, question, sessionId);

            String jobId = UUID.randomUUID().toString();
            jobOwnershipStore.register(jobId, userId);  // 스트림 구독 시 소유권 검증용
            queryJobQueue.publish(new QueryJob(jobId, userId, question, ctx.sessionId(), MDC.get(MDC_REQUEST_ID)));
            return new SubmitResult(jobId, ctx.sessionId());
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
     * 제출 결과 — jobId 반환 후 클라이언트가 답변 스트림(SSE)을 구독한다 (HTTP 202).
     */
    public record SubmitResult(String jobId, Long sessionId) {}
}
