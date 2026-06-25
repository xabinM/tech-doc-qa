package com.example.backend.interfaces.query;

import com.example.backend.application.query.port.AnswerStreamReader;
import com.example.backend.application.query.port.AnswerStreamReader.AnswerEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * answer:{jobId} 스트림을 SSE로 브라우저에 relay 한다.
 *
 * 가상 스레드에서 XREAD BLOCK 루프를 돌며 토큰을 전송하고, done/error 수신 또는
 * 유휴 타임아웃 시 종료한다. 각 SSE 이벤트의 id는 스트림 엔트리 ID이므로
 * 클라이언트가 끊긴 뒤 Last-Event-ID로 재구독하면 그 지점부터 재생된다.
 *
 * data 값은 JSON 문자열로 인코딩한다 — SSE 규격상 'data:'의 선행 공백 제거나
 * 개행이 이벤트 구분자로 먹히는 문제를 피해 토큰의 공백·개행을 온전히 보존한다.
 * 클라이언트는 JSON.parse 후 사용한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerSseRelay {

    private static final long EMITTER_TIMEOUT_MS = 5 * 60 * 1000L;  // SSE 연결 최대 유지
    private static final long BLOCK_MS = 2000L;                     // XREAD BLOCK 단위
    private static final long MAX_IDLE_MS = 60 * 1000L;             // 토큰 없이 최대 대기 후 종료

    private final AnswerStreamReader reader;
    private final ObjectMapper objectMapper;

    public SseEmitter subscribe(String jobId, String lastEventId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        Thread.ofVirtual().name("sse-relay-" + jobId).start(() -> pump(emitter, jobId, lastEventId));
        return emitter;
    }

    /** 즉시 error 이벤트 한 건을 보내고 종료하는 SSE 응답 (권한 없음 등). 재연결 루프 방지를 위해 4xx 대신 사용. */
    public SseEmitter error(String message) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        Thread.ofVirtual().name("sse-error").start(() -> {
            try {
                emitter.send(SseEmitter.event().name("error").data(toJson(message)));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void pump(SseEmitter emitter, String jobId, String lastEventId) {
        String offset = (lastEventId == null || lastEventId.isBlank()) ? "0" : lastEventId;
        long idleWaited = 0;
        try {
            while (true) {
                List<AnswerEvent> events = reader.readAfter(jobId, offset, BLOCK_MS);
                if (events.isEmpty()) {
                    idleWaited += BLOCK_MS;
                    if (idleWaited >= MAX_IDLE_MS) {
                        emitter.complete();
                        return;
                    }
                    continue;
                }
                idleWaited = 0;
                for (AnswerEvent event : events) {
                    // data를 JSON 문자열로 인코딩 → 공백·개행 보존 (클라이언트는 JSON.parse)
                    emitter.send(SseEmitter.event().id(event.id()).name(event.type()).data(toJson(event.payload())));
                    offset = event.id();
                    if ("done".equals(event.type()) || "error".equals(event.type())) {
                        emitter.complete();
                        return;
                    }
                }
            }
        } catch (IOException e) {
            // 클라이언트 연결 종료 — 정상 종료 처리
            emitter.complete();
        } catch (Exception e) {
            log.warn("SSE relay 오류 - jobId={}, error={}", jobId, e.getMessage());
            emitter.completeWithError(e);
        }
    }

    private String toJson(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "\"\"";  // String 직렬화라 사실상 발생하지 않음
        }
    }
}
