package com.example.backend.infrastructure.query;

import com.example.backend.application.query.QueryJob;
import com.example.backend.application.query.QueryJobProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static com.example.backend.infrastructure.query.RedisStreamQueryJobQueue.JOBS_STREAM_KEY;

/**
 * query:jobs 스트림 Consumer Group 소비자.
 *
 * 앱 기동 시 Consumer Group을 보장(없으면 생성)하고 컨테이너를 시작한다.
 * 메시지를 QueryJob으로 파싱해 QueryJobProcessor에 위임하고, 정상 처리 후 XACK 한다.
 * 처리기가 RAG 오류를 자체 처리(스트림에 error 발행)하므로 통상 경로는 항상 ack 된다.
 */
@Slf4j
@Component
public class RedisStreamJobConsumer
        implements StreamListener<String, MapRecord<String, String, String>>, InitializingBean, DisposableBean {

    static final String CONSUMER_GROUP = "workers";

    private final StringRedisTemplate redisTemplate;
    private final RedisConnectionFactory connectionFactory;
    private final QueryJobProcessor processor;
    private final String consumerName = "worker-" + UUID.randomUUID().toString().substring(0, 8);

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;

    public RedisStreamJobConsumer(StringRedisTemplate redisTemplate,
                                  RedisConnectionFactory connectionFactory,
                                  QueryJobProcessor processor) {
        this.redisTemplate = redisTemplate;
        this.connectionFactory = connectionFactory;
        this.processor = processor;
    }

    @Override
    public void afterPropertiesSet() {
        ensureConsumerGroup();

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(2))
                        .build();

        container = StreamMessageListenerContainer.create(connectionFactory, options);
        container.receive(
                Consumer.from(CONSUMER_GROUP, consumerName),
                StreamOffset.create(JOBS_STREAM_KEY, ReadOffset.lastConsumed()),
                this
        );
        container.start();
        log.info("질의 작업 컨슈머 시작 - group={}, consumer={}", CONSUMER_GROUP, consumerName);
    }

    private void ensureConsumerGroup() {
        try {
            // MKSTREAM 효과: 스트림이 없으면 함께 생성. 그룹이 이미 있으면 BUSYGROUP 예외 → 무시
            redisTemplate.opsForStream().createGroup(JOBS_STREAM_KEY, ReadOffset.from("0"), CONSUMER_GROUP);
        } catch (Exception e) {
            log.debug("Consumer Group 생성 생략(이미 존재 가능) - {}", e.getMessage());
        }
    }

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        try {
            processor.process(toJob(record.getValue()));
            redisTemplate.opsForStream().acknowledge(JOBS_STREAM_KEY, CONSUMER_GROUP, record.getId());
        } catch (Exception e) {
            // 파싱 등 예기치 못한 실패 — ack하지 않아 PEL에 남김 (Phase 3에서 XAUTOCLAIM/DLQ 처리)
            log.error("질의 작업 소비 실패 - recordId={}, error={}", record.getId(), e.getMessage(), e);
        }
    }

    private QueryJob toJob(Map<String, String> body) {
        String sessionId = body.get("sessionId");
        String requestId = body.get("requestId");
        return new QueryJob(
                body.get("jobId"),
                Long.valueOf(body.get("userId")),
                body.get("question"),
                sessionId == null || sessionId.isEmpty() ? null : Long.valueOf(sessionId),
                requestId == null || requestId.isEmpty() ? null : requestId
        );
    }

    @Override
    public void destroy() {
        if (container != null) {
            container.stop();
        }
    }
}
