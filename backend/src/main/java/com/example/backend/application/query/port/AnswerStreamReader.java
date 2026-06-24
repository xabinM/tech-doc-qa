package com.example.backend.application.query.port;

import java.util.List;

/**
 * 답변 스트림 구독(읽기) 포트.
 *
 * answer:{jobId} 스트림에서 lastEventId 이후의 엔트리를 읽는다.
 * SSE 전달 계층이 이 포트를 반복 호출해 토큰을 브라우저로 relay 한다.
 */
public interface AnswerStreamReader {

    /**
     * lastEventId 이후의 엔트리를 blockMs 동안 블로킹하며 읽는다.
     *
     * @param lastEventId 마지막으로 받은 엔트리 ID. "0"이면 스트림 처음부터(재연결 시 재생)
     * @return 새 엔트리 목록 (없으면 빈 리스트)
     */
    List<AnswerEvent> readAfter(String jobId, String lastEventId, long blockMs);

    /**
     * 답변 스트림 엔트리.
     *
     * @param id      스트림 엔트리 ID (SSE id로 사용, 재연결 시 Last-Event-ID가 됨)
     * @param type    token | done | error
     * @param payload token 텍스트, 또는 error 시 ErrorCode 코드 (done은 빈 문자열)
     */
    record AnswerEvent(String id, String type, String payload) {}
}
