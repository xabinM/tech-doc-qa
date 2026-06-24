package com.example.backend.application.query.port;

/**
 * 답변 스트림 발행 포트.
 *
 * 워커가 생성한 답변(토큰)·완료·오류를 jobId별 스트림으로 발행한다.
 * 구현체(Redis Streams)는 SSE 컨트롤러가 XREAD로 소비해 브라우저로 relay 한다.
 *
 * Phase 1에서는 토큰 1건(전체 답변) + done 으로 동작하고,
 * Phase 2에서 토큰 단위 스트리밍으로 확장된다.
 */
public interface AnswerStream {

    /** 답변 토큰(또는 Phase 1의 전체 답변)을 발행한다. */
    void publishToken(String jobId, String token);

    /** 답변 생성 완료를 알린다. */
    void publishDone(String jobId);

    /** 처리 오류를 발행한다 (ErrorCode 코드 전달). */
    void publishError(String jobId, String errorCode);
}
