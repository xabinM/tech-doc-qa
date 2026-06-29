---
id: OBS-004
title: 멱등성 처리 + Circuit Breaker 메트릭 구현
type: observation
status: confirmed
severity: n/a
discovered: 2026-06-04
resolved: ~
tags: [idempotency, circuit-breaker, metrics, redis]
resume_worthy: true
---

## 개요
`POST /api/v1/query` 처리 경로에 2가지 방어 장치를 적용했다.
Idempotency-Key 기반 멱등성 처리로 클라이언트 중복 요청을 흡수하고,
Resilience4j Circuit Breaker 상태를 포함한 지표를 Prometheus 메트릭으로 노출한다.

## 발견 배경
ISSUE-003에서 Tomcat 스레드 포화로 에러율 10.6%가 발생했다.
외부 LLM 의존 구간의 방어 장치(중복 요청 흡수·장애 가시성)를 보강하기 위해 구현했다.

## 도입 전 상태
- 멱등성 처리 없음 — 네트워크 재시도 시 LLM 중복 호출
- CB 트립 여부·에러율을 메트릭으로 확인 불가

## 적용한 변경

### 멱등성 처리 (`IdempotencyService.java`)
클라이언트가 `Idempotency-Key` 헤더를 제공하면 중복 요청을 LLM 재호출 없이 응답한다.

**Redis State Machine**
```
(없음) → SET NX → PROCESSING (60초 TTL)
PROCESSING → 완료 → COMPLETED (jobId 저장, 1시간 TTL)
PROCESSING → 실패 → FAILED (5분 TTL, 재시도 허용)
```

**키 구조**: `idempotency:{userId}:{idempotencyKey}` — 사용자 범위로 격리해 크로스-유저 재사용 방지

비동기 작업 발행 시 jobId를 저장하고, 동일 키의 중복 요청은 **같은 jobId로 동일 답변 스트림에 재구독**시켜 멱등성과 재연결을 jobId로 통합한다.

**Prometheus 메트릭**
- `idempotency.replayed`: 저장된 jobId로 재구독 처리된 횟수 (LLM 호출 절약)
- `idempotency.new`: 신규 처리 횟수

### Circuit Breaker 메트릭 (`RagClient.java`)
- Resilience4j CB 상태(CLOSED/OPEN/HALF_OPEN) Micrometer 자동 노출
- Grafana 대시보드에서 CB 트립 시점을 타임라인으로 확인 가능

## 결과 (After)
- 멱등성 처리로 클라이언트 재시도에 의한 중복 LLM 호출 차단
- Grafana에서 멱등성 replay 빈도, CB 상태를 실시간 모니터링 가능

## 관련 파일 및 코드
- `backend/src/main/java/com/example/backend/application/query/IdempotencyService.java`
- `backend/src/main/java/com/example/backend/interfaces/query/QueryController.java`
- `backend/src/main/java/com/example/backend/infrastructure/query/RagClient.java`
- `backend/src/main/resources/application.yaml` (idempotency.* 설정)

```java
// 중복 요청은 저장된 jobId로 동일 답변 스트림에 재구독
public StartResult tryStart(Long userId, String idempotencyKey) {
    String key = redisKey(userId, idempotencyKey);
    String raw = redisTemplate.opsForValue().get(key);
    if (raw != null) {
        IdempotencyRecord record = deserialize(raw);
        return switch (record.status) {
            case COMPLETED -> new StartResult.ReplayJob(record.jobId, record.sessionId);
            case PROCESSING -> waitForCompletion(key, idempotencyKey);
            case FAILED -> { markProcessing(key); yield new StartResult.New(); }
        };
    }
    // 새 키: PROCESSING으로 원자적 등록 (SET NX)
    ...
}
```

## 이력서 포인트
Redis State Machine 기반 멱등성 처리(PROCESSING → COMPLETED/FAILED)를 직접 설계·구현해 클라이언트 재시도에 의한 중복 LLM 호출을 차단하고, Resilience4j Circuit Breaker 상태를 Micrometer로 노출해 CB 트립 시점을 Grafana에서 실시간 확인 가능하게 했다.
