package com.example.backend.application.query;

import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.example.backend.domain.query.ChatSession;
import com.example.backend.domain.query.ChatSessionRepository;
import com.example.backend.domain.query.QueryLog;
import com.example.backend.domain.query.QueryLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final int TITLE_MAX_LENGTH = 100;

    private final ChatSessionRepository chatSessionRepository;
    private final QueryLogRepository queryLogRepository;

    @Transactional
    public SessionContext prepareSession(Long userId, String question, Long sessionId) {
        if (sessionId == null) {
            String title = question.length() > TITLE_MAX_LENGTH
                    ? question.substring(0, TITLE_MAX_LENGTH)
                    : question;
            ChatSession session = ChatSession.create(userId, title);
            chatSessionRepository.save(session);
            return new SessionContext(session.getId(), List.of());
        }

        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUERY_SESSION_NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.AUTH_FORBIDDEN);
        }

        List<ConversationTurn> history = queryLogRepository.findBySessionId(session.getId()).stream()
                .map(log -> new ConversationTurn(log.getQuestion(), log.getAnswer()))
                .toList();

        return new SessionContext(session.getId(), history);
    }

    @Transactional(readOnly = true)
    public List<ChatSession> listSessions(Long userId, Long cursorId, int size) {
        return chatSessionRepository.findByUserIdWithCursor(userId, cursorId, size);
    }

    @Transactional(readOnly = true)
    public List<QueryLog> getSessionMessages(Long userId, Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUERY_SESSION_NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.AUTH_FORBIDDEN);
        }
        return queryLogRepository.findBySessionId(sessionId);
    }

    public record SessionContext(Long sessionId, List<ConversationTurn> history) {}
}
