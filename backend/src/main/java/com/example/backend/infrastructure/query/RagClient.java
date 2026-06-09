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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
public class RagClient implements RagPort {

    private final WebClient webClient;
    private final MeterRegistry meterRegistry;

    @Value("${rag.server.ask-path:/ask}")
    private String askPath;

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
    @CircuitBreaker(name = "ragServer", fallbackMethod = "fallback")
    public String ask(String question, List<ConversationTurn> history) {
        List<AskRequest.HistoryItem> historyItems = history.stream()
                .map(t -> new AskRequest.HistoryItem(t.question(), t.answer()))
                .toList();

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return webClient.post()
                    .uri(askPath)
                    .bodyValue(new AskRequest(question, historyItems))
                    .retrieve()
                    .bodyToMono(AskResponse.class)
                    .map(AskResponse::answer)
                    .block();
        } finally {
            sample.stop(ragCallTimer);
        }
    }

    private String fallback(String question, List<ConversationTurn> history, Throwable e) {
        ragFallbackCounter.increment();
        throw new CustomException(ErrorCode.QUERY_RAG_SERVER_ERROR);
    }

    private record AskRequest(String question, List<HistoryItem> history) {
        private record HistoryItem(String question, String answer) {}
    }

    private record AskResponse(String answer) {}
}
