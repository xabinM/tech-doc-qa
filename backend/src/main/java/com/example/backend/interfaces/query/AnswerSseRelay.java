package com.example.backend.interfaces.query;

import com.example.backend.application.query.port.AnswerStreamReader;
import com.example.backend.application.query.port.AnswerStreamReader.AnswerEvent;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerSseRelay {

    private static final long EMITTER_TIMEOUT_MS = 5 * 60 * 1000L;  // SSE 연결 최대 유지
    private static final long BLOCK_MS = 2000L;                     // XREAD BLOCK 단위
    private static final long MAX_IDLE_MS = 60 * 1000L;             // 토큰 없이 최대 대기 후 종료

    private final AnswerStreamReader reader;

    public SseEmitter subscribe(String jobId, String lastEventId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        Thread.ofVirtual().name("sse-relay-" + jobId).start(() -> pump(emitter, jobId, lastEventId));
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
                    emitter.send(SseEmitter.event().id(event.id()).name(event.type()).data(event.payload()));
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
}
