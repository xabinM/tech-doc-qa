package com.example.backend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth
    AUTH_LOGIN_FAIL("AUTH_001", "이메일 또는 비밀번호가 올바르지 않습니다", HttpStatus.UNAUTHORIZED),
    AUTH_EMAIL_DUPLICATE("AUTH_002", "이미 사용 중인 이메일입니다", HttpStatus.CONFLICT),
    AUTH_TOKEN_INVALID("AUTH_003", "유효하지 않은 토큰입니다", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_EXPIRED("AUTH_004", "만료된 토큰입니다", HttpStatus.UNAUTHORIZED),
    AUTH_FORBIDDEN("AUTH_005", "접근 권한이 없습니다", HttpStatus.FORBIDDEN),

    // Query
    QUERY_RAG_SERVER_ERROR("QUERY_001", "답변 생성 중 오류가 발생했습니다", HttpStatus.SERVICE_UNAVAILABLE),
    QUERY_RATE_LIMIT_EXCEEDED("QUERY_002", "일일 요청 한도를 초과했습니다", HttpStatus.TOO_MANY_REQUESTS),

    // Common
    INVALID_INPUT("COMMON_001", "잘못된 요청입니다", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("COMMON_002", "서버 내부 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
