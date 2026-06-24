package com.example.backend.infrastructure.query;

import com.example.backend.application.query.port.AnswerStreamReader;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Redis Streams 기반 답변 스트림 읽기 구현체.
 *
 * answer:{jobId} 스트림을 XREAD BLOCK으로 읽는다. Consumer Group이 아닌 단순 XREAD이므로
 * 여러 SSE 연결이 동일 스트림을 독립적으로(각자의 offset으로) 읽을 수 있다 — 재연결/재생에 적합.
 */
@Component
@RequiredArgsConstructor
public class RedisAnswerStreamReader implements AnswerStreamReader {

    private static final int MAX_COUNT = 64;

    private final StringRedisTemplate redisTemplate;

    @Override
    public List<AnswerEvent> readAfter(String jobId, String lastEventId, long blockMs) {
        String key = RedisAnswerStream.STREAM_PREFIX + jobId;
        StreamReadOptions options = StreamReadOptions.empty()
                .block(Duration.ofMillis(blockMs))
                .count(MAX_COUNT);

        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .read(options, StreamOffset.create(key, ReadOffset.from(lastEventId)));

        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream().map(this::toEvent).toList();
    }

    private AnswerEvent toEvent(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();
        String type = str(value.get("type"));
        String payload = "error".equals(type) ? str(value.get("code")) : str(value.get("data"));
        return new AnswerEvent(record.getId().getValue(), type, payload);
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
