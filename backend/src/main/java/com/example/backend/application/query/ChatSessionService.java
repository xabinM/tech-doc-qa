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

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final int TITLE_MAX_LENGTH = 100;
    private static final int HISTORY_CONTEXT_MAX = 20;

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

        List<QueryLog> recentLogs = queryLogRepository.findLatestBySessionId(session.getId(), HISTORY_CONTEXT_MAX);
        Collections.reverse(recentLogs);
        List<ConversationTurn> history = recentLogs.stream()
                .map(log -> new ConversationTurn(log.getQuestion(), log.getAnswer()))
                .toList();

        return new SessionContext(session.getId(), history);
    }

    /**
     * 세션의 최근 대화 history를 조회한다 (비동기 워커가 RAG 호출 직전에 사용).
     * 소유권 검증은 제출 단계(prepareSession)에서 이미 수행됐으므로 여기서는 생략한다.
     */
    @Transactional(readOnly = true)
    public List<ConversationTurn> loadConversationHistory(Long sessionId) {
        List<QueryLog> recentLogs = queryLogRepository.findLatestBySessionId(sessionId, HISTORY_CONTEXT_MAX);
        Collections.reverse(recentLogs);
        return recentLogs.stream()
                .map(log -> new ConversationTurn(log.getQuestion(), log.getAnswer()))
                .toList();
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

    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUERY_SESSION_NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.AUTH_FORBIDDEN);
        }
        queryLogRepository.deleteBySessionId(sessionId);
        chatSessionRepository.deleteById(sessionId);
    }

    @Transactional
    public void deleteUserData(Long userId) {
        queryLogRepository.deleteByUserId(userId);
        chatSessionRepository.deleteByUserId(userId);
    }

    public record SessionContext(Long sessionId, List<ConversationTurn> history) {}
}
