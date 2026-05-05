package com.example.backend.infrastructure.query.persistence;

import com.example.backend.domain.query.QueryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QueryLogSpringDataJpaRepository extends JpaRepository<QueryLog, Long> {

    @Query("""
            SELECT q FROM QueryLog q
            WHERE q.userId = :userId
              AND (:cursorId IS NULL OR q.id < :cursorId)
            ORDER BY q.id DESC
            LIMIT :size
            """)
    List<QueryLog> findByUserIdWithCursor(
            @Param("userId") Long userId,
            @Param("cursorId") Long cursorId,
            @Param("size") int size
    );
}
