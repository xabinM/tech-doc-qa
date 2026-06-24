package com.example.backend.infrastructure.query;

import com.example.backend.application.query.QueryJob;
import com.example.backend.application.query.port.QueryJobQueue;
import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Redis Streams 기반 질의 작업 큐 발행 구현체.
 *
 * query:jobs 스트림에 XADD 한다. 소비는 Consumer Group 기반 워커(별도 컴포넌트)가 담당한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamQueryJobQueue implements QueryJobQueue {

    /** 질의 작업 큐 스트림 키 (소비 측과 공유) */
    public static final String JOBS_STREAM_KEY = "query:jobs";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void publish(QueryJob job) {
        // Redis 필드 값은 null 불가 — sessionId/requestId 부재 시 빈 문자열로 정규화
        Map<String, String> fields = new HashMap<>();
        fields.put("jobId", job.jobId());
        fields.put("userId", String.valueOf(job.userId()));
        fields.put("question", job.question());
        fields.put("sessionId", job.sessionId() == null ? "" : String.valueOf(job.sessionId()));
        fields.put("requestId", job.requestId() == null ? "" : job.requestId());

        try {
            RecordId recordId = redisTemplate.opsForStream()
                    .add(StreamRecords.newRecord().in(JOBS_STREAM_KEY).ofStrings(fields));
            log.debug("질의 잡 발행 - jobId={}, recordId={}", job.jobId(), recordId);
        } catch (Exception e) {
            log.error("질의 잡 발행 실패 - jobId={}, error={}", job.jobId(), e.getMessage(), e);
            throw new CustomException(ErrorCode.QUERY_STREAM_FAILED);
        }
    }
}
