package com.example.backend.interfaces.query.dto;

import com.example.backend.domain.query.QueryLog;

import java.time.LocalDateTime;
import java.util.List;

public record QueryHistoryResponse(
        List<Item> items,
        Long nextCursorId,
        boolean hasNext
) {
    public record Item(
            Long id,
            String question,
            String answer,
            LocalDateTime createdAt
    ) {
        public static Item from(QueryLog log) {
            return new Item(log.getId(), log.getQuestion(), log.getAnswer(), log.getCreatedAt());
        }
    }

    public static QueryHistoryResponse of(List<QueryLog> logs, int size) {
        boolean hasNext = logs.size() == size;
        Long nextCursorId = hasNext ? logs.get(logs.size() - 1).getId() : null;
        List<Item> items = logs.stream().map(Item::from).toList();
        return new QueryHistoryResponse(items, nextCursorId, hasNext);
    }
}
