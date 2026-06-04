package com.example.backend.application.query;

import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * POST /api/v1/query 멱등성 처리 서비스.
 *
 * 클라이언트가 Idempotency-Key 헤더를 제공하면, 동일한 키로 들어온 중복 요청에 대해
 * LLM 호출 없이 저장된 응답을 반환한다.
 *
 * Redis State Machine:
 *   (없음) → SET NX → PROCESSING(60s TTL)
 *   PROCESSING → 완료 시 → COMPLETED(24h TTL)
 *   PROCESSING → 실패 시 → FAILED(5m TTL, 재시도 허용)
 *
 * Key 구조: idempotency:{userId}:{idempotencyKey}
 *   → 사용자 범위로 격리해 크로스-유저 재사용 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX    = "idempotency:";
    private static final Duration PROCESSING_TTL = Duration.ofSeconds(60);
    private static final Duration COMPLETED_TTL  = Duration.ofHours(24);
    private static final Duration FAILED_TTL     = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Value("${idempotency.wait-ms:300}")
    private long waitMs;

    @Value("${idempotency.max-retries:10}")
    private int maxRetries;

    private Counter replayedCounter;
    private Counter newCounter;

    @PostConstruct
    void initMetrics() {
        replayedCounter = Counter.builder("idempotency.replayed")
                .description("중복 요청이 저장된 응답으로 처리된 횟수 (LLM 호출 절약)")
                .register(meterRegistry);
        newCounter = Counter.builder("idempotency.new")
                .description("Idempotency-Key가 새로 등록된 최초 요청 횟수")
                .register(meterRegistry);
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * 요청 처리를 시작하려고 시도한다.
     *
     * @return StartResult.New            → 최초 요청, 처리 진행
     *         StartResult.AlreadyCompleted → 이미 완료된 요청, 저장 응답 반환
     *         StartResult.StillProcessing  → 다른 스레드가 처리 중, 최대 대기 초과
     */
    public StartResult tryStart(Long userId, String idempotencyKey) {
        String key = redisKey(userId, idempotencyKey);
        String raw = redisTemplate.opsForValue().get(key);

        if (raw != null) {
            IdempotencyRecord record = deserialize(raw);
            return switch (record.status) {
                case COMPLETED -> {
                    replayedCounter.increment();
                    log.debug("idempotency: 중복 요청 차단 - key={}", idempotencyKey);
                    yield new StartResult.AlreadyCompleted(record.answer, record.sessionId);
                }
                case PROCESSING -> {
                    // 다른 스레드(또는 인스턴스)가 처리 중 → 폴링 대기
                    yield waitForCompletion(key, idempotencyKey);
                }
                case FAILED -> {
                    // 이전 실패 → 재시도 허용 (FAILED TTL 5분 이내)
                    log.debug("idempotency: 이전 실패 기록으로 재시도 허용 - key={}", idempotencyKey);
                    markProcessing(key);
                    newCounter.increment();
                    yield new StartResult.New();
                }
            };
        }

        // 새 키: PROCESSING으로 원자적 등록 (SET NX)
        boolean isNew = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(key, serialize(new IdempotencyRecord(Status.PROCESSING)), PROCESSING_TTL)
        );

        if (isNew) {
            newCounter.increment();
            return new StartResult.New();
        }

        // 극히 드문 경쟁 상황(동시 요청 간 레이스): 다시 대기
        return waitForCompletion(key, idempotencyKey);
    }

    /** 처리 성공 시 COMPLETED로 전환 (24시간 유지) */
    public void complete(Long userId, String idempotencyKey, String answer, Long sessionId) {
        String key = redisKey(userId, idempotencyKey);
        redisTemplate.opsForValue().set(
                key,
                serialize(new IdempotencyRecord(Status.COMPLETED, answer, sessionId, null)),
                COMPLETED_TTL
        );
    }

    /** 처리 실패 시 FAILED로 전환 (5분 후 재시도 허용) */
    public void fail(Long userId, String idempotencyKey, String errorCode) {
        String key = redisKey(userId, idempotencyKey);
        redisTemplate.opsForValue().set(
                key,
                serialize(new IdempotencyRecord(Status.FAILED, null, null, errorCode)),
                FAILED_TTL
        );
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    /**
     * PROCESSING 상태 대기 폴링.
     * 가상 스레드 환경: Thread.sleep() → park (carrier thread 비점유)
     */
    private StartResult waitForCompletion(String key, String idempotencyKey) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            String raw = redisTemplate.opsForValue().get(key);
            if (raw != null) {
                IdempotencyRecord record = deserialize(raw);
                switch (record.status) {
                    case COMPLETED -> {
                        replayedCounter.increment();
                        return new StartResult.AlreadyCompleted(record.answer, record.sessionId);
                    }
                    case FAILED -> {
                        // 원래 처리가 실패 → 바로 재시도 허용 (StillProcessing 반환 불필요)
                        log.debug("idempotency: 폴링 중 FAILED 감지, 재시도 허용 - key={}", idempotencyKey);
                        markProcessing(key);
                        newCounter.increment();
                        return new StartResult.New();
                    }
                    default -> { /* PROCESSING → 계속 대기 */ }
                }
            }
        }
        // 최대 대기 초과: 처리 중임을 클라이언트에 알림
        log.warn("idempotency: PROCESSING 대기 초과 - key={}", idempotencyKey);
        return new StartResult.StillProcessing();
    }

    private void markProcessing(String key) {
        redisTemplate.opsForValue().set(
                key, serialize(new IdempotencyRecord(Status.PROCESSING)), PROCESSING_TTL
        );
    }

    private static String redisKey(Long userId, String idempotencyKey) {
        return KEY_PREFIX + userId + ":" + idempotencyKey;
    }

    private String serialize(IdempotencyRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("IdempotencyRecord 직렬화 실패", e);
        }
    }

    private IdempotencyRecord deserialize(String json) {
        try {
            return objectMapper.readValue(json, IdempotencyRecord.class);
        } catch (Exception e) {
            throw new IllegalStateException("IdempotencyRecord 역직렬화 실패: " + json, e);
        }
    }

    // ─── Nested Types ─────────────────────────────────────────────────────────

    public enum Status { PROCESSING, COMPLETED, FAILED }

    /** Redis에 저장되는 멱등성 기록 */
    public static class IdempotencyRecord {
        @JsonProperty public Status status;
        @JsonProperty public String answer;
        @JsonProperty public Long   sessionId;
        @JsonProperty public String errorCode;

        public IdempotencyRecord() {}

        public IdempotencyRecord(Status status) {
            this.status = status;
        }

        public IdempotencyRecord(Status status, String answer, Long sessionId, String errorCode) {
            this.status    = status;
            this.answer    = answer;
            this.sessionId = sessionId;
            this.errorCode = errorCode;
        }
    }

    /**
     * tryStart() 반환 타입 — sealed interface + pattern matching
     *
     * Controller에서:
     *   switch (result) {
     *     case AlreadyCompleted c -> return c.answer ...
     *     case StillProcessing  ignored -> throw ...
     *     case New              ignored -> { ... }
     *   }
     */
    public sealed interface StartResult
            permits StartResult.New, StartResult.AlreadyCompleted, StartResult.StillProcessing {

        record New() implements StartResult {}

        record AlreadyCompleted(String answer, Long sessionId) implements StartResult {}

        record StillProcessing() implements StartResult {}
    }
}
