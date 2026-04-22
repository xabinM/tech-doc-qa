package com.example.backend.application.query.event;

public record QueryCompletedEvent(Long userId, String question, String answer) {
}
