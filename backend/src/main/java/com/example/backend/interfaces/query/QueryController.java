package com.example.backend.interfaces.query;

import com.example.backend.application.query.IdempotencyService;
import com.example.backend.application.query.QueryService;
import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.example.backend.common.response.ApiResponse;
import com.example.backend.interfaces.query.dto.QueryHistoryResponse;
import com.example.backend.interfaces.query.dto.QueryRequest;
import com.example.backend.interfaces.query.dto.QueryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/query")
@RequiredArgsConstructor
@Validated
public class QueryController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 128;

    private final QueryService queryService;
    private final IdempotencyService idempotencyService;

    @PostMapping
    public ApiResponse<QueryResponse> query(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid QueryRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return executeQuery(userId, request);
        }

        String key = sanitizeKey(idempotencyKey);

        return switch (idempotencyService.tryStart(userId, key)) {

            // 이미 완료된 요청: LLM 호출 없이 저장된 응답 즉시 반환
            case IdempotencyService.StartResult.AlreadyCompleted c ->
                    ApiResponse.ok("답변이 생성되었습니다", new QueryResponse(c.answer(), c.sessionId()));

            // 처리 중 타임아웃: 클라이언트에 재시도 안내
            case IdempotencyService.StartResult.StillProcessing ignored ->
                    throw new CustomException(ErrorCode.QUERY_IDEMPOTENCY_PROCESSING);

            // 최초 요청: 실제 처리 후 결과 저장
            case IdempotencyService.StartResult.New ignored -> {
                try {
                    QueryService.QueryResult result = queryService.query(userId, request.question(), request.sessionId());
                    idempotencyService.complete(userId, key, result.answer(), result.sessionId());
                    yield ApiResponse.ok("답변이 생성되었습니다", new QueryResponse(result.answer(), result.sessionId()));
                } catch (Exception e) {
                    // 실패 기록 저장: 5분 후 동일 키로 재시도 가능
                    String errorCode = e instanceof CustomException ce
                            ? ce.getErrorCode().getCode()
                            : ErrorCode.INTERNAL_SERVER_ERROR.getCode();
                    idempotencyService.fail(userId, key, errorCode);
                    throw e;
                }
            }
        };
    }

    @GetMapping("/history")
    public ApiResponse<QueryHistoryResponse> history(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") @Min(1) int size
    ) {
        int pageSize = Math.min(size, DEFAULT_PAGE_SIZE);
        var logs = queryService.getHistory(userId, cursorId, pageSize);
        return ApiResponse.ok("이력이 조회되었습니다", QueryHistoryResponse.of(logs, pageSize));
    }

    private ApiResponse<QueryResponse> executeQuery(Long userId, QueryRequest request) {
        QueryService.QueryResult result = queryService.query(userId, request.question(), request.sessionId());
        return ApiResponse.ok("답변이 생성되었습니다", new QueryResponse(result.answer(), result.sessionId()));
    }

    private static String sanitizeKey(String key) {
        String trimmed = key.trim();
        if (trimmed.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            return trimmed.substring(0, IDEMPOTENCY_KEY_MAX_LENGTH);
        }
        return trimmed;
    }
}
