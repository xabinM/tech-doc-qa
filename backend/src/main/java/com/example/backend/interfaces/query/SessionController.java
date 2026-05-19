package com.example.backend.interfaces.query;

import com.example.backend.application.query.ChatSessionService;
import com.example.backend.common.response.ApiResponse;
import com.example.backend.interfaces.query.dto.SessionListResponse;
import com.example.backend.interfaces.query.dto.SessionMessagesResponse;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ChatSessionService chatSessionService;

    @GetMapping
    public ApiResponse<SessionListResponse> listSessions(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") @Min(1) int size
    ) {
        int pageSize = Math.min(size, DEFAULT_PAGE_SIZE);
        var sessions = chatSessionService.listSessions(userId, cursorId, pageSize);
        return ApiResponse.ok("세션 목록이 조회되었습니다", SessionListResponse.of(sessions, pageSize));
    }

    @GetMapping("/{sessionId}/messages")
    public ApiResponse<SessionMessagesResponse> getMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId
    ) {
        var messages = chatSessionService.getSessionMessages(userId, sessionId);
        return ApiResponse.ok("대화 내역이 조회되었습니다", SessionMessagesResponse.of(messages));
    }
}
