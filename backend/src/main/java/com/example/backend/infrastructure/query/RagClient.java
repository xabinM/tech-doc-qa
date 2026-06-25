package com.example.backend.infrastructure.query;

import com.example.backend.application.query.ConversationTurn;
import com.example.backend.application.query.port.RagPort;
import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClientRequest;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static com.example.backend.common.filter.RequestLoggingFilter.MDC_REQUEST_ID;
import static com.example.backend.common.filter.RequestLoggingFilter.REQUEST_ID_HEADER;

@Component
public class RagClient implements RagPort {

    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final MeterRegistry meterRegistry;

    @Value("${rag.server.ask-stream-path:/ask/stream}")
    private String askStreamPath;

    @Value("${rag.server.stream-timeout-ms:120000}")
    private long streamTimeoutMs;

    private Timer ragCallTimer;
    private Counter ragFallbackCounter;

    public RagClient(@Qualifier("ragWebClient") WebClient webClient, MeterRegistry meterRegistry) {
        this.webClient = webClient;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void initMetrics() {
        ragCallTimer = Timer.builder("rag.call.duration")
                .description("RAG 서버 호출 소요 시간")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);
        ragFallbackCounter = Counter.builder("rag.fallback.total")
                .description("Circuit Breaker fallback 발생 횟수")
                .register(meterRegistry);
    }

    @Override
    @CircuitBreaker(name = "ragServer", fallbackMethod = "streamFallback")
    public void askStream(String question, List<ConversationTurn> history, Consumer<String> onToken) {
        List<AskRequest.HistoryItem> historyItems = history.stream()
                .map(t -> new AskRequest.HistoryItem(t.question(), t.answer()))
                .toList();

        // 다중 서버 환경에서 backend ↔ rag-server 로그를 동일 요청으로 추적하기 위해 전파
        String requestId = MDC.get(MDC_REQUEST_ID);

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            webClient.post()
                    .uri(askStreamPath)
                    // 스트리밍은 전체 응답이 길 수 있어 per-request로 응답 타임아웃을 늘린다
                    .httpRequest(req -> {
                        HttpClientRequest nativeReq = req.getNativeRequest();
                        nativeReq.responseTimeout(Duration.ofMillis(streamTimeoutMs));
                    })
                    .headers(headers -> {
                        if (requestId != null) {
                            headers.set(REQUEST_ID_HEADER, requestId);
                        }
                    })
                    .bodyValue(new AskRequest(question, historyItems))
                    .retrieve()
                    .bodyToFlux(SSE_TYPE)
                    .doOnNext(event -> handleEvent(event, onToken))
                    .blockLast();
        } finally {
            sample.stop(ragCallTimer);
        }
    }

    private void handleEvent(ServerSentEvent<String> event, Consumer<String> onToken) {
        if ("error".equals(event.event())) {
            throw new CustomException(ErrorCode.QUERY_RAG_SERVER_ERROR);
        }
        if ("token".equals(event.event()) && event.data() != null) {
            onToken.accept(event.data());
        }
        // "done" 이벤트는 스트림 자연 종료 — 별도 처리 불필요
    }

    private void streamFallback(String question, List<ConversationTurn> history,
                                Consumer<String> onToken, Throwable e) {
        ragFallbackCounter.increment();
        throw new CustomException(ErrorCode.QUERY_RAG_SERVER_ERROR);
    }

    private record AskRequest(String question, List<HistoryItem> history) {
        private record HistoryItem(String question, String answer) {}
    }
}
