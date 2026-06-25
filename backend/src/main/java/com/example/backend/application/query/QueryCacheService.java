package com.example.backend.application.query;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * L1(Caffeine) + L2(Redis) 이중 캐시 서비스.
 *
 * 읽기 순서: L1 → L2 → loader(RAG 호출)
 * 쓰기 순서: loader 결과를 L2 → L1 순서로 저장 (write-through)
 *
 * Stampede 방지: 동일 키 미스 시 Redis SET NX로 분산 락 획득,
 * 1개 스레드만 loader를 실행하고 나머지는 캐시 채워질 때까지 대기.
 * 가상 스레드에서 Thread.sleep()은 park(carrier 비점유)로 동작해 부하가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryCacheService {

    private static final String L2_PREFIX   = "query:cache:";
    private static final String LOCK_PREFIX = "query:lock:";

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${cache.query.l1-max-size:500}")
    private int l1MaxSize;

    @Value("${cache.query.l1-ttl-minutes:5}")
    private int l1TtlMinutes;

    @Value("${cache.query.l2-ttl-minutes:60}")
    private int l2TtlMinutes;

    @Value("${cache.query.lock-ttl-seconds:30}")
    private int lockTtlSeconds;

    @Value("${cache.query.lock-wait-ms:200}")
    private long lockWaitMs;

    @Value("${cache.query.lock-max-retries:15}")
    private int lockMaxRetries;

    private Cache<String, String> l1Cache;
    private Counter l1HitCounter;
    private Counter l2HitCounter;
    private Counter missCounter;
    private Counter stampedeBlockedCounter;

    @PostConstruct
    void init() {
        l1Cache = Caffeine.newBuilder()
                .maximumSize(l1MaxSize)
                .expireAfterWrite(l1TtlMinutes, TimeUnit.MINUTES)
                .recordStats()
                .build();

        l1HitCounter       = Counter.builder("cache.query.hit").tag("level", "L1").description("L1 캐시 히트").register(meterRegistry);
        l2HitCounter       = Counter.builder("cache.query.hit").tag("level", "L2").description("L2 캐시 히트").register(meterRegistry);
        missCounter        = Counter.builder("cache.query.miss").description("캐시 완전 미스 (RAG 실제 호출)").register(meterRegistry);
        stampedeBlockedCounter = Counter.builder("cache.query.stampede.blocked").description("Stampede 방지로 대기 후 캐시 반환된 요청").register(meterRegistry);

        // Caffeine 통계를 Gauge로 노출 (Grafana 확인용)
        Gauge.builder("cache.query.l1.hit_rate", l1Cache, c -> c.stats().hitRate())
                .description("L1 캐시 히트율 (0.0~1.0)").register(meterRegistry);
        Gauge.builder("cache.query.l1.size", l1Cache, c -> (double) c.estimatedSize())
                .description("L1 캐시 현재 항목 수").register(meterRegistry);
    }

    /**
     * loader 실행 없이 캐시(L1 → L2)만 조회한다.
     * 비동기 제출 단계에서 캐시 히트(동기 즉시 반환) 여부를 판정하는 데 사용한다.
     * 히트 시 L2 값을 L1로 승격한다. 미스이면 Optional.empty().
     */
    public Optional<String> getIfCached(String cacheKey) {
        String l1 = l1Cache.getIfPresent(cacheKey);
        if (l1 != null) {
            l1HitCounter.increment();
            return Optional.of(l1);
        }
        String l2 = redisTemplate.opsForValue().get(L2_PREFIX + cacheKey);
        if (l2 != null) {
            l2HitCounter.increment();
            l1Cache.put(cacheKey, l2);
            return Optional.of(l2);
        }
        return Optional.empty();
    }

    /**
     * 캐시에서 조회하고, 미스이면 loader를 실행해 결과를 캐시에 저장 후 반환한다.
     * Stampede 방지 포함: 동일 키에 동시 미스 발생 시 loader는 1회만 실행된다.
     */
    public String getOrCompute(String cacheKey, Supplier<String> loader) {
        // L1 체크
        String l1 = l1Cache.getIfPresent(cacheKey);
        if (l1 != null) {
            l1HitCounter.increment();
            return l1;
        }

        // L2 체크
        String l2 = redisTemplate.opsForValue().get(L2_PREFIX + cacheKey);
        if (l2 != null) {
            l2HitCounter.increment();
            l1Cache.put(cacheKey, l2);
            return l2;
        }

        // 캐시 미스 → Stampede 방지 후 loader 실행
        missCounter.increment();
        return computeWithLock(cacheKey, loader);
    }

    private String computeWithLock(String cacheKey, Supplier<String> loader) {
        String lockKey = LOCK_PREFIX + cacheKey;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", lockTtlSeconds, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(acquired)) {
            // 락 획득 → 이 스레드만 loader 실행
            try {
                // Double-check: 락 획득 사이에 다른 노드가 채웠을 가능성
                String cached = redisTemplate.opsForValue().get(L2_PREFIX + cacheKey);
                if (cached != null) {
                    l1Cache.put(cacheKey, cached);
                    return cached;
                }
                String result = loader.get();
                putBoth(cacheKey, result);
                return result;
            } finally {
                redisTemplate.delete(lockKey);
            }
        }

        // 락 획득 실패 → 다른 스레드가 RAG 호출 중, 폴링으로 캐시 대기
        // 가상 스레드: Thread.sleep()은 park 처리 → carrier thread 비점유
        return waitForCache(cacheKey, loader);
    }

    private String waitForCache(String cacheKey, Supplier<String> loader) {
        for (int i = 0; i < lockMaxRetries; i++) {
            try {
                Thread.sleep(lockWaitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            String l1 = l1Cache.getIfPresent(cacheKey);
            if (l1 != null) {
                stampedeBlockedCounter.increment();
                return l1;
            }
            String l2 = redisTemplate.opsForValue().get(L2_PREFIX + cacheKey);
            if (l2 != null) {
                stampedeBlockedCounter.increment();
                l1Cache.put(cacheKey, l2);
                return l2;
            }
        }

        // 최대 대기 초과 (드문 케이스) → 직접 실행 후 저장
        log.warn("캐시 Stampede 대기 초과, 직접 loader 실행: key={}", cacheKey);
        String result = loader.get();
        try {
            putBoth(cacheKey, result);
        } catch (Exception e) {
            log.warn("캐시 저장 실패 (요청은 정상 처리): error={}", e.getMessage());
        }
        return result;
    }

    /** 외부에서 계산한 값을 캐시에 저장한다 (스트리밍 완료 후 누적된 전체 답변 저장용). */
    public void put(String cacheKey, String value) {
        putBoth(cacheKey, value);
    }

    private void putBoth(String cacheKey, String value) {
        l1Cache.put(cacheKey, value);
        redisTemplate.opsForValue().set(L2_PREFIX + cacheKey, value, l2TtlMinutes, TimeUnit.MINUTES);
    }

    /**
     * 질문 텍스트를 정규화 후 SHA-256 해시로 변환한다.
     * - 앞뒤 공백 제거, 연속 공백 단일화, 소문자 변환
     * - 동일한 의미의 질문이 같은 키를 가지도록 한다
     */
    public static String cacheKeyOf(String question) {
        try {
            String normalized = question.strip().replaceAll("\\s+", " ").toLowerCase();
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
