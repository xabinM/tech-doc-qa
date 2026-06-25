package com.example.backend.infrastructure.query;

import com.example.backend.application.query.port.JobOwnershipStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 기반 작업 소유권 저장소.
 *
 * job:owner:{jobId} = userId 로 저장하고 TTL을 답변 스트림(1h)과 맞춘다.
 */
@Component
@RequiredArgsConstructor
public class RedisJobOwnershipStore implements JobOwnershipStore {

    static final String KEY_PREFIX = "job:owner:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void register(String jobId, Long userId) {
        redisTemplate.opsForValue().set(KEY_PREFIX + jobId, String.valueOf(userId), TTL);
    }

    @Override
    public boolean isOwner(String jobId, Long userId) {
        String owner = redisTemplate.opsForValue().get(KEY_PREFIX + jobId);
        return owner != null && owner.equals(String.valueOf(userId));
    }
}
