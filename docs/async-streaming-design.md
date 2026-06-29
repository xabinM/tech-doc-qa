# 비동기 토큰 스트리밍 RAG 파이프라인 설계 (Redis Streams)

- 상태: **구현 완료** — 구현·이슈 해결 기록은 [async-streaming-implementation.md](./async-streaming-implementation.md) 참조
- 작성일: 2026-06-24
- 영향 범위: `backend` (Spring) · `rag-server` (FastAPI) · `frontend` (Next.js)
- 목표: 동기 요청-응답으로 동작하는 질의 처리를 **비동기 + 토큰 스트리밍(ChatGPT식 타자기 효과) + 재연결/재생** 구조로 전환한다. 추가 미들웨어 없이 **Redis Streams 단독**으로 잡 큐와 토큰 전달을 모두 처리한다.

---

## 1. 배경 / 동기

현재 질의 흐름(`QueryService.query()`)은 동기 블로킹이다.

```
요청 → RateLimit(Redis) → prepareSession(DB커밋) → RAG호출(블로킹)
     → publishEvent(QueryCompletedEvent) → 응답
                  ↓
       @Async @EventListener → QueryLog DB 저장 (인메모리, at-most-once)
```

UI/UX 전면 개편에 맞춰 **토큰 스트리밍 UX**를 도입한다. 이는 단순 시각 효과가 아니라 다음을 함께 해결한다.

- 요청 스레드가 LLM 생성 시간만큼 묶이지 않음 (장문/지연에 강함)
- 인메모리 `@Async` 이력 저장의 **유실 가능성**(앱 크래시 시) → 워커 작업으로 흡수해 at-least-once 확보
- `ISSUE-003`에서 진단된 *prepareSession + @Async 핸들러의 DB 커넥션 경합* → 워커 풀이 쓰기 동시성을 자연 제한

### 기술 선택 근거
- **Redis Streams 단독**: 이미 Redis를 Rate Limit·Refresh Token에 운영 중 → 인프라 추가 0.
- Kafka는 "특정 브라우저 1명에게 실시간 타겟 전달"에 부적합(파티션/컨슈머그룹 모델). 마지막 홉은 Redis가 정석이므로, 잡 큐까지 Redis로 통일하는 것이 가장 단순하면서 강력하다.

### 알려진 사전 사실
- rag-server LLM 클라이언트는 코드상 **Groq(`AsyncGroq`)** 사용 (CLAUDE.md 기술과 불일치하나 코드 기준). Groq는 `stream=True` 토큰 스트리밍 지원 → 모델 B 구현 가능.

---

## 2. 전체 아키텍처

Redis Stream을 **두 가지 역할**로 사용한다.

### ① 잡 큐 — `query:jobs` (Consumer Group)
```
제출:  XADD query:jobs * jobId <id> userId <id> question <q> sessionId <id> requestId <rid>
워커:  XREADGROUP GROUP workers <consumer> COUNT n BLOCK t  →  처리  →  XACK
복구:  워커 크래시 시 XAUTOCLAIM 으로 미처리 메시지 회수 (at-least-once)
DLQ:   delivery count(XPENDING) N회 초과 → query:jobs:dlq 로 이동 + 에러 이벤트 발행
```
Spring Data Redis의 `StreamMessageListenerContainer`(Consumer Group 모드)로 폴링·ack 관리. 가상 스레드 환경과 정합.

### ② 답변 스트림 — `answer:{jobId}` (per-job, 토큰 전달)
```
워커:  토큰마다  XADD answer:{jobId} * type token  data "<chunk>"
       완료 시    XADD answer:{jobId} * type done   data ""
       오류 시    XADD answer:{jobId} * type error  code <ERROR_CODE>
       EXPIRE answer:{jobId} 3600   (1시간 후 키 자동 정리)
SSE:   XREAD BLOCK 으로 읽어 브라우저로 relay
재연결: EventSource가 Last-Event-ID(=stream entry id) 자동 전송
       → 해당 id 이후부터 XREAD → 끊긴 토큰부터 이어받기
```

### 멀티 인스턴스 라우팅
SSE를 보유한 인스턴스가 누가 `XADD` 했든 `answer:{jobId}`를 읽기만 하면 되므로, 인스턴스 간 직접 통신이 불필요하다. 잡 큐 Consumer Group은 워커 인스턴스 간 부하를 자동 분배한다.

---

## 3. 데이터 흐름 (3단계)

### ① 제출 (동기, 빠름) — `QueryService` 분리
```
1. checkRateLimit(userId)          유지. 큐에 넣기 전에 거절
2. prepareSession()                유지. sessionId·history 즉시 확보 후 워커에 전달
3. jobId 생성 → query:jobs 에 XADD
4. return 202 { jobId, sessionId }
```

### ② 처리 — 워커(Consumer)
```
1. query:jobs 에서 XREADGROUP 으로 job 수신
2. ragPort.askStream(question, history)  ← Circuit Breaker 여기 위치
3. RAG가 토큰 Flux 를 흘리면 → answer:{jobId} 에 토큰별 XADD
4. 완료:
     - QueryLog 저장  ← 이력 저장 흡수 (jobId 멱등 키로 중복 방지)
     - answer:{jobId} 에 done 마커 XADD + EXPIRE
     - XACK
5. 실패: 재시도 → 초과 시 DLQ + answer:{jobId} 에 error 이벤트
```

### ③ 전달 — 브라우저 push
```
브라우저: EventSource("/api/query/{jobId}/stream")
백엔드:   answer:{jobId} 를 XREAD BLOCK → 토큰을 SSE 이벤트로 relay
재연결:   Last-Event-ID 헤더로 재구독 → 그 id 이후부터 XREAD
```

---

## 4. 엔드포인트

| 메서드 | 경로 | 동작 | 상태코드 |
|--------|------|------|----------|
| POST | `/api/v1/query` | 잡 발행 | **202** `{jobId, sessionId}` |
| GET | `/api/v1/query/{jobId}/stream` | SSE 토큰 스트림 (Last-Event-ID 지원) | 200 `text/event-stream` |
| GET | `/api/v1/query/{jobId}` | 상태/결과 폴링 (SSE 차단 환경 폴백) | 200 |

rag-server (신규):

| 메서드 | 경로 | 동작 |
|--------|------|------|
| POST | `/ask/stream` | SSE/청크로 토큰 스트리밍 (`stream=True`) |

---

## 5. 서비스별 변경점

### backend (Spring)
- `QueryService`: 제출 로직만 남기고, 잡 발행 책임 추가.
- 신규 `QueryStreamPublisher` / `RedisStreamJobQueue`(infrastructure): `query:jobs` XADD.
- 신규 워커: `StreamMessageListenerContainer` + 리스너 → RAG 스트리밍 호출 → `answer:{jobId}` XADD.
- 신규 SSE 컨트롤러: `GET /query/{jobId}/stream` → `answer:{jobId}` XREAD relay.
- `RagPort`: `askStream(question, history)` 추가 (`Flux<String>` 또는 콜백). `RagClient`는 `exchangeToFlux`/`bodyToFlux`로 SSE 소비.
- `QueryCompletedEvent` + `@Async @EventListener` 이력 저장 → 워커로 이동(제거).
- `SecurityConfig`: 신규 엔드포인트 `authenticated` 명시.
- `ErrorCode`: 비동기 에러용 코드 추가(예: `QUERY_JOB_NOT_FOUND`, `QUERY_STREAM_FAILED`).
- Flyway: **불필요** (잡/스트림 상태는 Redis로 관리).

### rag-server (FastAPI)
- `router/query.py`: `POST /ask/stream` 추가 → `StreamingResponse`(SSE).
- `client/llm.py`: `generate_answer_stream(...)` 추가 → `stream=True`로 청크 yield.
- `service/rag.py`: 스트리밍 오케스트레이션(`ask_stream`) 추가. sources는 스트림 시작 또는 종료 이벤트로 전달.

### frontend (Next.js)
- `ChatPage`: `useQuery` → 제출은 `useMutation`(202+jobId), 스트림은 `EventSource`.
- BFF: Next Route Handler가 **SSE 프록시**. 제출·스트림 양쪽에 `getAccessToken()` AT 주입, 401 시 refresh 후 재구독.
- 비동기 에러 UX: 스트림 `error` 이벤트 → `toast.error`.

---

## 6. 기존 자산 매핑

| 기존 요소 | 비동기 구조 위치 |
|-----------|------------------|
| Rate Limit (Redis Lua) | 제출 단계 |
| Circuit Breaker (`RagClient`) | 워커 내부 |
| 이력 저장 (`QueryCompletedEvent`) | 워커에 흡수, jobId 멱등 키 |
| `X-Request-Id` (MDC) | jobId와 함께 큐 메시지에 실어 end-to-end 추적 |
| `query.duration` 타이머 | 제출시간 + 큐 대기(lag) + 생성시간으로 분해 |

---

## 7. 단계별 구현 계획

위험 구간(스트리밍)을 뒤로 미루고 파이프라인부터 검증한다.

- **Phase 0** — rag-server `POST /ask/stream` SSE 엔드포인트 (선행, Python)
- **Phase 1** — 비동기 골격: 제출→202+jobId, `query:jobs` 발행, 워커가 **기존 블로킹 `ask()`로** 전체 답 생성 후 `answer:{jobId}`에 한 번에 XADD, SSE relay (타자기 효과 없이 파이프라인만 증명)
- **Phase 2** — RAG 호출을 스트리밍(`Flux`/SSE)으로 교체, 토큰별 XADD → SSE 토큰 relay (타자기 효과 가동)
- **Phase 3** — 재연결/재생(Last-Event-ID), DLQ + XAUTOCLAIM 복구, 폴링 폴백, 메트릭(큐 깊이 `XLEN`·대기 lag·생성시간·활성 SSE 수)
- **Phase 4** — `ChatPage` 프론트 재작성(EventSource), BFF SSE 프록시, 비동기 에러 UX

---

## 8. 트레이드오프 / 신규 복잡도

- Job 생명주기(`pending/running/done/failed`) 관리, 폴링 폴백, DLQ, 멱등 컨슈머 필요.
- 에러가 비동기화 → 동기 예외 대신 스트림 `error` 이벤트로 전달, 프론트가 해석.
- at-least-once 중복 가능 → QueryLog 저장에 jobId 멱등 키 필요.
- 테스트 난이도 상승(스트림/타임아웃/재연결 시나리오).
- Redis가 이력 핫패스에 포함 → Redis 가용성 의존도 상승.
- 추정 공수: 2~3주, 3개 서비스 동시 변경.

---

## 9. 미해결 / 결정 필요

- 워커 위치: backend 내 별도 컴포넌트로 둘지, 추후 독립 워커 프로세스로 분리할지.
- sources(출처) 전달 시점: 스트림 시작 메타 이벤트 vs 종료 이벤트.
- 토큰 청크 단위: 토큰 1개 vs N자 버퍼링(Redis XADD 횟수와 지연 트레이드오프).
- 잡 TTL/스트림 보존: `answer:{jobId}` EXPIRE 3600 적정성, 재연결 허용 시간.
</content>
</invoke>
