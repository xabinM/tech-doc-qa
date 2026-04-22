package com.example.backend.application.query;

import com.example.backend.application.query.event.QueryCompletedEvent;
import com.example.backend.domain.query.QueryLog;
import com.example.backend.domain.query.QueryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryHistoryEventHandler {

    private final QueryLogRepository queryLogRepository;

    @Async
    @EventListener
    @Transactional
    public void handleQueryCompleted(QueryCompletedEvent event) {
        try {
            QueryLog queryLog = QueryLog.create(event.userId(), event.question(), event.answer());
            queryLogRepository.save(queryLog);
        } catch (Exception e) {
            log.error("검색 이력 저장 실패 - userId={}, error={}", event.userId(), e.getMessage(), e);
        }
    }
}
