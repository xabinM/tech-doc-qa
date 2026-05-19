package com.example.backend.domain.query;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository {

    ChatSession save(ChatSession session);

    Optional<ChatSession> findById(Long id);

    List<ChatSession> findByUserIdWithCursor(Long userId, Long cursorId, int size);

    void deleteById(Long id);

    void deleteByUserId(Long userId);
}
