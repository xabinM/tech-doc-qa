package com.example.backend.common.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient ragWebClient(
            @Value("${rag.server.url}") String ragServerUrl,
            @Value("${rag.server.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${rag.server.read-timeout-ms}") int readTimeoutMs
    ) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(readTimeoutMs));

        return WebClient.builder()
                .baseUrl(ragServerUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
