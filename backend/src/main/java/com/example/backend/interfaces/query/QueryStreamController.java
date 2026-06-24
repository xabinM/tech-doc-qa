package com.example.backend.interfaces.query;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
 * 인증은 SecurityConfig(/api/v1/query/** authenticated)가 보장한다 — BFF가 AT를 주입한다.
 * jobId는 추측 불가능한 UUID이므로 Phase 1에서는 그 자체를 접근 권한으로 사용한다
 * (jobId↔userId 소유권 검증은 Phase 3 강화 대상).
 */
@RestController
@RequestMapping("/api/v1/query")
@RequiredArgsConstructor
public class QueryStreamController {

    private final AnswerSseRelay answerSseRelay;

    @GetMapping(value = "/{jobId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String jobId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId
    ) {
        return answerSseRelay.subscribe(jobId, lastEventId);
    }
}
