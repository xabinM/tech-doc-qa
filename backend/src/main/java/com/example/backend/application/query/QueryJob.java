package com.example.backend.application.query;

/**
 * 비동기 질의 처리 작업 메시지.
 *
 * 제출 단계에서 캐시 미스 시 생성되어 query:jobs 스트림으로 발행되고,
 * 워커가 소비해 RAG 답변을 생성한다.
 *
 * @param jobId     작업 식별자(UUID). answer:{jobId} 스트림 키 및 이력 저장 멱등 키로 사용
 * @param userId    요청 사용자
 * @param question  질문 원문
 * @param sessionId 채팅 세션 (제출 단계에서 prepareSession으로 확정, null 아님)
 * @param requestId X-Request-Id (제출→워커→스트림 end-to-end 추적용, 없을 수 있음)
 */
public record QueryJob(
        String jobId,
        Long userId,
        String question,
        Long sessionId,
        String requestId
) {}
