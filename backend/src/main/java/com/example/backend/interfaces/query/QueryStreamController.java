package com.example.backend.interfaces.query;

import com.example.backend.application.query.port.JobOwnershipStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 답변 스트림 SSE 엔드포인트.
 *
 * 캐시 미스로 202를 받은 클라이언트가 jobId로 구독해 답변 토큰을 실시간 수신한다.
 * 인증은 SecurityConfig(/api/v1/query/** authenticated)가 보장하고(BFF가 AT 주입),
 * jobId 소유권은 JobOwnershipStore로 검증한다 — 타 사용자 스트림 접근(IDOR) 차단.
 */
@RestController
@RequestMapping("/api/v1/query")
@RequiredArgsConstructor
public class QueryStreamController {

    private final AnswerSseRelay answerSseRelay;
    private final JobOwnershipStore jobOwnershipStore;

    @GetMapping(value = "/{jobId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @AuthenticationPrincipal Long userId,
            @PathVariable String jobId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId
    ) {
        if (!jobOwnershipStore.isOwner(jobId, userId)) {
            return answerSseRelay.error("접근 권한이 없습니다");
        }
        return answerSseRelay.subscribe(jobId, lastEventId);
    }
}
