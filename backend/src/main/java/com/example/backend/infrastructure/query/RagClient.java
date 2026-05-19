package com.example.backend.infrastructure.query;

import com.example.backend.application.query.ConversationTurn;
import com.example.backend.application.query.port.RagPort;
import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
public class RagClient implements RagPort {

    private final WebClient webClient;

    public RagClient(@Qualifier("ragWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    @CircuitBreaker(name = "ragServer", fallbackMethod = "fallback")
    public String ask(String question, List<ConversationTurn> history) {
        List<AskRequest.HistoryItem> historyItems = history.stream()
                .map(t -> new AskRequest.HistoryItem(t.question(), t.answer()))
                .toList();

        return webClient.post()
                .uri("/ask")
                .bodyValue(new AskRequest(question, historyItems))
                .retrieve()
                .bodyToMono(AskResponse.class)
                .map(AskResponse::answer)
                .block();
    }

    private String fallback(String question, List<ConversationTurn> history, Exception e) {
        throw new CustomException(ErrorCode.QUERY_RAG_SERVER_ERROR);
    }

    private record AskRequest(String question, List<HistoryItem> history) {
        private record HistoryItem(String question, String answer) {}
    }

    private record AskResponse(String answer) {}
}
