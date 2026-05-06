package com.example.backend.infrastructure.query;

import com.example.backend.application.query.port.RagPort;
import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class RagClient implements RagPort {

    private final WebClient webClient;

    public RagClient(@Qualifier("ragWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    @CircuitBreaker(name = "ragServer", fallbackMethod = "fallback")
    public String ask(String question) {
        return webClient.post()
                .uri("/ask")
                .bodyValue(new AskRequest(question))
                .retrieve()
                .bodyToMono(AskResponse.class)
                .map(AskResponse::answer)
                .block();
    }

    private String fallback(String question, Exception e) {
        throw new CustomException(ErrorCode.QUERY_RAG_SERVER_ERROR);
    }

    private record AskRequest(String question) {}

    private record AskResponse(String answer) {}
}
