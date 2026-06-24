package com.example.backend.application.query;

import com.example.backend.application.query.port.AnswerStream;
import com.example.backend.application.query.port.RagPort;
import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.example.backend.domain.query.QueryLog;
import com.example.backend.domain.query.QueryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.example.backend.common.filter.RequestLoggingFilter.MDC_REQUEST_ID;

/**
 * 비동기 질의 작업 처리기.
 *
 * Redis Stream 컨슈머가 호출한다. RAG 답변을 생성해 answer:{jobId} 스트림으로 발행하고
 * 검색 이력을 저장한다.
 *
 * 트랜잭션을 열지 않는다 — RAG 호출 중 DB 커넥션 점유 금지 규칙 준수.
 * history 조회와 이력 저장은 각각 짧은 독립 트랜잭션으로 처리된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryJobProcessor {

    private final ChatSessionService chatSessionService;
    private final RagPort ragPort;
    private final QueryCacheService queryCacheService;
    private final QueryLogRepository queryLogRepository;
    private final AnswerStream answerStream;

    public void process(QueryJob job) {
        if (job.requestId() != null) {
            MDC.put(MDC_REQUEST_ID, job.requestId());  // 제출 시점 추적 ID 복원 (end-to-end 로그)
        }
        try {
            List<ConversationTurn> history = chatSessionService.loadConversationHistory(job.sessionId());
            String cacheKey = QueryCacheService.cacheKeyOf(job.question());

            // 캐시 재확인 포함(Stampede 방지) — 미스 시에만 RAG 호출
            String answer = queryCacheService.getOrCompute(cacheKey, () -> ragPort.ask(job.question(), history));

            answerStream.publishToken(job.jobId(), answer);
            answerStream.publishDone(job.jobId());

            saveHistoryQuietly(job, answer);
        } catch (CustomException e) {
            log.warn("질의 작업 처리 실패 - jobId={}, code={}", job.jobId(), e.getErrorCode().getCode());
            answerStream.publishError(job.jobId(), e.getErrorCode().getCode());
        } catch (Exception e) {
            log.error("질의 작업 처리 중 예외 - jobId={}", job.jobId(), e);
            answerStream.publishError(job.jobId(), ErrorCode.INTERNAL_SERVER_ERROR.getCode());
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    /** 이력 저장 흡수 — 답변은 이미 스트림으로 전달됐으므로 저장 실패는 로그만 남기고 삼킨다. */
    private void saveHistoryQuietly(QueryJob job, String answer) {
        try {
            queryLogRepository.save(QueryLog.create(job.userId(), job.sessionId(), job.question(), answer));
        } catch (Exception e) {
            log.error("이력 저장 실패 - jobId={}, error={}", job.jobId(), e.getMessage(), e);
        }
    }
}
