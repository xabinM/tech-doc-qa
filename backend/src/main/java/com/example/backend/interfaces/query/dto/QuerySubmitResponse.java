package com.example.backend.interfaces.query.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * POST /api/v1/query 응답.
 *
 * status="accepted", jobId 포함 (HTTP 202)
 * 클라이언트는 jobId로 답변 스트림(SSE)을 구독한다.
 *
 * 사용되지 않는 필드(null)는 응답에서 생략된다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuerySubmitResponse(String status, String jobId, Long sessionId) {

    public static QuerySubmitResponse accepted(String jobId, Long sessionId) {
        return new QuerySubmitResponse("accepted", jobId, sessionId);
    }
}
