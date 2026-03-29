package com.example.backend.infrastructure.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RedisTokenRepository {

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    public void saveRefreshToken(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + userId,
                refreshToken,
                Duration.ofMillis(refreshTokenExpiry)
        );
    }

    public void deleteRefreshToken(Long userId) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
    }

    public Optional<String> getRefreshToken(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + userId));
    }
}
