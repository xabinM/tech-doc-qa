package com.example.backend.infrastructure.query.persistence;

import com.example.backend.domain.query.QueryLog;
import com.example.backend.domain.query.QueryLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class QueryLogJpaRepository implements QueryLogRepository {

    private final QueryLogSpringDataJpaRepository jpaRepository;

    @Override
    public QueryLog save(QueryLog queryLog) {
        return jpaRepository.save(queryLog);
    }

    @Override
    public List<QueryLog> findByUserIdWithCursor(Long userId, Long cursorId, int size) {
        return jpaRepository.findByUserIdWithCursor(userId, cursorId, size);
    }
}
