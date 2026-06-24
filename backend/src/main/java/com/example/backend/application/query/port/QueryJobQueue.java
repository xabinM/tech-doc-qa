package com.example.backend.application.query.port;

import com.example.backend.application.query.QueryJob;

/**
 * 비동기 질의 작업 큐 포트.
 *
 * 구현체는 메시지 브로커(Redis Streams)에 작업을 발행한다.
 * 소비(워커)는 인프라 계층에서 구동되므로 이 포트는 발행 책임만 가진다.
 */
public interface QueryJobQueue {

    /**
     * 질의 작업을 큐에 발행한다.
     *
     * @throws com.example.backend.common.exception.CustomException 발행 실패 시 QUERY_STREAM_FAILED
     */
    void publish(QueryJob job);
}
