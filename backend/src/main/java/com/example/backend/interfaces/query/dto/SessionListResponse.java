package com.example.backend.interfaces.query.dto;

import com.example.backend.domain.query.ChatSession;

import java.time.LocalDateTime;
import java.util.List;

public record SessionListResponse(List<Item> items, Long nextCursorId, boolean hasNext) {

    public record Item(Long id, String title, LocalDateTime createdAt) {
        public static Item from(ChatSession session) {
            return new Item(session.getId(), session.getTitle(), session.getCreatedAt());
        }
    }

    public static SessionListResponse of(List<ChatSession> sessions, int size) {
        boolean hasNext = sessions.size() == size;
        Long nextCursorId = hasNext ? sessions.get(sessions.size() - 1).getId() : null;
        List<Item> items = sessions.stream().map(Item::from).toList();
        return new SessionListResponse(items, nextCursorId, hasNext);
    }
}
