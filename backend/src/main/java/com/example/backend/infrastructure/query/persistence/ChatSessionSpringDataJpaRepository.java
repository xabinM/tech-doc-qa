package com.example.backend.infrastructure.query.persistence;

import com.example.backend.domain.query.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatSessionSpringDataJpaRepository extends JpaRepository<ChatSession, Long> {

    @Query("""
            SELECT s FROM ChatSession s
            WHERE s.userId = :userId
              AND (:cursorId IS NULL OR s.id < :cursorId)
            ORDER BY s.id DESC
            LIMIT :size
            """)
    List<ChatSession> findByUserIdWithCursor(
            @Param("userId") Long userId,
            @Param("cursorId") Long cursorId,
            @Param("size") int size
    );

    @Modifying
    @Query("DELETE FROM ChatSession s WHERE s.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
