package com.example.backend.domain.query;

import java.util.List;

public interface QueryLogRepository {

    QueryLog save(QueryLog queryLog);

    List<QueryLog> findByUserIdWithCursor(Long userId, Long cursorId, int size);
}
