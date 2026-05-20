package com.example.backend.application.query;

import com.example.backend.application.query.event.QueryCompletedEvent;
import com.example.backend.application.query.port.RagPort;
import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.example.backend.domain.query.QueryLog;
import com.example.backend.domain.query.QueryLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

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

    private final RagPort ragPort;
    private final ChatSessionService chatSessionService;
    private final QueryLogRepository queryLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;

    @Value("${query.rate-limit.daily-max:20}")
    private int dailyMax;

    // 트랜잭션 없음 - RAG 호출 중 DB 커넥션 점유 방지
    // prepareSession에서 트랜잭션을 열고 커밋한 뒤 RAG 호출
    public QueryResult query(Long userId, String question, Long sessionId) {
        checkRateLimit(userId);

        ChatSessionService.SessionContext ctx = chatSessionService.prepareSession(userId, question, sessionId);

        String answer = ragPort.ask(question, ctx.history());

        eventPublisher.publishEvent(new QueryCompletedEvent(userId, question, answer, ctx.sessionId()));

        return new QueryResult(answer, ctx.sessionId());
    }

    @Transactional(readOnly = true)
    public List<QueryLog> getHistory(Long userId, Long cursorId, int size) {
        return queryLogRepository.findByUserIdWithCursor(userId, cursorId, size);
    }

    private void checkRateLimit(Long userId) {
        String key = RATE_LIMIT_PREFIX + userId + ":" + LocalDate.now();
        Long count = redisTemplate.execute(RATE_LIMIT_SCRIPT, List.of(key), SECONDS_PER_DAY);

        if (count != null && count > dailyMax) {
            throw new CustomException(ErrorCode.QUERY_RATE_LIMIT_EXCEEDED);
        }
    }

    public record QueryResult(String answer, Long sessionId) {}
}
