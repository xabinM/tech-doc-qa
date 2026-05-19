package com.example.backend.infrastructure.query.persistence;

import com.example.backend.domain.query.ChatSession;
import com.example.backend.domain.query.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatSessionJpaRepository implements ChatSessionRepository {

    private final ChatSessionSpringDataJpaRepository jpaRepository;

    @Override
    public ChatSession save(ChatSession session) {
        return jpaRepository.save(session);
    }

    @Override
    public Optional<ChatSession> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<ChatSession> findByUserIdWithCursor(Long userId, Long cursorId, int size) {
        return jpaRepository.findByUserIdWithCursor(userId, cursorId, size);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public void deleteByUserId(Long userId) {
        jpaRepository.deleteByUserId(userId);
    }
}
