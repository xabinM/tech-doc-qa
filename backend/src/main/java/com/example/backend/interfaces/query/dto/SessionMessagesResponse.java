package com.example.backend.interfaces.query.dto;

import com.example.backend.domain.query.QueryLog;

import java.time.LocalDateTime;
import java.util.List;

public record SessionMessagesResponse(List<Message> messages) {

    public record Message(Long id, String question, String answer, LocalDateTime createdAt) {
        public static Message from(QueryLog log) {
            return new Message(log.getId(), log.getQuestion(), log.getAnswer(), log.getCreatedAt());
        }
    }

    public static SessionMessagesResponse of(List<QueryLog> logs) {
        return new SessionMessagesResponse(logs.stream().map(Message::from).toList());
    }
}
