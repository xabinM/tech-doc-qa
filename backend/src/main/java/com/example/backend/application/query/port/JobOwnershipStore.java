package com.example.backend.application.query.port;

/**
 * 질의 작업(jobId)의 소유자(userId)를 저장·검증하는 포트.
 *
 * 답변 스트림 구독 시 다른 사용자의 jobId에 접근하지 못하도록(IDOR 방지) 사용한다.
 */
public interface JobOwnershipStore {

    /** 작업 발행 시 소유자를 등록한다 (답변 스트림 TTL과 정렬). */
    void register(String jobId, Long userId);

    /** 해당 userId가 jobId의 소유자인지 확인한다. */
    boolean isOwner(String jobId, Long userId);
}
