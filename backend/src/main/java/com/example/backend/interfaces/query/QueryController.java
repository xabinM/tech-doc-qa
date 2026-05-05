package com.example.backend.interfaces.query;

import com.example.backend.application.query.QueryService;
import com.example.backend.common.response.ApiResponse;
import com.example.backend.interfaces.query.dto.QueryHistoryResponse;
import com.example.backend.interfaces.query.dto.QueryRequest;
import com.example.backend.interfaces.query.dto.QueryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/query")
@RequiredArgsConstructor
public class QueryController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final QueryService queryService;

    @PostMapping
    public ApiResponse<QueryResponse> query(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid QueryRequest request
    ) {
        String answer = queryService.query(userId, request.question());
        return ApiResponse.ok(new QueryResponse(answer));
    }

    @GetMapping("/history")
    public ApiResponse<QueryHistoryResponse> history(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") int size
    ) {
        int pageSize = Math.min(size, DEFAULT_PAGE_SIZE);
        var logs = queryService.getHistory(userId, cursorId, pageSize);
        return ApiResponse.ok(QueryHistoryResponse.of(logs, pageSize));
    }
}
