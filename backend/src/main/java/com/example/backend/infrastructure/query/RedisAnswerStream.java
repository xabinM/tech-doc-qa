package com.example.backend.infrastructure.query;

import com.example.backend.application.query.port.AnswerStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Streams 기반 답변 스트림 발행 구현체.
 *
 * answer:{jobId} 스트림에 XADD 하고, 키에 TTL을 걸어 재연결 허용 시간 이후 자동 정리한다.
 * 각 엔트리는 type(token/done/error) + data/code 필드를 가진다.
 */
@Component
@RequiredArgsConstructor
public class RedisAnswerStream implements AnswerStream {

    static final String STREAM_PREFIX = "answer:";
    private static final Duration STREAM_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void publishToken(String jobId, String token) {
        Map<String, String> fields = new HashMap<>();
        fields.put("type", "token");
        fields.put("data", token);
        add(jobId, fields);
    }

    @Override
    public void publishDone(String jobId) {
        Map<String, String> fields = new HashMap<>();
        fields.put("type", "done");
        fields.put("data", "");
        add(jobId, fields);
    }

    @Override
    public void publishError(String jobId, String errorCode) {
        Map<String, String> fields = new HashMap<>();
        fields.put("type", "error");
        fields.put("code", errorCode);
        add(jobId, fields);
    }

    private void add(String jobId, Map<String, String> fields) {
        String key = STREAM_PREFIX + jobId;
        redisTemplate.opsForStream().add(StreamRecords.newRecord().in(key).ofStrings(fields));
        redisTemplate.expire(key, STREAM_TTL);
    }
}
